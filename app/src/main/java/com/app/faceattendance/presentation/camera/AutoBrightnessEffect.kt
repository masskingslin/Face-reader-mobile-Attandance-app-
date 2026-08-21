package com.app.faceattendance.presentation.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun AutoBrightnessEffect(
    lowLightThresholdLux: Float = 15f,
    restoreThresholdLux: Float = 40f
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return

    DisposableEffect(activity) {
        val sensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        var isMaxBrightnessActive = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val currentLux = it.values[0]

                    if (currentLux < lowLightThresholdLux && !isMaxBrightnessActive) {
                        setWindowBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL)
                        isMaxBrightnessActive = true
                    } else if (currentLux > restoreThresholdLux && isMaxBrightnessActive) {
                        setWindowBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
                        isMaxBrightnessActive = false
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (lightSensor != null) {
            sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            if (lightSensor != null) {
                sensorManager.unregisterListener(listener)
            }
            setWindowBrightness(activity, WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE)
        }
    }
}

private fun setWindowBrightness(activity: Activity, brightness: Float) {
    val layoutParams = activity.window.attributes
    layoutParams.screenBrightness = brightness
    activity.window.attributes = layoutParams
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}
