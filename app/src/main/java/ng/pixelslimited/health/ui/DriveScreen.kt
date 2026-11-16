package ng.pixelslimited.health.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.Typeface
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import ng.pixelslimited.health.data.metresBetween
import ng.pixelslimited.health.drive.Drive
import ng.pixelslimited.health.drive.Hit
import ng.pixelslimited.health.drive.Incident
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
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// Drive mode uses the app's own scheme: ink and paper base tones, one red accent, in light and dark.
private class Pal(val dark: Boolean, val bg: Color, val card: Color, val field: Color, val line: Color, val off: Color, val grey: Color, val ink: Color, val accent: Color)

private val DarkPal = Pal(true, Color(0xFF101417), Color(0xFF1B2226), Color(0xFF232B30), Color(0xFF2E373D), Color(0xFF3A444C), Color(0xFFA9B0B5), Color(0xFFF4F1EA), Color(0xFFE5484D))
private val LightPal = Pal(false, Color(0xFFF4F1EA), Color(0xFFE7E3D9), Color(0xFFF4F1EA), Color(0xFFCFCBC0), Color(0xFFCFCBC0), Color(0xFF555D62), Color(0xFF101417), Color(0xFFC62F35))
private var pal by mutableStateOf(DarkPal)

private val BG get() = pal.bg
private val CARD get() = pal.card
private val MAPBG get() = pal.card
private val FIELD get() = pal.field
private val LINE get() = pal.line
private val OFF get() = pal.off
private val GREY get() = pal.grey
private val INK get() = pal.ink
private val LIME get() = pal.accent      // the accent
private val RED get() = pal.accent
private val ONACC = Color(0xFFF4F1EA)    // text on the accent
private val AMBER = Color(0xFFFFB020)

private val INTER = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_semibold, FontWeight.SemiBold))
private val COND = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
private val CARDS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
private val NAMES = mapOf(0 to "N", 45 to "NE", 90 to "E", 135 to "SE", 180 to "S", 225 to "SW", 270 to "W", 315 to "NW")

