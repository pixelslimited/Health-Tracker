package ng.pixelslimited.health.ui

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Entry
import ng.pixelslimited.health.data.T
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** One limb: m=0 joint angles in degrees (x, y); m=1 reaches a fixed point; m=2 reaches a point relative to the head; s picks the bend side. */
class Lb(val m: Int, val x: Float, val y: Float, val s: Float)

/** Side-view skeleton pose (y down, angles in degrees, 0 = right, 90 = down): pelvis, torso, head, near and far arm, near and far leg, foot angles. */
class Pose(
    val px: Float, val py: Float, val tor: Float, val head: Float,
    val a1: Lb, val a2: Lb, val l1: Lb, val l2: Lb, val f1: Float, val f2: Float,
)

class Drill(
    val id: String, val name: Int, val how: Int, val result: Int,
    val sets: Int, val count: Int, val seconds: Boolean,
    val keys: List<Pose>, val ms: Int, val face: Offset, val zoom: Float = 1f,
    val bench: Pair<Offset, Offset>? = null,
)

object Workouts {
    /** Time allocated to one exercise: working time for every set plus 30 s rest between sets (about 3 s per rep). */
    fun allocSeconds(p: Planned): Int = p.sets * (if (p.drill.seconds) p.count else p.count * 3) + (p.sets - 1) * 30

