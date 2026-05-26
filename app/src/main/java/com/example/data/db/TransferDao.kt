package com.example.data.db

import androidx.room.*
import com.example.data.model.TransferRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<TransferRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: TransferRecord): Long

    @Update
    suspend fun updateRecord(record: TransferRecord)

    @Delete
    suspend fun deleteRecord(record: TransferRecord)

    @Query("DELETE FROM transfer_records")
    suspend fun clearAll()
}
