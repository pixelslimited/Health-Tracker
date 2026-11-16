package ng.pixelslimited.health.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Entry
import ng.pixelslimited.health.data.Level
import ng.pixelslimited.health.data.Profile
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.data.T
import ng.pixelslimited.health.data.bmi
import ng.pixelslimited.health.data.dayStart
import ng.pixelslimited.health.data.distanceText
import ng.pixelslimited.health.data.durationText
import ng.pixelslimited.health.data.metres
import ng.pixelslimited.health.data.hoursOf
import ng.pixelslimited.health.data.last
import ng.pixelslimited.health.data.level
import ng.pixelslimited.health.data.series
import ng.pixelslimited.health.data.since
import ng.pixelslimited.health.data.streak
import ng.pixelslimited.health.data.timeText
import ng.pixelslimited.health.data.typeName
import ng.pixelslimited.health.data.value
import ng.pixelslimited.health.data.weekSteps
import ng.pixelslimited.health.data.whenText
import ng.pixelslimited.health.heart.Hc
import ng.pixelslimited.health.steps.StepService
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

@Composable
private fun Cell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
fun SleepScreen(me: Profile, save: (Entry) -> Unit, onAdd: (String) -> Unit) {
    val ctx = LocalContext.current
    val last = me.last(T.SLEEP)
    val hours = last?.a ?: 0.0
    val week = me.since(T.SLEEP, now() - 7 * 86400000L)
    Block(fill = if (last == null) -1f else (hours / me.sleepGoal).toFloat()) {
        Text(s(R.string.last_sleep), style = MaterialTheme.typography.titleMedium)
        Text(
            if (last == null) s(R.string.none) else durationText(ctx, hours),
            fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-2).sp, fontWeight = FontWeight.Black,
        )
        SegBar((hours / me.sleepGoal).toFloat())
        if (last != null && hours < me.sleepGoal) {
            Text(
                ctx.getString(R.string.short_of_goal, durationText(ctx, me.sleepGoal - hours), me.sleepGoal),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val none = s(R.string.none)
            Cell(s(R.string.bedtime), if (last == null) none else timeText(last.ts - (last.a * 3600000).toLong()), Modifier.weight(1f))
            Cell(s(R.string.woke), if (last == null) none else timeText(last.ts), Modifier.weight(1f))
            Cell(s(R.string.avg_7d), if (week.isEmpty()) none else durationText(ctx, week.map { it.a }.average()), Modifier.weight(1f))
        }
    }
    Section(s(R.string.this_week)) {
        val nights = me.entries.filter { it.type == T.SLEEP }.sortedBy { it.ts }.takeLast(7)
        if (nights.isEmpty()) Text(s(R.string.none)) else SleepChart(nights, s(R.string.sleep_chart_desc)) { i -> whenText(nights[i].ts) + "   " + nights[i].value(ctx) }
        Stat(s(R.string.goal), ctx.getString(R.string.f_hours, me.sleepGoal.toDouble()))
    }
    if (me.sleepStart == 0L) {
        Button(onClick = { Store.edit { it.copy(sleepStart = now()) } }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.sleep_start)) }
    } else {
        Text(ctx.getString(R.string.sleeping_since, whenText(me.sleepStart)))
        Button(onClick = {
            val h = (now() - me.sleepStart) / 3600000.0
            if (h in 0.25..20.0) save(Entry(T.SLEEP, now(), h))
            Store.edit { it.copy(sleepStart = 0) }
        }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.sleep_stop)) }
    }
    OutlinedButton(onClick = { onAdd("sleep") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.manual) + " - " + s(R.string.t_sleep)) }
}

