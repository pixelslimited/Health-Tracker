package ng.pixelslimited.health.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Entry
import ng.pixelslimited.health.data.Level
import ng.pixelslimited.health.data.Profile
import ng.pixelslimited.health.data.Reminder
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.data.T
import ng.pixelslimited.health.data.bmi
import ng.pixelslimited.health.data.dayStart
import ng.pixelslimited.health.data.distanceText
import ng.pixelslimited.health.data.durationText
import ng.pixelslimited.health.data.hoursOf
import ng.pixelslimited.health.data.last
import ng.pixelslimited.health.data.level
import ng.pixelslimited.health.data.metres
import ng.pixelslimited.health.data.series
import ng.pixelslimited.health.data.since
import ng.pixelslimited.health.data.streak
import ng.pixelslimited.health.data.typeName
import ng.pixelslimited.health.data.value
import ng.pixelslimited.health.data.weekSteps
import ng.pixelslimited.health.data.whenText
import ng.pixelslimited.health.heart.Hc
import ng.pixelslimited.health.reminders.Reminders
import ng.pixelslimited.health.steps.StepService
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Segmented switch: the chosen part is solid ink. */
@Composable
fun Seg(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).border(2.dp, MaterialTheme.colorScheme.outlineVariant).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).background(if (on) MaterialTheme.colorScheme.onSurface else Color.Transparent).clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(l, style = MaterialTheme.typography.titleSmall, color = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Label(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun Big(t: String, size: Int = 26) = Text(t, style = MaterialTheme.typography.titleLarge.copy(fontSize = size.sp, lineHeight = (size + 4).sp, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum"), maxLines = 1, overflow = TextOverflow.Ellipsis)

@Composable
private fun Rule() = Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

private fun levelText(ctx: android.content.Context, l: Level) = ctx.getString(when (l) { Level.OK -> R.string.status_short_ok; Level.NOTE -> R.string.status_short_note; Level.DANGER -> R.string.status_short_danger })

/** The big steps tile: count, goal, progress and (on Home) the hourly bars. */
@Composable
fun StepsCard(me: Profile, hourly: Boolean, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
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
    val big = (LocalConfiguration.current.screenWidthDp * 0.26f).coerceIn(56f, 96f)
    var granted by remember { mutableStateOf(StepService.canRun(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = StepService.canRun(ctx) }
    LaunchedEffect(granted) { if (granted) StepService.start(ctx) }
    if (!StepService.hasSensor(ctx)) {
        Block { Text(s(R.string.no_sensor)) }
    } else if (!granted) {
        Block {
            Text(s(R.string.steps_permission))
            Button(onClick = { ask.launch(StepService.perms()) }) { Text(s(R.string.enable)) }
        }
    }
    Block(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier, fill = steps.toFloat() / me.dailyGoal.coerceAtLeast(1)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s(R.string.steps), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(String.format(Locale.getDefault(), "%d%%", (steps * 100L / me.dailyGoal.coerceAtLeast(1)).toInt()), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        }
        Text(
            String.format(Locale.getDefault(), "%,d", steps),
            modifier = Modifier.graphicsLayer { scaleX = press.value; scaleY = press.value; transformOrigin = TransformOrigin(0f, 1f) },
            fontSize = big.sp, lineHeight = big.sp, letterSpacing = (-4).sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false,
        )
        Label(ctx.getString(R.string.goal_n, me.dailyGoal) + "  /  " + distanceText(ctx, me.metres(steps)))
        SegBar(steps.toFloat() / me.dailyGoal, 12)
        if (hourly) {
            val hrs = me.hoursOf(today)
            HourBars(hrs, LocalTime.now().hour, s(R.string.hourly_desc)) { h ->
                String.format(Locale.US, "%02d:00 - %02d:59", h, h) + "   " + ctx.getString(R.string.steps) + ": " + String.format(Locale.getDefault(), "%,d", hrs[h])
            }
        }
    }
}

@Composable
private fun HomeTile(label: String, value: String, sub: String, frac: Float, segs: Int, modifier: Modifier, onClick: () -> Unit) {
    Block(modifier.clickable(onClick = onClick)) {
        Label(label)
        Big(value)
        if (segs > 0) SegBar(frac, segs)
        Label(sub)
    }
}

/** Home: today at a glance. Each tile opens its own page. */
@Composable
fun Home(me: Profile, go: (Int, Int) -> Unit, page: (String) -> Unit) {
    val ctx = LocalContext.current
    val start = dayStart()
    StepsCard(me, true) { go(1, 0) }
    val water = me.since(T.WATER, start).sumOf { it.a }.toInt()
    val goalB = (me.waterGoal / 750).coerceAtLeast(1)
    val sleep = me.last(T.SLEEP)
    val meals = me.since(T.MEAL, start)
    val glu = me.last(T.GLU)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeTile(s(R.string.t_water), ctx.getString(R.string.of_n, water / 750, goalB), s(R.string.bottles_word), water / 750f / goalB, goalB.coerceAtMost(8), Modifier.weight(1f)) { go(2, 2) }
        HomeTile(
            s(R.string.last_sleep), if (sleep == null) s(R.string.none) else durationText(ctx, sleep.a),
            if (sleep == null) s(R.string.goal) + " " + ctx.getString(R.string.f_hours, me.sleepGoal.toDouble())
            else if (sleep.a < me.sleepGoal) ctx.getString(R.string.short_by, durationText(ctx, me.sleepGoal - sleep.a)) else s(R.string.goal_met),
            ((sleep?.a ?: 0.0) / me.sleepGoal).toFloat(), 8, Modifier.weight(1f),
        ) { go(2, 1) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HomeTile(s(R.string.calories), String.format(Locale.getDefault(), "%,d", meals.sumOf { it.b }.toInt()), ctx.getString(R.string.f_carbs, meals.sumOf { it.a }.toInt()), 0f, 0, Modifier.weight(1f)) { go(2, 2) }
        HomeTile(
            s(R.string.t_glu), if (glu == null) s(R.string.none) else glu.a.toInt().toString() + " " + s(R.string.unit_mgdl),
            if (glu == null) s(R.string.add) else levelText(ctx, level(glu)), 0f, 0, Modifier.weight(1f),
        ) { go(2, 0) }
    }
    val mins = LocalTime.now().let { it.hour * 60 + it.minute }
    val next = me.reminders.filter { it.on }.minByOrNull { ((it.hour * 60 + it.minute) - mins + 1440) % 1440 }
    Block(Modifier.clickable { page("reminders") }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(s(R.string.next_reminder))
                Text(next?.label ?: s(R.string.none), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (next != null) Big(String.format(Locale.US, "%02d:%02d", next.hour, next.minute))
        }
    }
}

internal fun dayLabels(): List<String> = DayOfWeek.values().map { it.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()) }

/** Activity > Steps. */
@Composable
fun StepsPage(me: Profile) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val week = weekSteps(me, today)
    StepsCard(me, false)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Block(Modifier.weight(1f)) { Label(s(R.string.streak)); Big(ctx.getString(R.string.f_days, streak(me, today))) }
        Block(Modifier.weight(1f)) { Label(s(R.string.this_week)); Big(String.format(Locale.getDefault(), "%,d", week.sum())) }
    }
    Block {
        Text(s(R.string.by_hour), style = MaterialTheme.typography.titleMedium)
        val hrs = me.hoursOf(today)
        HourBars(hrs, LocalTime.now().hour, s(R.string.hourly_desc)) { h ->
            String.format(Locale.US, "%02d:00 - %02d:59", h, h) + "   " + ctx.getString(R.string.steps) + ": " + String.format(Locale.getDefault(), "%,d", hrs[h])
        }
    }
    Block {
        Text(s(R.string.goal_met), style = MaterialTheme.typography.titleMedium)
        DayStrip(week, me.dailyGoal, dayLabels(), today.dayOfWeek.value - 1, s(R.string.week_desc)) { i ->
            today.with(DayOfWeek.MONDAY).plusDays(i.toLong()).format(DateTimeFormatter.ofPattern("EEE d MMM")) + "   " + ctx.getString(R.string.steps) + ": " + String.format(Locale.getDefault(), "%,d", week[i])
        }
        SegBar(week.sum().toFloat() / me.weeklyGoal.coerceAtLeast(1))
        Label(ctx.getString(R.string.goal_n, me.weeklyGoal) + "  /  " + distanceText(ctx, me.metres(week.sum())))
    }
}

/** Activity > Workouts: pick the programme and level, see today's plan, start. */
@Composable
fun WorkoutsPage(me: Profile, save: (Entry) -> Unit) {
    val ctx = LocalContext.current
    var cat by rememberSaveable { mutableIntStateOf(0) }
    var training by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    // The programme starts the first time this is shown.
    LaunchedEffect(me.fitStart.isBlank()) {
        if (me.fitStart.isBlank()) Store.edit { it.copy(fitStart = LocalDate.now().toString()) }
    }
    val day = Workouts.programDay(me.fitStart)
    val list = Workouts.plan(cat, me.fitLevel, day, Workouts.adjust(me))
    val done = me.since(T.WORK, dayStart()).sumOf { it.a }.toInt()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(R.string.cat_flat, R.string.cat_build).forEachIndexed { i, label ->
            Column(
                Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, if (cat == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                    .clickable { cat = i }.padding(16.dp),
            ) { Big(s(label), 22) }
        }
    }
    Seg(listOf(s(R.string.level_easy), s(R.string.level_mid), s(R.string.level_hard)), me.fitLevel) { l -> Store.edit { it.copy(fitLevel = l) } }
    Block {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s(R.string.workout_today), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(ctx.getString(R.string.program_day, day), style = MaterialTheme.typography.titleSmall)
        }
        SegBar(done.toFloat() / list.size.coerceAtLeast(1), list.size.coerceIn(1, 12))
        if (Workouts.isRecovery(day)) Label(s(R.string.recovery_day))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column { Label(s(R.string.drills)); Big(list.size.toString()) }
            Column { Label(s(R.string.time_word)); Big(ctx.getString(R.string.drive_min, (list.sumOf { Workouts.allocSeconds(it) } + 30) / 60)) }
            Column { Label(s(R.string.done_today)); Big(done.coerceAtMost(list.size).toString()) }
        }
        list.forEach { pl ->
            Row(Modifier.fillMaxWidth()) {
                Text(s(pl.drill.name), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(ctx.getString(if (pl.drill.seconds) R.string.sets_secs else R.string.sets_reps, pl.sets, pl.count), style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Button(onClick = { training = true }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.start_workout)) }
    if (Workouts.isRecovery(day + 1)) Block {
        Text(s(R.string.tomorrow), style = MaterialTheme.typography.titleMedium)
        Label(s(R.string.recovery_day))
    }
    TextButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.restart_program)) }
    if (training) WorkoutDialog(cat, list, { training = false }) { it.forEach(save); training = false }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        text = { Text(s(R.string.restart_confirm)) },
        confirmButton = { TextButton(onClick = { Store.edit { it.copy(fitStart = LocalDate.now().toString()) }; confirm = false }) { Text(s(R.string.restart_yes)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(s(R.string.cancel)) } },
    )
}

@Composable
private fun VitalCard(
    title: String, value: String, status: Level?, extra: String?, values: List<Float>, open: Boolean, onToggle: () -> Unit,
    detail: @Composable ColumnScope.() -> Unit,
) {
    val ctx = LocalContext.current
    Block {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(title)
                Big(value)
                if (status != null) Text(levelText(ctx, status), style = MaterialTheme.typography.titleSmall, color = if (status == Level.OK) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                if (extra != null) Label(extra)
            }
            Box(Modifier.width(110.dp)) { Spark(values) }
        }
        if (open) {
            Rule()
            detail()
        }
    }
}

