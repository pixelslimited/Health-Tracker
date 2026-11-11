package ng.pixelslimited.health.steps

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Profile
import ng.pixelslimited.health.data.distanceText
import ng.pixelslimited.health.data.hoursOf
import ng.pixelslimited.health.data.metres
import java.time.LocalDate
import java.util.Locale

/** Draws the notification card with the app's own Inter fonts: step count, goal bar and, when expanded, the hourly bars. */
object StepCard {
    fun render(ctx: Context, me: Profile, big: Boolean): Bitmap {
        val today = LocalDate.now()
        val steps = me.steps[today.toString()] ?: 0
        val goal = me.dailyGoal.coerceAtLeast(1)
        val frac = (steps.toFloat() / goal).coerceIn(0f, 1f)
        val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val ink = if (night) 0xFFF4F1EA.toInt() else 0xFF101417.toInt()
        val accent = 0xFFE5484D.toInt()
        val w = 1000
        val h = if (big) 400 else 124
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        fun font(id: Int, fallback: Typeface) = ResourcesCompat.getFont(ctx, id) ?: fallback
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(R.font.inter_display_black, Typeface.DEFAULT_BOLD); color = ink; textSize = if (big) 150f else 88f
        }
        val pct = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(R.font.inter_semibold, Typeface.DEFAULT_BOLD); color = accent; textSize = if (big) 56f else 48f; textAlign = Paint.Align.RIGHT
        }
        val cap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font(R.font.inter_regular, Typeface.DEFAULT); color = (ink and 0x00FFFFFF) or (0xA0 shl 24); textSize = 50f
        }
        val base = if (big) 124f else 80f
        c.drawText(String.format(Locale.getDefault(), "%,d", steps), 0f, base, num)
        c.drawText(String.format(Locale.getDefault(), "%d%%", steps * 100L / goal), w.toFloat(), base, pct)
        val barTop = base + if (big) 26f else 16f
        val barH = if (big) 22f else 14f
        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (ink and 0x00FFFFFF) or (0x2A shl 24) }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        c.drawRoundRect(RectF(0f, barTop, w.toFloat(), barTop + barH), barH / 2, barH / 2, track)
        if (steps > 0) c.drawRoundRect(RectF(0f, barTop, maxOf(barH, w * frac), barTop + barH), barH / 2, barH / 2, fill)
        if (big) {
            c.drawText(ctx.getString(R.string.goal_n, me.dailyGoal) + "  /  " + distanceText(ctx, me.metres(steps)), 0f, barTop + barH + 66f, cap)
            val hours = me.hoursOf(today)
            val mx = (hours.maxOrNull() ?: 0).coerceAtLeast(1)
            val bottom = 392f
            val area = 118f
            val slot = w / 24f
            val bw = slot * 0.62f
            val now = java.time.LocalTime.now().hour
            val bar = Paint(Paint.ANTI_ALIAS_FLAG)
            for (i in 0 until 24) {
                val v = hours.getOrElse(i) { 0 }
                val bh = if (v > 0) maxOf(8f, area * v / mx) else 5f
                bar.color = if (i == now) accent else if (v > 0) (ink and 0x00FFFFFF) or (0xC0 shl 24) else (ink and 0x00FFFFFF) or (0x2A shl 24)
                val x = i * slot + (slot - bw) / 2
                c.drawRoundRect(RectF(x, bottom - bh, x + bw, bottom), 3f, 3f, bar)
            }
        }
        return bmp
    }
}
