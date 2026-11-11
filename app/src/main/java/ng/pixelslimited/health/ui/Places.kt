package ng.pixelslimited.health.ui

import android.Manifest
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Profile
import ng.pixelslimited.health.data.Stop
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.data.T
import ng.pixelslimited.health.data.autoLabels
import ng.pixelslimited.health.data.distanceText
import ng.pixelslimited.health.data.nameLabels
import ng.pixelslimited.health.data.placeName
import ng.pixelslimited.health.data.routeMetres
import ng.pixelslimited.health.data.routeOf
import ng.pixelslimited.health.data.stopsOn
import ng.pixelslimited.health.data.timeText
import ng.pixelslimited.health.places.PlaceTracker
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Today's stops and distance, with the switch that turns location logging on. */
@Composable
fun PlacesBlock(me: Profile) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(PlaceTracker.canRun(ctx)) }
    val bgAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (PlaceTracker.hasPermission(ctx)) {
            PlaceTracker.setEnabled(ctx, true)
            PlaceTracker.restartService(ctx)
            on = true
            if (Build.VERSION.SDK_INT >= 29 && !PlaceTracker.hasBackground(ctx)) bgAsk.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }
    val today = LocalDate.now()
    val stops = me.stopsOn(today)
    val metres = routeMetres(me.routeOf(today))
    Section(s(R.string.places)) {
        if (!on) {
            Text(s(R.string.places_desc))
            Button(onClick = { ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text(s(R.string.enable)) }
        } else {
            Stat(s(R.string.today), ctx.getString(R.string.n_stops, stops.size))
            Stat(s(R.string.distance), distanceText(ctx, metres))
            val here = PlaceTracker.hereSince(ctx)
            if (here > 0) Text(ctx.getString(R.string.places_here, timeText(here)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { open = true }) { Text(s(R.string.places_open)) }
    }
    if (open) PlacesDialog(me) { open = false }
}

@Composable
fun PlacesDialog(me: Profile, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var day by remember { mutableStateOf(LocalDate.now()) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val stops = remember(me.entries, day) { me.stopsOn(day) }
    val route = remember(me.routes, day) { me.routeOf(day) }
    val auto = remember(me.entries) { me.autoLabels() }
    val names = stops.map { me.placeName(ctx, it, auto) }
    val metres = routeMetres(route)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s(R.string.places_title), Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    TextButton(onClick = onClose) { Text(s(R.string.close)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { day = day.minusDays(1) }) { Text(s(R.string.day_prev)) }
                    Text(
                        day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    OutlinedButton(onClick = { if (day < LocalDate.now()) day = day.plusDays(1) }) { Text(s(R.string.day_next)) }
                }
                Text(ctx.getString(R.string.places_summary, stops.size, distanceText(ctx, metres)), style = MaterialTheme.typography.titleLarge)
                RouteMap(
                    route, stops, names, dark, MaterialTheme.colorScheme.primary.toArgb(), { renaming = it },
                    Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(12.dp)),
                )
                LazyColumn(Modifier.weight(1f)) {
                    if (stops.isEmpty()) item { Text(s(R.string.places_none), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    itemsIndexed(stops) { i, st ->
                        Column(Modifier.fillMaxWidth().clickable { renaming = i }.padding(vertical = 8.dp)) {
                            Text("${i + 1}. ${names[i]}", style = MaterialTheme.typography.titleMedium)
                            Text("${timeText(st.arrive)} - ${timeText(st.depart)}", style = MaterialTheme.typography.bodyLarge)
                            if (st.metres > 0) Text(ctx.getString(R.string.places_from_prev, distanceText(ctx, st.metres)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                String.format(Locale.US, "%.5f, %.5f", st.lat, st.lon),
                                Modifier.clickable { openInMaps(ctx, st, names[i]) },
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline,
                            )
                        }
                    }
                    item { TextButton(onClick = { confirm = true }) { Text(s(R.string.places_delete)) } }
                }
            }
        }
    }
    renaming?.let { i ->
        stops.getOrNull(i)?.let { st ->
            var text by remember(i) { mutableStateOf(names[i]) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text(s(R.string.places_rename)) },
                text = { OutlinedTextField(text, { text = it }, label = { Text(s(R.string.places_hint)) }, singleLine = true) },
                confirmButton = {
                    TextButton(onClick = {
                        Store.edit { it.copy(labels = nameLabels(it.labels, st.lat, st.lon, text)) }
                        renaming = null
                    }) { Text(s(R.string.places_save)) }
                },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text(s(R.string.cancel)) } },
            )
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(s(R.string.places_delete_q)) },
            confirmButton = {
                TextButton(onClick = {
                    Store.edit { it.copy(routes = emptyMap(), entries = it.entries.filter { e -> e.type != T.STOP }) }
                    confirm = false
                }) { Text(s(R.string.places_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(s(R.string.cancel)) } },
        )
    }
}

private fun openInMaps(ctx: android.content.Context, st: Stop, name: String) {
    val q = Uri.encode("${st.lat},${st.lon}($name)")
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${st.lat},${st.lon}?q=$q")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        toast(ctx, ctx.getString(R.string.places_maps))
    }
}

