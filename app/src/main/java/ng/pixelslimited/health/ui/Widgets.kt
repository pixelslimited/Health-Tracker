package ng.pixelslimited.health.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
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

@Composable
fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
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

@Composable
fun LineChart(values: List<Float>) {
    if (values.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(72.dp).padding(vertical = 4.dp)) {
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1)
            val y = size.height - (v - min) / span * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun Bars(values: List<Int>, goal: Int, labels: List<String>) {
    val top = maxOf(values.maxOrNull() ?: 0, goal, 1).toFloat()
    Column {
        Row(Modifier.fillMaxWidth().height(96.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            values.forEach { v ->
                Box(
                    Modifier.weight(1f).fillMaxHeight((v / top).coerceIn(0.03f, 1f)).background(
                        if (v >= goal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        RoundedCornerShape(6.dp),
                    )
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
fun Ring(fraction: Float, center: @Composable () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val w = 14.dp.toPx()
            val area = Size(size.width - w, size.height - w)
            val origin = Offset(w / 2, w / 2)
            drawArc(track, -90f, 360f, false, origin, area, style = Stroke(w))
            drawArc(accent, -90f, 360f * fraction.coerceIn(0f, 1f), false, origin, area, style = Stroke(w, cap = StrokeCap.Round))
        }
        center()
    }
}
