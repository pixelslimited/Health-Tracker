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
import ng.pixelslimited.health.update.Updater
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

private fun Entry.short(ctx: android.content.Context): String = when (type) {
    T.HR -> ctx.getString(R.string.f_hr, a.toInt())
    T.BP -> a.toInt().toString() + "/" + b.toInt()
    T.GLU -> a.toInt().toString() + " " + ctx.getString(R.string.unit_mgdl)
    T.SLEEP -> durationText(ctx, a)
    else -> value(ctx)
}

private fun dayLabels(): List<String> = DayOfWeek.values().map { it.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()) }

@Composable
private fun Tile(title: String, e: Entry?, values: List<Float>, modifier: Modifier, live: Boolean = false, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Block(modifier.clickable(onClick = onClick)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(
            e?.short(ctx) ?: s(R.string.none),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
        )
        if (e != null && e.type != T.SLEEP && level(e) != Level.OK) LevelText(level(e))
        if (live) PulseWave(emptyList(), e?.a?.toInt() ?: 60, 32.dp) else Spark(values)
    }
}

@Composable
fun Today(me: Profile, release: Updater.Release?, open: (Int) -> Unit, save: (Entry) -> Unit) {
    val ctx = LocalContext.current
    var cat by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    var training by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val steps = me.steps[today.toString()] ?: 0
    // Each new step presses the number down and lets it spring back.
    val press = remember { androidx.compose.animation.core.Animatable(1f) }
    var seen by remember { mutableStateOf(steps) }
    LaunchedEffect(steps) {
        if (steps != seen) {
            seen = steps
            press.animateTo(0.86f, androidx.compose.animation.core.tween(50))
            press.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.3f, stiffness = 600f))
        }
    }
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val big = (wide * 0.26f).coerceIn(56f, 96f)
    val week = weekSteps(me, today)
    var granted by remember { mutableStateOf(StepService.canRun(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = StepService.canRun(ctx) }
    LaunchedEffect(granted) { if (granted) StepService.start(ctx) }

    if (release != null) UpdateCard(release)
    if (!StepService.hasSensor(ctx)) {
        Section(s(R.string.steps)) { Text(s(R.string.no_sensor)) }
    } else if (!granted) {
        Section(s(R.string.steps)) {
            Text(s(R.string.steps_permission))
            Button(onClick = { ask.launch(StepService.perms()) }) { Text(s(R.string.enable)) }
        }
    }
    Block(fill = steps.toFloat() / me.dailyGoal.coerceAtLeast(1)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s(R.string.steps), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(
                String.format(Locale.getDefault(), "%d%%", (steps * 100L / me.dailyGoal.coerceAtLeast(1)).toInt()),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            )
        }
        Text(
            String.format(Locale.getDefault(), "%,d", steps),
            modifier = Modifier.graphicsLayer {
                scaleX = press.value
                scaleY = press.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)
            },
            fontSize = big.sp, lineHeight = big.sp, letterSpacing = (-4).sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false,
        )
        Text(
            ctx.getString(R.string.goal_n, me.dailyGoal) + "  /  " + distanceText(ctx, me.metres(steps)),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegBar(steps.toFloat() / me.dailyGoal)
        HourBars(me.hoursOf(today), LocalTime.now().hour, s(R.string.hourly_desc))
    }
    // Two columns on normal screens, one on very narrow ones, four across on wide ones.
    val per = if (wide < 340) 1 else if (wide >= 600) 4 else 2
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> Tile(s(R.string.t_hr), me.last(T.HR), me.series(T.HR), m, live = true) { open(1) } },
        { m -> Tile(s(R.string.t_bp), me.last(T.BP), me.series(T.BP), m) { open(1) } },
        { m -> Tile(s(R.string.t_glu), me.last(T.GLU), me.series(T.GLU), m) { open(1) } },
        { m -> Tile(s(R.string.t_sleep), me.last(T.SLEEP), me.series(T.SLEEP), m) { open(2) } },
    )
    tiles.chunked(per).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { t -> t(Modifier.weight(1f)) }
        }
    }
    Section(s(R.string.this_week)) {
        DayStrip(week, me.dailyGoal, dayLabels(), today.dayOfWeek.value - 1, s(R.string.week_desc))
        Stat(s(R.string.goal), ctx.getString(R.string.of_total, week.sum(), me.weeklyGoal))
        Stat(s(R.string.distance), distanceText(ctx, me.metres(week.sum())))
        SegBar(week.sum().toFloat() / me.weeklyGoal)
        Stat(s(R.string.streak), ctx.getString(R.string.f_days, streak(me, today)))
    }
    WorkoutBlock(me, me.since(T.WORK, dayStart(today)).sumOf { it.a }.toInt(), { training = true }, cat) { cat = it }
    if (training) WorkoutDialog(cat, Workouts.plan(cat, me.fitLevel, Workouts.programDay(me.fitStart), Workouts.adjust(me)), { training = false }) { it.forEach(save); training = false }
    val water = me.since(T.WATER, dayStart(today)).sumOf { it.a }.toInt()
    Section(s(R.string.t_water)) {
        Stat(s(R.string.today), ctx.getString(R.string.of_total, water, me.waterGoal))
        SegBar(water.toFloat() / me.waterGoal)
        Button(onClick = { save(Entry(T.WATER, now(), 250.0)) }) { Text(s(R.string.add_glass)) }
    }
}

