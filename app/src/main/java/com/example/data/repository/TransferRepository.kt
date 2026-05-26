package com.example.data.repository

import com.example.data.db.TransferDao
import com.example.data.model.TransferRecord
import kotlinx.coroutines.flow.Flow

class TransferRepository(private val transferDao: TransferDao) {
    val allRecords: Flow<List<TransferRecord>> = transferDao.getAllRecords()

    suspend fun insert(record: TransferRecord): Long = transferDao.insertRecord(record)

    suspend fun update(record: TransferRecord) = transferDao.updateRecord(record)

    suspend fun delete(record: TransferRecord) = transferDao.deleteRecord(record)

    suspend fun clearAll() = transferDao.clearAll()
}
