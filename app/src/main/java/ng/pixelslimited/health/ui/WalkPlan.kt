package ng.pixelslimited.health.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.location.LocationManager
import android.view.MotionEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Profile
import ng.pixelslimited.health.data.distanceText
import ng.pixelslimited.health.data.metres
import ng.pixelslimited.health.drive.Drive
import ng.pixelslimited.health.walk.LL
import ng.pixelslimited.health.walk.Plan
import ng.pixelslimited.health.walk.WalkPlanner
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@SuppressLint("MissingPermission")
private fun hereNow(ctx: Context): LL? {
    Drive.fix?.let { if (now() - it.ts < 120_000) return it.lat to it.lon }
    return try {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    } catch (e: Exception) {
        null
    }
}

private fun n(v: Int) = String.format(Locale.getDefault(), "%,d", v)

/** Activity > Steps > Plan a walk: pick points on the map, or let the app plan a round trip or a route to a destination that adds up to your remaining steps. */
@Composable
fun WalkPage(me: Profile) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val have = me.steps[LocalDate.now().toString()] ?: 0
    val remain = (me.dailyGoal - have).coerceAtLeast(0)
    val stride = me.metres(1).coerceAtLeast(0.3)
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var pts by remember { mutableStateOf(listOf<LL>()) }
    var origin by remember { mutableStateOf<LL?>(null) }
    var dest by remember { mutableStateOf<LL?>(null) }
    var plan by remember { mutableStateOf<Plan?>(null) }
    var options by remember { mutableStateOf(listOf<Plan>()) }
    var pick by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    fun reset(m: Int) {
        mode = m
        pts = emptyList(); origin = null; dest = null; plan = null; options = emptyList(); pick = 0; failed = false
    }

    // Draw mode: the route follows walkable paths between the tapped points.
    LaunchedEffect(pts, mode) {
        if (mode != 0) return@LaunchedEffect
        if (pts.size < 2) { plan = null; return@LaunchedEffect }
        busy = true
        val p = withContext(Dispatchers.IO) { WalkPlanner.route(pts) ?: WalkPlanner.straight(pts) }
        plan = p
        busy = false
    }

    val goHere: () -> LL? = {
        val h = hereNow(ctx)
        if (h == null) toast(ctx, ctx.getString(R.string.walk_loc))
        h
    }

    Seg(listOf(s(R.string.walk_draw), s(R.string.walk_loop), s(R.string.walk_dest)), mode) { reset(it) }
    Text(
        s(when (mode) { 0 -> R.string.walk_tap; 1 -> R.string.walk_loop_hint; else -> R.string.walk_tap_dest }),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val marks = when (mode) { 0 -> pts; 1 -> listOfNotNull(origin); else -> listOfNotNull(origin, dest) }
    WalkMap(
        plan?.line.orEmpty(), marks, mode == 0 || mode == 2,
        MaterialTheme.colorScheme.primary.toArgb(), hereNow(ctx),
        { g ->
            if (mode == 0) pts = pts + g else { dest = g; plan = null; failed = false }
        },
        Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(8.dp)),
    )

    when (mode) {
        0 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton({ pts = pts.dropLast(1) }, Modifier.weight(1f), enabled = pts.isNotEmpty()) { Text(s(R.string.walk_undo)) }
            OutlinedButton({ pts = emptyList() }, Modifier.weight(1f), enabled = pts.isNotEmpty()) { Text(s(R.string.walk_clear)) }
            OutlinedButton({ goHere()?.let { pts = pts + it } }, Modifier.weight(1f)) { Text(s(R.string.walk_me)) }
        }
        1 -> {
            Button({
                val h = goHere() ?: return@Button
                scope.launch {
                    busy = true; failed = false; origin = h
                    options = WalkPlanner.loops(h, remain * stride)
                    pick = 0
                    plan = options.firstOrNull()
                    failed = plan == null
                    busy = false
                }
            }, Modifier.fillMaxWidth(), enabled = !busy && remain > 0) { Text(s(R.string.walk_plan_btn)) }
            if (options.size > 1) OutlinedButton({ pick = (pick + 1) % options.size; plan = options[pick] }, Modifier.fillMaxWidth()) { Text(s(R.string.walk_next)) }
        }
        else -> Button({
            val d = dest ?: return@Button
            val h = goHere() ?: return@Button
            scope.launch {
                busy = true; failed = false; origin = h
                plan = WalkPlanner.toward(h, d, remain * stride)
                failed = plan == null
                busy = false
            }
        }, Modifier.fillMaxWidth(), enabled = !busy && dest != null && remain > 0) { Text(s(R.string.walk_plan_btn)) }
    }
    if (remain == 0) Text(s(R.string.walk_goal_met), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (failed) Text(s(R.string.walk_fail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    if (busy) Text(s(R.string.walk_busy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    plan?.let { p ->
        val steps = (p.metres / stride).roundToInt()
        val mins = (p.metres / WalkPlanner.PACE).roundToInt()
        val eta = LocalTime.now().plusMinutes(mins.toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))
        Block {
            Text(ctx.getString(R.string.walk_steps_n, n(steps)), style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) { Cap(s(R.string.walk_dist)); Text(distanceText(ctx, p.metres), style = MaterialTheme.typography.titleMedium) }
                Column(Modifier.weight(1f)) { Cap(s(R.string.walk_time)); Text(ctx.getString(R.string.dur_hm, mins / 60, mins % 60), style = MaterialTheme.typography.titleMedium) }
                Column(Modifier.weight(1f)) { Cap(s(R.string.walk_eta)); Text(eta, style = MaterialTheme.typography.titleMedium) }
            }
            if (remain > 0) {
                val left = remain - steps
                Cap(
                    if (left > 0) ctx.getString(R.string.walk_left_n, n(left), n(remain))
                    else ctx.getString(R.string.walk_done, n(remain)),
                )
            }
        }
    }
}

@Composable
private fun Cap(t: String) = Text(t, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun WalkMap(line: List<LL>, marks: List<LL>, tappable: Boolean, accent: Int, here: LL?, onTap: (LL) -> Unit, modifier: Modifier) {
    val ctx = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.let { (it.red + it.green + it.blue) / 3f < 0.5f }
    val paper = MaterialTheme.colorScheme.background
    val start = remember { here }
    val tap by rememberUpdatedState(if (tappable) onTap else null)
    val taps = remember { TapOverlay { g -> tap?.invoke(g.latitude to g.longitude) } }
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osm")
            osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BOUNDS
            if (start != null) {
                controller.setZoom(16.0)
                controller.setCenter(GeoPoint(start.first, start.second))
            } else {
                controller.setZoom(6.0)
                controller.setCenter(GeoPoint(9.0, 8.0))
            }
        }
    }
    DisposableEffect(Unit) { onDispose { map.onDetach() } }
    val key = "${line.size}/${line.firstOrNull()}/${marks.size}/${marks.lastOrNull()}/$dark/$accent"
    AndroidView({ map }, modifier) { m ->
        if (m.tag == key) return@AndroidView
        m.tag = key
        m.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else multiplyFilter(paper))
        m.overlays.clear()
        m.overlays.add(taps)
        val geo = line.map { GeoPoint(it.first, it.second) }
        if (geo.size > 1) {
            m.overlays.add(Polyline().apply {
                setPoints(geo)
                outlinePaint.color = 0xCCFFFFFF.toInt()
                outlinePaint.strokeWidth = 18f
                outlinePaint.strokeCap = Paint.Cap.ROUND
            })
            m.overlays.add(Polyline().apply {
                setPoints(geo)
                outlinePaint.color = accent
                outlinePaint.strokeWidth = 9f
                outlinePaint.strokeCap = Paint.Cap.ROUND
            })
        }
        val density = m.resources.displayMetrics.density
        m.overlays.add(MarkOverlay(marks.map { GeoPoint(it.first, it.second) }, accent, density))
        if (geo.size > 1) m.zoomToBoundingBox(BoundingBox.fromGeoPoints(geo), false, (48 * density).toInt())
        m.invalidate()
    }
}

private class TapOverlay(val onTap: (GeoPoint) -> Unit) : Overlay() {
    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {}

    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val g = map.projection.fromPixels(e.x.toInt(), e.y.toInt())
        onTap(GeoPoint(g.latitude, g.longitude))
        return true
    }
}

private class MarkOverlay(val pts: List<GeoPoint>, val fill: Int, val density: Float) : Overlay() {
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = true }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        dot.color = fill
        num.textSize = 14f * density
        val pt = Point()
        val r = 13f * density
        pts.forEachIndexed { i, g ->
            osmv.projection.toPixels(g, pt)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r + 3 * density, ring)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), r, dot)
            c.drawText("${i + 1}", pt.x.toFloat(), pt.y.toFloat() + 5 * density, num)
        }
    }
}
