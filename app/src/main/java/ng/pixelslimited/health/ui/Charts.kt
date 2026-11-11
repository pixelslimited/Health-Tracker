package ng.pixelslimited.health.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
fun Block(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(16.dp),
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
        drawPath(path, ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(accent, 4.dp.toPx(), at(values.size - 1))
    }
}

/** 24 bars, one per hour; the current hour carries the accent. */
@Composable
fun HourBars(hours: List<Int>, nowHour: Int, desc: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val grow = rememberGrow()
    Canvas(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = desc }) {
        val top = (hours.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val gap = 2.dp.toPx()
        val bw = (size.width - gap * 23) / 24
        hours.forEachIndexed { i, v ->
            val h = (v / top * size.height * grow).coerceAtLeast(2.dp.toPx())
            val c = when {
                i == nowHour -> accent
                v == 0 -> line
                else -> ink.copy(alpha = 0.75f)
            }
            drawRect(c, Offset(i * (bw + gap), size.height - h), Size(bw, h))
        }
    }
}

/** Seven days: solid when the goal was met, accent for today, ring otherwise. */
@Composable
fun DayStrip(values: List<Int>, goal: Int, labels: List<String>, today: Int, desc: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().semantics { contentDescription = desc }, horizontalArrangement = Arrangement.SpaceBetween) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val fill = when {
                    i == today -> MaterialTheme.colorScheme.primary
                    v >= goal -> ink
                    else -> Color.Transparent
                }
                Box(Modifier.size(32.dp).background(fill, CircleShape).border(1.5.dp, if (fill == Color.Transparent) line else fill, CircleShape))
                Text(
                    labels[i], style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    fontWeight = if (i == today) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == today) ink else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Blood pressure: one bar per reading from diastolic up to systolic. */
@Composable
fun RangeBars(readings: List<Pair<Float, Float>>, desc: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val grow = rememberGrow()
    Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = desc }) {
        val lo = 40f
        val hi = 200f
        fun y(v: Float) = size.height - (v.coerceIn(lo, hi) - lo) / (hi - lo) * size.height
        listOf(80f, 120f).forEach { drawLine(line, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 1.dp.toPx()) }
        val n = readings.size
        readings.forEachIndexed { i, (sys, dia) ->
            val x = size.width * (i + 0.5f) / n
            drawLine(if (i == n - 1) accent else ink.copy(alpha = 0.7f), Offset(x, y(dia) + (y(sys) - y(dia)) * grow), Offset(x, y(dia)), strokeWidth = 8.dp.toPx(), cap = StrokeCap.Round)
        }
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
fun SleepChart(entries: List<Entry>, desc: String) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val zone = ZoneId.systemDefault()
    val grow = rememberGrow()
    Canvas(Modifier.fillMaxWidth().height(132.dp).semantics { contentDescription = desc }) {
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
            drawRect(if (i == entries.size - 1) accent else ink.copy(alpha = 0.75f), Offset(x - bw / 2, top), Size(bw, minOf(len, size.height - top)))
        }
    }
}

@Composable
fun NavIcon(kind: Int, tint: Color, bg: Color) {
    Canvas(Modifier.size(24.dp)) {
        val sw = 2.dp.toPx()
        when (kind) {
            0 -> {
                drawCircle(tint, size.minDimension / 2 - sw, style = Stroke(sw))
                drawCircle(tint, 3.dp.toPx())
            }
            1 -> {
                val p = Path()
                p.moveTo(0f, size.height * 0.55f)
                p.lineTo(size.width * 0.3f, size.height * 0.55f)
                p.lineTo(size.width * 0.42f, size.height * 0.15f)
                p.lineTo(size.width * 0.62f, size.height * 0.9f)
                p.lineTo(size.width * 0.74f, size.height * 0.55f)
                p.lineTo(size.width, size.height * 0.55f)
                drawPath(p, tint, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            2 -> {
                val r = size.minDimension / 2 - sw / 2
                drawCircle(tint, r)
                drawCircle(bg, r * 0.8f, Offset(size.width / 2 + r * 0.5f, size.height / 2 - r * 0.3f))
            }
            else -> {
                val bw = size.width / 5
                listOf(0.5f, 0.95f, 0.7f).forEachIndexed { i, f ->
                    drawRect(tint, Offset(i * 2 * bw, size.height * (1 - f)), Size(bw, size.height * f))
                }
            }
        }
    }
}

/** Four tabs with a solid Add button in the centre slot. */
@Composable
fun BottomBar(labels: List<String>, selected: Int, addLabel: String, onSelect: (Int) -> Unit, onAdd: () -> Unit) {
    val fg = MaterialTheme.colorScheme.onSurface
    val mut = MaterialTheme.colorScheme.onSurfaceVariant
    val bg = MaterialTheme.colorScheme.background
    Column(Modifier.fillMaxWidth().background(bg).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Row(Modifier.fillMaxWidth()) {
            for (slot in 0..4) {
                if (slot == 2) {
                    Box(
                        Modifier.weight(1f).height(76.dp).clickable(onClick = onAdd).semantics { contentDescription = addLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(48.dp).background(fg, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(20.dp)) {
                                val sw = 3.dp.toPx()
                                drawLine(bg, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = sw)
                                drawLine(bg, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = sw)
                            }
                        }
                    }
                } else {
                    val i = if (slot < 2) slot else slot - 1
                    val on = i == selected
                    val tint = if (on) fg else mut
                    Column(Modifier.weight(1f).height(76.dp).clickable { onSelect(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(if (on) fg else Color.Transparent))
                        Box(Modifier.padding(top = 12.dp, bottom = 2.dp)) { NavIcon(i, tint, bg) }
                        Text(labels[i], style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = tint, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Live pulse trace: the line scrolls as the finger signal arrives and a dot rides its newest point. */
@Composable
fun PulseWave(wave: List<Float>) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val line = MaterialTheme.colorScheme.outlineVariant
    val beat by androidx.compose.animation.core.rememberInfiniteTransition(label = "beat").animateFloat(
        0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900)), label = "ring",
    )
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        drawLine(line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1.dp.toPx())
        if (wave.size < 2) return@Canvas
        val pad = 8.dp.toPx()
        val h = size.height / 2 - pad
        fun at(i: Int) = Offset(size.width * i / 149f, size.height / 2 - wave[i] * h)
        val path = Path()
        wave.indices.forEach { i -> if (i == 0) path.moveTo(at(i).x, at(i).y) else path.lineTo(at(i).x, at(i).y) }
        drawPath(path, ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(accent.copy(alpha = 0.35f * (1f - beat)), (7 + 14 * beat).dp.toPx(), at(wave.size - 1))
        drawCircle(accent, 7.dp.toPx(), at(wave.size - 1))
    }
}
