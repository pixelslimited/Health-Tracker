package ng.pixelslimited.health.drive

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs

/**
 * Live heading from the phone's compass, many times a second, so the direction changes the moment you turn.
 * GPS only reports a direction once a second and only while moving, so it lags.
 * While driving at speed, the GPS direction slowly teaches an offset, so a phone sitting at an angle in its
 * holder still reads the direction the car travels.
 */
object Compass {
    /** Degrees from true north, 0 to 360, or null when the phone has no compass. */
    var heading by mutableStateOf<Float?>(null)
        private set

    private var sm: SensorManager? = null
    private var ctx: Context? = null
    private var smooth = Float.NaN
    private var offset = 0f
    private var learned = 0
    private val rot = FloatArray(9)
    private val remap = FloatArray(9)
    private val ori = FloatArray(3)

    private fun diff(a: Float, b: Float) = ((a - b + 540f) % 360f) - 180f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rot, e.values)
            // Lying flat: the top of the phone points the way. Standing up in a holder: the back of the phone does.
            val upright = abs(rot[7]) > 0.7f
            val surf = try {
                @Suppress("DEPRECATION")
                (ctx?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            } catch (x: Exception) {
                Surface.ROTATION_0
            }
            if (upright) {
                val (ax, ay) = when (surf) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Z to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Z
                }
                SensorManager.remapCoordinateSystem(rot, ax, ay, remap)
            } else {
                val (ax, ay) = when (surf) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rot, ax, ay, remap)
            }
            SensorManager.getOrientation(remap, ori)
            var deg = Math.toDegrees(ori[0].toDouble()).toFloat()
            // Magnetic north to true north, from where you are.
            Drive.fix?.let { f -> deg += GeomagneticField(f.lat.toFloat(), f.lon.toFloat(), f.alt.toFloat(), System.currentTimeMillis()).declination }
            deg = (deg + 360f) % 360f
            // Light smoothing: removes jitter, keeps turns instant.
            smooth = if (smooth.isNaN()) deg else (smooth + diff(deg, smooth) * 0.35f + 360f) % 360f
            val f = Drive.fix
            if (f != null && f.speed > 5f && System.currentTimeMillis() - f.ts < 2000) {
                val want = diff(f.bearing, smooth)
                offset += diff(want, offset) * 0.02f
                if (learned < 200) learned++
            }
            val out = (smooth + (if (learned > 30) offset else 0f) + 360f) % 360f
            val cur = heading
            if (cur == null || abs(diff(out, cur)) >= 0.5f) heading = out
        }

        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    fun start(c: Context) {
        if (sm != null) return
        val m = c.applicationContext.getSystemService(SensorManager::class.java) ?: return
        val s = m.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: m.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) ?: return
        ctx = c.applicationContext
        if (m.registerListener(listener, s, SensorManager.SENSOR_DELAY_GAME)) sm = m
    }

    fun stop() {
        sm?.unregisterListener(listener)
        sm = null
        smooth = Float.NaN
        heading = null
    }
}
