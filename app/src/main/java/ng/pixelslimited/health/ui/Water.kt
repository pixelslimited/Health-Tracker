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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Where the steps card is and how full it is; the water layer spills from it once the goal is passed. */
object Spill {
    var card by mutableStateOf<Rect?>(null)
    var level by mutableFloatStateOf(0f)
}

private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float)

/**
 * Water that gushes over one side of the steps card when the goal is reached (the side alternates by day),
 * smashes against the page, pours down that screen edge and runs on under the navigation bar; nothing
 * collects above it. Drawn in front of the page content, down to the navigation bar. The drops are blurred together and cut back to a sharp edge, which makes them merge
 * like liquid (Android 12 and newer; older phones get plain drops).
 */
@Composable
fun WaterLayer(modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val water = if (dark) oklch(0.88f, 0.20f, 122f) else oklch(0.80f, 0.20f, 124f)
    val drops = remember { ArrayList<Drop>() }
    // One side per day: right on even days, left on odd days.
    val rightToday = remember { java.time.LocalDate.now().toEpochDay() % 2L == 0L }
    var flow by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableLongStateOf(0L) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var w by remember { mutableFloatStateOf(0f) }
    var h by remember { mutableFloatStateOf(0f) }
    val active = Spill.level >= 1f && Spill.card != null

    LaunchedEffect(active) {
        var last = 0L
        var carry = 0f
        while (active || drops.isNotEmpty() || flow > 0.01f) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else min(0.033f, (now - last) / 1e9f)
                last = now
                val dp = density
                // The streams swell in while it gushes and thin out after.
                flow = if (Spill.level >= 1f && Spill.card != null) min(1f, flow + 2f * dt) else max(0f, flow - 1f * dt)
                val card = Spill.card
                if (card != null && Spill.level >= 1f) {
                    // A heavy, continuous pour on one side: 300 drops a second at the goal, up to 520, merged into a stream.
                    carry += dt * (300f + 220f * (Spill.level - 1f).coerceIn(0f, 1f))
                    val top = card.top - origin.y
                    val left = card.left - origin.x
                    val right = card.right - origin.x
                    while (carry >= 1f && drops.size < 1100) {
                        carry -= 1f
                        val r = (5f + Random.nextFloat() * 6f) * dp
                        // Over today's side of the card only, down the screen edge; drops three times as big.
                        val big = r * 3f
                        val x = if (rightToday) right + big * 0.3f else left - big * 0.3f
                        val out = (8f + Random.nextFloat() * 40f) * dp
                        drops.add(Drop(x, top + Random.nextFloat() * 6f * dp, if (rightToday) out else -out, (Random.nextFloat() * 120f - 20f) * dp, big))
                        // Smashing against the card: spray flung back over it from the brim and along its edge.
                        if (Random.nextFloat() < 0.4f) {
                            val sy = top + Random.nextFloat() * (card.bottom - card.top)
                            val back = (50f + Random.nextFloat() * 170f) * dp
                            drops.add(Drop(if (rightToday) right else left, sy, if (rightToday) -back else back, -(40f + Random.nextFloat() * 240f) * dp, (2.5f + Random.nextFloat() * 3f) * dp))
                        }
                    }
                }
                val g = 1200f * dp
                val vmax = 950f * dp
                val iter = drops.iterator()
                while (iter.hasNext()) {
                    val d = iter.next()
                    d.vy = min(vmax, d.vy + g * dt)
                    d.vx *= (1f - 3f * dt)
                    d.x += d.vx * dt
                    d.y += d.vy * dt
                    // Pour down the screen edges like a sheet of water.
                    if (d.x < d.r * 0.6f) { d.x = d.r * 0.6f; d.vx = 0f }
                    if (d.x > w - d.r * 0.6f) { d.x = w - d.r * 0.6f; d.vx = 0f }
                    // Runs on under the navigation bar and is gone.
                    if (d.y > h + 40f * dp) iter.remove()
                }
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
        val card = Spill.card
        if (card != null && flow > 0.01f) {
            val t = frame / 1e9f
            val top = card.top - origin.y
            val navY = size.height // the navigation bar covers the end of the run
            val gap = card.left - origin.x
            // Today's side: a sheet from the brim down the edge, three times the old width, its edge rippling as the water runs.
            listOf(rightToday).forEach { right ->
                val p = Path()
                var y = top
                val pts = ArrayList<Offset>()
                while (y <= navY) {
                    val wob = sin(y / (38f * density) - t * 9f) * 3.5f * density + sin(y / (17f * density) - t * 14f) * 1.8f * density
                    val width = ((gap * 0.62f * 3f + wob) * flow).coerceAtLeast(4f * density)
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
        }
        for (d in drops) drawCircle(water, d.r, Offset(d.x, d.y))
    }
}
