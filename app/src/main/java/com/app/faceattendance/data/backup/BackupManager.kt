package com.app.faceattendance.data.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.app.faceattendance.data.local.AttendanceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    suspend fun exportAndShareCsv(context: Context, dao: AttendanceDao) = withContext(Dispatchers.IO) {
        val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        val records = dao.getRecentAttendanceList(ninetyDaysAgo)

        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(cacheDir, "attendance_backup_${System.currentTimeMillis()}.csv")

        FileWriter(file).use { writer ->
            writer.append("ID,Employee ID,Name,Punch Type,Date,Time,Image URI\n")
            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            records.forEach { r ->
                val d = dateFmt.format(Date(r.timestamp))
                val t = timeFmt.format(Date(r.timestamp))
                writer.append("${r.id},\"${r.userId}\",\"${r.userName}\",${r.type},$d,$t,\"${r.imageUri}\"\n")
            }
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, "Share Attendance Backup"))
        }
    }
}
