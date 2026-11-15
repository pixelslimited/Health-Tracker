package ng.pixelslimited.health.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Typeface
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.distanceText
import ng.pixelslimited.health.drive.Drive
import ng.pixelslimited.health.drive.Hit
import ng.pixelslimited.health.drive.Route
import ng.pixelslimited.health.drive.Routing
import ng.pixelslimited.health.places.PlaceTracker
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

private val CARDS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

private fun clockText(ms: Long): String {
    val t = ms / 1000
    return if (t >= 3600) String.format(Locale.US, "%d:%02d:%02d", t / 3600, t % 3600 / 60, t % 60) else String.format(Locale.US, "%d:%02d", t / 60, t % 60)
}

@Composable
fun DriveScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = MaterialTheme.colorScheme.primary
    val panel = MaterialTheme.colorScheme.background.copy(alpha = 0.94f)
    val ink = MaterialTheme.colorScheme.onBackground
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val kb = LocalSoftwareKeyboardController.current

    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    DisposableEffect(granted) {
        if (granted) Drive.listen(app)
        onDispose { Drive.unlisten() }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    var big by remember { mutableIntStateOf(0) }          // 0 split, 1 speedometer large, 2 map large
    var showSet by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf(Drive.key(ctx)) }
    var auto by remember { mutableStateOf(Drive.auto(ctx)) }
    var traffic by remember { mutableStateOf(true) }
    var follow by remember { mutableStateOf(true) }
    var fit by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var dest by remember { mutableStateOf<Hit?>(null) }
    var route by remember { mutableStateOf<Route?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastFetch by remember { mutableLongStateOf(0L) }
    var avgSp by remember { mutableFloatStateOf(0f) }

    val fix = Drive.fix
    val live = fix != null && now - fix.ts < 6000
    val kmh by animateFloatAsState(if (fix != null && live) fix.speed * 3.6f else 0f, spring(dampingRatio = 0.8f, stiffness = 120f), label = "speed")
    val bearing = fix?.bearing
    var hdg by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(bearing) {
        if (bearing != null) hdg += ((bearing - hdg % 360f + 540f) % 360f) - 180f
    }
    val hdgA by animateFloatAsState(hdg, tween(700), label = "heading")
    val nb = (((bearing ?: 0f) % 360f) + 360f) % 360f
    val card = CARDS[(((nb + 22.5f) / 45f).toInt()) % 8]
    LaunchedEffect(fix) { fix?.let { avgSp = avgSp * 0.9f + it.speed * 0.1f } }

    fun reroute() {
        val d = dest ?: return
        val f = Drive.fix ?: return
        if (busy) return
        busy = true
        lastFetch = System.currentTimeMillis()
        val k = key
        scope.launch(Dispatchers.IO) {
            val r = Routing.route(f.lat, f.lon, d.lat, d.lon, k)
            withContext(Dispatchers.Main) {
                busy = false
                if (dest === d) {
                    val first = route == null && r.route != null
                    if (r.route != null) route = r.route
                    status = if (r.route == null) ctx.getString(R.string.drive_noroute) else if (r.keyBad) ctx.getString(R.string.drive_keybad) else ""
                    if (first) {
                        fit++
                        if ((Drive.fix?.speed ?: 0f) < 2f) follow = false
                    }
                }
            }
        }
    }
    LaunchedEffect(dest) {
        route = null
        status = ""
        while (dest != null) {
            if (Drive.fix != null) reroute()
            delay(if (route == null) 3000L else 90_000L)
        }
    }
    val prog = remember(fix, route) {
        val r = route
        if (r != null && fix != null) r.progress(fix.lat, fix.lon) else null
    }
    val remM = prog?.first
    val off = prog?.second
    LaunchedEffect(now) {
        if (off != null && off > 150.0 && now - lastFetch > 20_000L) reroute()
    }
    LaunchedEffect(remM) {
        if (remM != null && off != null && remM < 40.0 && off < 120.0) {
            Toast.makeText(ctx, ctx.getString(R.string.drive_arrived), Toast.LENGTH_LONG).show()
            dest = null
            route = null
        }
    }

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        kb?.hide()
        val f = Drive.fix
        scope.launch(Dispatchers.IO) {
            val r = Routing.search(app, q, f?.lat, f?.lon)
            withContext(Dispatchers.Main) {
                hits = r
                status = if (r.isEmpty()) ctx.getString(R.string.drive_noplace) else ""
            }
        }
    }

    val gW by animateFloatAsState(when (big) { 0 -> 1f; 1 -> 2.4f; else -> 0.5f }, tween(450), label = "gw")
    val mW by animateFloatAsState(when (big) { 0 -> 1.3f; 1 -> 0.6f; else -> 2.6f }, tween(450), label = "mw")
    val compactG = big == 2
    val compactM = big == 1

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                val pulse by rememberInfiniteTransition(label = "live").animateFloat(0.25f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pa")
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (live) accent.copy(alpha = pulse) else sub.copy(alpha = 0.4f)))
                    Spacer(Modifier.width(10.dp))
                    Text(s(R.string.drive_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { showSet = !showSet }) { Text(s(R.string.drive_settings)) }
                    TextButton(onClick = onClose) { Text(s(R.string.close)) }
                }
                AnimatedVisibility(showSet) {
                    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s(R.string.drive_auto), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Switch(checked = auto, onCheckedChange = { auto = it; Drive.setAuto(ctx, it) })
                            }
                            OutlinedTextField(
                                value = key, onValueChange = { key = it; Drive.setKey(ctx, it) }, singleLine = true,
                                label = { Text(s(R.string.drive_key)) }, modifier = Modifier.fillMaxWidth(),
                            )
                            Text(s(R.string.drive_key_hint), style = MaterialTheme.typography.bodyMedium, color = sub)
                        }
                    }
                }
                if (!granted) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s(R.string.drive_no_perm), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = { ask.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text(s(R.string.allow_btn)) }
                    }
                }
                GaugePanel(
                    kmh, hdgA, card, nb.roundToInt(), live, compactG, fix, avgSp,
                    Modifier.weight(gW).fillMaxWidth().pointerInput(Unit) { detectTapGestures(onDoubleTap = { big = if (big == 1) 0 else 1 }) },
                )
                Box(Modifier.weight(mW).fillMaxWidth()) {
                    DriveMap(
                        fix, route, dest, if (key.isNotEmpty() && traffic) key + "|" + (now / 180_000L) else "", dark, accent.toArgb(), follow, fit,
                        { follow = it },
                        { la, lo -> dest = Hit(String.format(Locale.US, "%.5f, %.5f", la, lo), la, lo); hits = emptyList() },
                        { big = if (big == 2) 0 else 2 },
                        Modifier.fillMaxSize(),
                    )
                    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!compactM) {
                            Surface(color = panel, shape = RoundedCornerShape(14.dp), shadowElevation = 4.dp) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = query, onValueChange = { query = it }, singleLine = true,
                                            placeholder = { Text(s(R.string.drive_search)) }, modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            keyboardActions = KeyboardActions(onSearch = { search() }),
                                        )
                                        Button(onClick = { search() }) { Text(s(R.string.drive_go)) }
                                    }
                                    dest?.let { d ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(d.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                            TextButton(onClick = { dest = null; route = null }) { Text(s(R.string.drive_clear)) }
                                        }
                                    }
                                    hits.forEach { h ->
                                        Text(
                                            h.name,
                                            Modifier.fillMaxWidth().clickable { dest = h; hits = emptyList(); query = ""; follow = false }.padding(vertical = 8.dp),
                                            style = MaterialTheme.typography.bodyLarge, maxLines = 2,
                                        )
                                    }
                                    if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium, color = accent)
                                }
                            }
                        }
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                            if (!compactM) {
                                FilterChip(
                                    selected = key.isNotEmpty() && traffic,
                                    onClick = { if (key.isEmpty()) showSet = true else traffic = !traffic },
                                    label = { Text(s(R.string.drive_traffic)) },
                                )
                            }
                            if (!follow) Button(onClick = { follow = true }) { Text(s(R.string.drive_recenter)) }
                        }
                        val r = route
                        if (r != null && remM != null) {
                            val base = if (r.metres > 0) r.secs / r.metres else 0.0
                            var remS = remM * base
                            if (avgSp > 4f) remS = 0.5 * remS + 0.5 * (remM / avgSp)
                            val etaMs = now + (remS * 1000).toLong()
                            val mins = (remS / 60).roundToInt()
                            val free = (r.secs - r.delay).coerceAtLeast(1.0)
                            val ratio = r.delay / free
                            val level = if (!r.live) s(R.string.drive_nolive) else if (ratio < 0.05) s(R.string.drive_t_clear) else if (ratio < 0.2) s(R.string.drive_t_mod) else s(R.string.drive_t_heavy)
                            Surface(color = panel, shape = RoundedCornerShape(14.dp), shadowElevation = 4.dp) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Cell(s(R.string.drive_eta), java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date(etaMs)), ink, true)
                                        Cell(s(R.string.drive_left), if (mins < 60) ctx.getString(R.string.drive_min, mins) else ctx.getString(R.string.dur_hm, mins / 60, mins % 60), ink, true)
                                        Cell(s(R.string.distance), distanceText(ctx, remM), ink, true)
                                    }
                                    if (!compactM) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Cell(s(R.string.drive_traffic), level, if (r.live && ratio >= 0.2) accent else ink, false)
                                            if (r.live) Cell(s(R.string.drive_delay), ctx.getString(R.string.drive_min, (r.delay / 60).roundToInt()), ink, false)
                                        }
                                    }
                                }
                            }
                        } else if (dest != null && !compactM) {
                            Surface(color = panel, shape = RoundedCornerShape(14.dp)) {
                                Text(s(if (busy) R.string.drive_routing else R.string.drive_waiting), Modifier.padding(12.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(label: String, value: String, color: Color, strong: Boolean) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun GaugePanel(kmh: Float, hdg: Float, card: String, deg: Int, live: Boolean, compact: Boolean, fix: Drive.Fix?, avgSp: Float, modifier: Modifier) {
    val ctx = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onBackground
    val sub = MaterialTheme.colorScheme.onSurfaceVariant
    val tripA by animateFloatAsState(Drive.tripM.toFloat(), tween(800), label = "trip")
    val maxA by animateFloatAsState(Drive.maxMs * 3.6f, tween(800), label = "max")
    val avg = if (Drive.movingMs > 3000) (Drive.tripM / (Drive.movingMs / 1000.0) * 3.6).toFloat() else 0f
    val avgA by animateFloatAsState(avg, tween(800), label = "avg")
    val tick = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD } }

    if (compact) {
        Row(modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(kmh.roundToInt().toString(), fontSize = 56.sp, fontWeight = FontWeight.Bold, color = ink)
            Spacer(Modifier.width(8.dp))
            Text(s(R.string.kmh_unit), style = MaterialTheme.typography.titleMedium, color = sub)
            Spacer(Modifier.width(24.dp))
            Dial(hdg, 56, accent, ink, tick)
            Spacer(Modifier.width(10.dp))
            Text(card, style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.Bold)
        }
        return
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = min(maxWidth.value, maxHeight.value).dp
            val maxScale = if (kmh > 190f) 300f else 200f
            val labelStep = if (maxScale > 200f) 60 else 40
            ComposeCanvas(Modifier.size(side)) {
                val stroke = size.minDimension * 0.065f
                val r = size.minDimension / 2 - stroke
                val c = Offset(size.width / 2, size.height / 2)
                val frac = (kmh / maxScale).coerceIn(0f, 1f)
                val tl = Offset(c.x - r, c.y - r)
                val sz = Size(r * 2, r * 2)
                drawArc(track, 150f, 240f, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(accent, 150f, 240f * frac, false, tl, sz, style = Stroke(stroke, cap = StrokeCap.Round))
                tick.color = sub.toArgb()
                tick.textSize = 16.sp.toPx()
                val steps = (maxScale / 10).toInt()
                for (i in 0..steps) {
                    val a = Math.toRadians((150 + 240.0 * i / steps))
                    val major = i * 10 % labelStep == 0
                    val r1 = r - stroke * (if (major) 1.9f else 1.5f)
                    val r2 = r - stroke * 1.1f
                    drawLine(if (major) ink else sub.copy(alpha = 0.5f), Offset(c.x + (r1 * Math.cos(a)).toFloat(), c.y + (r1 * Math.sin(a)).toFloat()), Offset(c.x + (r2 * Math.cos(a)).toFloat(), c.y + (r2 * Math.sin(a)).toFloat()), strokeWidth = if (major) 3f else 1.5f)
                    if (major) {
                        val rl = r - stroke * 3.1f
                        drawContext.canvas.nativeCanvas.drawText((i * 10).toString(), c.x + (rl * Math.cos(a)).toFloat(), c.y + (rl * Math.sin(a)).toFloat() + tick.textSize / 3, tick)
                    }
                }
                val na = Math.toRadians(150 + 240.0 * frac)
                drawLine(ink, c, Offset(c.x + (r * 0.74f * Math.cos(na)).toFloat(), c.y + (r * 0.74f * Math.sin(na)).toFloat()), strokeWidth = 4f, cap = StrokeCap.Round)
                drawCircle(accent, stroke * 0.55f, c)
                drawCircle(Color.White, stroke * 0.2f, c)
            }
            Column(Modifier.padding(top = side * 0.22f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(kmh.roundToInt().toString(), fontSize = (side.value * 0.27f).sp, fontWeight = FontWeight.Bold, color = ink, textAlign = TextAlign.Center)
                Text(s(if (live) R.string.kmh_unit else R.string.drive_waiting), style = MaterialTheme.typography.titleMedium, color = sub)
            }
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = side * 0.04f), verticalAlignment = Alignment.CenterVertically) {
                Dial(hdg, 52, accent, ink, tick)
                Spacer(Modifier.width(10.dp))
                Text("$card  $deg°", style = MaterialTheme.typography.headlineSmall, color = accent, fontWeight = FontWeight.Bold)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Cell(s(R.string.drive_trip), distanceText(ctx, tripA.toDouble()), ink, false)
            Cell(s(R.string.drive_max), ctx.getString(R.string.f_kmh, maxA.roundToInt()), ink, false)
            Cell(s(R.string.drive_avg), ctx.getString(R.string.f_kmh, avgA.roundToInt()), ink, false)
            Cell(s(R.string.drive_time), clockText(Drive.movingMs), ink, false)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Cell(s(R.string.drive_alt), ctx.getString(R.string.f_m, (fix?.alt ?: 0.0).roundToInt()), ink, false)
            Spacer(Modifier.width(24.dp))
            Cell(s(R.string.drive_acc), ctx.getString(R.string.f_m, (fix?.acc ?: 0f).roundToInt()), ink, false)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { Drive.resetTrip() }) { Text(s(R.string.drive_reset)) }
        }
    }
}

/** Compass dial: the letters turn so the direction you face is always at the top. */
@Composable
private fun Dial(hdg: Float, sizeDp: Int, accent: Color, ink: Color, paint: Paint) {
    ComposeCanvas(Modifier.size(sizeDp.dp)) {
        val r = size.minDimension / 2
        val c = Offset(r, r)
        drawCircle(ink.copy(alpha = 0.35f), r - 2f, c, style = Stroke(3f))
        paint.textSize = r * 0.5f
        listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (t, a) ->
            rotate(a - hdg, c) {
                paint.color = (if (t == "N") accent else ink).toArgb()
                drawContext.canvas.nativeCanvas.drawText(t, c.x, c.y - r * 0.55f + paint.textSize / 3, paint)
            }
        }
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(c.x, 0f); lineTo(c.x - r * 0.14f, r * 0.2f); lineTo(c.x + r * 0.14f, r * 0.2f); close()
        }
        drawPath(p, accent)
    }
}

