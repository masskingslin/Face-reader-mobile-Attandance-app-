package com.app.faceattendance.data.backup

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.app.faceattendance.data.local.AttendanceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.Workbook
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val WINDOW_DAYS = 90L
    private const val WINDOW_MS = WINDOW_DAYS * 24 * 60 * 60 * 1000

    // ---------- CSV ----------

    suspend fun exportAndShareCsv(context: Context, dao: AttendanceDao) = withContext(Dispatchers.IO) {
        val records = dao.getRecentAttendanceList(System.currentTimeMillis() - WINDOW_MS)

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

        shareFile(context, file, "text/csv", "Share Attendance Backup (CSV)")
    }

    // ---------- PDF ----------

    suspend fun exportAndSharePdf(context: Context, dao: AttendanceDao) = withContext(Dispatchers.IO) {
        val records = dao.getRecentAttendanceList(System.currentTimeMillis() - WINDOW_MS)

        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(cacheDir, "attendance_backup_${System.currentTimeMillis()}.pdf")

        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val marginLeft = 32f
        val rowHeight = 20f
        val rowsPerPage = 34

        val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
        val headerPaint = Paint().apply { textSize = 10f; isFakeBoldText = true }
        val cellPaint = Paint().apply { textSize = 9f }

        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val columns = listOf("ID", "Employee ID", "Name", "Type", "Date & Time")
        val columnX = listOf(marginLeft, marginLeft + 40f, marginLeft + 120f, marginLeft + 280f, marginLeft + 340f)

        var recordIndex = 0
        var pageNumber = 1

        while (recordIndex < records.size || recordIndex == 0) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            var y = 40f
            canvas.drawText("Attendance Report", marginLeft, y, titlePaint)
            y += 24f
            canvas.drawText("Generated: ${dateFmt.format(Date())}", marginLeft, y, cellPaint)
            y += 20f

            columns.forEachIndexed { i, col -> canvas.drawText(col, columnX[i], y, headerPaint) }
            y += rowHeight

            var rowsOnPage = 0
            while (recordIndex < records.size && rowsOnPage < rowsPerPage) {
                val r = records[recordIndex]
                canvas.drawText(r.id.toString(), columnX[0], y, cellPaint)
                canvas.drawText(r.userId, columnX[1], y, cellPaint)
                canvas.drawText(r.userName.take(20), columnX[2], y, cellPaint)
                canvas.drawText(r.type, columnX[3], y, cellPaint)
                canvas.drawText(dateFmt.format(Date(r.timestamp)), columnX[4], y, cellPaint)
                y += rowHeight
                recordIndex++
                rowsOnPage++
            }

            pdfDocument.finishPage(page)
            pageNumber++

            if (records.isEmpty()) break
        }

        FileOutputStream(file).use { out -> pdfDocument.writeTo(out) }
        pdfDocument.close()

        shareFile(context, file, "application/pdf", "Share Attendance Backup (PDF)")
    }

    // ---------- Excel / XLSX ----------

    suspend fun exportAndShareExcel(context: Context, dao: AttendanceDao) = withContext(Dispatchers.IO) {
        val records = dao.getRecentAttendanceList(System.currentTimeMillis() - WINDOW_MS)

        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(cacheDir, "attendance_backup_${System.currentTimeMillis()}.xlsx")

        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        FileOutputStream(file).use { out ->
            val wb = Workbook(out, "FaceSync Attendance App", "1.0")
            val ws = wb.newWorksheet("Attendance")

            val headers = listOf("ID", "Employee ID", "Name", "Punch Type", "Date", "Time", "Image URI")
            headers.forEachIndexed { col, header -> ws.value(0, col, header) }

            records.forEachIndexed { rowIdx, r ->
                val row = rowIdx + 1
                ws.value(row, 0, r.id)
                ws.value(row, 1, r.userId)
                ws.value(row, 2, r.userName)
                ws.value(row, 3, r.type)
                ws.value(row, 4, dateFmt.format(Date(r.timestamp)))
                ws.value(row, 5, timeFmt.format(Date(r.timestamp)))
                ws.value(row, 6, r.imageUri)
            }

            wb.finish()
        }

        shareFile(
            context,
            file,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "Share Attendance Backup (Excel)"
        )
    }

    // ---------- shared helper ----------

    private suspend fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) =
        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
        }
}