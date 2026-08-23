package com.app.faceattendance.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users ORDER BY name ASC")
    suspend fun getAllUsers(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecordEntity): Long

    @Query("SELECT * FROM attendance_records WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getRecentAttendance(sinceTimestamp: Long): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM attendance_records WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    suspend fun getRecentAttendanceList(sinceTimestamp: Long): List<AttendanceRecordEntity>

    @Query("SELECT * FROM attendance_records WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastRecordForUser(userId: String): AttendanceRecordEntity?

    @Query("SELECT imageUri FROM attendance_records WHERE timestamp < :cutoffTimestamp")
    suspend fun getExpiredImageUris(cutoffTimestamp: Long): List<String>

    @Query("DELETE FROM attendance_records WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteExpiredRecords(cutoffTimestamp: Long): Int
}