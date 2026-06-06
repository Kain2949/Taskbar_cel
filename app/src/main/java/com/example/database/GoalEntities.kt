package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val targetAmount: Double,
    val startDateMillis: Long,
    val targetDateMillis: Long?, // Nullable if no end date
    val isArchived: Boolean = false
)

@Entity(
    tableName = "progress_logs",
    indices = [Index(value = ["goalId", "dateMillis"], unique = true)]
)
data class ProgressLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val goalId: Long,
    val dateMillis: Long, // Normalized to midnight
    val accumulatedValue: Double
)