@Composable
private fun Tx(t: String, size: Int, color: Color = INK, cond: Boolean = false, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(
        t, modifier, color = color, fontSize = size.sp, fontFamily = if (cond) COND else INTER,
        fontWeight = if (cond) FontWeight.SemiBold else FontWeight.Normal, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Cell(label: String, value: String, size: Int = 36, color: Color = INK, align: Alignment.Horizontal = Alignment.Start) {
    Column(horizontalAlignment = align) {
        Tx(value, size, color, cond = true, maxLines = 1)
        Tx(label, 16, GREY, maxLines = 1)
    }
}

@Composable
fun DriveScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current
    val want = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkPal else LightPal
    if (pal !== want) pal = want

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
    var alt by remember { mutableStateOf<Route?>(null) }
    var altTried by remember { mutableStateOf(false) }
    var useAlt by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastFetch by remember { mutableLongStateOf(0L) }
    var avgSp by remember { mutableFloatStateOf(0f) }
    var limit by remember { mutableStateOf<Int?>(null) }
    var limitAt by remember { mutableLongStateOf(0L) }
    var incidents by remember { mutableStateOf<List<Incident>>(emptyList()) }

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
    LaunchedEffect(fix) { fix?.let { avgSp = avgSp * 0.9f + it.speed * 0.1f } }

    fun reroute() {
        val d = dest ?: return
        val f = Drive.fix ?: return
        if (busy) return
        busy = true
        lastFetch = System.currentTimeMillis()
        val k = key
        val wantAlt = useAlt
        scope.launch(Dispatchers.IO) {
            val r = Routing.route(f.lat, f.lon, d.lat, d.lon, k)
            val a = if (wantAlt) Routing.route(f.lat, f.lon, d.lat, d.lon, k, true) else null
            withContext(Dispatchers.Main) {
                busy = false
                if (dest === d) {
                    val first = route == null && r.route != null
                    if (r.route != null) route = r.route
                    if (a?.route != null) alt = a.route
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
        alt = null
        altTried = false
        useAlt = false
        status = ""
        while (dest != null) {
            if (Drive.fix != null) reroute()
            delay(if (route == null) 3000L else 90_000L)
        }
    }
    // "Avoid tolls" alternative, fetched once the map is enlarged.
    LaunchedEffect(big, dest, route) {
        val d = dest
        val f = Drive.fix
        if (big == 2 && d != null && f != null && route != null && alt == null && !altTried) {
            altTried = true
            val k = key
            val r = withContext(Dispatchers.IO) { Routing.route(f.lat, f.lon, d.lat, d.lon, k, true) }
            if (dest === d) alt = r.route
        }
    }
    val rt = run {
        val a = alt
        if (useAlt && a != null) a else route
    }
    val prog = remember(fix, rt) {
        if (rt != null && fix != null) rt.progress(fix.lat, fix.lon) else null
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
    // Speed limit of the road you are on (OpenStreetMap), checked every 15 s while moving.
    LaunchedEffect(now / 15_000L) {
        val f = Drive.fix
        if (f != null && f.speed > 2f) {
            val l = withContext(Dispatchers.IO) { Routing.limit(f.lat, f.lon) }
            if (l != null) { limit = l; limitAt = System.currentTimeMillis() } else if (System.currentTimeMillis() - limitAt > 60_000L) limit = null
        }
    }
    // Incidents nearby (TomTom key), only while the map is enlarged.
    LaunchedEffect(now / 60_000L, big, key) {
        val f = Drive.fix
        if (big == 2 && f != null && key.isNotEmpty()) incidents = withContext(Dispatchers.IO) { Routing.incidents(f.lat, f.lon, key) }
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
    val speak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (t != null) { query = t; search() }
    }

    val gW by animateFloatAsState(when (big) { 0 -> 360f; 1 -> 590f; else -> 160f }, tween(450), label = "gw")
    val mW by animateFloatAsState(when (big) { 0 -> 424f; 1 -> 194f; else -> 624f }, tween(450), label = "mw")
    val over = limit != null && kmh > (limit ?: 0) + 2

    // Permissions that let the screen appear by itself.
    val canFs = remember(now / 3000) { android.os.Build.VERSION.SDK_INT < 34 || ctx.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent() }
    val canOver = remember(now / 3000) { Settings.canDrawOverlays(ctx) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        Box(Modifier.fillMaxSize().background(BG)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // ---------------- speed panel ----------------
                Column(
                    Modifier.weight(gW).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CARD)
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { big = if (big == 1) 0 else 1 }) },
                ) {
                    val pulse by rememberInfiniteTransition(label = "live").animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pa")
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (live) LIME.copy(alpha = pulse) else OFF))
                        Spacer(Modifier.width(8.dp))
                        Tx(s(R.string.drive_title), 16, GREY, modifier = Modifier.weight(1f), maxLines = 1)
                        Tx(s(R.string.drive_settings), 16, INK, modifier = Modifier.clickable { showSet = !showSet }.padding(horizontal = 10.dp, vertical = 10.dp))
                        Tx(s(R.string.close), 16, LIME, modifier = Modifier.clickable { onClose() }.padding(horizontal = 10.dp, vertical = 10.dp))
                        if (limit != null) {
                            Spacer(Modifier.width(6.dp))
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White).border(3.dp, RED, CircleShape), contentAlignment = Alignment.Center) {
                                Tx((limit ?: 0).toString(), 20, Color(0xFF101417), cond = true)
                            }
                        }
                    }
                    if (big == 2) {
                        // Map enlarged: a one-line speed strip.
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Tx(kmh.roundToInt().toString(), 84, if (over) RED else INK, cond = true)
                                Spacer(Modifier.width(8.dp))
                                Tx(s(R.string.kmh_unit), 16, GREY, modifier = Modifier.padding(bottom = 10.dp))
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                                Tx(CARDS[(((nb + 22.5f) / 45f).toInt()) % 8], 40, INK, cond = true)
                                Tx(String.format(Locale.US, "%03d°", nb.roundToInt() % 360), 16, GREY)
                            }
                        }
                        ComposeCanvas(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).height(24.dp)) {
                            for (i in 0..36) {
                                val h = if (i % 4 == 0) 24.dp.toPx() else 12.dp.toPx()
                                val x = size.width * i / 36f
                                drawLine(if (i * 5 <= kmh) LIME else OFF, Offset(x, size.height), Offset(x, size.height - h), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    } else {
                        // ---- tick-ring gauge ----
                        val scale = if (kmh > 180f) 240 else 180
                        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val r = min(maxWidth.value / 2 - 30f, maxHeight.value / 2 - 4f).coerceAtLeast(40f)
                            val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL) } }
                            ComposeCanvas(Modifier.fillMaxSize()) {
                                val c = Offset(size.width / 2, size.height / 2)
                                val rr = r.dp.toPx()
                                paint.color = GREY.toArgb()
                                paint.textSize = 20.sp.toPx()
                                var v = 0
                                while (v <= scale) {
                                    val a = Math.toRadians(150.0 + v.toDouble() / scale * 240.0)
                                    val len = (if (v % 20 == 0) 26 else if (v % 10 == 0) 18 else 10).dp.toPx()
                                    val w = (if (v % 20 == 0) 3 else 2).dp.toPx()
                                    val x0 = c.x + ((rr - len) * cos(a)).toFloat(); val y0 = c.y + ((rr - len) * sin(a)).toFloat()
                                    val x1 = c.x + (rr * cos(a)).toFloat(); val y1 = c.y + (rr * sin(a)).toFloat()
                                    drawLine(if (v <= kmh) LIME else OFF, Offset(x0, y0), Offset(x1, y1), strokeWidth = w, cap = StrokeCap.Round)
                                    if (v % 20 == 0) {
                                        val rl = rr - 46.dp.toPx()
                                        drawContext.canvas.nativeCanvas.drawText(v.toString(), c.x + (rl * cos(a)).toFloat(), c.y + (rl * sin(a)).toFloat() + paint.textSize / 3, paint)
                                    }
                                    v += 5
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Tx(kmh.roundToInt().toString(), (r * 0.74f).toInt(), if (over) RED else INK, cond = true)
                                Tx(s(if (live) R.string.kmh_unit else R.string.drive_waiting), 16, GREY)
                            }
                        }
                        // ---- heading and tape ----
                        Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                            Tx(CARDS[(((nb + 22.5f) / 45f).toInt()) % 8], 32, INK, cond = true)
                            Spacer(Modifier.width(10.dp))
                            Tx(String.format(Locale.US, "%03d° ", nb.roundToInt() % 360) + s(R.string.drive_heading), 16, GREY, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        val tapePaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT } }
                        ComposeCanvas(Modifier.fillMaxWidth().padding(top = 6.dp).height(40.dp)) {
                            val cx = size.width / 2
                            tapePaint.color = GREY.toArgb()
                            tapePaint.textSize = 16.sp.toPx()
                            var d = (Math.floor(((hdgA - 50f) / 5f).toDouble()) * 5).toInt()
                            while (d <= hdgA + 50f) {
                                val n = ((d % 360) + 360) % 360
                                val x = cx + (d - hdgA) * 4.dp.toPx()
                                val nm = NAMES[n]
                                val hgt = (if (nm != null) 14 else if (n % 15 == 0) 10 else 6).dp.toPx()
                                drawLine(if (nm != null) INK else GREY.copy(alpha = 0.6f), Offset(x, 12.dp.toPx()), Offset(x, 12.dp.toPx() + hgt), strokeWidth = 2.dp.toPx())
                                if (nm != null) drawContext.canvas.nativeCanvas.drawText(nm, x, 12.dp.toPx() + hgt + 16.dp.toPx(), tapePaint)
                                d += 5
                            }
                            val p = androidx.compose.ui.graphics.Path().apply {
                                moveTo(cx - 6.dp.toPx(), 0f); lineTo(cx + 6.dp.toPx(), 0f); lineTo(cx, 9.dp.toPx()); close()
                            }
                            drawPath(p, LIME)
                        }
                        if (big == 1) {
                            val avg = if (Drive.movingMs > 3000) (Drive.tripM / (Drive.movingMs / 1000.0) * 3.6).roundToInt() else 0
                            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Cell(s(R.string.drive_avg), ctx.getString(R.string.f_kmh, avg), 28)
                                Cell(s(R.string.drive_top), ctx.getString(R.string.f_kmh, (Drive.maxMs * 3.6f).roundToInt()), 28)
                                Cell(s(R.string.drive_trip), distanceText(ctx, Drive.tripM), 28)
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }

                // ---------------- map panel ----------------
                Box(Modifier.weight(mW).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MAPBG)) {
                    DriveMap(
                        fix, rt, dest, if (key.isNotEmpty() && traffic) key + "|" + (now / 180_000L) else "", follow, fit,
                        { follow = it },
                        { la, lo -> dest = Hit(String.format(Locale.US, "%.5f, %.5f", la, lo), la, lo); hits = emptyList() },
                        { big = if (big == 2) 0 else 2 },
                        Modifier.fillMaxSize(),
                    )
                    Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(FIELD).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            ComposeCanvas(Modifier.size(22.dp)) {
                                val u = size.minDimension / 24f
                                drawCircle(GREY, 7 * u, Offset(11 * u, 11 * u), style = Stroke(2 * u, cap = StrokeCap.Round))
                                drawLine(GREY, Offset(16 * u, 16 * u), Offset(20 * u, 20 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                            }
                            Spacer(Modifier.width(10.dp))
                            BasicTextField(
                                value = query, onValueChange = { query = it }, singleLine = true,
                                textStyle = TextStyle(color = INK, fontSize = 16.sp, fontFamily = INTER),
                                cursorBrush = SolidColor(LIME),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { search() }),
                                modifier = Modifier.weight(1f).height(44.dp),
                                decorationBox = { inner ->
                                    Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                                        if (query.isEmpty()) Tx(s(R.string.drive_search), 16, GREY)
                                        inner()
                                    }
                                },
                            )
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).clickable {
                                    try {
                                        speak.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, ctx.getString(R.string.drive_novoice), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                ComposeCanvas(Modifier.size(22.dp)) {
                                    val u = size.minDimension / 24f
                                    drawRoundRect(INK, Offset(9 * u, 3 * u), Size(6 * u, 12 * u), CornerRadius(3 * u), style = Stroke(2 * u))
                                    drawArc(INK, 0f, 180f, false, Offset(5 * u, 4 * u), Size(14 * u, 14 * u), style = Stroke(2 * u, cap = StrokeCap.Round))
                                    drawLine(INK, Offset(12 * u, 18 * u), Offset(12 * u, 21 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                                }
                            }
                        }
                        if (hits.isNotEmpty()) {
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FIELD).padding(horizontal = 14.dp)) {
                                hits.forEach { h ->
                                    Tx(h.name, 16, INK, modifier = Modifier.fillMaxWidth().clickable { dest = h; hits = emptyList(); query = ""; follow = false }.padding(vertical = 12.dp), maxLines = 2)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (big != 1) {
                                Row(
                                    Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(FIELD)
                                        .clickable { if (key.isEmpty()) showSet = true else traffic = !traffic }.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (key.isNotEmpty() && traffic) RED else OFF))
                                    Spacer(Modifier.width(8.dp))
                                    Tx(s(if (key.isNotEmpty() && traffic) R.string.drive_live else R.string.drive_nolive), 16, INK)
                                }
                            }
                            dest?.let { d ->
                                Row(
                                    Modifier.height(36.dp).weight(1f, fill = false).clip(RoundedCornerShape(18.dp)).background(FIELD).padding(start = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Tx(d.name, 16, INK, modifier = Modifier.weight(1f, fill = false), maxLines = 1)
                                    Tx(s(R.string.drive_clear), 16, LIME, modifier = Modifier.clickable { dest = null; route = null }.padding(horizontal = 12.dp, vertical = 8.dp))
                                }
                            }
                        }
                        if (status.isNotEmpty()) Tx(status, 16, AMBER)
                    }
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!follow) {
                            Box(Modifier.align(Alignment.End).height(40.dp).clip(RoundedCornerShape(20.dp)).background(LIME).clickable { follow = true }.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                                Tx(s(R.string.drive_recenter), 16, ONACC)
                            }
                        }
                        if (rt != null && remM != null) {
                            val base = if (rt.metres > 0) rt.secs / rt.metres else 0.0
                            var remS = remM * base
                            if (avgSp > 4f) remS = 0.5 * remS + 0.5 * (remM / avgSp)
                            val etaMs = now + (remS * 1000).toLong()
                            val mins = (remS / 60).roundToInt()
                            val free = (rt.secs - rt.delay).coerceAtLeast(1.0)
                            val ratio = rt.delay / free
                            val dm = (rt.delay / 60).roundToInt()
                            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CARD).border(1.dp, LINE, RoundedCornerShape(18.dp)).padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                    Cell(s(R.string.drive_eta), java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date(etaMs)))
                                    Cell(s(R.string.drive_left), if (mins < 60) ctx.getString(R.string.drive_min, mins) else ctx.getString(R.string.dur_hm, mins / 60, mins % 60), align = Alignment.CenterHorizontally)
                                    Cell(s(R.string.distance), distanceText(ctx, remM), align = Alignment.End)
                                }
                                if (big != 1) {
                                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val heavy = rt.live && ratio >= 0.2
                                        val mod = rt.live && ratio in 0.05..0.2
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (heavy) RED else if (mod) AMBER else if (rt.live) GREY else OFF))
                                        Spacer(Modifier.width(8.dp))
                                        Tx(
                                            if (!rt.live) s(R.string.drive_nolive) else if (heavy) ctx.getString(R.string.drive_heavy_adds, dm) else if (mod) ctx.getString(R.string.drive_mod_adds, dm) else s(R.string.drive_clear_ahead),
                                            16, INK,
                                        )
                                    }
                                }
                                if (big == 2) {
                                    Column(Modifier.padding(top = 12.dp).fillMaxWidth().border(0.dp, Color.Transparent), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
                                        incidents.forEach { inc ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(Modifier.size(10.dp).clip(CircleShape).background(AMBER))
                                                Spacer(Modifier.width(8.dp))
                                                val dd = if (fix != null) distanceText(ctx, metresBetween(fix.lat, fix.lon, inc.lat, inc.lon)) else ""
                                                Tx(ctx.getString(R.string.drive_incident_away, inc.text, dd), 16, INK, maxLines = 1)
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val a2 = alt
                                            Box(
                                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (!useAlt) LIME else FIELD).clickable { useAlt = false },
                                                contentAlignment = Alignment.Center,
                                            ) { Tx(ctx.getString(R.string.drive_fastest, ((route?.secs ?: 0.0) / 60).roundToInt()), 16, if (!useAlt) ONACC else INK, maxLines = 1) }
                                            Box(
                                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (useAlt) LIME else FIELD).clickable { if (a2 != null) useAlt = true },
                                                contentAlignment = Alignment.Center,
                                            ) { Tx(if (a2 != null) ctx.getString(R.string.drive_avoid, (a2.secs / 60).roundToInt()) else s(R.string.drive_avoid_none), 16, if (useAlt) ONACC else INK, maxLines = 1) }
                                        }
                                    }
                                }
                            }
                        } else if (dest != null) {
                            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(CARD).padding(12.dp)) { Tx(s(if (busy) R.string.drive_routing else R.string.drive_waiting), 16, INK) }
                        } else if (big != 1) {
                            Box(Modifier.clip(RoundedCornerShape(14.dp)).background(CARD).padding(12.dp)) { Tx(s(R.string.drive_hint), 16, GREY) }
                        }
                    }
                }
            }

            // ---------------- settings ----------------
            AnimatedVisibility(showSet, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, start = 18.dp, end = 18.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(FIELD).border(1.dp, LINE, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!granted) {
                        Tx(s(R.string.drive_no_perm), 16, AMBER)
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(LIME).clickable { ask.launch(Manifest.permission.ACCESS_FINE_LOCATION) }.padding(horizontal = 16.dp, vertical = 10.dp)) { Tx(s(R.string.allow_btn), 16, ONACC) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tx(s(R.string.drive_auto), 16, INK, modifier = Modifier.weight(1f))
                        Switch(checked = auto, onCheckedChange = { auto = it; Drive.setAuto(ctx, it) })
                    }
                    if (!canFs) {
                        Row(Modifier.clickable {
                            if (android.os.Build.VERSION.SDK_INT >= 34) ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Tx(s(R.string.drive_lock_perm), 16, LIME, modifier = Modifier.weight(1f).padding(vertical = 6.dp))
                        }
                    }
                    if (!canOver) {
                        Tx(
                            s(R.string.drive_overlay), 16, LIME,
                            modifier = Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.padding(vertical = 6.dp),
                        )
                    }
                    OutlinedTextField(
                        value = key, onValueChange = { key = it; Drive.setKey(ctx, it) }, singleLine = true,
                        label = { Text(s(R.string.drive_key)) }, modifier = Modifier.fillMaxWidth(),
                    )
                    Tx(s(R.string.drive_key_hint), 16, GREY)
                    Tx(s(R.string.drive_reset), 16, LIME, modifier = Modifier.clickable { Drive.resetTrip() }.padding(vertical = 6.dp))
                }
            }
        }
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
    fix: Drive.Fix?, route: Route?, dest: Hit?, trafficKey: String, follow: Boolean, fit: Int,
    onFollow: (Boolean) -> Unit, onPick: (Double, Double) -> Unit, onDouble: () -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    val pick by rememberUpdatedState(onPick)
    val dbl by rememberUpdatedState(onDouble)
    val fol by rememberUpdatedState(onFollow)
    val folNow by rememberUpdatedState(follow)
    val accent = LIME.toArgb()
    val dark = pal.dark
    val car = remember(dark) { CarOverlay(ctx.resources.displayMetrics.density, accent, if (dark) 0xFF101417.toInt() else 0xFFFFFFFF.toInt()) }
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
                outlinePaint.color = if (dark) 0xCC101417.toInt() else 0xCCFFFFFF.toInt()
                outlinePaint.strokeWidth = 22f
                outlinePaint.strokeCap = Paint.Cap.ROUND
            })
            m.overlays.add(Polyline().apply {
                setPoints(pts)
                outlinePaint.color = accent
                outlinePaint.strokeWidth = 10f
                outlinePaint.strokeCap = Paint.Cap.ROUND
            })
        }
        if (dest != null) m.overlays.add(PinOverlay(GeoPoint(dest.lat, dest.lon), m.resources.displayMetrics.density, if (dark) 0xFFF4F1EA.toInt() else 0xFF101417.toInt(), if (dark) 0xFF101417.toInt() else 0xFFF4F1EA.toInt()))
        m.overlays.add(car)
        m.invalidate()
    }
}

