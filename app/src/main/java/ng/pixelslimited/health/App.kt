package ng.pixelslimited.health

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import ng.pixelslimited.health.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_STEPS, getString(R.string.ch_steps), NotificationManager.IMPORTANCE_LOW))
        // Channel settings cannot change once made, so reminders moved to a new channel with sound and vibration.
        nm.deleteNotificationChannel("reminders")
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, getString(R.string.ch_remind), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500)
                setSound(
                    android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
        ng.pixelslimited.health.steps.StepJob.schedule(this)
    }

    companion object {
        const val CH_STEPS = "steps"
        const val CH_REMIND = "reminders_alert"
    }
}
