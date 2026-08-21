package com.app.faceattendance.presentation.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class FeedbackManager(context: Context) : TextToSpeech.OnInitListener, AutoCloseable {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var isTtsReady = false

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("FeedbackManager", "TTS Language not supported.")
            } else {
                tts?.setSpeechRate(1.05f)
                tts?.setPitch(1.0f)
                isTtsReady = true
            }
        }
    }

    fun notifyPunchSuccess(userName: String, punchType: String) {
        triggerSuccessHaptic()
        val speechText = "Thank you $userName. Punch $punchType recorded."
        speak(speechText)
    }

    fun notifyPunchError(errorMessage: String = "Face not recognized. Please look at the camera.") {
        triggerErrorHaptic()
        speak(errorMessage)
    }

    private fun speak(text: String) {
        if (isTtsReady) {
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "ATTENDANCE_PUNCH_ID_${System.currentTimeMillis()}"
            )
        }
    }

    private fun triggerSuccessHaptic() {
        vibrator?.let { v ->
            if (!v.hasVibrator()) return@let

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0, 60, 80, 100)
                val amplitudes = intArrayOf(0, 180, 0, 255)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 60, 80, 100), -1)
            }
        }
    }

    private fun triggerErrorHaptic() {
        vibrator?.let { v ->
            if (!v.hasVibrator()) return@let

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
                v.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(300)
            }
        }
    }

    override fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}