private class Holder { var traffic: TilesOverlay? = null }

private class TomTomFlow(val key: String, name: String) : OnlineTileSourceBase(name, 0, 18, 256, ".png", arrayOf("https://api.tomtom.com/")) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl + "traffic/map/4/tile/flow/relative0/" + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + ".png?key=" + key
}

/** Map below the speedometer: follows the car, long-press sets a destination, double-tap enlarges it. */
@Composable
private fun DriveMap(
    fix: Drive.Fix?, route: Route?, dest: Hit?, trafficKey: String, dark: Boolean, accent: Int, follow: Boolean, fit: Int,
    onFollow: (Boolean) -> Unit, onPick: (Double, Double) -> Unit, onDouble: () -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    val pick by rememberUpdatedState(onPick)
    val dbl by rememberUpdatedState(onDouble)
    val fol by rememberUpdatedState(onFollow)
    val folNow by rememberUpdatedState(follow)
    val car = remember { CarOverlay(ctx.resources.displayMetrics.density, accent) }
    val holder = remember { Holder() }
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
            val start = Drive.fix?.let { it.lat to it.lon } ?: PlaceTracker.lastFix(ctx)
            if (start != null) {
                controller.setZoom(17.0)
                controller.setCenter(GeoPoint(start.first, start.second))
            } else {
                controller.setZoom(6.0)
                controller.setCenter(GeoPoint(9.0, 8.0))
            }
            setOnTouchListener { _, ev ->
                if (ev.action == MotionEvent.ACTION_MOVE && folNow) fol(false)
                false
            }
        }
    }
    DisposableEffect(Unit) { onDispose { map.onDetach() } }
    val t = remember { Animatable(1f) }
    var curLa by remember { mutableFloatStateOf(0f) }
    var curLo by remember { mutableFloatStateOf(0f) }
    var curSet by remember { mutableStateOf(false) }
    LaunchedEffect(fix) {
        val f = fix ?: return@LaunchedEffect
        car.show = true
        car.bearing = f.bearing
        val fromLa = if (curSet) curLa.toDouble() else f.lat
        val fromLo = if (curSet) curLo.toDouble() else f.lon
        val firstFix = !curSet
        curSet = true
        t.snapTo(0f)
        t.animateTo(1f, tween(900, easing = LinearEasing)) {
            val la = fromLa + (f.lat - fromLa) * value
            val lo = fromLo + (f.lon - fromLo) * value
            curLa = la.toFloat(); curLo = lo.toFloat()
            car.lat = la; car.lon = lo
            if (folNow || firstFix) map.controller.setCenter(GeoPoint(la, lo))
            map.invalidate()
        }
        curLa = f.lat.toFloat(); curLo = f.lon.toFloat()
    }
    LaunchedEffect(follow) {
        if (follow && curSet) map.controller.setCenter(GeoPoint(car.lat, car.lon))
    }
    LaunchedEffect(fit) {
        val r = route
        if (fit > 0 && r != null && r.lat.size > 1) {
            val pts = r.lat.indices.map { GeoPoint(r.lat[it], r.lon[it]) } + (fix?.let { listOf(GeoPoint(it.lat, it.lon)) } ?: emptyList())
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), true, (48 * ctx.resources.displayMetrics.density).toInt())
        }
    }
    val key = "$dark/${route?.hashCode()}/${dest?.hashCode()}/$trafficKey/$accent"
    AndroidView({ map }, modifier) { m ->
        if (m.tag == key) return@AndroidView
        m.tag = key
        holder.traffic?.onDetach(m)
        holder.traffic = null
        m.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        m.overlays.clear()
        m.overlays.add(object : Overlay() {
            override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {}
            override fun onDoubleTap(e: MotionEvent, mapView: MapView): Boolean {
                dbl()
                return true
            }

            override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
                val g = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
                pick(g.latitude, g.longitude)
                return true
            }
        })
        if (trafficKey.isNotEmpty()) {
            val parts = trafficKey.split("|")
            val tp = MapTileProviderBasic(ctx.applicationContext, TomTomFlow(parts[0], "TTFlow" + parts.getOrElse(1) { "" }))
            val ov = TilesOverlay(tp, ctx)
            ov.loadingBackgroundColor = android.graphics.Color.TRANSPARENT
            ov.loadingLineColor = android.graphics.Color.TRANSPARENT
            m.overlays.add(ov)
            holder.traffic = ov
        }
        if (route != null && route.lat.size > 1) {
            val pts = route.lat.indices.map { GeoPoint(route.lat[it], route.lon[it]) }
            m.overlays.add(Polyline().apply {
                setPoints(pts)
                outlinePaint.color = 0xCCFFFFFF.toInt()
                outlinePaint.strokeWidth = 20f
                outlinePaint.strokeCap = Paint.Cap.ROUND
            })
            m.overlays.add(Polyline().apply {
                setPoints(pts)
                outlinePaint.color = accent
                outlinePaint.strokeWidth = 10f
                outlinePaint.strokeCap = Paint.Cap.ROUND
            })
        }
        if (dest != null) m.overlays.add(PinOverlay(GeoPoint(dest.lat, dest.lon), m.resources.displayMetrics.density, accent))
        m.overlays.add(car)
        m.invalidate()
    }
}

