package ng.pixelslimited.health.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Entry
import ng.pixelslimited.health.data.T
import java.time.LocalDate

/** Side-view stick figure: head, neck, pelvis, elbow, wrist, knee, ankle, in a unit square (y grows downward). */
class Pose(val h: Offset, val n: Offset, val p: Offset, val e: Offset, val w: Offset, val k: Offset, val a: Offset)

private fun o(x: Float, y: Float) = Offset(x, y)
private fun pose(h: Offset, n: Offset, p: Offset, e: Offset, w: Offset, k: Offset, a: Offset) = Pose(h, n, p, e, w, k, a)

class Drill(
    val id: String, val name: Int, val how: Int, val result: Int,
    val sets: Int, val count: Int, val seconds: Boolean,
    val from: Pose, val to: Pose, val bench: Pair<Offset, Offset>? = null,
)

object Workouts {
    private val crunch = Drill("crunch", R.string.ex_crunch, R.string.ex_crunch_how, R.string.ex_crunch_res, 3, 15, false,
        pose(o(.04f, .83f), o(.12f, .85f), o(.38f, .85f), o(.1f, .72f), o(.04f, .8f), o(.56f, .68f), o(.66f, .88f)),
        pose(o(.14f, .54f), o(.2f, .6f), o(.38f, .85f), o(.2f, .5f), o(.13f, .52f), o(.56f, .68f), o(.66f, .88f)))
    private val legRaise = Drill("legraise", R.string.ex_legraise, R.string.ex_legraise_how, R.string.ex_legraise_res, 3, 12, false,
        pose(o(.04f, .86f), o(.12f, .87f), o(.38f, .87f), o(.25f, .88f), o(.4f, .88f), o(.58f, .87f), o(.78f, .87f)),
        pose(o(.04f, .86f), o(.12f, .87f), o(.38f, .87f), o(.25f, .88f), o(.4f, .88f), o(.38f, .67f), o(.38f, .47f)))
    private val plank = Drill("plank", R.string.ex_plank, R.string.ex_plank_how, R.string.ex_plank_res, 3, 30, true,
        pose(o(.88f, .52f), o(.78f, .58f), o(.52f, .7f), o(.78f, .88f), o(.92f, .88f), o(.32f, .79f), o(.14f, .87f)),
        pose(o(.88f, .5f), o(.78f, .56f), o(.52f, .66f), o(.78f, .88f), o(.92f, .88f), o(.32f, .76f), o(.14f, .87f)))
    private val climber = Drill("climber", R.string.ex_climber, R.string.ex_climber_how, R.string.ex_climber_res, 3, 30, true,
        pose(o(.88f, .55f), o(.8f, .6f), o(.54f, .71f), o(.8f, .74f), o(.8f, .88f), o(.34f, .79f), o(.15f, .87f)),
        pose(o(.88f, .55f), o(.8f, .6f), o(.54f, .71f), o(.8f, .74f), o(.8f, .88f), o(.66f, .78f), o(.52f, .87f)))
    private val flutter = Drill("flutter", R.string.ex_flutter, R.string.ex_flutter_how, R.string.ex_flutter_res, 3, 30, true,
        pose(o(.04f, .86f), o(.12f, .87f), o(.38f, .87f), o(.25f, .88f), o(.4f, .88f), o(.58f, .86f), o(.78f, .83f)),
        pose(o(.04f, .86f), o(.12f, .87f), o(.38f, .87f), o(.25f, .88f), o(.4f, .88f), o(.58f, .8f), o(.78f, .7f)))
    private val pushUp = Drill("pushup", R.string.ex_pushup, R.string.ex_pushup_how, R.string.ex_pushup_res, 3, 10, false,
        pose(o(.88f, .55f), o(.8f, .6f), o(.54f, .71f), o(.8f, .74f), o(.8f, .88f), o(.34f, .79f), o(.15f, .87f)),
        pose(o(.87f, .74f), o(.78f, .76f), o(.5f, .81f), o(.68f, .76f), o(.8f, .88f), o(.3f, .84f), o(.15f, .87f)))
    private val squat = Drill("squat", R.string.ex_squat, R.string.ex_squat_how, R.string.ex_squat_res, 3, 15, false,
        pose(o(.5f, .13f), o(.5f, .22f), o(.5f, .5f), o(.58f, .33f), o(.71f, .34f), o(.52f, .7f), o(.5f, .9f)),
        pose(o(.59f, .34f), o(.55f, .42f), o(.45f, .68f), o(.66f, .43f), o(.8f, .43f), o(.63f, .74f), o(.5f, .9f)))
    private val bridge = Drill("bridge", R.string.ex_bridge, R.string.ex_bridge_how, R.string.ex_bridge_res, 3, 15, false,
        pose(o(.04f, .85f), o(.12f, .86f), o(.38f, .86f), o(.25f, .88f), o(.4f, .88f), o(.56f, .68f), o(.66f, .88f)),
        pose(o(.04f, .85f), o(.12f, .86f), o(.38f, .6f), o(.25f, .88f), o(.4f, .88f), o(.56f, .64f), o(.66f, .88f)))
    private val dip = Drill("dip", R.string.ex_dip, R.string.ex_dip_how, R.string.ex_dip_res, 3, 10, false,
        pose(o(.3f, .16f), o(.3f, .26f), o(.4f, .56f), o(.3f, .42f), o(.3f, .58f), o(.6f, .62f), o(.8f, .88f)),
        pose(o(.3f, .32f), o(.3f, .42f), o(.4f, .7f), o(.2f, .5f), o(.3f, .58f), o(.6f, .72f), o(.8f, .88f)),
        bench = o(.14f, .6f) to o(.46f, .6f))
    private val superman = Drill("superman", R.string.ex_superman, R.string.ex_superman_how, R.string.ex_superman_res, 3, 12, false,
        pose(o(.8f, .85f), o(.72f, .86f), o(.46f, .87f), o(.84f, .87f), o(.96f, .87f), o(.26f, .87f), o(.06f, .87f)),
        pose(o(.8f, .72f), o(.7f, .8f), o(.46f, .87f), o(.84f, .72f), o(.96f, .66f), o(.26f, .8f), o(.06f, .72f)))