/** Health > Vitals: one card per measure; tap a card for its chart and entry buttons. */
@Composable
fun VitalsPage(me: Profile, onAdd: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableIntStateOf(-1) }
    val toggle: (Int) -> Unit = { open = if (open == it) -1 else it }
    val runImport: () -> Unit = {
        scope.launch {
            try {
                toast(ctx, ctx.getString(R.string.imported, Hc.sync(ctx)))
            } catch (e: Exception) {
                toast(ctx, ctx.getString(R.string.hc_failed))
            }
        }
    }
    val hcAsk = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got -> if (got.isNotEmpty()) runImport() }
    val none = s(R.string.none)

    val hr = me.last(T.HR)
    VitalCard(s(R.string.t_hr), hr?.value(ctx) ?: none, hr?.let { level(it) }, hr?.let { ctx.getString(R.string.measured_at, whenText(it.ts)) }, me.series(T.HR), open == 0, { toggle(0) }) {
        LineChart(me.series(T.HR))
        Button(onClick = { onAdd("hr") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.measure)) }
        OutlinedButton(onClick = { onAdd("hrm") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add)) }
    }
    val bpL = me.last(T.BP)
    VitalCard(s(R.string.t_bp), bpL?.value(ctx) ?: none, bpL?.let { level(it) }, bpL?.let { ctx.getString(R.string.measured_at, whenText(it.ts)) }, me.series(T.BP), open == 1, { toggle(1) }) {
        val bpE = me.entries.filter { it.type == T.BP }.sortedBy { it.ts }.takeLast(7)
        val bp = bpE.map { it.a.toFloat() to it.b.toFloat() }
        if (bp.isNotEmpty()) RangeBars(bp, s(R.string.bp_chart_desc)) { i -> whenText(bpE[i].ts) + "   " + bpE[i].value(ctx) }
        Label(s(R.string.bp_hint))
        Button(onClick = { onAdd("bp") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add)) }
    }
    val glu = me.last(T.GLU)
    VitalCard(
        s(R.string.t_glu), glu?.value(ctx) ?: none, glu?.let { level(it) },
        if (glu != null && glu.b.toInt() == 0 && glu.a > me.fastingMax) ctx.getString(R.string.above_target, me.fastingMax) else ctx.getString(R.string.fasting_limit, me.fastingMax),
        me.series(T.GLU), open == 2, { toggle(2) },
    ) {
        val gl = me.series(T.GLU)
        if (gl.isNotEmpty()) DotBand(gl, 70f, me.fastingMax.toFloat(), s(R.string.glu_chart_desc))
        Label(s(R.string.glu_hint))
        Button(onClick = { onAdd("glu") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add)) }
    }
    val w = me.last(T.WEIGHT)
    VitalCard(s(R.string.t_weight), w?.value(ctx) ?: none, null, me.bmi()?.let { s(R.string.bmi) + " " + String.format(Locale.US, "%.1f", it) }, me.series(T.WEIGHT), open == 3, { toggle(3) }) {
        LineChart(me.series(T.WEIGHT))
        Button(onClick = { onAdd("weight") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add)) }
    }
    Block {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(s(R.string.t_hba1c))
                Big(me.last(T.HBA1C)?.value(ctx) ?: none)
                Label(s(R.string.hba1c_target) + " " + String.format(Locale.US, "%.1f %%", me.hba1cTarget / 10.0))
            }
            TextButton(onClick = { onAdd("a1c") }) { Text(s(R.string.update_short)) }
        }
    }
    Block(Modifier.clickable {
        if (!Hc.available(ctx)) toast(ctx, ctx.getString(R.string.hc_missing)) else scope.launch {
            try {
                if (Hc.granted(ctx)) runImport() else hcAsk.launch(Hc.perms)
            } catch (e: Exception) {
                toast(ctx, ctx.getString(R.string.hc_failed))
            }
        }
    }) {
        Text(s(R.string.import_watch), style = MaterialTheme.typography.titleSmall)
        Label(s(R.string.hc_desc_short))
    }
}

