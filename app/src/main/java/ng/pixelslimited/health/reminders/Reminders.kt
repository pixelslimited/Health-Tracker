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
import ng.pixelslimited.health.steps.StepJob
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
        // One alarm at a time; the receiver sets tomorrow's. Exact when allowed so it rings on the minute.
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.timeInMillis, pi)
    }

    fun canExact(ctx: Context) = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun cancel(ctx: Context, r: Reminder) = ctx.getSystemService(AlarmManager::class.java).cancel(intent(ctx, r))

    fun scheduleAll(ctx: Context) = Store.data.value.me.reminders.forEach { schedule(ctx, it) }

    fun cancelAll(ctx: Context) = Store.data.value.me.reminders.forEach { cancel(ctx, it) }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 2)
        // Set tomorrow's alarm first so the chain never breaks.
        Store.init(ctx.applicationContext)
        Store.data.value.me.reminders.firstOrNull { it.id == id }?.let { Reminders.schedule(ctx, it) }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val label = intent.getStringExtra("label") ?: ""
        val n = Notification.Builder(ctx, App.CH_REMIND)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(ctx.getString(R.string.reminder))
            .setContentText(label)
            .setStyle(Notification.BigTextStyle().bigText(label))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(id, n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED") return
        Store.init(ctx.applicationContext)
        Reminders.scheduleAll(ctx)
        StepService.start(ctx)
        StepJob.schedule(ctx)
    }
}
