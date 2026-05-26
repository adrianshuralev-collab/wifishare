package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transfer_records")
data class TransferRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileName: String,
    val fileSize: Long,
    val direction: String, // "SENT" or "RECEIVED"
    val peerName: String,
    val status: String, // "SUCCESS", "FAILED"
    val timestamp: Long = System.currentTimeMillis(),
    val filePath: String? = null // local path to file if received
)
