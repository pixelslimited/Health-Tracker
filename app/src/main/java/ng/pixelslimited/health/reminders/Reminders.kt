package ng.pixelslimited.health.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import ng.pixelslimited.health.App
import ng.pixelslimited.health.MainActivity
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Reminder
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.steps.StepService
import java.util.Calendar

object Reminders {
    private fun intent(ctx: Context, r: Reminder): PendingIntent = PendingIntent.getBroadcast(
        ctx, r.id,
        Intent(ctx, ReminderReceiver::class.java).putExtra("label", r.label).putExtra("id", r.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun schedule(ctx: Context, r: Reminder) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = intent(ctx, r)
        am.cancel(pi)
        if (!r.on) return
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, r.hour)
            set(Calendar.MINUTE, r.minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, c.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    fun cancel(ctx: Context, r: Reminder) = ctx.getSystemService(AlarmManager::class.java).cancel(intent(ctx, r))

    fun scheduleAll(ctx: Context) = Store.data.value.me.reminders.forEach { schedule(ctx, it) }

    fun cancelAll(ctx: Context) = Store.data.value.me.reminders.forEach { cancel(ctx, it) }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(ctx, App.CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(ctx.getString(R.string.reminder))
            .setContentText(intent.getStringExtra("label") ?: "")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(intent.getIntExtra("id", 2), n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Store.init(ctx.applicationContext)
        Reminders.scheduleAll(ctx)
        StepService.start(ctx)
    }
}