/** The centre Add button: a picker, then the ruler dialog for the chosen item. */
@Composable
fun AddFlow(me: Profile, mode: String, set: (String) -> Unit, save: (Entry) -> Unit) {
    var glucoseWhen by remember { mutableIntStateOf(0) }
    val close = { set("") }
    val done: (Entry) -> Unit = { save(it); set("") }
    when (mode) {
        "pick" -> AlertDialog(
            onDismissRequest = close,
            title = { Text(s(R.string.add)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "food" to s(R.string.scan_food),
                        "hr" to s(R.string.measure_hr),
                        "hrm" to s(R.string.t_hr) + " - " + s(R.string.manual),
                        "bp" to s(R.string.t_bp),
                        "glu" to s(R.string.t_glu),
                        "a1c" to s(R.string.t_hba1c),
                        "meal" to s(R.string.t_meal),
                        "water" to s(R.string.t_water),
                        "weight" to s(R.string.t_weight),
                        "sleep" to s(R.string.t_sleep) + " - " + s(R.string.manual),
                    ).forEach { (key, label) ->
                        OutlinedButton(onClick = { set(key) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = close) { Text(s(R.string.cancel)) } },
        )
        "food" -> FoodScan(onClose = close) { done(it) }
        "hr" -> MeasureHr(onDone = { done(Entry(T.HR, now(), it.toDouble())) }, onClose = close)
        "hrm" -> RulerDialog(s(R.string.t_hr), listOf(RulerSpec(s(R.string.unit_bpm), 30..220, 1, 72)), onClose = close) { v, _ ->
            done(Entry(T.HR, now(), v[0].toDouble()))
        }
        "bp" -> RulerDialog(
            s(R.string.t_bp),
            listOf(RulerSpec(s(R.string.systolic), 60..260, 1, 120), RulerSpec(s(R.string.diastolic), 30..160, 1, 80)),
            onClose = close,
        ) { v, _ -> done(Entry(T.BP, now(), v[0].toDouble(), v[1].toDouble())) }
        "glu" -> RulerDialog(
            s(R.string.t_glu),
            listOf(RulerSpec(s(R.string.unit_mgdl), 20..600, 1, 100) { "$it  (" + String.format(Locale.US, "%.1f", it / 18.0) + " mmol/L)" }),
            extra = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.fasting, R.string.after_meal, R.string.random).forEachIndexed { i, label ->
                        FilterChip(selected = glucoseWhen == i, onClick = { glucoseWhen = i }, label = { Text(s(label)) })
                    }
                }
            },
            onClose = close,
        ) { v, _ -> done(Entry(T.GLU, now(), v[0].toDouble(), glucoseWhen.toDouble())) }
        "a1c" -> RulerDialog(s(R.string.t_hba1c), listOf(RulerSpec("%", 40..150, 1, 60) { String.format(Locale.US, "%.1f", it / 10.0) }), onClose = close) { v, _ ->
            done(Entry(T.HBA1C, now(), v[0] / 10.0))
        }
        "meal" -> RulerDialog(s(R.string.t_meal), listOf(RulerSpec(s(R.string.carbs_g), 0..300, 5, 45)), note = true, onClose = close) { v, note ->
            done(Entry(T.MEAL, now(), v[0].toDouble(), note = note))
        }
        "water" -> RulerDialog(s(R.string.t_water), listOf(RulerSpec("ml", 50..3000, 50, 750)), onClose = close) { v, _ ->
            done(Entry(T.WATER, now(), v[0].toDouble()))
        }
        "weight" -> RulerDialog(
            s(R.string.t_weight),
            listOf(RulerSpec("kg", 200..2500, 5, ((me.last(T.WEIGHT)?.a ?: 70.0) * 10).toInt() / 5 * 5) { String.format(Locale.US, "%.1f", it / 10.0) }),
            onClose = close,
        ) { v, _ -> done(Entry(T.WEIGHT, now(), v[0] / 10.0)) }
        "sleep" -> RulerDialog(s(R.string.t_sleep), listOf(RulerSpec(s(R.string.hours), 1..32, 1, 14) { String.format(Locale.US, "%.1f", it / 2.0) }), onClose = close) { v, _ ->
            done(Entry(T.SLEEP, now(), v[0] / 2.0))
        }
    }
}