/** Street map with the day's route drawn as a line and each stop as a numbered, labelled marker. */
@Composable
private fun RouteMap(
    route: List<Pair<Double, Double>>, stops: List<Stop>, names: List<String>, dark: Boolean,
    accent: Int, onTap: (Int) -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osm")
            osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(6.0)
            controller.setCenter(GeoPoint(9.0, 8.0))
        }
    }
    DisposableEffect(Unit) { onDispose { map.onDetach() } }
    AndroidView({ map }, modifier) { m ->
        m.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        m.overlays.clear()
        val line = route.map { GeoPoint(it.first, it.second) }
        if (line.size > 1) {
            m.overlays.add(Polyline().apply {
                setPoints(line)
                outlinePaint.color = 0xCCFFFFFF.toInt()
                outlinePaint.strokeWidth = 18f
                outlinePaint.strokeCap = Paint.Cap.ROUND
            })
            m.overlays.add(Polyline().apply {
                setPoints(line)
                outlinePaint.color = accent
                outlinePaint.strokeWidth = 9f
                outlinePaint.strokeCap = Paint.Cap.ROUND
            })
        }
        val density = m.resources.displayMetrics.density
        m.overlays.add(StopOverlay(stops.map { GeoPoint(it.lat, it.lon) }, names, accent, density, onTap))
        val all = line + stops.map { GeoPoint(it.lat, it.lon) }
        val key = "${route.size}/${stops.size}/${all.firstOrNull()}"
        if (all.isNotEmpty() && m.tag != key) {
            m.tag = key
            if (all.size == 1 || all.all { it.latitude == all[0].latitude && it.longitude == all[0].longitude }) {
                m.controller.setZoom(17.0)
                m.controller.setCenter(all[0])
            } else {
                m.zoomToBoundingBox(BoundingBox.fromGeoPoints(all), false, (48 * density).toInt())
            }
        }
        m.invalidate()
    }
}

private class StopOverlay(
    val pts: List<GeoPoint>, val labels: List<String>, val fill: Int, val density: Float, val onTap: (Int) -> Unit,
) : Overlay() {
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF101417.toInt() }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        dot.color = fill
        num.textSize = 14f * density
        txt.textSize = 16f * density
        val pr = osmv.projection
        val pt = Point()
        val r = 15f * density
        pts.forEachIndexed { i, g ->
            pr.toPixels(g, pt)
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            val label = labels.getOrElse(i) { "" }.let { if (it.length > 24) it.take(23) + "." else it }
            val w = txt.measureText(label)
            val box = RectF(x + r + 4 * density, y - 14 * density, x + r + 4 * density + w + 12 * density, y + 14 * density)
            c.drawRoundRect(box, 8 * density, 8 * density, bg)
            c.drawText(label, box.left + 6 * density, y + 6 * density, txt)
            c.drawCircle(x, y, r + 3 * density, ring)
            c.drawCircle(x, y, r, dot)
            c.drawText("${i + 1}", x, y + 5 * density, num)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, map: MapView): Boolean {
        val pr = map.projection
        val pt = Point()
        var best = -1
        var bd = Float.MAX_VALUE
        pts.forEachIndexed { i, g ->
            pr.toPixels(g, pt)
            val dx = pt.x - e.x
            val dy = pt.y - e.y
            val d = dx * dx + dy * dy
            if (d < bd) { bd = d; best = i }
        }
        if (best >= 0 && bd <= (32f * density) * (32f * density)) {
            onTap(best)
            return true
        }
        return false
    }
}