/** Health > Food: today's meals and water. */
@Composable
fun FoodPage(me: Profile, onAdd: (String) -> Unit, save: (Entry) -> Unit) {
    val ctx = LocalContext.current
    val meals = me.since(T.MEAL, dayStart()).sortedByDescending { it.ts }
    Block {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) { Label(s(R.string.cal_today)); Big(ctx.getString(R.string.f_kcal, meals.sumOf { it.b }.toInt())) }
            Column(horizontalAlignment = Alignment.End) { Label(s(R.string.carbs_today)); Big(ctx.getString(R.string.f_carbs, meals.sumOf { it.a }.toInt())) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { onAdd("food") }, modifier = Modifier.weight(1f)) { Text(s(R.string.scan_food), maxLines = 1) }
            OutlinedButton(onClick = { onAdd("meal") }, modifier = Modifier.weight(1f)) { Text(s(R.string.add_meal), maxLines = 1) }
        }
    }
    Block {
        Text(s(R.string.meals), style = MaterialTheme.typography.titleMedium)
        if (meals.isEmpty()) Label(s(R.string.none))
        meals.forEachIndexed { i, e ->
            if (i > 0) Rule()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.note.ifBlank { s(R.string.t_meal) }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Label(ng.pixelslimited.health.data.timeText(e.ts) + "  /  " + ctx.getString(R.string.f_carbs, e.a.toInt()))
                }
                if (e.b > 0) Text(e.b.toInt().toString(), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    val water = me.since(T.WATER, dayStart()).sumOf { it.a }.toInt()
    val goalB = (me.waterGoal / 750).coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary
    Block {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s(R.string.t_water), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Label(ctx.getString(R.string.of_n, water / 750, goalB) + " " + s(R.string.bottles_word))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(goalB.coerceAtMost(8)) { i ->
                val part = (water / 750f - i).coerceIn(0f, 1f)
                Box(Modifier.width(30.dp).height(64.dp).border(2.dp, accent), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height((64 * part).dp).background(accent))
                }
            }
        }
        Button(onClick = { save(Entry(T.WATER, now(), 750.0)) }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add_glass)) }
        TextButton(onClick = { onAdd("water") }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.other_amount)) }
    }
}