@Composable
fun Vitals(me: Profile, onAdd: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val runImport: () -> Unit = {
        scope.launch {
            try {
                toast(ctx, ctx.getString(R.string.imported, Hc.sync(ctx)))
            } catch (e: Exception) {
                toast(ctx, ctx.getString(R.string.hc_failed))
            }
        }
    }
    val hcAsk = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
        if (got.isNotEmpty()) runImport()
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Block {
        val e = me.last(T.HR)
        MetricHeader(s(R.string.t_hr), e?.value(ctx) ?: s(R.string.none))
        if (e != null) {
            LevelText(level(e))
            Text(ctx.getString(R.string.measured_at, whenText(e.ts)), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        PulseWave(emptyList(), e?.a?.toInt() ?: 60)
        LineChart(me.series(T.HR))
        Button(onClick = { onAdd("hr") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.measure)) }
        OutlinedButton(onClick = { onAdd("hrm") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add)) }
    }
    Block {
        val e = me.last(T.BP)
        MetricHeader(s(R.string.t_bp), e?.value(ctx) ?: s(R.string.none))
        if (e != null) {
            LevelText(level(e))
            Text(ctx.getString(R.string.measured_at, whenText(e.ts)), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        val bp = me.entries.filter { it.type == T.BP }.sortedBy { it.ts }.takeLast(7).map { it.a.toFloat() to it.b.toFloat() }
        if (bp.isNotEmpty()) RangeBars(bp, s(R.string.bp_chart_desc))
        Text(s(R.string.bp_hint), style = MaterialTheme.typography.bodyMedium, color = muted)
        Button(onClick = { onAdd("bp") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add)) }
    }
    Block {
        val e = me.last(T.GLU)
        MetricHeader(s(R.string.t_glu), e?.value(ctx) ?: s(R.string.none))
        if (e != null) {
            LevelText(level(e))
            Text(ctx.getString(R.string.measured_at, whenText(e.ts)), style = MaterialTheme.typography.bodyMedium, color = muted)
        }
        if (e != null && e.b.toInt() == 0 && e.a > me.fastingMax) {
            Text(ctx.getString(R.string.above_target, me.fastingMax), color = MaterialTheme.colorScheme.primary)
        }
        val gl = me.series(T.GLU)
        if (gl.isNotEmpty()) DotBand(gl, 70f, me.fastingMax.toFloat(), s(R.string.glu_chart_desc))
        Text(ctx.getString(R.string.fasting_limit, me.fastingMax), style = MaterialTheme.typography.bodyMedium, color = muted)
        Stat(s(R.string.t_hba1c), me.last(T.HBA1C)?.value(ctx) ?: s(R.string.none))
        Text(s(R.string.glu_hint), style = MaterialTheme.typography.bodyMedium, color = muted)
        Button(onClick = { onAdd("glu") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add)) }
        OutlinedButton(onClick = { onAdd("a1c") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.t_hba1c)) }
    }
    Section(s(R.string.wearable)) {
        Text(s(R.string.hc_desc), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {
            if (!Hc.available(ctx)) toast(ctx, ctx.getString(R.string.hc_missing)) else scope.launch {
                try {
                    if (Hc.granted(ctx)) runImport() else hcAsk.launch(Hc.perms)
                } catch (e: Exception) {
                    toast(ctx, ctx.getString(R.string.hc_failed))
                }
            }
        }) { Text(s(R.string.hc_import)) }
    }
}

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
        if (nights.isEmpty()) Text(s(R.string.none)) else SleepChart(nights, s(R.string.sleep_chart_desc))
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
        "water" -> RulerDialog(s(R.string.t_water), listOf(RulerSpec("ml", 50..2000, 50, 250)), onClose = close) { v, _ ->
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

/** Weight, meals and the entry history, shown inside Reports. */
@Composable
fun LogSections(me: Profile, onAdd: (String) -> Unit) {
    val ctx = LocalContext.current
    Section(s(R.string.t_weight)) {
        Stat(s(R.string.latest), me.last(T.WEIGHT)?.value(ctx) ?: s(R.string.none))
        me.bmi()?.let { Stat(s(R.string.bmi), String.format(Locale.US, "%.1f", it)) }
        LineChart(me.series(T.WEIGHT))
        OutlinedButton(onClick = { onAdd("weight") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add)) }
    }
    val carbs = me.since(T.MEAL, dayStart()).sumOf { it.a }.toInt()
    Section(s(R.string.t_meal)) {
        Stat(s(R.string.carbs_today), ctx.getString(R.string.f_carbs, carbs))
        Stat(s(R.string.cal_today), ctx.getString(R.string.f_kcal, me.since(T.MEAL, dayStart()).sumOf { it.b }.toInt()))
        OutlinedButton(onClick = { onAdd("food") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.scan_food)) }
        OutlinedButton(onClick = { onAdd("meal") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add)) }
    }
    Section(s(R.string.recent)) {
        val recent = me.entries.sortedByDescending { it.ts }.take(40)
        if (recent.isEmpty()) Text(s(R.string.none))
        recent.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(typeName(ctx, e.type) + ": " + e.value(ctx), style = MaterialTheme.typography.bodyLarge)
                    Text(whenText(e.ts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { Store.edit { p -> p.copy(entries = p.entries - e) } }) { Icon(Icons.Filled.Delete, contentDescription = s(R.string.delete)) }
            }
        }
    }
}
