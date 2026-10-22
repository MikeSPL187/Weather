package app.aura.ui.scene

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * How far the phone leans from its usual reading angle, each axis in -1..1, smoothed.
 * Uses the gravity sensor, which is cheap and present on almost every phone; stays at zero
 * when the sensor is missing, motion is reduced, or in previews.
 */
@Composable
fun rememberTilt(enabled: Boolean): State<Offset> {
    val state = remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    DisposableEffect(enabled, preview) {
        val manager = if (enabled && !preview) context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager else null
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val g = SensorManager.GRAVITY_EARTH
                // Phones are usually held tilted back about 35°: treat that as the rest position.
                val x = (-event.values[0] / g).coerceIn(-1f, 1f)
                val y = (event.values[1] / g - 0.57f).coerceIn(-1f, 1f) * 2f
                val previous = state.value
                state.value = Offset(previous.x + (x - previous.x) * 0.12f, previous.y + (y.coerceIn(-1f, 1f) - previous.y) * 0.12f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager?.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager?.unregisterListener(listener) }
    }
    return state
}
