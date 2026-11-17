package ng.pixelslimited.health.ui

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Where the steps card is and how full it is; the water layer spills from it once the goal is passed. */
object Spill {
    var card by mutableStateOf<Rect?>(null)
    var level by mutableFloatStateOf(0f)
    var navTop by mutableFloatStateOf(0f)
}

private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float)

/**
 * Water that gushes over both sides of the steps card when the goal is reached, pours down the screen edges
 * and collects in a pool that runs on under the navigation bar. Drawn behind the page, so it shows in the
 * margins and gaps. The drops are blurred together and cut back to a sharp edge, which makes them merge
 * like liquid (Android 12 and newer; older phones get plain drops).
 */
@Composable
fun WaterLayer(modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val water = if (dark) oklch(0.88f, 0.20f, 122f) else oklch(0.80f, 0.20f, 124f)
    val drops = remember { ArrayList<Drop>() }
    val n = 64
    val wave = remember { FloatArray(n) }
    val waveV = remember { FloatArray(n) }
    var depth by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableLongStateOf(0L) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var w by remember { mutableFloatStateOf(0f) }
    var h by remember { mutableFloatStateOf(0f) }
    val active = Spill.level >= 1f && Spill.card != null

    LaunchedEffect(active) {
        var last = 0L
        var carry = 0f
        while (active || drops.isNotEmpty() || depth > 0.5f) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else min(0.033f, (now - last) / 1e9f)
                last = now
                val dp = density
                val navY = (if (Spill.navTop > 0f) Spill.navTop else h) - origin.y
                // The pool fills while it gushes and drains slowly after.
                val full = 34f * dp
                depth = if (Spill.level >= 1f && Spill.card != null) min(full, depth + 40f * dp * dt) else max(0f, depth - 12f * dp * dt)
                val surface = navY - depth
                val card = Spill.card
                if (card != null && Spill.level >= 1f) {
                    // More over the goal, more water: 70 drops a second at the goal, up to 160.
                    carry += dt * (70f + 90f * (Spill.level - 1f).coerceIn(0f, 1f))
                    val top = card.top - origin.y
                    while (carry >= 1f) {
                        carry -= 1f
                        val left = Random.nextBoolean()
                        val x = if (left) card.left - origin.x else card.right - origin.x
                        val y = top + Random.nextFloat() * 10f * dp
                        val out = (30f + Random.nextFloat() * 90f) * dp
                        drops.add(Drop(x, y, if (left) -out else out, (-60f + Random.nextFloat() * 60f) * dp, (3f + Random.nextFloat() * 4.5f) * dp))
                    }
                }
                val g = 1500f * dp
                val iter = drops.iterator()
                while (iter.hasNext()) {
                    val d = iter.next()
                    d.vy += g * dt
                    d.vx *= (1f - 2.5f * dt)
                    d.x += d.vx * dt
                    d.y += d.vy * dt
                    // Pour down the screen edges like a sheet of water.
                    if (d.x < d.r) { d.x = d.r; d.vx = -d.vx * 0.2f }
                    if (d.x > w - d.r) { d.x = w - d.r; d.vx = -d.vx * 0.2f }
                    if (d.y >= surface && depth > 1f) {
                        val i = ((d.x / w) * (n - 1)).toInt().coerceIn(0, n - 1)
                        waveV[i] += d.vy * 0.004f * d.r / dp
                        iter.remove()
                    } else if (d.y > h + 40f * dp) iter.remove()
                }
                // Ripples spread across the pool and die away.
                for (i in 0 until n) {
                    val l = if (i > 0) wave[i - 1] else wave[i]
                    val r = if (i < n - 1) wave[i + 1] else wave[i]
                    waveV[i] += ((l + r) / 2f - wave[i]) * 60f * dt - wave[i] * 8f * dt
                    waveV[i] *= (1f - 2.2f * dt)
                }
                for (i in 0 until n) wave[i] = (wave[i] + waveV[i] * dt * 60f).coerceIn(-14f * dp, 14f * dp)
                frame = now
            }
        }
        drops.clear()
    }

    val goo = remember {
        if (Build.VERSION.SDK_INT >= 31) {
            val m = ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 22f, -9f * 255f))
            RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(m), RenderEffect.createBlurEffect(7f * density, 7f * density, Shader.TileMode.DECAL)).asComposeRenderEffect()
        } else null
    }
    Canvas(
        modifier
            .onGloballyPositioned { c -> origin = c.boundsInRoot().topLeft; w = c.size.width.toFloat(); h = c.size.height.toFloat() }
            .graphicsLayer { renderEffect = goo; alpha = 0.9f },
    ) {
        frame // redraw every frame
        if (depth > 0.5f) {
            val navY = (if (Spill.navTop > 0f) Spill.navTop else size.height) - origin.y
            val surface = navY - depth
            val t = frame / 1e9f
            val p = Path()
            p.moveTo(0f, size.height)
            for (i in 0 until n) {
                val x = size.width * i / (n - 1)
                val swell = sin(x / size.width * 2f * PI.toFloat() * 1.5f + t * 2.2f) * 4f * density
                p.lineTo(x, surface + wave[i] + swell)
            }
            p.lineTo(size.width, size.height)
            p.close()
            drawPath(p, water)
        }
        for (d in drops) drawCircle(water, d.r, Offset(d.x, d.y))
    }
}
