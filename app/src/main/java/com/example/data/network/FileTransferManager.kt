package com.example.data.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.ServerSocket
import java.net.Socket

class FileTransferManager(private val context: Context) {

    private val tag = "FileTransferManager"
    private var serverSocket: ServerSocket? = null
    private var isReceiverRunning = false

    data class ActiveTransferState(
        val isTransferring: Boolean = false,
        val fileName: String = "",
        val fileSize: Long = 0,
        val bytesTransferred: Long = 0,
        val progress: Float = 0F,
        val speedMbps: Double = 0.0,
        val isSender: Boolean = false,
        val peerName: String = "",
        val status: String = "IDLE", // "IDLE", "CONNECTING", "TRANSFERRING", "DONE", "ERROR"
        val errorMessage: String? = null,
        val completedFilePath: String? = null
    )

    private val _activeTransfer = MutableStateFlow(ActiveTransferState())
    val activeTransfer: StateFlow<ActiveTransferState> = _activeTransfer.asStateFlow()

    fun resetState() {
        _activeTransfer.value = ActiveTransferState()
    }

    suspend fun startReceiverServer(onFileReceived: (fileName: String, fileSize: Long, filePath: String) -> Unit): Int = withContext(Dispatchers.IO) {
        try {
            // Bind to zero to choose an available ephemeral port
            val socket = ServerSocket(0)
            serverSocket = socket
            isReceiverRunning = true
            val chosenPort = socket.localPort
            Log.d(tag, "Server started on port: $chosenPort")

            while (isReceiverRunning) {
                try {
                    val clientSocket = socket.accept()
                    Log.d(tag, "Client connected: ${clientSocket.inetAddress.hostAddress}")
                    handleIncomingConnection(clientSocket, onFileReceived)
                } catch (e: Exception) {
                    if (isReceiverRunning) {
                        Log.e(tag, "Exception accepting connection", e)
                    }
                }
            }
            chosenPort
        } catch (e: Exception) {
            Log.e(tag, "Failed to start server socket", e)
            -1
        }
    }

