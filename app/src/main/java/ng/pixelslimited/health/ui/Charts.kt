package ng.pixelslimited.health.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ng.pixelslimited.health.data.Entry
import java.time.Instant
import java.time.ZoneId

/** 0 to 1 once, shortly after the chart first appears, so bars and lines grow in. */
@Composable
fun rememberGrow(): Float {
    var go by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { go = true }
    val v by androidx.compose.animation.core.animateFloatAsState(if (go) 1f else 0f, androidx.compose.animation.core.tween(800), label = "grow")
    return v
}

/** Bordered tile on the soft surface. */
@Composable
fun Block(modifier: Modifier = Modifier, fill: Float = -1f, hero: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    // Hierarchy: only the page's main card carries a frame; every other card is a quiet fill.
    Column(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, CornerCard)
            .topLight(MaterialTheme.colorScheme.background.luminance() < 0.5f)
            .liquid(fill)
            .then(if (hero) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CornerCard) else Modifier)
            .padding(if (hero) 20.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun MetricHeader(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), textAlign = TextAlign.End)
    }
}

@Composable
fun Spark(values: List<Float>) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val pad = 4.dp.toPx()
        if (values.size < 2) {
            drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 2.dp.toPx())
            return@Canvas
        }
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0f } ?: 1f
        val h = size.height - 2 * pad
        val w = size.width - 2 * pad
        fun at(i: Int) = Offset(pad + w * i / (values.size - 1), pad + h - (values[i] - min) / span * h)
        val path = Path()
        values.indices.forEach { i -> if (i == 0) path.moveTo(at(i).x, at(i).y) else path.lineTo(at(i).x, at(i).y) }
        drawPath(path, ink.copy(alpha = 0.45f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(accent, 4.dp.toPx(), at(values.size - 1))
    }
}

/** Tap a bar to read its value; tap it again to hide it. */
@Composable
private fun Tip(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/** 24 bars, one per hour; the current hour carries the accent. Tap an hour to see its steps. */
@Composable
fun HourBars(hours: List<Int>, nowHour: Int, desc: String, info: (Int) -> String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val grow = rememberGrow()
    var sel by remember { mutableIntStateOf(-1) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = desc }
                .pointerInput(hours.size) { detectTapGestures { o -> val i = (o.x / size.width * 24).toInt().coerceIn(0, 23); sel = if (sel == i) -1 else i } },
        ) {
            val top = (hours.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
            val gap = 2.dp.toPx()
            val bw = (size.width - gap * 23) / 24
            hours.forEachIndexed { i, v ->
                val h = (v / top * size.height * grow).coerceAtLeast(2.dp.toPx())
                val c = when {
                    i == sel -> accent
                    i == nowHour -> accent.copy(alpha = 0.6f)
                    v == 0 -> line
                    else -> ink.copy(alpha = if (sel >= 0) 0.35f else 0.75f)
                }
                drawRect(c, Offset(i * (bw + gap), size.height - h), Size(bw, h))
            }
        }
        if (sel in hours.indices) Tip(info(sel))
    }
}

/** Seven days: solid when the goal was met, accent for today, ring otherwise. */
@Composable
fun DayStrip(values: List<Int>, goal: Int, labels: List<String>, today: Int, desc: String, info: (Int) -> String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val line = MaterialTheme.colorScheme.outlineVariant
    var sel by remember { mutableIntStateOf(-1) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(Modifier.fillMaxWidth().semantics { contentDescription = desc }, horizontalArrangement = Arrangement.SpaceBetween) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f).clickable { sel = if (sel == i) -1 else i }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val fill = when {
                    i == today -> MaterialTheme.colorScheme.primary
                    v >= goal -> ink
                    else -> Color.Transparent
                }
                Box(Modifier.size(32.dp).background(fill, CircleShape).border(if (i == sel) 3.dp else 1.5.dp, if (i == sel) MaterialTheme.colorScheme.primary else if (fill == Color.Transparent) line else fill, CircleShape))
                Text(
                    labels[i], style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    fontWeight = if (i == today) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == today) ink else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (sel in values.indices) Tip(info(sel))
    }
}

