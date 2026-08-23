package com.app.faceattendance.presentation.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class FeedbackManager(context: Context) : TextToSpeech.OnInitListener, AutoCloseable {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private var isTtsReady = false

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
        val speechText = if (punchType.equals("IN", ignoreCase = true)) {
            "Welcome, $userName"
        } else {
            "See you, $userName"
        }
        speak(speechText)
    }

    fun notifyPunchError(errorMessage: String = "Face not recognized. Please look at the camera.") {
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

    override fun close() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }
}