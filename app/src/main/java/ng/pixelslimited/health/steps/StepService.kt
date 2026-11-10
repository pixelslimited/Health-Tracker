package ng.pixelslimited.health.steps

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ng.pixelslimited.health.App
import ng.pixelslimited.health.MainActivity
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.data.summary
import ng.pixelslimited.health.widget.StepsWidget
import java.time.LocalDate

/** Counts steps in the background from the phone's hardware step counter. */
class StepService : Service(), SensorEventListener {
    private var lastShown = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Store.init(applicationContext)
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH else 0
        try {
            ServiceCompat.startForeground(this, 1, notification(), type)
        } catch (e: Exception) {
            stopSelf()
            return
        }
        val sm = getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sensor == null) stopSelf() else sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Each start also refreshes the notification, so new readings show at once.
        try {
            getSystemService(android.app.NotificationManager::class.java).notify(1, notification())
        } catch (e: Exception) {
        }
        return START_STICKY
    }

    override fun onDestroy() {
        getSystemService(SensorManager::class.java).unregisterListener(this)
        Store.flush()
        super.onDestroy()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        val total = e.values[0].toLong()
        val prefs = getSharedPreferences("steps", Context.MODE_PRIVATE)
        val last = prefs.getLong("last", -1)
        prefs.edit().putLong("last", total).apply()
        if (last < 0) return
        // The counter restarts from zero after a reboot.
        val delta = if (total >= last) total - last else total
        if (delta in 1..50000) Store.addSteps(LocalDate.now().toString(), delta.toInt())
        val now = System.currentTimeMillis()
        if (now - lastShown > 30000) {
            lastShown = now
            getSystemService(android.app.NotificationManager::class.java).notify(1, notification())
            StepsWidget.refresh(this)
        }
    }

    private fun notification(): Notification {
        val me = Store.data.value.me
        val steps = me.steps[LocalDate.now().toString()] ?: 0
        val text = summary(this, me)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, App.CH_STEPS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.steps_today, steps, me.dailyGoal))
            .setContentText(text.substringBefore('\n'))
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        fun hasSensor(ctx: Context) = ctx.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        fun perms(): Array<String> = buildList {
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()

        fun canRun(ctx: Context) = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

        fun start(ctx: Context) {
            if (!canRun(ctx) || !hasSensor(ctx)) return
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, StepService::class.java))
            } catch (e: Exception) {
                // Background start not allowed right now; the app starts it when opened.
            }
        }
    }
}