/** Blood pressure: one bar per reading from diastolic up to systolic. */
@Composable
fun RangeBars(readings: List<Pair<Float, Float>>, desc: String, info: (Int) -> String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val grow = rememberGrow()
    var sel by remember { mutableIntStateOf(-1) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Canvas(
        Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = desc }
            .pointerInput(readings.size) { detectTapGestures { o -> val i = (o.x / size.width * readings.size).toInt().coerceIn(0, readings.size - 1); sel = if (sel == i) -1 else i } },
    ) {
        val lo = 40f
        val hi = 200f
        fun y(v: Float) = size.height - (v.coerceIn(lo, hi) - lo) / (hi - lo) * size.height
        listOf(80f, 120f).forEach { drawLine(line, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 1.dp.toPx()) }
        val n = readings.size
        readings.forEachIndexed { i, (sys, dia) ->
            val x = size.width * (i + 0.5f) / n
            drawLine(if (i == sel || (sel < 0 && i == n - 1)) accent else ink.copy(alpha = 0.7f), Offset(x, y(dia) + (y(sys) - y(dia)) * grow), Offset(x, y(dia)), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
        }
    }
    if (sel in readings.indices) Tip(info(sel))
    }
}

/** Glucose: dots against the target band. */
@Composable
fun DotBand(values: List<Float>, lo: Float, hi: Float, desc: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = desc }) {
        val min = minOf(values.minOrNull() ?: lo, lo) - 10f
        val max = maxOf(values.maxOrNull() ?: hi, hi) + 10f
        fun y(v: Float) = size.height - (v - min) / (max - min) * size.height
        drawRect(line.copy(alpha = 0.6f), Offset(0f, y(hi)), Size(size.width, y(lo) - y(hi)))
        val n = values.size
        values.forEachIndexed { i, v ->
            val x = size.width * (i + 0.5f) / n.coerceAtLeast(1)
            drawCircle(if (i == n - 1) accent else ink, if (i == n - 1) 7.dp.toPx() else 5.dp.toPx(), Offset(x, y(v)))
        }
    }
}

/** Sleep: one floating bar per night from bedtime to wake time (20:00 at the top, 14:00 at the bottom). */
@Composable
fun SleepChart(entries: List<Entry>, desc: String, info: (Int) -> String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val zone = ZoneId.systemDefault()
    val grow = rememberGrow()
    var sel by remember { mutableIntStateOf(-1) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Canvas(
        Modifier.fillMaxWidth().height(132.dp).semantics { contentDescription = desc }
            .pointerInput(entries.size) {
                detectTapGestures { o ->
                    val slots = maxOf(entries.size, 7)
                    val i = (o.x / size.width * slots).toInt()
                    if (i in entries.indices) sel = if (sel == i) -1 else i
                }
            },
    ) {
        val span = 1080f
        listOf(0f, 360f, 720f).forEach { m ->
            val yy = m / span * size.height
            drawLine(line, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1.dp.toPx())
        }
        val n = entries.size.coerceAtLeast(1)
        val bw = (size.width / 7f) * 0.5f
        entries.forEachIndexed { i, e ->
            val bed = Instant.ofEpochMilli(e.ts - (e.a * 3600000).toLong()).atZone(zone)
            val rel = ((bed.hour * 60 + bed.minute - 1200) + 1440) % 1440
            val top = (rel / span * size.height).coerceIn(0f, size.height)
            val len = (e.a.toFloat() * 60f / span * size.height * grow).coerceAtLeast(3.dp.toPx())
            val x = size.width * (i + 0.5f) / maxOf(n, 7)
            drawRect(if (i == sel || (sel < 0 && i == entries.size - 1)) accent else ink.copy(alpha = 0.75f), Offset(x - bw / 2, top), Size(bw, minOf(len, size.height - top)))
        }
    }
    if (sel in entries.indices) Tip(info(sel))
    }
}

