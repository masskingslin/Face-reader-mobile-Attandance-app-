Face Attendance Android
An on-device, offline-first facial recognition attendance system built with Kotlin, Jetpack Compose, CameraX, ML Kit, TensorFlow Lite (MobileFaceNet), and Room DB.
Features
 * Automatic Facial Recognition: On-device identity matching using a 192-dimensional MobileFaceNet embedding model with Cosine Similarity scoring.
 * Active Liveness Detection: Anti-spoofing challenge-response state machine requiring interactive blinks or smiles before verifying attendance.
 * IN / OUT Punch Tracking: One-tap toggle for clocking in or out with automatic photo capture.
 * Scoped Storage Gallery Isolation: Automatically saves punch snapshot images to Pictures/AttendanceApp/ via the Android MediaStore API.
 * 90-Day Rolling Data Retention: SQLite database (Room) with an automated WorkManager background job that runs every 24 hours to purge expired records and image files older than 90 days.
 * CSV Export & Backup: Generates formatted attendance reports on demand and exports them through the Android System Share Sheet using FileProvider.
 * Hardware Integration:
   * Ambient light monitoring via SensorManager that triggers automatic full-screen brightness boosting in low-light environments.
   * Spoken audio confirmations via Android TextToSpeech.
   * Success/Error tactile feedback via multi-stage VibrationEffect waveforms.
 * Automated CI/CD: Complete GitHub Actions workflows for linting, unit testing, and building signed release APKs and Android App Bundles (.aab).
System Architecture
┌─────────────────────────────────────────────────────────────────────────────┐
│                             PRESENTATION LAYER                              │
│   CameraPreviewScreen    │    AttendanceHistoryScreen   │ UserEnrollScreen  │
│   • CameraX Viewport     │    • 90-Day Filter Engine    │ • Template Capture│
│   • Bounding Box Canvas  │    • User / Date Search      │ • Float Embedding │
│   • Liveness Guidance    │    • Snapshot Modal Dialog   │   Serialization   │
│   • IN / OUT Switcher    │    • CSV Export Action Sheet │                   │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ StateFlow / Events
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                           DOMAIN & ML VISION LAYER                          │
│   ┌────────────────────────┐ ┌──────────────────────┐ ┌──────────────────┐  │
│   │   Face Detection       │ │  Active Liveness     │ │ MobileFaceNet    │  │
│   │   (Google ML Kit)      │─▶  (Blink / Smile FSM) ─▶│ (TFLite 192-d)   │  │
│   └────────────────────────┘ └──────────────────────┘ └──────────────────┘  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                       DATA & HARDWARE SERVICES LAYER                        │
│  ┌───────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐  │
│  │   Room SQLite DB      │  │  MediaStore Service  │  │ Device Feedback  │  │
│  │   • User Templates    │  │  • Pictures/         │  │ • TTS Engine     │  │
│  │   • Attendance Logs   │  │    AttendanceApp/    │  │ • Waveform Haptic│  │
│  │   • 90-Day Auto Prune │  │  • Scoped Storage    │  │ • Light Sensor   │  │
│  └───────────────────────┘  └──────────────────────┘  └──────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────┘

Tech Stack & Dependencies
 * Language: Kotlin 1.9+
 * UI Framework: Jetpack Compose & Material 3
 * Camera Pipeline: CameraX (Core, Camera2, Lifecycle, View 1.3.4)
 * Vision & ML:
   * Google ML Kit Face Detection (16.1.7)
   * TensorFlow Lite Runtime + GPU Delegate (2.14.0)
 * Local Database: Room SQLite with Kotlin Coroutines & Flow (2.6.1)
 * Image Loading: Coil Compose (2.6.0)
 * Background Tasks: AndroidX WorkManager (2.9.0)
Directory Structure
face-attendance-android/
├── .github/
│   └── workflows/
│       ├── build_and_test.yml          # CI: PR & Branch build verification
│       └── release.yml                 # CD: Tagged release signer (APK + AAB)
├── app/
│   ├── proguard-rules.pro              # R8 minification rules for TFLite/ML Kit
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── assets/
│           │   └── mobile_facenet.tflite # 112x112 MobileFaceNet model file
│           ├── res/xml/
│           │   └── file_paths.xml      # FileProvider cache paths for CSV exports
│           ├── AndroidManifest.xml
│           └── java/com/app/faceattendance/
│               ├── AttendanceApplication.kt
│               ├── MainActivity.kt
│               ├── data/
│               │   ├── backup/BackupManager.kt
│               │   ├── local/
│               │   │   ├── AppDatabase.kt
│               │   │   ├── AttendanceDao.kt
│               │   │   ├── AttendanceRecordEntity.kt
│               │   │   └── UserEntity.kt
│               │   ├── ml/
│               │   │   ├── FaceNetModel.kt
│               │   │   ├── FaceRecognitionAnalyzer.kt
│               │   │   ├── ImageUtils.kt
│               │   │   └── LivenessDetector.kt
│               │   └── storage/GalleryStorageManager.kt
│               ├── presentation/
│               │   ├── camera/
│               │   │   ├── AutoBrightnessEffect.kt
│               │   │   └── CameraScreen.kt
│               │   ├── enroll/UserEnrollmentScreen.kt
│               │   ├── feedback/FeedbackManager.kt
│               │   └── history/
│               │       ├── AttendanceHistoryScreen.kt
│               │       └── AttendanceHistoryViewModel.kt
│               └── worker/DataPruningWorker.kt
├── build.gradle.kts
└── settings.gradle.kts

Setup & Installation
1. Prerequisites
 * Android Studio Iguana (2023.2.1) or newer
 * JDK 17
 * Android Device or Emulator running Android 8.0 (API level 26) or higher with a working camera
2. Clone the Repository
git clone https://github.com/<your-username>/face-attendance-android.git
cd face-attendance-android

3. Add the TFLite Model Asset
Ensure the MobileFaceNet weight file is placed inside the assets directory:
app/src/main/assets/mobile_facenet.tflite

4. Build and Run
# Debug build and install to connected device
./gradlew installDebug

GitHub Actions Release Automation
To generate signed releases via GitHub Actions:
 * Base64-encode your release keystore:
   base64 -w 0 my-release-key.jks > keystore_base64.txt

 * In your GitHub repository, navigate to Settings → Secrets and variables → Actions and add:
   * SIGNING_KEY: Paste the base64 string from keystore_base64.txt.
   * KEY_STORE_PASSWORD: Keystore password.
   * ALIAS: Key alias name.
   * KEY_PASSWORD: Key alias password.
 * Trigger an automated release by pushing a version tag:
   git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0

 * The workflow will build, sign, and upload FaceAttendance-v1.0.0.apk, FaceAttendance-v1.0.0.aab, and the ProGuard mapping-v1.0.0.txt file directly to the GitHub Releases page.
License