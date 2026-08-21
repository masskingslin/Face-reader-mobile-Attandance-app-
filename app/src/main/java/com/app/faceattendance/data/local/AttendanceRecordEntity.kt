package com.app.faceattendance.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_records",
    indices = [
        Index("timestamp"),
        Index("userId")
    ]
)
data class AttendanceRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val userName: String,
    val type: String, // "IN" or "OUT"
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String
)
