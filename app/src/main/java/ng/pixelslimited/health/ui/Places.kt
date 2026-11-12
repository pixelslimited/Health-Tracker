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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import ng.pixelslimited.health.data.metresBetween
import ng.pixelslimited.health.data.routesBetween
import ng.pixelslimited.health.data.stopsBetween
import ng.pixelslimited.health.data.thinRoutes
import ng.pixelslimited.health.data.distanceText
import ng.pixelslimited.health.data.nameLabels
import ng.pixelslimited.health.data.placeName
import ng.pixelslimited.health.data.routeMetres
import ng.pixelslimited.health.data.routeOf
import ng.pixelslimited.health.data.stopsOn
import ng.pixelslimited.health.data.timeText
import ng.pixelslimited.health.places.PlaceTracker
import kotlinx.coroutines.delay
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

/** Today's stop count, distance and whether you are at a stop now; refreshes every few seconds without re-reading the whole log. */
@Composable
fun rememberPlaceToday(me: Profile): Triple<Int, Double, Long> {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            tick++
        }
    }
    val n = remember(me.entries) { me.stopsOn(today).size }
    val routeM = remember(me.routes[today.toString()]) { routeMetres(me.routeOf(today)) }
    val live = if (tick >= 0) PlaceTracker.todayMetres(ctx) else 0.0
    return Triple(n, maxOf(routeM, live), PlaceTracker.hereSince(ctx))
}

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
    val (stopCount, metres, here) = rememberPlaceToday(me)
    Section(s(R.string.places)) {
        if (!on) {
            Text(s(R.string.places_desc))
            Button(onClick = { ask.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text(s(R.string.enable)) }
        } else {
            Stat(s(R.string.today), ctx.getString(R.string.n_stops, stopCount))
            Stat(s(R.string.distance), distanceText(ctx, metres))
            if (here > 0) Text(ctx.getString(R.string.places_here, timeText(here)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { open = true }) { Text(s(R.string.places_open)) }
    }
    if (open) PlacesDialog(me) { open = false }
}

private class DayRow(val date: LocalDate, val stops: Int, val metres: Double)
private class Top(val name: String, val lat: Double, val lon: Double, var visits: Int = 0, var ms: Long = 0)

@Composable
fun PlacesDialog(me: Profile, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    var mode by remember { mutableStateOf(0) }
    var day by remember { mutableStateOf(today) }
    var rFrom by remember { mutableStateOf(today.minusDays(6)) }
    var rTo by remember { mutableStateOf(today) }
    var renaming by remember { mutableStateOf<Int?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var startOnMe by remember { mutableStateOf(true) }
    var here by remember { mutableStateOf(PlaceTracker.lastFix(ctx)) }
    var locateTick by remember { mutableIntStateOf(0) }
    val from = when (mode) {
        0 -> day
        1 -> day.minusDays((day.dayOfWeek.value - 1).toLong())
        2 -> day.withDayOfMonth(1)
        3 -> day.withDayOfYear(1)
        else -> rFrom
    }
    val to = when (mode) {
        0 -> day
        1 -> from.plusDays(6)
        2 -> from.plusMonths(1).minusDays(1)
        3 -> from.plusYears(1).minusDays(1)
        else -> rTo
    }
    val stops = remember(me.entries, from, to) { me.stopsBetween(from, to) }
    val lines = remember(me.routes, from, to) { me.routesBetween(from, to) }
    val auto = remember(me.entries) { me.autoLabels() }
    val names = stops.map { me.placeName(ctx, it, auto) }
    val zone = java.time.ZoneId.systemDefault()
    val days = remember(stops, lines, from, to) {
        val byDay = stops.groupBy { java.time.Instant.ofEpochMilli(it.arrive).atZone(zone).toLocalDate() }
        val out = ArrayList<DayRow>()
        var d = to
        while (!d.isBefore(from)) {
            val n = byDay[d]?.size ?: 0
            val m = if (me.routes.containsKey(d.toString())) routeMetres(me.routeOf(d)) else 0.0
            if (n > 0 || m > 0) out.add(DayRow(d, n, m))
            d = d.minusDays(1)
        }
        out
    }
    val metres = days.sumOf { it.metres }
    val top = remember(stops, auto) {
        val out = ArrayList<Top>()
        stops.forEach { st ->
            val t = out.firstOrNull { metresBetween(it.lat, it.lon, st.lat, st.lon) < 150 } ?: Top(me.placeName(ctx, st, auto), st.lat, st.lon).also { out.add(it) }
            t.visits++
            t.ms += maxOf(0L, st.depart - st.arrive)
        }
        out.sortedByDescending { it.ms }.take(5)
    }
    val med = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val title = when (mode) {
        0 -> day.format(med)
        1, 4 -> from.format(med) + " - " + to.format(med)
        2 -> from.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        else -> from.format(DateTimeFormatter.ofPattern("yyyy"))
    }
    fun shift(dir: Long) {
        startOnMe = false
        day = when (mode) {
            0 -> day.plusDays(dir)
            1 -> day.plusWeeks(dir)
            2 -> day.plusMonths(dir)
            else -> day.plusYears(dir)
        }
    }
    fun pick(cur: LocalDate, set: (LocalDate) -> Unit) {
        android.app.DatePickerDialog(ctx, { _, y, m, d -> startOnMe = false; set(LocalDate.of(y, m + 1, d)) }, cur.year, cur.monthValue - 1, cur.dayOfMonth).show()
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var listOpen by remember { mutableStateOf(false) }
    val panel = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                // The map is the whole page; every control floats over it.
                RouteMap(
                    lines, stops, names, dark, MaterialTheme.colorScheme.primary.toArgb(), "$from/$to/$mode/$startOnMe/$locateTick",
                    here, startOnMe, { renaming = it }, Modifier.fillMaxSize(),
                )
                Surface(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(10.dp),
                    color = panel, shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(s(R.string.places_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            TextButton(onClick = onClose) { Text(s(R.string.close)) }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(R.string.pl_day, R.string.pl_week, R.string.pl_month, R.string.pl_year, R.string.pl_range).forEachIndexed { i, label ->
                                FilterChip(selected = mode == i, onClick = { mode = i; startOnMe = false }, label = { Text(s(label)) })
                            }
                        }
                        if (mode == 4) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { pick(rFrom) { rFrom = it; if (rTo.isBefore(it)) rTo = it } }, modifier = Modifier.weight(1f)) { Text(s(R.string.pl_from) + " " + rFrom.format(med)) }
                                OutlinedButton(onClick = { pick(rTo) { rTo = it; if (rFrom.isAfter(it)) rFrom = it } }, modifier = Modifier.weight(1f)) { Text(s(R.string.pl_to) + " " + rTo.format(med)) }
                            }
                        } else {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { shift(-1) }) { Text(s(R.string.day_prev)) }
                                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                OutlinedButton(onClick = { if (to.isBefore(today)) shift(1) }) { Text(s(R.string.day_next)) }
                            }
                        }
                        Text(ctx.getString(R.string.places_summary, stops.size, distanceText(ctx, metres)), style = MaterialTheme.typography.titleMedium)
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        Button(onClick = { here = PlaceTracker.lastFix(ctx); startOnMe = true; locateTick++ }) { Text(s(R.string.places_locate)) }
                        Button(onClick = { listOpen = !listOpen }) { Text(s(if (listOpen) R.string.places_hide else R.string.places_list)) }
                    }
                    if (listOpen) Surface(
                        Modifier.fillMaxWidth().heightIn(max = 340.dp),
                        color = panel, shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp,
                    ) {
                        LazyColumn(Modifier.padding(horizontal = 14.dp)) {
                    if (stops.isEmpty() && days.isEmpty()) item { Text(s(R.string.places_none), Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (mode == 0) {
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
                    } else {
                        if (top.isNotEmpty()) {
                            item { Text(s(R.string.pl_top), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium) }
                            items(top.size) { i ->
                                val t = top[i]
                                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                    Text(t.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(ctx.getString(R.string.pl_visits, t.visits, ctx.getString(R.string.f_hours, t.ms / 3_600_000.0)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (days.isNotEmpty()) {
                            item { Text(s(R.string.pl_days), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium) }
                            items(days.size) { i ->
                                val r = days[i]
                                Text(
                                    ctx.getString(R.string.pl_day_row, r.date.format(med), r.stops, distanceText(ctx, r.metres)),
                                    Modifier.fillMaxWidth().clickable { startOnMe = false; day = r.date; mode = 0 }.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                    item { TextButton(onClick = { confirm = true }) { Text(s(R.string.places_delete)) } }
                        }
                    }
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

/** Street map with the route drawn as a line, numbered stops and a dot for where you are. Rebuilt only when its inputs change. */
@Composable
private fun RouteMap(
    lines: List<List<Pair<Double, Double>>>, stops: List<Stop>, names: List<String>, dark: Boolean,
    accent: Int, scope: String, here: Pair<Double, Double>?, onMe: Boolean, onTap: (Int) -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    val start = remember { here }
    val map = remember {
        Configuration.getInstance().apply {
            userAgentValue = ctx.packageName
            osmdroidBasePath = File(ctx.cacheDir, "osm")
            osmdroidTileCache = File(ctx.cacheDir, "osm/tiles")
            tileDownloadThreads = 4.toShort()
            tileFileSystemThreads = 4.toShort()
        }
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            // Open straight on your last known position so the first tiles loaded are the ones you need.
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
    val key = "$scope/${stops.size}/${lines.sumOf { it.size }}/${names.hashCode()}/$dark/$accent"
    AndroidView({ map }, modifier) { m ->
        if (m.tag == key) return@AndroidView
        m.tag = key
        m.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        m.overlays.clear()
        val geo = thinRoutes(lines).map { l -> l.map { GeoPoint(it.first, it.second) } }
        geo.filter { it.size > 1 }.forEach { line ->
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
        here?.let { m.overlays.add(MeOverlay(GeoPoint(it.first, it.second), density)) }
        val all = geo.flatten() + stops.map { GeoPoint(it.lat, it.lon) }
        if (onMe && here != null) {
            m.controller.setZoom(16.0)
            m.controller.setCenter(GeoPoint(here.first, here.second))
        } else if (all.size == 1 || (all.isNotEmpty() && all.all { it.latitude == all[0].latitude && it.longitude == all[0].longitude })) {
            m.controller.setZoom(17.0)
            m.controller.setCenter(all[0])
        } else if (all.isNotEmpty()) {
            m.zoomToBoundingBox(BoundingBox.fromGeoPoints(all), false, (48 * density).toInt())
        }
        m.invalidate()
    }
}

private class MeOverlay(val p: GeoPoint, val density: Float) : Overlay() {
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x331A73E8 }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A73E8.toInt() }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val pt = Point()
        osmv.projection.toPixels(p, pt)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 26f * density, halo)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 10f * density, ring)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 7f * density, dot)
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
        val compact = pts.size > 30
        pts.forEachIndexed { i, g ->
            pr.toPixels(g, pt)
            val x = pt.x.toFloat()
            val y = pt.y.toFloat()
            if (compact) {
                c.drawCircle(x, y, 8f * density, ring)
                c.drawCircle(x, y, 5f * density, dot)
                return@forEachIndexed
            }
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
