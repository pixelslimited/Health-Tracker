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

private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float, val splash: Boolean = false)

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
                val full = 48f * dp
                depth = if (Spill.level >= 1f && Spill.card != null) min(full, depth + 40f * dp * dt) else max(0f, depth - 12f * dp * dt)
                val surface = navY - depth
                val card = Spill.card
                if (card != null && Spill.level >= 1f) {
                    // A heavy, continuous pour: 300 drops a second at the goal, up to 520, merged into streams.
                    carry += dt * (300f + 220f * (Spill.level - 1f).coerceIn(0f, 1f))
                    val top = card.top - origin.y
                    val left = card.left - origin.x
                    val right = card.right - origin.x
                    while (carry >= 1f && drops.size < 1100) {
                        carry -= 1f
                        val k = Random.nextFloat()
                        val r = (5f + Random.nextFloat() * 6f) * dp
                        if (k < 0.82f) {
                            // Over both sides of the card, down the screen edges.
                            val onLeft = k < 0.41f
                            val x = if (onLeft) left - r * 0.3f else right + r * 0.3f
                            val out = (8f + Random.nextFloat() * 40f) * dp
                            drops.add(Drop(x, top + Random.nextFloat() * 6f * dp, if (onLeft) -out else out, (Random.nextFloat() * 120f - 20f) * dp, r))
                        } else {
                            // Out of the bottom of the card, down the gap between the two columns of tiles.
                            val x = (left + right) / 2f + (Random.nextFloat() - 0.5f) * 6f * dp
                            drops.add(Drop(x, card.bottom - origin.y - r, (Random.nextFloat() - 0.5f) * 20f * dp, (80f + Random.nextFloat() * 120f) * dp, r * 0.8f))
                        }
                    }
                }
                val g = 1200f * dp
                val vmax = 950f * dp
                val iter = drops.iterator()
                val splashes = ArrayList<Drop>()
                while (iter.hasNext()) {
                    val d = iter.next()
                    d.vy = min(vmax, d.vy + g * dt)
                    d.vx *= (1f - 3f * dt)
                    d.x += d.vx * dt
                    d.y += d.vy * dt
                    // Pour down the screen edges like a sheet of water.
                    if (d.x < d.r * 0.6f) { d.x = d.r * 0.6f; d.vx = 0f }
                    if (d.x > w - d.r * 0.6f) { d.x = w - d.r * 0.6f; d.vx = 0f }
                    if (d.y >= surface && depth > 1f && d.vy > 0f) {
                        val i = ((d.x / w) * (n - 1)).toInt().coerceIn(0, n - 1)
                        waveV[i] += d.vy * 0.0025f * d.r / dp
                        // Some of it splashes back up out of the pool.
                        if (!d.splash && Random.nextFloat() < 0.18f) {
                            repeat(2) {
                                splashes.add(Drop(d.x, surface, (Random.nextFloat() - 0.5f) * 260f * dp, -(220f + Random.nextFloat() * 260f) * dp, d.r * 0.45f, true))
                            }
                        }
                        iter.remove()
                    } else if (d.y > h + 40f * dp) iter.remove()
                }
                drops.addAll(splashes)
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
        val card = Spill.card
        if (card != null && Spill.level >= 1f && depth > 0.5f) {
            val t = frame / 1e9f
            val top = card.top - origin.y
            val navY = (if (Spill.navTop > 0f) Spill.navTop else size.height) - origin.y
            val gap = card.left - origin.x
            // Each side: a sheet from the brim to the pool, its edge rippling downwards as the water runs.
            listOf(false, true).forEach { right ->
                val p = Path()
                var y = top
                val pts = ArrayList<Offset>()
                while (y <= navY) {
                    val wob = sin(y / (38f * density) - t * 9f) * 3.5f * density + sin(y / (17f * density) - t * 14f) * 1.8f * density
                    val width = (gap * 0.62f + wob).coerceAtLeast(4f * density)
                    pts.add(Offset(if (right) size.width - width else width, y))
                    y += 6f * density
                }
                val edgeX = if (right) size.width else 0f
                p.moveTo(edgeX, top)
                pts.forEach { p.lineTo(it.x, it.y) }
                p.lineTo(edgeX, navY)
                p.close()
                drawPath(p, water)
            }
            // The middle stream through the gap between the tiles.
            val cx = (card.left + card.right) / 2f - origin.x
            val bottom = card.bottom - origin.y
            val mid = Path()
            var y = bottom
            mid.moveTo(cx, bottom)
            val pts = ArrayList<Offset>()
            while (y <= navY) {
                val wob = sin(y / (30f * density) - t * 11f) * 1.5f * density
                pts.add(Offset(cx + 3.5f * density + wob, y))
                y += 6f * density
            }
            pts.forEach { mid.lineTo(it.x, it.y) }
            pts.asReversed().forEach { mid.lineTo(2 * cx - it.x, it.y) }
            mid.close()
            drawPath(mid, water)
        }
        for (d in drops) drawCircle(water, d.r, Offset(d.x, d.y))
    }
}