private class CarOverlay(val density: Float, val color: Int) : Overlay() {
    var lat = 0.0
    var lon = 0.0
    var bearing = 0f
    var show = false
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = (this@CarOverlay.color and 0x00FFFFFF) or 0x33000000 }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@CarOverlay.color }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow || !show) return
        val pt = Point()
        osmv.projection.toPixels(GeoPoint(lat, lon), pt)
        c.save()
        c.translate(pt.x.toFloat(), pt.y.toFloat())
        c.drawCircle(0f, 0f, 26f * density, halo)
        c.rotate(bearing - osmv.mapOrientation)
        val arrow = Path().apply {
            moveTo(0f, -15f * density); lineTo(11f * density, 12f * density); lineTo(0f, 6f * density); lineTo(-11f * density, 12f * density); close()
        }
        c.drawPath(arrow, ring.apply { style = Paint.Style.STROKE; strokeWidth = 4f * density; strokeJoin = Paint.Join.ROUND })
        c.drawPath(arrow, fill)
        c.restore()
    }
}

private class PinOverlay(val p: GeoPoint, val density: Float, val color: Int) : Overlay() {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFFFFFFFF.toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@PinOverlay.color }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val pt = Point()
        osmv.projection.toPixels(p, pt)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 13f * density, ring)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 9f * density, fill)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 3.5f * density, ring)
    }
}
