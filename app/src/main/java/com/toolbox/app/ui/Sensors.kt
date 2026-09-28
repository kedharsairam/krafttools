package com.toolbox.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The pattern every tool reuses: lifecycle-aware foreground listener.
 * Registers on compose, unregisters on dispose. No background work,
 * no permissions needed for accelerometer/mag/light/baro/proximity.
 *
 * @param type one of [Sensor.TYPE_*]; null when the device lacks it,
 *   in which case [reading] stays null and the tool shows its gate.
 * @param delay one of [SensorManager.SENSOR_DELAY_*].
 */
@Composable
fun rememberSensor(
    type: Int,
    delay: Int = SensorManager.SENSOR_DELAY_UI,
): State<FloatArray?> {
    val context = LocalContext.current
    val reading = remember { mutableStateOf<FloatArray?>(null) }
    DisposableEffect(type) {
        val manager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(type)
        if (sensor == null) {
            onDispose { }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    reading.value = event.values.clone()
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                    // Tools that care (compass/mag) read accuracy live
                    // via their own listener; the scaffold stays simple.
                }
            }
            manager.registerListener(listener, sensor, delay)
            onDispose { manager.unregisterListener(listener) }
        }
    }
    return reading
}

/** Pitch/roll in degrees from a gravity vector (accelerometer). */
fun pitchRoll(gravity: FloatArray): Pair<Float, Float> {
    val ax = gravity[0].toDouble()
    val ay = gravity[1].toDouble()
    val az = gravity[2].toDouble()
    val pitch = Math.toDegrees(Math.atan2(-ax, Math.hypot(ay, az))).toFloat()
    val roll = Math.toDegrees(Math.atan2(ay, Math.hypot(ax, az))).toFloat()
    return pitch to roll
}
