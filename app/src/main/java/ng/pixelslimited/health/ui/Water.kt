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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Where the steps card is and how full it is; the water layer spills from it once the goal is passed. */
object Spill {
    var card by mutableStateOf<Rect?>(null)
    var level by mutableFloatStateOf(0f)
    /** True while a finger is down; wakes the water so it can react. */
    var touching by mutableStateOf(false)
}

/** The finger in the water layer's own coordinates, with a smoothed speed in px/s. */
private object Finger {
    @Volatile var x = 0f
    @Volatile var y = 0f
    @Volatile var vx = 0f
    @Volatile var vy = 0f
    @Volatile var down = false
}

/**
 * Lets the water feel the finger without taking the touch: it only watches (before the children see the event)
 * and never consumes, so taps, scrolling and buttons keep working. Apply it to the box that holds [WaterLayer].
 */
fun Modifier.waterTouch(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val c = e.changes.firstOrNull { it.pressed }
            if (c == null) {
                Finger.down = false
                Finger.vx = 0f
                Finger.vy = 0f
                Spill.touching = false
                continue
            }
            val dt = (c.uptimeMillis - c.previousUptimeMillis) / 1000f
            if (c.previousPressed && dt > 0f) {
                Finger.vx = Finger.vx * 0.5f + (c.position.x - c.previousPosition.x) / dt * 0.5f
                Finger.vy = Finger.vy * 0.5f + (c.position.y - c.previousPosition.y) / dt * 0.5f
            } else {
                Finger.vx = 0f
                Finger.vy = 0f
            }
            Finger.x = c.position.x
            Finger.y = c.position.y
            Finger.down = true
            Spill.touching = true
        }
    }
}

private class Drop(var x: Float, var y: Float, var vx: Float, var vy: Float, val r: Float)

/**
 * Water that gushes over one side of the steps card when the goal is reached (the side alternates by day),
 * smashes against the page, pours down that screen edge and runs on under the navigation bar; nothing
 * collects above it. Drawn in front of the page content, down to the navigation bar.
 *
 * It reacts to touch (see [waterTouch]): a finger is a solid obstacle, so the stream and its drops break around
 * it and rejoin below; dragging pushes drops along; a quick flick throws them off as a splash. The same goes for
 * the liquid inside the card before the goal is reached: moving a finger through it raises a crest, and a flick
 * throws droplets out of it.
 *
 * The drops are blurred together and cut back to a sharp edge, which makes them merge like liquid
 * (Android 12 and newer; older phones get plain drops).
 */
