package ng.pixelslimited.health.data

import android.content.Context
import ng.pixelslimited.health.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.pow

enum class Level { OK, NOTE, DANGER }

fun bpLevel(sys: Int, dia: Int): Level = when {
    sys >= 180 || dia >= 120 -> Level.DANGER
    sys >= 140 || dia >= 90 || sys < 90 || dia < 60 -> Level.NOTE
    else -> Level.OK
}

fun gluLevel(mg: Int, fasting: Boolean): Level = when {
    mg < 54 || mg >= 300 -> Level.DANGER
    mg < 70 || mg >= 200 || (fasting && mg >= 126) -> Level.NOTE
    else -> Level.OK
}

fun hrLevel(bpm: Int): Level = when {
    bpm < 40 || bpm > 150 -> Level.DANGER
    bpm < 50 || bpm > 120 -> Level.NOTE
    else -> Level.OK
}

fun level(e: Entry): Level = when (e.type) {
    T.HR -> hrLevel(e.a.toInt())
    T.BP -> bpLevel(e.a.toInt(), e.b.toInt())
    T.GLU -> gluLevel(e.a.toInt(), e.b.toInt() == 0)
    else -> Level.OK
}

fun Profile.last(type: String): Entry? = entries.filter { it.type == type }.maxByOrNull { it.ts }

fun Profile.series(type: String, n: Int = 14): List<Float> =
    entries.filter { it.type == type }.sortedBy { it.ts }.takeLast(n).map { it.a.toFloat() }

fun Profile.since(type: String, fromMs: Long): List<Entry> = entries.filter { it.type == type && it.ts >= fromMs }

fun Profile.bmi(): Double? = last(T.WEIGHT)?.let { it.a / (heightCm / 100.0).pow(2) }

fun dayStart(d: LocalDate = LocalDate.now()): Long = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun weekSteps(p: Profile, today: LocalDate = LocalDate.now()): List<Int> {
    val mon = today.with(DayOfWeek.MONDAY)
    return (0..6).map { p.steps[mon.plusDays(it.toLong()).toString()] ?: 0 }
}

fun streak(p: Profile, today: LocalDate = LocalDate.now()): Int {
    var d = today
    if ((p.steps[d.toString()] ?: 0) < p.dailyGoal) d = d.minusDays(1)
    var n = 0
    while ((p.steps[d.toString()] ?: 0) >= p.dailyGoal && n < 3650) {
        n++
        d = d.minusDays(1)
    }
    return n
}

private val stamp = DateTimeFormatter.ofPattern("d MMM, HH:mm")

private val clock = DateTimeFormatter.ofPattern("HH:mm")
private val dayName = DateTimeFormatter.ofPattern("EEE d MMM")

fun timeText(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(clock)

fun dateText(): String = LocalDate.now().format(dayName)

fun whenText(ts: Long): String = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(stamp)

fun typeName(ctx: Context, type: String): String = ctx.getString(
    when (type) {
        T.HR -> R.string.t_hr
        T.BP -> R.string.t_bp
        T.GLU -> R.string.t_glu
        T.WEIGHT -> R.string.t_weight
        T.SLEEP -> R.string.t_sleep
        T.WATER -> R.string.t_water
        T.MEAL -> R.string.t_meal
        else -> R.string.t_hba1c
    }
)

fun Entry.value(ctx: Context): String = when (type) {
    T.HR -> ctx.getString(R.string.f_hr, a.toInt())
    T.BP -> ctx.getString(R.string.f_bp, a.toInt(), b.toInt())
    T.GLU -> ctx.getString(R.string.f_glu, a.toInt(), a / 18.0)
    T.WEIGHT -> ctx.getString(R.string.f_kg, a)
    T.SLEEP -> ctx.getString(R.string.f_hours, a)
    T.WATER -> ctx.getString(R.string.f_ml, a.toInt())
    T.MEAL -> ctx.getString(R.string.f_carbs, a.toInt()) + if (note.isBlank()) "" else " - $note"
    else -> ctx.getString(R.string.f_pct, a)
}

/** FINDRISC diabetes risk score (0-26). A screening score, not a diagnosis. */
fun findrisc(age: Int, bmi: Double, waistCm: Int, male: Boolean, active: Boolean, vegDaily: Boolean, bpMeds: Boolean, highGlucose: Boolean, family: Int): Int {
    var s = when {
        age < 45 -> 0
        age < 55 -> 2
        age < 65 -> 3
        else -> 4
    }
    s += when {
        bmi < 25 -> 0
        bmi <= 30 -> 1
        else -> 3
    }
    val (w1, w2) = if (male) 94 to 102 else 80 to 88
    s += when {
        waistCm < w1 -> 0
        waistCm <= w2 -> 3
        else -> 4
    }
    if (!active) s += 2
    if (!vegDaily) s += 1
    if (bpMeds) s += 2
    if (highGlucose) s += 5
    s += when (family) {
        1 -> 3
        2 -> 5
        else -> 0
    }
    return s
}

/** Short status shown in the ongoing notification. */
fun summary(ctx: Context, p: Profile): String {
    val parts = ArrayList<String>()
    val water = p.since(T.WATER, dayStart()).sumOf { it.a }.toInt()
    if (water > 0) parts.add(typeName(ctx, T.WATER) + ": " + ctx.getString(R.string.f_ml, water))
    listOf(T.HR, T.BP, T.GLU, T.SLEEP).forEach { type -> p.last(type)?.let { parts.add(typeName(ctx, type) + ": " + it.value(ctx)) } }
    return parts.joinToString("\n")
}
