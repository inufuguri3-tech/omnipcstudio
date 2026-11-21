package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "live_messages")
data class LiveMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val role: String, // "USER" or "AI"
    val mode: String, // LiveProMode.name
    val text: String,
    val hasVisualContext: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "operator_logs")
data class OperatorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val goal: String,
    val stepNumber: Int,
    val actionType: String,
    val targetSummary: String,
    val reasoning: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