    private val flat = listOf(crunch, legRaise, plank, climber, flutter)
    private val build = listOf(pushUp, squat, bridge, dip, superman)

    /** One drill with its sets and count for a given day and difficulty. */
    class Planned(val drill: Drill, val sets: Int, val count: Int)

    /** Programme day number: 1 on the day it starts, then counting up. */
    fun programDay(start: String, today: LocalDate = LocalDate.now()): Int =
        if (start.isBlank()) 1 else (java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(start), today) + 1).toInt().coerceAtLeast(1)

    fun isRecovery(day: Int) = day % 7 == 0

    /**
     * Harder with each programme day (about 4% more a day, up to 80% more), by difficulty level
     * (more drills, more sets, higher counts), with a lighter recovery day every seventh day.
     */
    /** Net review score from earlier days of this programme (+ too easy, - too hard); today's review counts from tomorrow. */
    fun adjust(me: ng.pixelslimited.health.data.Profile, today: LocalDate = LocalDate.now()): Int {
        val from = if (me.fitStart.isBlank()) 0L else ng.pixelslimited.health.data.dayStart(LocalDate.parse(me.fitStart))
        val to = ng.pixelslimited.health.data.dayStart(today)
        return me.entries.filter { it.type == T.REVIEW && it.ts >= from && it.ts < to }.sumOf { it.a }.toInt().coerceIn(-6, 6)
    }

    fun plan(cat: Int, level: Int, day: Int, adj: Int = 0): List<Planned> {
        val list = if (cat == 0) flat else build
        val start = (day - 1) % list.size
        val base = listOf(0.7f, 1f, 1.3f)[level.coerceIn(0, 2)]
        val ramp = (1f + 0.04f * (day - 1)).coerceAtMost(1.8f)
        val factor = base * ramp * (1f + 0.08f * adj).coerceAtLeast(0.4f) * (if (isRecovery(day)) 0.6f else 1f)
        val drills = 3 + level.coerceIn(0, 2)
        val sets = 2 + level.coerceIn(0, 2)
        return List(drills) { n ->
            val d = list[(start + n) % list.size]
            val raw = d.count * factor
            val count = if (d.seconds) (Math.round(raw / 5f) * 5).coerceAtLeast(10) else Math.round(raw).coerceAtLeast(5)
            Planned(d, sets, count)
        }
    }
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private val Skin = Color(0xFF5A3825)
private val SkinHi = Color(0xFF7D4F35)
private val SkinFar = Color(0xFF3F271A)
private val Hair = Color(0xFF14100E)
private val Shorts = Color(0xFF2D3644)
private val Shoe = Color(0xFFE8E4DA)