private class CarOverlay(val density: Float, accent: Int, ringColor: Int) : Overlay() {
    var lat = 0.0
    var lon = 0.0
    var bearing = 0f
    var show = false
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (accent and 0x00FFFFFF) or 0x33000000 }
    private val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ringColor }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4F1EA.toInt() }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow || !show) return
        val pt = Point()
        osmv.projection.toPixels(GeoPoint(lat, lon), pt)
        c.save()
        c.translate(pt.x.toFloat(), pt.y.toFloat())
        c.drawCircle(0f, 0f, 30f * density, halo)
        c.drawCircle(0f, 0f, 18f * density, dark)
        c.drawCircle(0f, 0f, 14f * density, fill)
        c.rotate(bearing - osmv.mapOrientation)
        val arrow = Path().apply {
            moveTo(0f, -9f * density); lineTo(8f * density, 8f * density); lineTo(0f, 4f * density); lineTo(-8f * density, 8f * density); close()
        }
        c.drawPath(arrow, arrowPaint)
        c.restore()
    }
}

private class PinOverlay(val p: GeoPoint, val density: Float, ringColor: Int, dotColor: Int) : Overlay() {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ringColor }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow) return
        val pt = Point()
        osmv.projection.toPixels(p, pt)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 14f * density, ring)
        c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 6f * density, dot)
    }
}
