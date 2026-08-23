# FaceSync — Face Recognition Attendance App

**Capture • Sync • Secure**

FaceSync is an Android attendance tracking app that uses on-device face recognition with liveness detection to verify employee punch in/out, without needing a server or internet connection.

---

## Features

- **Face Recognition** — On-device face matching using a MobileFaceNet TFLite model (192-dimension embeddings), so no photos or biometric data ever leave the device.
- **Liveness Detection** — Randomized blink or smile challenge before every punch, to prevent spoofing with a photo or video.
- **Punch In / Punch Out** — Simple toggle to record attendance type, with duplicate-punch protection (can't punch IN twice in a row, or OUT twice in a row).
- **Voice Feedback** — Spoken confirmation on every successful punch ("Welcome, [Name]" / "See you, [Name]") via Android Text-to-Speech.
- **Employee Enrollment** — Register new employees by capturing their face and storing a local embedding.
- **Attendance History** — Search, filter by date or punch type, and review photo snapshots for every recorded punch.
- **Export & Share** — Export attendance records as CSV, PDF, or Excel (XLSX), and share directly via WhatsApp, email, or any installed app.
- **Auto Brightness** — Automatically boosts screen brightness in low light for better face detection.
- **Local Photo Backup** — Attendance snapshots are saved to the device gallery (`Pictures/AttendanceApp`) for audit purposes.

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose, Material 3 |
| Face Detection | Google ML Kit Face Detection |
| Face Recognition | TensorFlow Lite (MobileFaceNet, 192-d embeddings) |
| Camera | CameraX |
| Local Database | Room |
| Image Loading | Coil |
| Navigation | Jetpack Navigation Compose |
| Background Work | WorkManager |
| Excel Export | fastexcel |
| PDF Export | Android `PdfDocument` API |
| Language | Kotlin |

---

## Requirements

- Android Studio (latest stable)
- JDK 17
- Android SDK 35 (compile & target)
- Minimum SDK: 26 (Android 8.0+)
- A physical or emulated device with a front-facing camera

---

## Project Structure

```
app/src/main/java/com/app/faceattendance/
├── MainActivity.kt
├── AttendanceApplication.kt
├── data/
│   ├── local/          # Room entities & DAO
│   ├── ml/              # Face recognition, liveness detection, image analysis
│   ├── storage/         # Gallery image storage
│   └── backup/          # CSV / PDF / Excel export
└── presentation/
    ├── camera/           # Main punch-in/out camera screen
    ├── enroll/            # Employee enrollment screen
    ├── history/           # Attendance history & filtering
    └── feedback/          # Voice feedback (TTS)
```

---

## Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/masskingslin/Face-reader-mobile-Attandance-app-.git
   ```
2. Open in Android Studio.
3. Ensure `app/src/main/assets/mobile_facenet.tflite` contains a valid MobileFaceNet model (192-d output). This file is required for face recognition to function.
4. Build and run on a device with a front-facing camera.

### Required Permissions

| Permission | Purpose |
|---|---|
| `CAMERA` | Face capture for enrollment and attendance |

Camera permission is requested at runtime on first launch.

---

## CI/CD

This repo uses GitHub Actions (`.github/workflows/build_and_test.yml`) to automatically build a debug APK on every push to `main`, `master`, or `develop`, and on pull requests. The workflow:

1. Sets up JDK 17 and Gradle
2. Ensures the TFLite model asset is present
3. Builds the debug APK (`assembleDebug`)
4. Uploads the APK as a build artifact

---

## How It Works

1. **Enrollment** — An employee's face is captured, an embedding is generated via the TFLite model, and stored locally in Room.
2. **Recognition** — On the camera screen, ML Kit detects a face in each frame; if a face is present, a liveness challenge (blink or smile) is triggered.
3. **Verification** — Once liveness passes, the face embedding is compared against all enrolled users via cosine similarity. A match above the similarity threshold (0.72) triggers a punch.
4. **Recording** — The punch is checked against the employee's last record to prevent duplicates, then saved to the local database along with a photo snapshot.
