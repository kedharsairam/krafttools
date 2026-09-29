package com.krafttools.app.ui

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
 * The pattern every tool reuses: a foreground sensor listener.
 *
 * It was documented as "lifecycle-aware" and was not. It registered on
 * composition and unregistered on disposal, which covers navigation away
 * from a tool but NOT the app being backgrounded: a listener stayed live
 * with the screen off, draining battery for a reading nobody could see.
 * `PermGate` already had the correct pattern — a `LifecycleEventObserver`
 * on ON_RESUME — and this now does too.
 *
 * `registerListener` also returns a `Boolean`, and it was discarded. A
 * `false` means the sensor will never deliver — a HAL that refuses the
 * requested rate, or a device that has the sensor but will not stream it
 * — and the tool then shows a permanently blank reading with no
 * explanation, which is indistinguishable from a broken app. The result
 * is reported through [SensorReading.failed].
 *
 * @param type one of [Sensor.TYPE_*].
 * @param delay one of [SensorManager.SENSOR_DELAY_*]. Never pass
 *   SENSOR_DELAY_FASTEST (0 microseconds): modern Android throws
 *   SecurityException without HIGH_SAMPLING_RATE_SENSORS. GAME rate
 *   (≈50 Hz) is plenty for every viewer tool here.
 */
@Composable
fun rememberSensor(
    type: Int,
    delay: Int = SensorManager.SENSOR_DELAY_UI,
): SensorReading = rememberSensorReading(type, delay)

/** The state object returned by [rememberSensorReading]. */

/**
 * A sensor's latest sample, or the reason there is not one.
 *
 * Distinguishing "no value yet" from "this will never work" matters: the
 * first is a spinner, the second is an explanation.
 */
class SensorReading {
    /** Set by the composable that owns this reading, to redraw on it. */
    internal var onChange: () -> Unit = {}
    var values: FloatArray? = null
        private set

    /** The device does not have this sensor at all. */
    var absent: Boolean = false
        private set

    /** The sensor exists but refused to stream. */
    var failed: Boolean = false
        private set

    /** Most recent accuracy report, or null if the sensor gave none. */
    var accuracy: Int? = null
        private set

    internal fun deliver(v: FloatArray) {
        values = v
        onChange()
    }

    internal fun reportAccuracy(a: Int) {
        accuracy = a
        onChange()
    }

    internal fun markAbsent() {
        absent = true
        onChange()
    }

    internal fun markFailed() {
        failed = true
        onChange()
    }

    /** True once a usable value has ever arrived. */
    val hasReading: Boolean get() = values != null

    /** Why there is no reading, in words a user can act on. */
    val problem: String?
        get() = when {
            absent -> "This phone has no such sensor."
            failed ->
                "The sensor would not start. It exists, but the driver " +
                    "declined to stream from it."
            else -> null
        }
}

@Composable
fun rememberSensorReading(
    type: Int,
    delay: Int = SensorManager.SENSOR_DELAY_UI,
): SensorReading {
    val context = LocalContext.current
    val reading = remember { SensorReading() }
    // Subscribe to every mutation: a sensor that fails and a sensor that
    // has not started yet must both be able to redraw. The state object
    // is read below purely to establish the dependency.
    val tick = remember { mutableStateOf(0) }
    reading.onChange = { tick.value++ }
    require(tick.value >= 0)

    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(type, owner) {
        val manager =
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(type)
        var listener: SensorEventListener? = null

        fun unregister() {
            listener?.let { manager.unregisterListener(it) }
            listener = null
        }

        if (sensor == null) {
            reading.markAbsent()
            onDispose { }
        } else {
            val l = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    reading.deliver(event.values.clone())
                }

                override fun onAccuracyChanged(sensor: Sensor, a: Int) {
                    // SENSOR_STATUS_ACCURACY_LOW on a magnetometer means
                    // hard-iron interference — a ferromagnetic object
                    // close enough to distort the reading — and throwing
                    // that away discards the cheapest metal indicator the
                    // hardware offers.
                    reading.reportAccuracy(a)
                }
            }
            // Only stream while the app is in the foreground. A listener
            // left running behind a locked screen spends battery on a
            // reading no one can see.
            //
            // `listener` is assigned HERE and not above: it doubles as
            // the "already registered" flag, so assigning it eagerly
            // made start() return immediately every time and no sensor
            // ever registered at all.
            fun start() {
                if (listener != null) return
                val ok = manager.registerListener(l, sensor, delay)
                listener = if (ok) l else null
                if (!ok) reading.markFailed()
            }
            val obs = androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> start()
                    androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> unregister()
                    else -> Unit
                }
            }
            owner.lifecycle.addObserver(obs)
            // The app may already be resumed, in which case ON_RESUME
            // has already been delivered and the observer will not fire
            // again. Start now, and the observer only governs the rest.
            if (owner.lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED,
                )
            ) {
                start()
            }
            // Exactly one onDispose per branch. Two calls here do not
            // produce a compiler error, but DisposableEffect keeps only
            // the last, so the observer would never be removed and would
            // hold the activity's LifecycleRegistry for the process.
            onDispose {
                owner.lifecycle.removeObserver(obs)
                unregister()
            }
        }
    }
    return reading
}
