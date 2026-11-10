package ng.pixelslimited.health.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ng.pixelslimited.health.MainActivity
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Store
import java.time.LocalDate

class StepsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = refresh(context)

    companion object {
        fun refresh(ctx: Context) {
            Store.init(ctx.applicationContext)
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, StepsWidget::class.java))
            if (ids.isEmpty()) return
            val me = Store.data.value.me
            val views = RemoteViews(ctx.packageName, R.layout.widget_steps)
            views.setTextViewText(R.id.steps, (me.steps[LocalDate.now().toString()] ?: 0).toString())
            views.setTextViewText(R.id.goal, ctx.getString(R.string.of_goal, me.dailyGoal))
            views.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            mgr.updateAppWidget(ids, views)
        }
    }
}
