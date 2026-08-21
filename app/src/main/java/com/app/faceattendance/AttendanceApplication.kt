package com.app.faceattendance

import android.app.Application
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.app.faceattendance.data.local.AppDatabase
import com.app.faceattendance.data.ml.FaceNetModel
import com.app.faceattendance.presentation.feedback.FeedbackManager
import com.app.faceattendance.worker.DataPruningWorker
import java.util.concurrent.TimeUnit

class AttendanceApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var faceNetModel: FaceNetModel
        private set

    lateinit var feedbackManager: FeedbackManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "face_attendance.db"
        ).build()

        faceNetModel = FaceNetModel(this)
        feedbackManager = FeedbackManager(this)

        scheduleDailyDataPruning()
    }

    private fun scheduleDailyDataPruning() {
        val pruneRequest = PeriodicWorkRequestBuilder<DataPruningWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "Attendance90DayPruning",
            ExistingPeriodicWorkPolicy.KEEP,
            pruneRequest
        )
    }

    override fun onTerminate() {
        super.onTerminate()
        faceNetModel.close()
        feedbackManager.close()
    }
}
