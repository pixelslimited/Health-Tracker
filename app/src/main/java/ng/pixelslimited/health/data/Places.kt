package ng.pixelslimited.health.data

import android.content.Context
import ng.pixelslimited.health.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One place you stopped at: when you arrived and left, where, how far you travelled to get there. */
class Stop(val arrive: Long, val depart: Long, val lat: Double, val lon: Double, val metres: Double, val name: String)

fun metresBetween(a1: Double, o1: Double, a2: Double, o2: Double): Double {
    val p1 = Math.toRadians(a1)
    val p2 = Math.toRadians(a2)
    val dp = p2 - p1
    val dl = Math.toRadians(o2 - o1)
    val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * 6371000.0 * asin(sqrt(h))
}

fun Entry.toStop(): Stop {
    val p = note.split("|", limit = 3)
    return Stop(ts, p.getOrNull(0)?.toLongOrNull() ?: ts, a, b, p.getOrNull(1)?.toDoubleOrNull() ?: 0.0, p.getOrNull(2) ?: "")
}

fun Profile.stopsOn(day: LocalDate): List<Stop> {
    val from = dayStart(day)
    val to = dayStart(day.plusDays(1))
    return entries.filter { it.type == T.STOP && it.ts >= from && it.ts < to }.sortedBy { it.ts }.map { it.toStop() }
}

fun Profile.routeOf(day: LocalDate): List<Pair<Double, Double>> =
    (routes[day.toString()] ?: "").split(';').mapNotNull {
        val p = it.split(',')
        val la = p.getOrNull(0)?.toDoubleOrNull()
        val lo = p.getOrNull(1)?.toDoubleOrNull()
        if (la == null || lo == null) null else la to lo
    }

fun routeMetres(pts: List<Pair<Double, Double>>): Double =
    pts.zipWithNext().sumOf { (a, b) -> metresBetween(a.first, a.second, b.first, b.second) }

class AutoLabels(val home: Pair<Double, Double>?, val work: Pair<Double, Double>?)

/** Home is the place you spend most nights; work is where you spend most weekday daytime hours. */
fun Profile.autoLabels(): AutoLabels {
    class C(val lat: Double, val lon: Double, var night: Int = 0, var work: Int = 0)
    val cs = ArrayList<C>()
    val zone = ZoneId.systemDefault()
    entries.filter { it.type == T.STOP }.forEach { e ->
        val s = e.toStop()
        val c = cs.firstOrNull { metresBetween(it.lat, it.lon, s.lat, s.lon) < 150 } ?: C(s.lat, s.lon).also { cs.add(it) }
        var t = s.arrive
        var k = 0
        while (t <= s.depart && k < 400) {
            val z = Instant.ofEpochMilli(t).atZone(zone)
            val h = z.hour
            if (h >= 22 || h < 6) c.night++
            if (z.dayOfWeek.value <= 5 && h in 9..16) c.work++
            t += 1_800_000L
            k++
        }
    }
    val home = cs.filter { it.night >= 2 }.maxByOrNull { it.night }
    val work = cs.filter { it !== home && it.work >= 2 }.maxByOrNull { it.work }
    return AutoLabels(home?.let { it.lat to it.lon }, work?.let { it.lat to it.lon })
}

/** Name to show: a name you gave (reused within 150 m), else Home or Work, else the looked-up address, else coordinates. */
fun Profile.placeName(ctx: Context, s: Stop, auto: AutoLabels): String {
    labels.forEach {
        val p = it.split("|", limit = 3)
        val la = p.getOrNull(0)?.toDoubleOrNull()
        val lo = p.getOrNull(1)?.toDoubleOrNull()
        if (la != null && lo != null && p.size == 3 && metresBetween(la, lo, s.lat, s.lon) < 150) return p[2]
    }
    auto.home?.let { if (metresBetween(it.first, it.second, s.lat, s.lon) < 150) return ctx.getString(R.string.places_home) }
    auto.work?.let { if (metresBetween(it.first, it.second, s.lat, s.lon) < 150) return ctx.getString(R.string.places_work) }
    return s.name.ifBlank { String.format(java.util.Locale.US, "%.4f, %.4f", s.lat, s.lon) }
}

/** Name every stop near this spot (an empty name clears it). */
fun nameLabels(old: List<String>, lat: Double, lon: Double, name: String): List<String> {
    val kept = old.filter {
        val p = it.split("|", limit = 3)
        val la = p.getOrNull(0)?.toDoubleOrNull()
        val lo = p.getOrNull(1)?.toDoubleOrNull()
        la == null || lo == null || metresBetween(la, lo, lat, lon) >= 150
    }
    val clean = name.trim().replace("|", " ")
    return if (clean.isEmpty()) kept else kept + String.format(java.util.Locale.US, "%.5f|%.5f|%s", lat, lon, clean)
}