    private val crunch = Drill("crunch", R.string.ex_crunch, R.string.ex_crunch_how, R.string.ex_crunch_res, 3, 15, false,
        listOf(
        Pose(0.5000f, 0.8350f, 180.0000f, 180.0000f,
            Lb(2, -0.0300f, 0.0350f, -1.0000f), Lb(2, -0.0200f, 0.0300f, -1.0000f), Lb(1, 0.7400f, 0.8550f, -1.0000f), Lb(1, 0.7800f, 0.8550f, -1.0000f), 0.0000f, 0.0000f),
        Pose(0.5000f, 0.8350f, 218.0000f, 212.0000f,
            Lb(2, -0.0300f, 0.0350f, -1.0000f), Lb(2, -0.0200f, 0.0300f, -1.0000f), Lb(1, 0.7400f, 0.8550f, -1.0000f), Lb(1, 0.7800f, 0.8550f, -1.0000f), 0.0000f, 0.0000f)),
        3200, Offset(0.0000f, -1.0000f), 1.0000f)
    private val legRaise = Drill("legraise", R.string.ex_legraise, R.string.ex_legraise_how, R.string.ex_legraise_res, 3, 12, false,
        listOf(
        Pose(0.4500f, 0.8350f, 180.0000f, 180.0000f,
            Lb(0, 3.0000f, 0.0000f, 1.0000f), Lb(0, 3.0000f, 0.0000f, 1.0000f), Lb(0, 2.0000f, 2.0000f, 1.0000f), Lb(0, 2.0000f, 2.0000f, 1.0000f), -80.0000f, -80.0000f),
        Pose(0.4500f, 0.8280f, 181.0000f, 181.0000f,
            Lb(0, 3.0000f, 0.0000f, 1.0000f), Lb(0, 3.0000f, 0.0000f, 1.0000f), Lb(0, -88.0000f, -88.0000f, 1.0000f), Lb(0, -88.0000f, -88.0000f, 1.0000f), -165.0000f, -165.0000f)),
        4000, Offset(0.0000f, -1.0000f), 1.0000f)
    private val plank = Drill("plank", R.string.ex_plank, R.string.ex_plank_how, R.string.ex_plank_res, 3, 30, true,
        listOf(
        Pose(0.5487f, 0.6668f, -19.1145f, -31.1145f,
            Lb(1, 0.7800f, 0.8540f, -1.0000f), Lb(1, 0.8000f, 0.8540f, -1.0000f), Lb(1, 0.1500f, 0.8050f, 1.0000f), Lb(1, 0.1700f, 0.8050f, 1.0000f), 85.0000f, 85.0000f),
        Pose(0.5487f, 0.6729f, -18.3299f, -30.3299f,
            Lb(1, 0.7800f, 0.8540f, -1.0000f), Lb(1, 0.8000f, 0.8540f, -1.0000f), Lb(1, 0.1500f, 0.8050f, 1.0000f), Lb(1, 0.1700f, 0.8050f, 1.0000f), 85.0000f, 85.0000f)),
        3000, Offset(0.0000f, 1.0000f), 1.0000f)
    private val climber = Drill("climber", R.string.ex_climber, R.string.ex_climber_how, R.string.ex_climber_res, 3, 30, true,
        listOf(
        Pose(0.5487f, 0.6588f, -19.1145f, -31.1145f,
            Lb(1, 0.7800f, 0.8540f, -1.0000f), Lb(1, 0.8000f, 0.8540f, -1.0000f), Lb(1, 0.5800f, 0.7900f, 1.0000f), Lb(1, 0.1500f, 0.8050f, 1.0000f), 85.0000f, 85.0000f),
        Pose(0.5487f, 0.6588f, -19.1145f, -31.1145f,
            Lb(1, 0.7800f, 0.8540f, -1.0000f), Lb(1, 0.8000f, 0.8540f, -1.0000f), Lb(1, 0.1500f, 0.8050f, 1.0000f), Lb(1, 0.5800f, 0.7900f, 1.0000f), 85.0000f, 85.0000f)),
        1200, Offset(0.0000f, 1.0000f), 1.0000f)
    private val flutter = Drill("flutter", R.string.ex_flutter, R.string.ex_flutter_how, R.string.ex_flutter_res, 3, 30, true,
        listOf(
        Pose(0.4200f, 0.8350f, 190.0000f, 195.0000f,
            Lb(0, 3.0000f, 0.0000f, 1.0000f), Lb(0, 3.0000f, 0.0000f, 1.0000f), Lb(0, -10.0000f, -10.0000f, 1.0000f), Lb(0, -34.0000f, -34.0000f, 1.0000f), -70.0000f, -70.0000f),
        Pose(0.4200f, 0.8350f, 190.0000f, 195.0000f,
            Lb(0, 3.0000f, 0.0000f, 1.0000f), Lb(0, 3.0000f, 0.0000f, 1.0000f), Lb(0, -34.0000f, -34.0000f, 1.0000f), Lb(0, -10.0000f, -10.0000f, 1.0000f), -70.0000f, -70.0000f)),
        1100, Offset(0.0000f, -1.0000f), 1.0000f)
    private val pushUp = Drill("pushup", R.string.ex_pushup, R.string.ex_pushup_how, R.string.ex_pushup_res, 3, 10, false,
        listOf(
        Pose(0.5487f, 0.6668f, -19.1145f, -29.1145f,
            Lb(1, 0.7800f, 0.8540f, -1.0000f), Lb(1, 0.8000f, 0.8540f, -1.0000f), Lb(1, 0.1500f, 0.8050f, 1.0000f), Lb(1, 0.1700f, 0.8050f, 1.0000f), 85.0000f, 85.0000f),
        Pose(0.5678f, 0.8483f, -20.8978f, -30.8978f,
            Lb(1, 0.7800f, 0.8540f, -1.0000f), Lb(1, 0.8000f, 0.8540f, -1.0000f), Lb(1, 0.1500f, 0.8050f, 1.0000f), Lb(1, 0.1700f, 0.8050f, 1.0000f), 85.0000f, 85.0000f)),
        2600, Offset(0.0000f, 1.0000f), 1.0000f)
    private val squat = Drill("squat", R.string.ex_squat, R.string.ex_squat_how, R.string.ex_squat_res, 3, 15, false,
        listOf(
        Pose(0.5000f, 0.4400f, -90.0000f, -92.0000f,
            Lb(1, 0.7000f, 0.3300f, -1.0000f), Lb(1, 0.6800f, 0.3300f, -1.0000f), Lb(1, 0.5000f, 0.8550f, -1.0000f), Lb(1, 0.4700f, 0.8550f, -1.0000f), 0.0000f, 0.0000f),
        Pose(0.4000f, 0.6050f, -50.0000f, -60.0000f,
            Lb(1, 0.7200f, 0.4600f, -1.0000f), Lb(1, 0.7000f, 0.4600f, -1.0000f), Lb(1, 0.5000f, 0.8550f, -1.0000f), Lb(1, 0.4700f, 0.8550f, -1.0000f), 0.0000f, 0.0000f)),
        3000, Offset(1.0000f, 0.0000f), 1.0000f)
    private val bridge = Drill("bridge", R.string.ex_bridge, R.string.ex_bridge_how, R.string.ex_bridge_res, 3, 15, false,
        listOf(
        Pose(0.4300f, 0.8350f, 180.0000f, 180.0000f,
            Lb(0, 2.0000f, 0.0000f, 1.0000f), Lb(0, 2.0000f, 0.0000f, 1.0000f), Lb(1, 0.6800f, 0.8550f, -1.0000f), Lb(1, 0.7200f, 0.8550f, -1.0000f), 0.0000f, 0.0000f),
        Pose(0.4300f, 0.6000f, 122.0000f, 180.0000f,
            Lb(0, 2.0000f, 0.0000f, 1.0000f), Lb(0, 2.0000f, 0.0000f, 1.0000f), Lb(1, 0.6800f, 0.8550f, -1.0000f), Lb(1, 0.7200f, 0.8550f, -1.0000f), 0.0000f, 0.0000f)),
        2800, Offset(0.0000f, -1.0000f), 1.0000f)
    private val dip = Drill("dip", R.string.ex_dip, R.string.ex_dip_how, R.string.ex_dip_res, 3, 10, false,
        listOf(
        Pose(0.3700f, 0.6000f, -104.5345f, -92.0000f,
            Lb(1, 0.2800f, 0.5850f, 1.0000f), Lb(1, 0.2800f, 0.5850f, 1.0000f), Lb(1, 0.6900f, 0.8550f, -1.0000f), Lb(1, 0.7200f, 0.8550f, -1.0000f), -50.0000f, -50.0000f),
        Pose(0.3700f, 0.7300f, -104.5345f, -92.0000f,
            Lb(1, 0.2800f, 0.5850f, 1.0000f), Lb(1, 0.2800f, 0.5850f, 1.0000f), Lb(1, 0.6900f, 0.8550f, -1.0000f), Lb(1, 0.7200f, 0.8550f, -1.0000f), -50.0000f, -50.0000f)),
        2800, Offset(1.0000f, 0.0000f), 1.0000f, bench = Offset(0.1200f, 0.6200f) to Offset(0.3600f, 0.6200f))
    private val superman = Drill("superman", R.string.ex_superman, R.string.ex_superman_how, R.string.ex_superman_res, 3, 12, false,
        listOf(
        Pose(0.5200f, 0.8350f, 0.0000f, 0.0000f,
            Lb(0, 0.0000f, 0.0000f, 1.0000f), Lb(0, 0.0000f, 0.0000f, 1.0000f), Lb(0, 180.0000f, 180.0000f, 1.0000f), Lb(0, 180.0000f, 180.0000f, 1.0000f), 180.0000f, 180.0000f),
        Pose(0.5200f, 0.8350f, -17.6000f, -22.0000f,
            Lb(0, -28.6000f, -28.6000f, 1.0000f), Lb(0, -28.6000f, -28.6000f, 1.0000f), Lb(0, 199.8000f, 199.8000f, 1.0000f), Lb(0, 199.8000f, 199.8000f, 1.0000f), 180.0000f, 180.0000f)),
        3000, Offset(0.0000f, 1.0000f), 0.8600f)

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

private const val LTOR = 0.27f
private const val LNECK = 0.05f
private const val LTH = 0.215f
private const val LSH = 0.205f
private const val LUA = 0.14f
private const val LFA = 0.13f
private const val GROUND = 0.89f

private val Skin = Color(0xFF5C3926)
private val SkinHi = Color(0xFF87583C)
private val SkinFar = Color(0xFF3C261A)
private val EarShade = Color(0xFF281810)
private val Hair = Color(0xFF14100E)
private val Shorts = Color(0xFF2D3644)
private val ShortsFar = Color(0xFF1E242E)
private val Shoe = Color(0xFFE8E4DA)
private val ShoeFar = Color(0xFF96948E)
private val Sole = Color(0xFF282C32)

private fun mixf(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun mixLb(p: Lb, q: Lb, t: Float) = Lb(p.m, mixf(p.x, q.x, t), mixf(p.y, q.y, t), p.s)
private fun mixPose(p: Pose, q: Pose, t: Float) = Pose(
    mixf(p.px, q.px, t), mixf(p.py, q.py, t), mixf(p.tor, q.tor, t), mixf(p.head, q.head, t),
    mixLb(p.a1, q.a1, t), mixLb(p.a2, q.a2, t), mixLb(p.l1, q.l1, t), mixLb(p.l2, q.l2, t),
    mixf(p.f1, q.f1, t), mixf(p.f2, q.f2, t),
)

/** Pose at cycle position s in [0,1): eased move from each key to the next, wrapping round to the first. */
private fun poseAt(keys: List<Pose>, s: Float): Pose {
    val n = keys.size
    val v = s * n
    val i = kotlin.math.floor(v).toInt() % n
    val f = v - kotlin.math.floor(v)
    val e = (1f - cos(PI.toFloat() * f)) / 2f
    return mixPose(keys[i], keys[(i + 1) % n], e)
}

private fun dirv(deg: Float): Offset {
    val r = deg * PI.toFloat() / 180f
    return Offset(cos(r), sin(r))
}

private fun nrm(a: Offset, b: Offset): Offset {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val l = hypot(dx, dy).coerceAtLeast(1e-6f)
    return Offset(-dy / l, dx / l)
}

/** Two-bone reach from s to t: returns the upper and lower bone angles in degrees. */
private fun ik(s: Offset, t: Offset, l1: Float, l2: Float, bend: Float): Pair<Float, Float> {
    val dx = t.x - s.x
    val dy = t.y - s.y
    val dist = minOf(hypot(dx, dy), l1 + l2 - 1e-4f).coerceAtLeast(1e-3f)
    val base = atan2(dy, dx)
    val a = acos(((l1 * l1 + dist * dist - l2 * l2) / (2f * l1 * dist)).coerceIn(-1f, 1f))
    val a1 = base + bend * a
    val ex = s.x + l1 * cos(a1)
    val ey = s.y + l1 * sin(a1)
    val a2 = atan2(t.y - ey, t.x - ex)
    return Pair(a1 * 180f / PI.toFloat(), a2 * 180f / PI.toFloat())
}

private fun reach(s: Offset, head: Offset, lb: Lb, l1: Float, l2: Float): Pair<Offset, Offset> {
    if (lb.m == 0) {
        val e = s + dirv(lb.x) * l1
        return Pair(e, e + dirv(lb.y) * l2)
    }
    val t = if (lb.m == 1) Offset(lb.x, lb.y) else head + Offset(lb.x, lb.y)
    val (a1, a2) = ik(s, t, l1, l2, lb.s)
    val e = s + dirv(a1) * l1
    return Pair(e, e + dirv(a2) * l2)
}

/** The drill as a looping, eased keyframe animation of a jointed figure with real bone lengths, on a mat. */
@Composable
fun DrillAnimation(d: Drill) {
    val shirt = MaterialTheme.colorScheme.primary
    val shirtDark = colorLerp(shirt, Color.Black, 0.3f)
    val mat = MaterialTheme.colorScheme.outlineVariant
    val tr = rememberInfiniteTransition(label = "drill")
    val s by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(d.ms, easing = LinearEasing), RepeatMode.Restart), label = "s")
    val breath by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart), label = "breath")
    Canvas(Modifier.fillMaxWidth().height(240.dp)) {
        val u = minOf(size.width, size.height)
        val left = (size.width - u) / 2
        val z = d.zoom
        val k = u * z
        fun X(p: Offset) = Offset(left + (0.5f + (p.x - 0.5f) * z) * u, (GROUND + (p.y - GROUND) * z) * u)
        fun poly(pts: List<Offset>, c: Color, al: Float = 1f) {
            val pa = Path()
            pts.forEachIndexed { i, p -> val q = X(p); if (i == 0) pa.moveTo(q.x, q.y) else pa.lineTo(q.x, q.y) }
            pa.close()
            drawPath(pa, c, alpha = al)
        }
        fun circ(p: Offset, r: Float, c: Color, al: Float = 1f) = drawCircle(c, r * k, X(p), alpha = al)
        fun taper(a: Offset, b: Offset, wa: Float, wm: Float, wb: Float, c: Color, al: Float = 1f, tm: Float = 0.4f) {
            val n = nrm(a, b)
            val m = a + (b - a) * tm
            poly(listOf(a + n * (wa / 2), m + n * (wm / 2), b + n * (wb / 2), b - n * (wb / 2), m - n * (wm / 2), a - n * (wa / 2)), c, al)
            circ(a, wa / 2, c, al)
            circ(b, wb / 2, c, al)
        }
        fun lit(a: Offset, b: Offset): Offset {
            val n = nrm(a, b)
            return if (n.x * -0.5f + n.y * -0.85f < 0f) Offset(-n.x, -n.y) else n
        }
        fun shaded(a: Offset, b: Offset, wa: Float, wm: Float, wb: Float, c: Color, hi: Color, tm: Float = 0.4f) {
            taper(a, b, wa, wm, wb, c, 1f, tm)
            val n = lit(a, b)
            taper(a + n * (wa * 0.22f), b + n * (wb * 0.22f), wa * 0.3f, wm * 0.3f, wb * 0.3f, hi, 0.59f, tm)
            taper(a - n * (wa * 0.3f), b - n * (wb * 0.3f), wa * 0.25f, wm * 0.25f, wb * 0.25f, Color.Black, 0.27f, tm)
        }
        fun foot(a: Offset, ft: Offset, c: Color) {
            val heel = a - ft * 0.02f
            val toe = a + ft * 0.085f
            taper(heel, toe, 0.052f, 0.05f, 0.04f, c)
            val nn = nrm(a, toe)
            val sg = if (nn.y > 0f) 1f else -1f
            drawLine(Sole, X(heel + nn * (0.028f * sg)), X(toe + nn * (0.022f * sg)), strokeWidth = 0.012f * k)
        }

        // Pose for this instant, with a slow breath in the chest.
        val br = sin(2f * PI.toFloat() * breath)
        val base = poseAt(d.keys, s)
        val po = Pose(base.px, base.py, base.tor + 0.7f * br, base.head - 0.5f * br, base.a1, base.a2, base.l1, base.l2, base.f1, base.f2)
        val P = Offset(po.px, po.py)
        val N = P + dirv(po.tor) * LTOR
        val H = N + dirv(po.head) * (LNECK + 0.045f)
        val S = N - dirv(po.tor) * 0.035f
        val (E, W) = reach(S, H, po.a1, LUA, LFA)
        val (E2, W2) = reach(S, H, po.a2, LUA, LFA)
        val (K, A) = reach(P, H, po.l1, LTH, LSH)
        val (K2, A2) = reach(P, H, po.l2, LTH, LSH)
        val ft = dirv(po.f1)
        val ft2 = dirv(po.f2)

        // Mat, bench and the shadow the body casts.
        val mp = Path().apply {
            moveTo(0f, (GROUND + 0.04f) * u); lineTo(0.07f * u + left, GROUND * u)
            lineTo(0.93f * u + left, GROUND * u); lineTo(u + left, (GROUND + 0.04f) * u); close()
        }
        drawPath(mp, mat)
        d.bench?.let { (b1, b2) ->
            taper(Offset(b1.x + 0.02f, b1.y + 0.02f), Offset(b1.x + 0.02f, GROUND), 0.022f, 0.022f, 0.022f, ShortsFar)
            taper(Offset(b2.x - 0.02f, b2.y + 0.02f), Offset(b2.x - 0.02f, GROUND), 0.022f, 0.022f, 0.022f, ShortsFar)
            taper(b1, b2, 0.035f, 0.035f, 0.035f, Shorts)
        }
        val xs = listOf(P, N, H, E, W, K, A, K2, A2, E2, W2).map { it.x }
        val cx = (xs.min() + xs.max()) / 2
        val wd = (xs.max() - xs.min()) / 2 + 0.04f
        val sa = X(Offset(cx - wd, GROUND - 0.012f))
        val sb = X(Offset(cx + wd, GROUND + 0.02f))
        drawOval(Color.Black, sa, androidx.compose.ui.geometry.Size(sb.x - sa.x, sb.y - sa.y), alpha = 0.43f)

        // Far arm and leg, darker and behind.
        shaded(S, E2, 0.06f, 0.065f, 0.048f, SkinFar, SkinFar); shaded(E2, W2, 0.048f, 0.052f, 0.036f, SkinFar, SkinFar); circ(W2, 0.034f, SkinFar)
        shaded(P, K2, 0.1f, 0.108f, 0.07f, SkinFar, SkinFar)
        taper(P, P + (K2 - P) * 0.55f, 0.11f, 0.1f, 0.095f, ShortsFar)
        shaded(K2, A2, 0.07f, 0.082f, 0.046f, SkinFar, SkinFar); foot(A2, ft2, ShoeFar)
        // Neck and torso with shirt shading.
        taper(N, H, 0.062f, 0.058f, 0.056f, Skin)
        taper(P, N, 0.115f, 0.105f, 0.145f, shirt, 1f, 0.35f)
        val tn = lit(P, N)
        taper(P + tn * 0.03f, N + tn * 0.04f, 0.03f, 0.03f, 0.04f, Color.White, 0.18f, 0.35f)
        taper(P - tn * 0.04f, N - tn * 0.055f, 0.035f, 0.04f, 0.05f, shirtDark, 0.59f, 0.35f)
        // Near leg with shorts and shoe.
        shaded(P, K, 0.1f, 0.11f, 0.072f, Skin, SkinHi)
        taper(P, P + (K - P) * 0.58f, 0.118f, 0.108f, 0.098f, Shorts)
        taper(P + (K - P) * 0.5f, P + (K - P) * 0.58f, 0.1f, 0.1f, 0.098f, ShortsFar, 0.63f)
        shaded(K, A, 0.072f, 0.085f, 0.046f, Skin, SkinHi); foot(A, ft, Shoe)
        // Near arm and hand.
        shaded(S, E, 0.062f, 0.068f, 0.05f, Skin, SkinHi); shaded(E, W, 0.05f, 0.054f, 0.037f, Skin, SkinHi)
        circ(W, 0.036f, Skin); circ(W, 0.02f, SkinHi, 0.35f)
        // Head: face turned toward the preferred side, short hair, ear, eye and brow.
        val upv = (H - N).let { v -> val l = hypot(v.x, v.y).coerceAtLeast(1e-6f); Offset(v.x / l, v.y / l) }
        val c1 = Offset(upv.y, -upv.x)
        val c2 = Offset(-upv.y, upv.x)
        val face = if (c1.x * d.face.x + c1.y * d.face.y >= c2.x * d.face.x + c2.y * d.face.y) c1 else c2
        circ(H, 0.057f, Skin)
        circ(H - face * 0.035f + upv * 0.02f, 0.026f, EarShade, 0.47f)
        circ(H - face * 0.02f + upv * 0.024f, 0.046f, Hair)
        circ(H - face * 0.035f - upv * 0.004f, 0.034f, Hair)
        circ(H + face * 0.045f - upv * 0.002f, 0.014f, Skin)
        val eye = H + face * 0.025f + upv * 0.008f
        circ(eye, 0.0085f, Color(0xFFF5F5F5)); circ(eye + face * 0.003f, 0.0045f, Color(0xFF0F0C0A))
        val brow = eye + upv * 0.016f
        drawLine(Hair, X(brow - face * 0.012f), X(brow + face * 0.012f), strokeWidth = 0.007f * k)
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
private fun DrillTimer(total: Int) {
    val ctx = LocalContext.current
    var left by remember { mutableIntStateOf(total) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running && left > 0) {
            delay(1000)
            left--
        }
        if (running && left == 0) {
            running = false
            @Suppress("DEPRECATION")
            (ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(s(R.string.timer_alloc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (left == 0) s(R.string.timer_done) else String.format(java.util.Locale.US, "%d:%02d", left / 60, left % 60),
            style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
        )
        LinearProgressIndicator(progress = { 1f - left.toFloat() / total }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (left == 0) left = total; running = !running }, modifier = Modifier.weight(1f)) {
                Text(s(if (running) R.string.timer_pause else R.string.timer_start))
            }
            OutlinedButton(onClick = { running = false; left = total }, modifier = Modifier.weight(1f)) { Text(s(R.string.timer_reset)) }
        }
    }
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
                androidx.compose.runtime.key(i) { DrillTimer(Workouts.allocSeconds(pl)) }
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