/** The drill as a looping two-pose animation of a figure on a mat: it moves from start to end and back. */
@Composable
fun DrillAnimation(d: Drill) {
    val shirt = MaterialTheme.colorScheme.primary
    val mat = MaterialTheme.colorScheme.outlineVariant
    val t by rememberInfiniteTransition(label = "drill").animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (d.seconds) 600 else 1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "t",
    )
    Canvas(Modifier.fillMaxWidth().height(240.dp)) {
        val side = minOf(size.width, size.height)
        val left = (size.width - side) / 2
        fun at(p: Offset) = Offset(left + p.x * side, p.y * side)
        val f = d.from
        val g = d.to
        val h = at(lerp(f.h, g.h, t)); val n = at(lerp(f.n, g.n, t)); val p = at(lerp(f.p, g.p, t))
        val e = at(lerp(f.e, g.e, t)); val w = at(lerp(f.w, g.w, t)); val k = at(lerp(f.k, g.k, t)); val a = at(lerp(f.a, g.a, t))
        val u = side
        fun seg(s: Offset, t2: Offset, width: Float, c: Color) = drawLine(c, s, t2, strokeWidth = width * u, cap = StrokeCap.Round)
        fun limb(s: Offset, t2: Offset, width: Float) {
            seg(s, t2, width, Skin)
            seg(s + Offset(-0.012f * u, -0.012f * u), t2 + Offset(-0.012f * u, -0.012f * u), width * 0.3f, SkinHi)
        }
        // Floor mat.
        drawRoundRect(mat, Offset(left + 0.02f * u, 0.89f * u), androidx.compose.ui.geometry.Size(0.96f * u, 0.045f * u), androidx.compose.ui.geometry.CornerRadius(0.02f * u))
        d.bench?.let { seg(at(it.first), at(it.second), 0.04f, Shorts) }
        // Far side arm and leg, darker and slightly behind.
        val far = Offset(0.022f * u, -0.014f * u)
        seg(n + far, e + far, 0.045f, SkinFar); seg(e + far, w + far, 0.038f, SkinFar)
        seg(p + far, k + far, 0.07f, SkinFar); seg(k + far, a + far, 0.05f, SkinFar)
        drawCircle(Shoe, 0.04f * u, a + far + Offset(0.02f * u, 0f))
        // Torso in the shirt colour, then near leg, shorts, shoe, near arm and head.
        seg(n, p, 0.105f, shirt)
        seg(n + Offset(-0.014f * u, -0.01f * u), p + Offset(-0.014f * u, -0.01f * u), 0.03f, shirt.copy(alpha = 0.55f).compositeOver(Color.White))
        limb(p, k, 0.075f); limb(k, a, 0.055f)
        seg(p, lerp(p, k, 0.62f), 0.088f, Shorts)
        drawCircle(Shoe, 0.045f * u, a + Offset(0.022f * u, 0f))
        limb(n, e, 0.05f); limb(e, w, 0.042f)
        drawCircle(Skin, 0.05f * u, w)
        val up = (h - n).let { v -> val len = kotlin.math.sqrt(v.x * v.x + v.y * v.y).coerceAtLeast(1f); Offset(v.x / len, v.y / len) }
        drawCircle(Skin, 0.054f * u, h)
        drawCircle(Hair, 0.05f * u, h + up * (0.018f * u))
    }
}