@Composable
fun NavIcon(kind: Int, tint: Color) {
    Canvas(Modifier.size(26.dp)) {
        val u = size.minDimension / 24f
        val st = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            0 -> {
                val p = Path()
                p.moveTo(3 * u, 11 * u); p.lineTo(12 * u, 3 * u); p.lineTo(21 * u, 11 * u); p.lineTo(21 * u, 21 * u)
                p.lineTo(15 * u, 21 * u); p.lineTo(15 * u, 14 * u); p.lineTo(9 * u, 14 * u); p.lineTo(9 * u, 21 * u); p.lineTo(3 * u, 21 * u); p.close()
                drawPath(p, tint, style = st)
            }
            1 -> {
                val p = Path()
                p.moveTo(3 * u, 12 * u); p.lineTo(7 * u, 12 * u); p.lineTo(10 * u, 4 * u); p.lineTo(14 * u, 20 * u); p.lineTo(17 * u, 12 * u); p.lineTo(21 * u, 12 * u)
                drawPath(p, tint, style = st)
            }
            2 -> {
                val p = Path()
                p.moveTo(12 * u, 20 * u)
                p.cubicTo(12 * u, 20 * u, 5 * u, 15.5f * u, 5 * u, 10 * u)
                p.cubicTo(5 * u, 6.2f * u, 9.6f * u, 5.2f * u, 12 * u, 7.5f * u)
                p.cubicTo(14.4f * u, 5.2f * u, 19 * u, 6.2f * u, 19 * u, 10 * u)
                p.cubicTo(19 * u, 15.5f * u, 12 * u, 20 * u, 12 * u, 20 * u)
                drawPath(p, tint, style = st)
            }
            else -> listOf(5f, 12f, 19f).forEach { drawCircle(tint, 1.7f * u, Offset(it * u, 12 * u)) }
        }
    }
}

/** Home, Activity, a solid Add button in the centre, Health, More. The open tab carries the accent. */
@Composable
fun BottomBar(labels: List<String>, selected: Int, addLabel: String, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    val on = MaterialTheme.colorScheme.onSurface
    val mut = MaterialTheme.colorScheme.onSurfaceVariant
    val plusBg = MaterialTheme.colorScheme.primary
    val plus = MaterialTheme.colorScheme.onPrimary
    Column(
        Modifier.fillMaxWidth().onGloballyPositioned { Spill.navTop = it.boundsInRoot().top }
            .background(MaterialTheme.colorScheme.background).navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            for (slot in 0..4) {
                if (slot == 2) {
                    Box(Modifier.weight(1f).height(76.dp).clickable(onClick = onAdd).semantics { contentDescription = addLabel }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(56.dp).background(plusBg, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(22.dp)) {
                                val sw = 3.dp.toPx()
                                drawLine(plus, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = sw, cap = StrokeCap.Round)
                                drawLine(plus, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = sw, cap = StrokeCap.Round)
                            }
                        }
                    }
                } else {
                    val i = if (slot < 2) slot else slot - 1
                    val tint = if (i == selected) on else mut
                    Column(
                        Modifier.weight(1f).height(76.dp).clickable { onSelect(i) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        NavIcon(i, tint)
                        Text(labels[i], style = MaterialTheme.typography.labelMedium, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal, color = tint, maxLines = 1)
                        Box(Modifier.width(20.dp).height(3.dp).background(if (i == selected) on else Color.Transparent))
                    }
                }
            }
        }
    }
}

/** Live pulse trace: the line scrolls as the finger signal arrives and a dot rides its newest point. */
@Composable
fun PulseWave(wave: List<Float>, bpm: Int = 60, height: androidx.compose.ui.unit.Dp = 96.dp) {
    val period = (60000 / bpm.coerceIn(30, 200))
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val beat by androidx.compose.animation.core.rememberInfiniteTransition(label = "beat").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(period)), label = "ring",
    )
    val phase by androidx.compose.animation.core.rememberInfiniteTransition(label = "ecg").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(period, easing = androidx.compose.animation.core.LinearEasing)), label = "scroll",
    )
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val k = (size.height / 96.dp.toPx()).coerceIn(0.4f, 1f)
        drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        if (wave.size < 2) {
            // No finger yet: a steady heartbeat trace keeps running, with the dot riding its leading edge.
            fun beat(p: Float): Float {
                fun g(c: Float, w: Float, a: Float) = a * kotlin.math.exp(-(((p - c) / w) * ((p - c) / w)))
                return g(0.15f, 0.04f, 0.12f) + g(0.31f, 0.012f, -0.15f) + g(0.35f, 0.014f, 1f) + g(0.39f, 0.012f, -0.3f) + g(0.62f, 0.06f, 0.25f)
            }
            val n = 120
            val amp = size.height / 2 - 8.dp.toPx() * k
            val idle = Path()
            var tip = Offset.Zero
            for (i in 0..n) {
                val pos = i / n.toFloat() * 2.5f + phase
                val pt = Offset(size.width * i / n, size.height / 2 - beat(pos - kotlin.math.floor(pos)) * amp)
                if (i == 0) idle.moveTo(pt.x, pt.y) else idle.lineTo(pt.x, pt.y)
                tip = pt
            }
            drawPath(idle, ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(accent.copy(alpha = 0.35f * (1f - beat)), (7f + 14f * beat).dp.toPx() * k, tip)
            drawCircle(accent, 7.dp.toPx() * k, tip)
            return@Canvas
        }
        val pad = 8.dp.toPx() * k
        val h = size.height / 2 - pad
        fun at(i: Int) = Offset(size.width * i / 149f, size.height / 2 - wave[i] * h)
        val path = Path()
        wave.indices.forEach { i -> if (i == 0) path.moveTo(at(i).x, at(i).y) else path.lineTo(at(i).x, at(i).y) }
        drawPath(path, ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(accent.copy(alpha = 0.35f * (1f - beat)), (7f + 14f * beat).dp.toPx() * k, at(wave.size - 1))
        drawCircle(accent, 7.dp.toPx() * k, at(wave.size - 1))
    }
}

