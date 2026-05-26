package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.TransferRecord
import com.example.data.network.ConnectionUtils
import com.example.data.network.FileTransferManager
import com.example.data.network.NsdHelper
import com.example.data.repository.TransferRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class TransferViewModel(
    private val repository: TransferRepository,
    private val nsdHelper: NsdHelper,
    private val fileTransferManager: FileTransferManager,
    private val appContext: Context
) : ViewModel() {

    // Network status
    private val _localIp = MutableStateFlow("0.0.0.0")
    val localIp: StateFlow<String> = _localIp.asStateFlow()

    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected: StateFlow<Boolean> = _isWifiConnected.asStateFlow()

    val localDeviceName = ConnectionUtils.getDeviceName()

    // Discovery state
    val discoveredPeers = nsdHelper.discoveredPeers
    val isDiscovering = nsdHelper.isDiscovering

    // Socket Active state
    val activeTransfer = fileTransferManager.activeTransfer

    // History state
    val transferHistory: StateFlow<List<TransferRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Receive Mode switch status
    private val _isReceiveModeActive = MutableStateFlow(false)
    val isReceiveModeActive: StateFlow<Boolean> = _isReceiveModeActive.asStateFlow()

    init {
        refreshNetworkInfo()
        // Automatically start discovery on launch as a sender
        startPeerDiscovery()
    }

    fun refreshNetworkInfo() {
        _localIp.value = ConnectionUtils.getLocalIpAddress()
        _isWifiConnected.value = ConnectionUtils.isWifiConnected(appContext)
    }

    fun startPeerDiscovery() {
        viewModelScope.launch {
            nsdHelper.startDiscovery()
        }
    }

    fun stopPeerDiscovery() {
        nsdHelper.stopDiscovery()
    }

    fun toggleReceiveMode() {
        viewModelScope.launch {
            if (_isReceiveModeActive.value) {
                // Deactivate
                fileTransferManager.stopReceiverServer()
                nsdHelper.unregisterService()
                _isReceiveModeActive.value = false
                // Re-enable sender scanning
                startPeerDiscovery()
            } else {
                // Activate
                _isReceiveModeActive.value = true
                // Stop discovery scanning when in receiving mode to keep network quiet
                stopPeerDiscovery()

                // Boot TCP Server Socket
                launch(Dispatchers.IO) {
                    val port = fileTransferManager.startReceiverServer { fileName, fileSize, filePath ->
                        // Completed callback for downloaded files
                        viewModelScope.launch {
                            repository.insert(
                                TransferRecord(
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    direction = "RECEIVED",
                                    peerName = "WiFi Peer",
                                    status = "SUCCESS",
                                    filePath = filePath
                                )
                            )
                        }
                    }

                    if (port > 0) {
                        // Advertise Receiver port via DNS-SD/mDNS NSD
                        nsdHelper.registerService(port, localDeviceName)
                    } else {
                        _isReceiveModeActive.value = false
                    }
                }
            }
        }
    }

    fun sendFileToPeer(peer: NsdHelper.WifiPeer, uri: Uri) {
        viewModelScope.launch {
            val recordId = repository.insert(
                TransferRecord(
                    fileName = "Preparing...",
                    fileSize = 0,
                    direction = "SENT",
                    peerName = peer.name,
                    status = "PENDING"
                )
            )

            launch(Dispatchers.IO) {
                var fileName = ""
                var fileSize = 0L

                val success = fileTransferManager.sendFile(
                    peerIp = peer.ip,
                    peerPort = peer.port,
                    uri = uri,
                    destPeerName = peer.name
                ) { name, size ->
                    fileName = name
                    fileSize = size
                }

                val finalRecord = TransferRecord(
                    id = recordId.toInt(),
                    fileName = if (fileName.isEmpty()) "File Share" else fileName,
                    fileSize = fileSize,
                    direction = "SENT",
                    peerName = peer.name,
                    status = if (success) "SUCCESS" else "FAILED"
                )
                repository.update(finalRecord)
            }
        }
    }

    fun sendFileToManualIp(ip: String, portStr: String, uri: Uri) {
        val port = portStr.toIntOrNull() ?: 8888
        viewModelScope.launch {
            val recordId = repository.insert(
                TransferRecord(
                    fileName = "Preparing...",
                    fileSize = 0,
                    direction = "SENT",
                    peerName = ip,
                    status = "PENDING"
                )
            )

            launch(Dispatchers.IO) {
                var fileName = ""
                var fileSize = 0L

                val success = fileTransferManager.sendFile(
                    peerIp = ip,
                    peerPort = port,
                    uri = uri,
                    destPeerName = "IP Manual"
                ) { name, size ->
                    fileName = name
                    fileSize = size
                }

                val finalRecord = TransferRecord(
                    id = recordId.toInt(),
                    fileName = if (fileName.isEmpty()) "File Share" else fileName,
                    fileSize = fileSize,
                    direction = "SENT",
                    peerName = ip,
                    status = if (success) "SUCCESS" else "FAILED"
                )
                repository.update(finalRecord)
            }
        }
    }

    fun dismissActiveTransfer() {
        fileTransferManager.resetState()
    }

    fun deleteHistoryRecord(record: TransferRecord) {
        viewModelScope.launch {
            repository.delete(record)
            // Optionally delete physics file
            record.filePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun openCompletedFile(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return

            val authority = "${appContext.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(appContext, authority, file)

            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    class Factory(
        private val repository: TransferRepository,
        private val nsdHelper: NsdHelper,
        private val fileTransferManager: FileTransferManager,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TransferViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TransferViewModel(repository, nsdHelper, fileTransferManager, appContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
