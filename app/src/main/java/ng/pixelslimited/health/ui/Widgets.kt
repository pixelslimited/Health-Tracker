package ng.pixelslimited.health.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Level

@Composable
fun s(id: Int): String = stringResource(id)

fun toast(ctx: Context, text: String) = Toast.makeText(ctx, text, Toast.LENGTH_LONG).show()

/** Flat section: a heavy rule, a title, then content. No cards. */
@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.onSurface))
        Column(Modifier.padding(top = 14.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
fun Stat(label: String, value: String) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, Modifier.weight(1.3f), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), textAlign = TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
    }
}

/** Primary action: solid ink. The accent colour is kept for data. */
@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface),
        content = content,
    )
}

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onSurface),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        content = content,
    )
}

/** Goal progress as ten blocks. */
@Composable
fun SegBar(fraction: Float, segments: Int = 10) {
    val filled = (fraction.coerceIn(0f, 1f) * segments).toInt()
    Row(Modifier.fillMaxWidth().height(14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(segments) { i ->
            Box(Modifier.weight(1f).fillMaxHeight().background(if (i < filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
        }
    }
}

@Composable
fun LevelText(level: Level) {
    val text = when (level) {
        Level.OK -> s(R.string.status_ok)
        Level.NOTE -> s(R.string.status_note)
        Level.DANGER -> s(R.string.status_danger)
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (level == Level.OK) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        fontWeight = if (level == Level.DANGER) FontWeight.Bold else FontWeight.Normal,
    )
}

/** Ruler-style slider: drag the ruler under the fixed accent marker. */
@Composable
fun Ruler(value: Int, onChange: (Int) -> Unit, range: IntRange, step: Int = 1, label: String, fmt: (Int) -> String = { it.toString() }) {
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val current by rememberUpdatedState(value)
    val change by rememberUpdatedState(onChange)
    var carry by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(fmt(value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Canvas(
            Modifier.fillMaxWidth().height(52.dp).pointerInput(range, step) {
                val tick = 12.dp.toPx()
                detectHorizontalDragGestures(onDragEnd = { carry = 0f }) { input, dx ->
                    input.consume()
                    carry -= dx
                    val n = (carry / tick).toInt()
                    if (n != 0) {
                        carry -= n * tick
                        change((current + n * step).coerceIn(range.first, range.last))
                    }
                }
            }
        ) {
            val tick = 12.dp.toPx()
            val cx = size.width / 2
            val count = (cx / tick).toInt() + 1
            for (i in -count..count) {
                val v = value + i * step
                if (v < range.first || v > range.last) continue
                val major = (v / step) % 5 == 0
                val x = cx + i * tick
                drawLine(ink.copy(alpha = if (major) 0.8f else 0.35f), Offset(x, if (major) size.height * 0.3f else size.height * 0.55f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
            drawLine(accent, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

class RulerSpec(val label: String, val range: IntRange, val step: Int, val init: Int, val fmt: (Int) -> String = { it.toString() })

@Composable
fun RulerDialog(
    title: String,
    specs: List<RulerSpec>,
    note: Boolean = false,
    extra: @Composable () -> Unit = {},
    onClose: () -> Unit,
    onSave: (List<Int>, String) -> Unit,
) {
    val values = remember { specs.map { it.init }.toMutableStateList() }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                specs.forEachIndexed { i, sp -> Ruler(values[i], { values[i] = it }, sp.range, sp.step, sp.label, sp.fmt) }
                extra()
                if (note) OutlinedTextField(text, { text = it }, label = { Text(s(R.string.note)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(values.toList(), text.trim()) }) { Text(s(R.string.save)) } },
        dismissButton = { TextButton(onClick = onClose) { Text(s(R.string.cancel)) } },
    )
}

/** Trend line in ink; only the latest point carries the accent. */
@Composable
fun LineChart(values: List<Float>) {
    if (values.size < 2) return
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    Canvas(Modifier.fillMaxWidth().height(96.dp)) {
        val pad = 9.dp.toPx()
        val h = size.height - 2 * pad
        val w = size.width - 2 * pad
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0f } ?: 1f
        fun at(i: Int) = Offset(pad + w * i / (values.size - 1), pad + h - (values[i] - min) / span * h)
        for (g in 0..2) drawLine(ink.copy(alpha = 0.12f), Offset(0f, pad + h * g / 2), Offset(size.width, pad + h * g / 2), strokeWidth = 1.dp.toPx())
        val path = Path()
        values.indices.forEach { i -> if (i == 0) path.moveTo(at(i).x, at(i).y) else path.lineTo(at(i).x, at(i).y) }
        drawPath(path, ink, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(ink, 4.dp.toPx(), at(0))
        drawCircle(bg, 9.dp.toPx(), at(values.size - 1))
        drawCircle(accent, 7.dp.toPx(), at(values.size - 1))
    }
}
