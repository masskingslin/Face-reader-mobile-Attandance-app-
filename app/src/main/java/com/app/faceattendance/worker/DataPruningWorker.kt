package com.app.faceattendance.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.faceattendance.AttendanceApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DataPruningWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val dao = (applicationContext as AttendanceApplication).database.attendanceDao()
            val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)

            val expiredUris = dao.getExpiredImageUris(ninetyDaysAgo)
            expiredUris.forEach { uriStr ->
                try {
                    applicationContext.contentResolver.delete(Uri.parse(uriStr), null, null)
                } catch (_: Exception) {}
            }

            dao.deleteExpiredRecords(ninetyDaysAgo)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