@Composable
private fun MoreGroup(title: String, rows: List<Pair<String, () -> Unit>>) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Label(title)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).border(2.dp, MaterialTheme.colorScheme.outlineVariant).padding(horizontal = 18.dp)) {
        rows.forEachIndexed { i, (label, act) ->
            if (i > 0) Rule()
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = act), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Canvas(Modifier.size(14.dp)) {
                    drawLine(tint, Offset(size.width * 0.3f, 0f), Offset(size.width * 0.8f, size.height / 2), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(tint, Offset(size.width * 0.8f, size.height / 2), Offset(size.width * 0.3f, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** More: everything that is not a daily page. */
@Composable
fun MorePage(go: (Int, Int) -> Unit, page: (String) -> Unit, onUpdate: () -> Unit) {
    MoreGroup(s(R.string.grp_travel), listOf(s(R.string.places_title) to { go(1, 2) }, s(R.string.drive_title) to { ng.pixelslimited.health.drive.Drive.openManual() }))
    MoreGroup(s(R.string.grp_records), listOf(s(R.string.more_reports) to { page("reports") }, s(R.string.reminders) to { page("reminders") }))
    MoreGroup(s(R.string.grp_profile), listOf(s(R.string.more_profile) to { page("settings") }, s(R.string.targets) to { page("settings") }, s(R.string.emergency) to { page("settings") }))
    MoreGroup(s(R.string.grp_app), listOf(s(R.string.security) to { page("settings") }, s(R.string.more_lang) to { page("settings") }, s(R.string.keep_alive) to { page("settings") }, s(R.string.check_updates) to onUpdate))
}

@Composable
fun RemindersPage(me: Profile) {
    val ctx = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    if (me.reminders.isEmpty()) Label(s(R.string.none))
    me.reminders.sortedBy { it.hour * 60 + it.minute }.forEach { r ->
        Block {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Big(String.format(Locale.US, "%02d:%02d", r.hour, r.minute), 28)
                    Text(r.label, style = MaterialTheme.typography.titleSmall)
                    Label(s(R.string.sound_vibration))
                }
                Switch(checked = r.on, onCheckedChange = { on ->
                    val changed = r.copy(on = on)
                    Store.edit { p -> p.copy(reminders = p.reminders.map { if (it.id == r.id) changed else it }) }
                    Reminders.schedule(ctx, changed)
                })
                IconButton(onClick = {
                    Reminders.cancel(ctx, r)
                    Store.edit { p -> p.copy(reminders = p.reminders.filter { it.id != r.id }) }
                }) { Icon(Icons.Outlined.Delete, contentDescription = s(R.string.delete)) }
            }
        }
    }
    Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.add_reminder)) }
    if (!Reminders.canExact(ctx)) Block {
        Text(s(R.string.exact_alarm_desc), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }) { Text(s(R.string.allow_btn)) }
    }
    if (adding) RulerDialog(
        s(R.string.add_reminder),
        listOf(RulerSpec(s(R.string.hour), 0..23, 1, 8), RulerSpec(s(R.string.minute), 0..55, 5, 0)),
        note = true,
        onClose = { adding = false },
    ) { v, note ->
        val r = Reminder((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), note.ifBlank { ctx.getString(R.string.reminder_default) }, v[0], v[1])
        Store.edit { it.copy(reminders = it.reminders + r) }
        Reminders.schedule(ctx, r)
        adding = false
    }
}

/** Reports: every logged reading, newest first, with delete. */
@Composable
fun RecentEntries(me: Profile) {
    val ctx = LocalContext.current
    Block {
        Text(s(R.string.recent), style = MaterialTheme.typography.titleMedium)
        val recent = me.entries.sortedByDescending { it.ts }.take(40)
        if (recent.isEmpty()) Label(s(R.string.none))
        recent.forEachIndexed { i, e ->
            if (i > 0) Rule()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(typeName(ctx, e.type) + ": " + e.value(ctx), style = MaterialTheme.typography.titleSmall)
                    Label(whenText(e.ts))
                }
                IconButton(onClick = { Store.edit { p -> p.copy(entries = p.entries - e) } }) { Icon(Icons.Outlined.Delete, contentDescription = s(R.string.delete)) }
            }
        }
    }
}
