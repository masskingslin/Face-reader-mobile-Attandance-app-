package com.app.faceattendance.data.ml

import com.google.mlkit.vision.face.Face

enum class LivenessChallenge(val prompt: String) {
    BLINK("Please Blink your eyes"),
    SMILE("Please Smile for the camera")
}

sealed class LivenessStatus {
    data object Idle : LivenessStatus()
    data class InProgress(val challenge: LivenessChallenge, val progressMessage: String) : LivenessStatus()
    data object Passed : LivenessStatus()
    data class Failed(val reason: String) : LivenessStatus()
}

class LivenessDetector(
    private val challengeTimeoutMs: Long = 5000L
) {
    private var currentChallenge: LivenessChallenge = LivenessChallenge.BLINK
    private var challengeStartTime: Long = 0L

    private var blinkStage = 0
    private var smileStage = 0

    var isLivenessPassed: Boolean = false
        private set

    init {
        resetChallenge()
    }

    fun resetChallenge() {
        currentChallenge = if (Math.random() > 0.5) LivenessChallenge.BLINK else LivenessChallenge.SMILE
        challengeStartTime = System.currentTimeMillis()
        blinkStage = 0
        smileStage = 0
        isLivenessPassed = false
    }

    fun processFace(face: Face): LivenessStatus {
        if (isLivenessPassed) return LivenessStatus.Passed

        if (System.currentTimeMillis() - challengeStartTime > challengeTimeoutMs) {
            resetChallenge()
            return LivenessStatus.Failed("Liveness check timed out. Please try again.")
        }

        if (face.headEulerAngleY !in -12f..12f || face.headEulerAngleZ !in -12f..12f) {
            return LivenessStatus.InProgress(currentChallenge, "Please face the camera directly")
        }

        val leftEyeOpen = face.leftEyeOpenProbability ?: -1f
        val rightEyeOpen = face.rightEyeOpenProbability ?: -1f
        val smileProb = face.smilingProbability ?: -1f

        return when (currentChallenge) {
            LivenessChallenge.BLINK -> handleBlinkChallenge(leftEyeOpen, rightEyeOpen)
            LivenessChallenge.SMILE -> handleSmileChallenge(smileProb)
        }
    }

    private fun handleBlinkChallenge(leftEye: Float, rightEye: Float): LivenessStatus {
        if (leftEye < 0f || rightEye < 0f) {
            return LivenessStatus.InProgress(LivenessChallenge.BLINK, "Position eyes clearly in frame")
        }

        val eyesOpen = leftEye > 0.70f && rightEye > 0.70f
        val eyesClosed = leftEye < 0.25f && rightEye < 0.25f

        when (blinkStage) {
            0 -> if (eyesOpen) blinkStage = 1
            1 -> if (eyesClosed) blinkStage = 2
            2 -> {
                if (eyesOpen) {
                    isLivenessPassed = true
                    return LivenessStatus.Passed
                }
            }
        }

        return LivenessStatus.InProgress(LivenessChallenge.BLINK, LivenessChallenge.BLINK.prompt)
    }

    private fun handleSmileChallenge(smileProb: Float): LivenessStatus {
        if (smileProb < 0f) {
            return LivenessStatus.InProgress(LivenessChallenge.SMILE, "Position face in frame")
        }

        when (smileStage) {
            0 -> if (smileProb < 0.30f) smileStage = 1
            1 -> {
                if (smileProb > 0.70f) {
                    isLivenessPassed = true
                    return LivenessStatus.Passed
                }
            }
        }

        return LivenessStatus.InProgress(LivenessChallenge.SMILE, LivenessChallenge.SMILE.prompt)
    }
}
