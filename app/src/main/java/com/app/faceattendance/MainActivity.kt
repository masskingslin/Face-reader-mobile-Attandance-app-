package com.app.faceattendance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.app.faceattendance.data.backup.BackupManager
import com.app.faceattendance.data.local.AttendanceRecordEntity
import com.app.faceattendance.data.storage.GalleryStorageManager
import com.app.faceattendance.presentation.camera.CameraScreen
import com.app.faceattendance.presentation.enroll.ManageUsersScreen
import com.app.faceattendance.presentation.enroll.UserEnrollmentScreen
import com.app.faceattendance.presentation.history.AttendanceHistoryScreen
import com.app.faceattendance.presentation.history.AttendanceHistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Camera permission is required for face reader.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val sw = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(sw))
            getSharedPreferences("crash_prefs", MODE_PRIVATE)
                .edit()
                .putString("last_crash", sw.toString())
                .apply()
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        val crashPrefs = getSharedPreferences("crash_prefs", MODE_PRIVATE)
        val lastCrash = crashPrefs.getString("last_crash", null)
        if (lastCrash != null) {
            crashPrefs.edit().remove("last_crash").apply()
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("crash log", lastCrash))
            Toast.makeText(this, "Crash log copied to clipboard — paste it to Claude", Toast.LENGTH_LONG).show()
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        val app = application as AttendanceApplication
        val dao = app.database.attendanceDao()
        val faceNet = app.faceNetModel
        val feedbackManager = app.feedbackManager

        lifecycleScope.launch(Dispatchers.IO) {
            val users = dao.getAllUsers()
            faceNet.loadUsers(users)
        }

        setContent {
            MaterialTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "camera") {
                    composable("camera") {
                        CameraScreen(
                            faceNetModel = faceNet,
                            onAttendancePunched = { userId, name, type, frame ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    try {
                                        val lastRecord = dao.getLastRecordForUser(userId)

                                        if (lastRecord?.type.equals(type.name, ignoreCase = true)) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "⚠️ $name is already Punched ${type.name}",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                            return@launch
                                        }

                                        val uri = GalleryStorageManager.saveAttendanceImage(
                                            this@MainActivity,
                                            frame,
                                            userId,
                                            type.name
                                        )

                                        val record = AttendanceRecordEntity(
                                            userId = userId,
                                            userName = name,
                                            type = type.name,
                                            imageUri = uri?.toString() ?: ""
                                        )
                                        dao.insertAttendance(record)

                                        withContext(Dispatchers.Main) {
                                            feedbackManager.notifyPunchSuccess(userName = name, punchType = type.name)
                                            Toast.makeText(
                                                this@MainActivity,
                                                "✅ Punch ${type.name} Verified — $name",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        val sw = java.io.StringWriter()
                                        e.printStackTrace(java.io.PrintWriter(sw))
                                        getSharedPreferences("crash_prefs", MODE_PRIVATE)
                                            .edit()
                                            .putString("last_crash", "PUNCH FLOW ERROR:\n" + sw.toString())
                                            .apply()

                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "⚠️ Could not save attendance. Please try again.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            },
                            onNavigateToHistory = { navController.navigate("history") },
                            onNavigateToEnroll = { navController.navigate("enroll") },
                            onNavigateToManageUsers = { navController.navigate("manageUsers") }
                        )
                    }

                    composable("enroll") {
                        UserEnrollmentScreen(
                            faceNetModel = faceNet,
                            onUserEnrolled = { newUser ->
                                lifecycleScope.launch(Dispatchers.IO) {
                                    dao.insertUser(newUser)
                                    val users = dao.getAllUsers()
                                    faceNet.loadUsers(users)

                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(this@MainActivity, "User ${newUser.name} Enrolled!", Toast.LENGTH_SHORT).show()
                                        navController.popBackStack()
                                    }
                                }
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("manageUsers") {
                        ManageUsersScreen(
                            dao = dao,
                            faceNetModel = faceNet,
                            onNavigateToEnroll = { navController.navigate("enroll") },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("history") {
                        val historyViewModel = remember { AttendanceHistoryViewModel(dao) }
                        AttendanceHistoryScreen(
                            viewModel = historyViewModel,
                            onExportCsv = {
                                lifecycleScope.launch {
                                    BackupManager.exportAndShareCsv(this@MainActivity, dao)
                                }
                            },
                            onExportPdf = {
                                lifecycleScope.launch {
                                    BackupManager.exportAndSharePdf(this@MainActivity, dao)
                                }
                            },
                            onExportExcel = {
                                lifecycleScope.launch {
                                    BackupManager.exportAndShareExcel(this@MainActivity, dao)
                                }
                            },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}