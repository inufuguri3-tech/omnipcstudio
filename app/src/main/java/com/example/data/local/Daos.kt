package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LiveMessageDao {
    @Query("SELECT * FROM live_messages ORDER BY timestamp ASC")
    fun observeAllMessages(): Flow<List<LiveMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: LiveMessageEntity): Long

    @Query("DELETE FROM live_messages")
    suspend fun clearAll()
}

@Dao
interface OperatorLogDao {
    @Query("SELECT * FROM operator_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentLogs(): Flow<List<OperatorLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: OperatorLogEntity): Long

    @Query("DELETE FROM operator_logs")
    suspend fun clearAll()
}
