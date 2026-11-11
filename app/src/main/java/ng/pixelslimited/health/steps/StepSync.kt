package ng.pixelslimited.health.steps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import ng.pixelslimited.health.data.Store
import java.time.LocalDate
import java.time.LocalTime

/** Turns readings of the phone's own step counter into stored steps. No other device is involved. */
object StepSync {
    @Synchronized
    fun apply(ctx: Context, total: Long) {
        val prefs = ctx.getSharedPreferences("steps", Context.MODE_PRIVATE)
        val last = prefs.getLong("last", -1)
        prefs.edit().putLong("last", total).apply()
        if (last < 0) return
        // The counter restarts from zero after a reboot.
        val delta = if (total >= last) total - last else total
        if (delta in 1..50000) Store.addSteps(LocalDate.now().toString(), LocalTime.now().hour, delta.toInt())
    }

    /** The hardware counter keeps counting while the app is stopped; read it once to add those steps. */
    fun catchUp(ctx: Context, done: () -> Unit = {}) {
        if (!StepService.canRun(ctx)) { done(); return }
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) { done(); return }
        val once = object : SensorEventListener {
            var finished = false

            override fun onSensorChanged(e: SensorEvent) {
                if (finished) return
                finished = true
                sm.unregisterListener(this)
                apply(ctx, e.values[0].toLong())
                done()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(once, sensor, SensorManager.SENSOR_DELAY_FASTEST)
        // If the sensor gives nothing in a few seconds, stop waiting.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!once.finished) {
                once.finished = true
                sm.unregisterListener(once)
                done()
            }
        }, 8000)
    }
}