@Composable
fun WorkoutBlock(me: ng.pixelslimited.health.data.Profile, done: Int, onStart: (Int) -> Unit, cat: Int, setCat: (Int) -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    // The programme starts the first time this is shown.
    LaunchedEffect(me.fitStart.isBlank()) {
        if (me.fitStart.isBlank()) ng.pixelslimited.health.data.Store.edit { it.copy(fitStart = LocalDate.now().toString()) }
    }
    val day = Workouts.programDay(me.fitStart)
    val list = Workouts.plan(cat, me.fitLevel, day, Workouts.adjust(me))
    Block {
        MetricHeader(s(R.string.workout_today), ctx.getString(R.string.of_total, done.coerceAtMost(list.size), list.size))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ctx.getString(R.string.program_day, day), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { confirm = true }) { Text(s(R.string.restart_program)) }
        }
        if (Workouts.isRecovery(day)) Text(s(R.string.recovery_day), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = cat == 0, onClick = { setCat(0) }, label = { Text(s(R.string.cat_flat)) })
            FilterChip(selected = cat == 1, onClick = { setCat(1) }, label = { Text(s(R.string.cat_build)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.level_easy, R.string.level_mid, R.string.level_hard).forEachIndexed { i, label ->
                FilterChip(selected = me.fitLevel == i, onClick = { ng.pixelslimited.health.data.Store.edit { it.copy(fitLevel = i) } }, label = { Text(s(label)) })
            }
        }
        list.forEach { pl ->
            Row(Modifier.fillMaxWidth()) {
                Text(s(pl.drill.name), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(
                    ctx.getString(if (pl.drill.seconds) R.string.sets_secs else R.string.sets_reps, pl.sets, pl.count),
                    style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = { onStart(cat) }, modifier = Modifier.fillMaxWidth()) { Text(s(R.string.start_workout)) }
    }
    if (confirm) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirm = false },
        text = { Text(s(R.string.restart_confirm)) },
        confirmButton = {
            TextButton(onClick = {
                ng.pixelslimited.health.data.Store.edit { it.copy(fitStart = LocalDate.now().toString()) }
                confirm = false
            }) { Text(s(R.string.restart_yes)) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(s(R.string.cancel)) } },
    )
}

@Composable
fun WorkoutDialog(cat: Int, list: List<Workouts.Planned>, onClose: () -> Unit, onFinish: (List<Entry>) -> Unit) {
    val ctx = LocalContext.current
    var i by remember { mutableIntStateOf(0) }
    var reviewing by remember { mutableStateOf(false) }
    val pl = list[i]
    val d = pl.drill
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s(if (cat == 0) R.string.cat_flat else R.string.cat_build), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onClose) { Text(s(R.string.close)) }
                }
                if (reviewing) {
                    Text(s(R.string.review_title), style = MaterialTheme.typography.headlineMedium)
                    Text(s(R.string.review_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOf(R.string.rev_easy to 1.0, R.string.rev_ok to 0.0, R.string.rev_hard to -1.0).forEach { (label, score) ->
                        Button(onClick = {
                            val now = System.currentTimeMillis()
                            onFinish(listOf(Entry(T.WORK, now, list.size.toDouble(), cat.toDouble()), Entry(T.REVIEW, now, score)))
                        }, modifier = Modifier.fillMaxWidth()) { Text(s(label)) }
                    }
                    return@Column
                }
                Text(ctx.getString(R.string.drill_of, i + 1, list.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegBar((i + 1f) / list.size, list.size)
                Block { DrillAnimation(d) }
                Text(s(d.name), style = MaterialTheme.typography.headlineMedium)
                Text(
                    ctx.getString(if (d.seconds) R.string.sets_secs else R.string.sets_reps, pl.sets, pl.count),
                    style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                )
                Text(s(R.string.how_to), style = MaterialTheme.typography.titleMedium)
                Text(s(d.how), style = MaterialTheme.typography.bodyLarge)
                Text(s(R.string.expected), style = MaterialTheme.typography.titleMedium)
                Text(s(d.result), style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (i > 0) OutlinedButton(onClick = { i-- }, modifier = Modifier.weight(1f)) { Text(s(R.string.prev_drill)) }
                    if (i < list.size - 1) Button(onClick = { i++ }, modifier = Modifier.weight(1f)) { Text(s(R.string.next_drill)) }
                    else Button(onClick = { reviewing = true }, modifier = Modifier.weight(1f)) { Text(s(R.string.finish_workout)) }
                }
                Text(s(R.string.workout_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
