package ng.pixelslimited.health.sleep

import android.content.Context
import ng.pixelslimited.health.data.Entry
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.data.T
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/** Finds the night's sleep from the phone's own screen on/off times: the longest unbroken night-time stretch. */
object SleepDetector {
    private const val GAP_MS = 15 * 60000L
    private const val MIN_MS = 3 * 3600000L
    private const val MAX_MS = 14 * 3600000L

    @Synchronized
    fun log(ctx: Context, on: Boolean) {
        val p = ctx.getSharedPreferences("screen", Context.MODE_PRIVATE)
        val list = p.getString("events", "")!!.split(",").filter { it.isNotBlank() }.takeLast(399)
        p.edit().putString("events", (list + "${System.currentTimeMillis()}:${if (on) 1 else 0}").joinToString(",")).apply()
    }

    fun detect(ctx: Context) {
        val events = ctx.getSharedPreferences("screen", Context.MODE_PRIVATE).getString("events", "")!!
            .split(",").filter { it.contains(':') }
            .map { it.substringBefore(':').toLong() to (it.substringAfter(':') == "1") }
        // Off stretches, joining those separated by a short look at the phone.
        val stretches = ArrayList<LongArray>()
        var off = -1L
        for ((ts, on) in events) {
            if (!on && off < 0) off = ts
            if (on && off >= 0) {
                val last = stretches.lastOrNull()
                if (last != null && off - last[1] < GAP_MS) last[1] = ts else stretches.add(longArrayOf(off, ts))
                off = -1L
            }
        }
        val zone = ZoneId.systemDefault()
        val me = Store.data.value.me
        val found = stretches.filter { s ->
            val len = s[1] - s[0]
            val start = Instant.ofEpochMilli(s[0]).atZone(zone).hour
            val end = Instant.ofEpochMilli(s[1]).atZone(zone).hour
            len in MIN_MS..MAX_MS && (start >= 19 || start < 4) && end in 3..13
        }.groupBy { Instant.ofEpochMilli(it[1]).atZone(zone).toLocalDate() }
            .map { (_, list) -> list.maxBy { it[1] - it[0] } }
            .filter { s -> me.entries.none { it.type == T.SLEEP && abs(it.ts - s[1]) < 8 * 3600000L } }
        if (found.isEmpty()) return
        Store.edit { p -> p.copy(entries = p.entries + found.map { Entry(T.SLEEP, it[1], (it[1] - it[0]) / 3600000.0, note = "auto") }) }
    }
}
