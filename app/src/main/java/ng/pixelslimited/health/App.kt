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
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, getString(R.string.ch_remind), NotificationManager.IMPORTANCE_HIGH))
    }

    companion object {
        const val CH_STEPS = "steps"
        const val CH_REMIND = "reminders"
    }
}