@Composable
fun WaterLayer(modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val water = if (dark) oklch(0.88f, 0.20f, 122f) else oklch(0.80f, 0.20f, 124f)
    val drops = remember { ArrayList<Drop>() }
    // One side per day: right on even days, left on odd days.
    val rightToday = remember { java.time.LocalDate.now().toEpochDay() % 2L == 0L }
    val n = 48
    val rip = remember { FloatArray(n) }
    val ripV = remember { FloatArray(n) }
    // [0] top and [1] bottom of the stretch of stream hidden behind the finger (NaN: none); [2] 1 while the surface is rippling.
    val st = remember { floatArrayOf(Float.NaN, Float.NaN, 0f) }
    var flow by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableLongStateOf(0L) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var w by remember { mutableFloatStateOf(0f) }
    var h by remember { mutableFloatStateOf(0f) }
    val active = Spill.level >= 1f && Spill.card != null
    val touching = Spill.touching

    LaunchedEffect(active, touching) {
        var last = 0L
        var carry = 0f
        var around = 0f
        var burst = 0f
        while (active || touching || drops.isNotEmpty() || flow > 0.01f || st[2] > 0f) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else min(0.033f, (now - last) / 1e9f)
                last = now
                val dp = density
                val f = Finger
                val reach = 34f * dp
                val speed = hypot(f.vx, f.vy)
                val flick = f.down && speed > 900f * dp
                // The streams swell in while it gushes and thin out after.
                flow = if (Spill.level >= 1f && Spill.card != null) min(1f, flow + 2f * dt) else max(0f, flow - 1f * dt)
                val card = Spill.card
                st[0] = Float.NaN
                st[1] = Float.NaN
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
                            drops.add(Drop(if (rightToday) right else left, sy, if (rightToday) -back else back, -(40f + Random.nextFloat() * 240f) * dp, (4f + Random.nextFloat() * 3f) * dp))
                        }
                    }
                    // A finger in the stream blocks it: the water splits round the finger and joins up again below.
                    val sheet = left * 0.62f * 3f
                    val inStream = f.down && f.y > top && f.y < h && (if (rightToday) f.x + reach > w - sheet else f.x - reach < sheet)
                    if (inStream) {
                        st[0] = f.y - reach
                        st[1] = f.y + reach * 1.2f
                        around += dt * 140f
                        while (around >= 1f && drops.size < 1500) {
                            around -= 1f
                            val side = if (Random.nextBoolean()) 1f else -1f
                            drops.add(Drop(f.x + side * reach * 0.9f, f.y - reach * 0.7f, side * (70f + Random.nextFloat() * 100f) * dp, Random.nextFloat() * 60f * dp, (6f + Random.nextFloat() * 5f) * dp))
                        }
                    }
                }
                // The liquid inside the card, full or not: a moving finger raises a crest that spreads and settles.
                var rippling = false
                if (card != null && Spill.level > 0.01f) {
                    val cl = card.left - origin.x
                    val cr = card.right - origin.x
                    val cb = card.bottom - origin.y
                    val base = card.top - origin.y + card.height * (1f - min(1f, Spill.level)) - (if (Spill.level >= 1f) 6f * dp else 0f)
                    val near = f.down && f.x > cl - reach && f.x < cr + reach && f.y > base - reach && f.y < cb + reach
                    for (i in 0 until n) {
                        val cx = cl + (cr - cl) * i / (n - 1)
                        if (near) {
                            val fo = max(0f, 1f - abs(f.x - cx) / reach)
                            ripV[i] += (abs(f.vy) + abs(f.vx) * 0.7f) * 0.012f * fo
                        }
                        val l = if (i > 0) rip[i - 1] else rip[i]
                        val r = if (i < n - 1) rip[i + 1] else rip[i]
                        ripV[i] += ((l + r) / 2f - rip[i]) * 60f * dt - rip[i] * 8f * dt
                        ripV[i] *= (1f - 2.2f * dt)
                    }
                    for (i in 0 until n) {
                        rip[i] = (rip[i] + ripV[i] * dt).coerceIn(-18f * dp, 18f * dp)
                        if (abs(rip[i]) > 0.5f * dp || abs(ripV[i]) > 2f * dp) rippling = true
                    }
                    // A flick through the liquid throws droplets out of the surface.
                    if (flick && near && f.y < cb) {
                        burst += dt * 70f
                        while (burst >= 1f && drops.size < 1500) {
                            burst -= 1f
                            val sx = (f.x + (Random.nextFloat() - 0.5f) * reach * 1.4f).coerceIn(cl, cr)
                            drops.add(Drop(sx, min(f.y, base + 4f * dp), f.vx * 0.5f + (Random.nextFloat() - 0.5f) * 260f * dp, f.vy * 0.5f - Random.nextFloat() * 260f * dp, (4f + Random.nextFloat() * 4f) * dp))
                        }
                    }
                } else {
                    for (i in 0 until n) { rip[i] = 0f; ripV[i] = 0f }
                }
                st[2] = if (rippling) 1f else 0f
                val g = 1200f * dp
                val vmax = 950f * dp
                val cap = 1700f * dp
                val iter = drops.iterator()
                while (iter.hasNext()) {
                    val d = iter.next()
                    d.vy = min(vmax, d.vy + g * dt)
                    d.vx *= (1f - 3f * dt)
                    d.x += d.vx * dt
                    d.y += d.vy * dt
                    if (f.down) {
                        val dx = d.x - f.x
                        val dy = d.y - f.y
                        val dist = hypot(dx, dy)
                        val gapTo = reach + d.r
                        if (dist < gapTo) {
                            val nx = if (dist > 0.01f) dx / dist else 0f
                            val ny = if (dist > 0.01f) dy / dist else -1f
                            d.x = f.x + nx * gapTo
                            d.y = f.y + ny * gapTo
                            // Blocked: lose the speed into the finger, keep what slides along it.
                            val vn = d.vx * nx + d.vy * ny
                            if (vn < 0f) {
                                d.vx -= vn * nx
                                d.vy -= vn * ny
                            }
                            if (flick) {
                                // Flicked: thrown off along the swipe, scattered.
                                d.vx = (f.vx * 0.8f + (Random.nextFloat() - 0.5f) * 500f * dp).coerceIn(-cap, cap)
                                d.vy = (f.vy * 0.8f + (Random.nextFloat() - 0.5f) * 500f * dp).coerceIn(-cap, cap)
                            } else {
                                // Dragged: carried a little with the finger.
                                d.vx += (f.vx - d.vx) * 0.25f
                                d.vy += (f.vy - d.vy) * 0.25f
                            }
                        }
                    }
                    // Pour down the screen edges like a sheet of water.
                    if (d.x < d.r * 0.6f) { d.x = d.r * 0.6f; d.vx = 0f }
                    if (d.x > w - d.r * 0.6f) { d.x = w - d.r * 0.6f; d.vx = 0f }
                    // Runs on under the navigation bar and is gone.
                    if (d.y > h + 40f * dp || d.y < -200f * dp) iter.remove()
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
        // The crest raised on the liquid by a moving finger.
        if (card != null && Spill.level > 0.01f && st[2] > 0f) {
            val cl = card.left - origin.x
            val cr = card.right - origin.x
            val base = card.top - origin.y + card.height * (1f - min(1f, Spill.level)) - (if (Spill.level >= 1f) 6f * density else 0f)
            val p = Path()
            p.moveTo(cl, base + 2f * density)
            for (i in 0 until n) p.lineTo(cl + (cr - cl) * i / (n - 1), base - max(0f, rip[i]))
            p.lineTo(cr, base + 2f * density)
            p.close()
            drawPath(p, water)
        }
        if (card != null && flow > 0.01f) {
            val t = frame / 1e9f
            val top = card.top - origin.y
            val navY = size.height // the navigation bar covers the end of the run
            val gap = card.left - origin.x
            val right = rightToday
            val edgeX = if (right) size.width else 0f
            // Today's side: a sheet from the brim down the edge, three times the old width, its edge rippling as the water runs.
            fun sheet(y0: Float, y1: Float) {
                if (y1 <= y0) return
                val p = Path()
                p.moveTo(edgeX, y0)
                var y = y0
                while (y < y1) {
                    val wob = sin(y / (38f * density) - t * 9f) * 3.5f * density + sin(y / (17f * density) - t * 14f) * 1.8f * density
                    val width = ((gap * 0.62f * 3f + wob) * flow).coerceAtLeast(4f * density)
                    p.lineTo(if (right) size.width - width else width, y)
                    y += 6f * density
                }
                p.lineTo(edgeX, y1)
                p.close()
                drawPath(p, water)
            }
            if (st[0].isNaN()) sheet(top, navY) else {
                // A finger blocks the stream: it runs above the finger, then re-forms below it.
                sheet(top, st[0])
                sheet(st[1], navY)
            }
        }
        for (d in drops) drawCircle(water, d.r, Offset(d.x, d.y))
    }
}