    fun stopReceiverServer() {
        isReceiverRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(tag, "Error closing server socket", e)
        }
        serverSocket = null
    }

    private suspend fun handleIncomingConnection(
        socket: Socket,
        onFileReceived: (fileName: String, fileSize: Long, filePath: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var dis: DataInputStream? = null
        var bos: FileOutputStream? = null

        try {
            _activeTransfer.value = ActiveTransferState(
                isTransferring = true,
                status = "CONNECTING",
                isSender = false,
                peerName = socket.inetAddress.hostAddress ?: "Sender"
            )

            dis = DataInputStream(BufferedInputStream(socket.getInputStream()))

            // Read metadata
            val fileName = dis.readUTF()
            val fileSize = dis.readLong()
            Log.d(tag, "Receiving file: $fileName, Size: $fileSize bytes")

            // Create WifiShare directory in internal downloads folder
            val downloadDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "WifiShare")
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            // Ensure unique filename
            var destinationFile = File(downloadDir, fileName)
            var count = 1
            val baseName = destinationFile.nameWithoutExtension
            val extension = destinationFile.extension
            while (destinationFile.exists()) {
                destinationFile = File(downloadDir, "$baseName-$count.$extension")
                count++
            }

            bos = FileOutputStream(destinationFile)
            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            val startTime = System.currentTimeMillis()

            _activeTransfer.value = ActiveTransferState(
                isTransferring = true,
                fileName = fileName,
                fileSize = fileSize,
                bytesTransferred = 0,
                progress = 0F,
                speedMbps = 0.0,
                isSender = false,
                peerName = socket.inetAddress.hostAddress ?: "Sender",
                status = "TRANSFERRING"
            )

            while (totalBytesRead < fileSize) {
                val sizeToRead = minOf(buffer.size.toLong(), fileSize - totalBytesRead).toInt()
                val bytesRead = dis.read(buffer, 0, sizeToRead)
                if (bytesRead == -1) {
                    break
                }
                bos.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                val now = System.currentTimeMillis()
                val duration = (now - startTime) / 1000.0
                val speedMbps = if (duration > 0) {
                    (totalBytesRead * 8.0) / (1024.0 * 1024.0 * duration)
                } else {
                    0.0
                }

                _activeTransfer.value = ActiveTransferState(
                    isTransferring = true,
                    fileName = fileName,
                    fileSize = fileSize,
                    bytesTransferred = totalBytesRead,
                    progress = totalBytesRead.toFloat() / fileSize.toFloat(),
                    speedMbps = speedMbps,
                    isSender = false,
                    peerName = socket.inetAddress.hostAddress ?: "Sender",
                    status = "TRANSFERRING"
                )
            }

            bos.flush()
            
            if (totalBytesRead == fileSize) {
                Log.d(tag, "File received successfully: ${destinationFile.absolutePath}")
                _activeTransfer.value = ActiveTransferState(
                    isTransferring = true,
                    fileName = fileName,
                    fileSize = fileSize,
                    bytesTransferred = totalBytesRead,
                    progress = 1F,
                    speedMbps = 0.0,
                    isSender = false,
                    peerName = socket.inetAddress.hostAddress ?: "Sender",
                    status = "DONE",
                    completedFilePath = destinationFile.absolutePath
                )
                onFileReceived(fileName, fileSize, destinationFile.absolutePath)
            } else {
                throw Exception("Connection interrupted, file incomplete. Managed to read $totalBytesRead of $fileSize")
            }

        } catch (e: Exception) {
            Log.e(tag, "Error receiving file", e)
            _activeTransfer.value = ActiveTransferState(
                isTransferring = false,
                status = "ERROR",
                isSender = false,
                errorMessage = e.localizedMessage ?: "Unknown file receipt error"
            )
        } finally {
            try {
                bos?.close()
                dis?.close()
                socket.close()
            } catch (e: Exception) {
                // Ignore final closures
            }
        }
    }

    suspend fun sendFile(
        peerIp: String,
        peerPort: Int,
        uri: Uri,
        destPeerName: String,
        onFileSent: (fileName: String, fileSize: Long) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        var dos: DataOutputStream? = null
        var fis: java.io.InputStream? = null

        try {
            _activeTransfer.value = ActiveTransferState(
                isTransferring = true,
                status = "CONNECTING",
                isSender = true,
                peerName = destPeerName
            )

            // Dynamic URI query for metadata
            var fileName = "unamed_file"
            var fileSize = 0L
            
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex) ?: "file"
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            Log.d(tag, "Sending file: $fileName, size: $fileSize to $peerIp:$peerPort")

            socket = Socket(peerIp, peerPort)
            dos = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

            // Write metadata headers
            dos.writeUTF(fileName)
            dos.writeLong(fileSize)
            dos.flush()

            fis = context.contentResolver.openInputStream(uri) ?: throw Exception("Failed to open Uri stream")
            val buffer = ByteArray(8192)
            var totalBytesSent = 0L
            val startTime = System.currentTimeMillis()

            _activeTransfer.value = ActiveTransferState(
                isTransferring = true,
                fileName = fileName,
                fileSize = fileSize,
                bytesTransferred = 0,
                progress = 0F,
                speedMbps = 0.0,
                isSender = true,
                peerName = destPeerName,
                status = "TRANSFERRING"
            )

            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                dos.write(buffer, 0, bytesRead)
                totalBytesSent += bytesRead

                val now = System.currentTimeMillis()
                val duration = (now - startTime) / 1000.0
                val speedMbps = if (duration > 0) {
                    (totalBytesSent * 8.0) / (1024.0 * 1024.0 * duration)
                } else {
                    0.0
                }

                _activeTransfer.value = ActiveTransferState(
                    isTransferring = true,
                    fileName = fileName,
                    fileSize = fileSize,
                    bytesTransferred = totalBytesSent,
                    progress = if (fileSize > 0) totalBytesSent.toFloat() / fileSize.toFloat() else 1F,
                    speedMbps = speedMbps,
                    isSender = true,
                    peerName = destPeerName,
                    status = "TRANSFERRING"
                )
            }

            dos.flush()
            Log.d(tag, "File sent successfully")

            _activeTransfer.value = ActiveTransferState(
                isTransferring = true,
                fileName = fileName,
                fileSize = fileSize,
                bytesTransferred = totalBytesSent,
                progress = 1F,
                speedMbps = 0.0,
                isSender = true,
                peerName = destPeerName,
                status = "DONE"
            )

            onFileSent(fileName, fileSize)
            true
        } catch (e: Exception) {
            Log.e(tag, "Error sending file", e)
            _activeTransfer.value = ActiveTransferState(
                isTransferring = false,
                status = "ERROR",
                isSender = true,
                errorMessage = e.localizedMessage ?: "Unknown file transmission error"
            )
            false
        } finally {
            try {
                fis?.close()
                dos?.close()
                socket?.close()
            } catch (e: Exception) {
                // Ignore final closures
            }
        }
    }
}