/**
 * Fills the block from the bottom like a lemon-green liquid up to [level] (steps / goal), with two slowly moving
 * wave surfaces. At 1 or more the block is full and keeps overflowing: the surface brims over the top edge and
 * runs down both sides in drops, faster the further past the goal. Negative turns it off.
 */
@Composable
fun Modifier.liquid(level: Float): Modifier {
    if (level < 0f) return this
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lemon = if (dark) oklch(0.88f, 0.20f, 122f) else oklch(0.80f, 0.20f, 124f)
    val over = level >= 1f
    val target by androidx.compose.animation.core.animateFloatAsState(level.coerceIn(0f, 1f), androidx.compose.animation.core.tween(1400), label = "level")
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "liquid")
    val a by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(4200, easing = androidx.compose.animation.core.LinearEasing)), label = "a")
    val b by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(6300, easing = androidx.compose.animation.core.LinearEasing)), label = "b")
    // The spill speeds up the further past the goal you go (up to twice as fast at double the goal).
    val spillMs = (1800 / level.coerceIn(1f, 2f)).toInt()
    val c by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(spillMs, easing = androidx.compose.animation.core.LinearEasing)), label = "c")
    // Once full, the liquid rises over the brim smoothly instead of jumping.
    val brim by androidx.compose.animation.core.animateFloatAsState(if (over) 1f else 0f, androidx.compose.animation.core.tween(900), label = "brim")
    return drawBehind {
        if (target <= 0.005f) return@drawBehind
        val lift = 6.dp.toPx() * brim
        val base = size.height * (1f - target) - lift
        val amp = 5.dp.toPx() * (if (target >= 0.995f) 0.6f + 0.4f * brim else 1f)
        fun surface(phase: Float, freq: Float, h: Float): Path {
            val p = Path()
            p.moveTo(0f, size.height)
            var x = 0f
            while (x <= size.width + 8f) {
                p.lineTo(x, base + kotlin.math.sin(x / size.width * 6.2832f * freq + phase * 6.2832f) * h)
                x += 8f
            }
            p.lineTo(size.width, size.height)
            p.close()
            return p
        }
        val card = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))) }
        androidx.compose.ui.graphics.drawscope.clipPath(card) {
            drawPath(surface(1f - b, 1.2f, amp * 0.8f), lemon.copy(alpha = 0.22f))
            drawPath(surface(a, 1.6f, amp), lemon.copy(alpha = 0.34f))
        }
        if (brim <= 0.01f) return@drawBehind
        val r = 3.5.dp.toPx()
        // Spill over the brim: little bulges that roll off the top corners.
        for (k in 0..1) {
            val f = (c * 2f + k * 0.5f) % 1f
            val y = -lift - 3.dp.toPx() * kotlin.math.sin(f * 3.1416f)
            drawCircle(lemon.copy(alpha = 0.7f * brim), r, Offset(size.width * (0.5f - 0.5f * f), y))
            drawCircle(lemon.copy(alpha = 0.7f * brim), r, Offset(size.width * (0.5f + 0.5f * f), y))
        }
    }
}
