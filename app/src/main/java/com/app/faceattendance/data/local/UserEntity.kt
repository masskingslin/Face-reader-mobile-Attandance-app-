package com.app.faceattendance.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val embedding: String // Serialized comma-separated 192-d float array
)
