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
import android.speech.tts.TextToSpeech
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import java.io.File
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.distanceText
import ng.pixelslimited.health.data.metresBetween
import ng.pixelslimited.health.drive.Drive
import ng.pixelslimited.health.drive.Hit
import ng.pixelslimited.health.drive.PlaceInfo
import ng.pixelslimited.health.drive.Route
import ng.pixelslimited.health.drive.Routing
import ng.pixelslimited.health.drive.Sug
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
private val ACCTXT get() = pal.accent      // accent used for text
private val ONACC = Color(0xFFF4F1EA)    // text on the accent
private val AMBER = Color(0xFFFFB020)


private val INTER = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_semibold, FontWeight.SemiBold))
private val CARDS = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
private val SQ = RoundedCornerShape(8.dp)

@Composable
private fun Tx(t: String, size: Int, color: Color = INK, cond: Boolean = false, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    Text(
        t, modifier, color = color, fontSize = size.sp, lineHeight = (size * 1.15f).sp, fontFamily = if (cond) Brand else INTER,
        fontWeight = if (cond) FontWeight.Black else FontWeight.Normal, textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        letterSpacing = if (cond && size > 40) (-2).sp else 0.sp,
    )
}

@Composable
private fun Pill(label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 44.dp).clip(SQ).background(if (on) LIME else FIELD).clickable(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Tx(label, 16, if (on) ONACC else INK, maxLines = 1)
    }
}

@Composable
private fun Btn(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(52.dp).clip(SQ).background(if (primary) LIME else Color.Transparent).border(2.dp, if (primary) LIME else INK, SQ).clickable(onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Tx(label, 16, if (primary) ONACC else INK, maxLines = 1) }
}

@Composable
private fun MapBtn(desc: String, onClick: () -> Unit, glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit) {
    val ink = INK
    Box(Modifier.size(48.dp).clip(SQ).background(BG).border(1.dp, LINE, SQ).clickable(onClick = onClick).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        ComposeCanvas(Modifier.size(24.dp)) { glyph(ink) }
    }
}

/** Arrow for the next manoeuvre, turned to match it. */
@Composable
private fun TurnGlyph(man: String, color: Color) {
    val ang = when {
        man.contains("UTURN") -> 180f
        man.contains("SHARP_LEFT") -> -135f
        man.contains("SHARP_RIGHT") -> 135f
        man.contains("SLIGHT_LEFT") || man.contains("KEEP_LEFT") || man.contains("BEAR_LEFT") -> -45f
        man.contains("SLIGHT_RIGHT") || man.contains("KEEP_RIGHT") || man.contains("BEAR_RIGHT") -> 45f
        man.contains("LEFT") -> -90f
        man.contains("RIGHT") -> 90f
        else -> 0f
    }
    ComposeCanvas(Modifier.size(44.dp)) {
        val w = size.minDimension
        val sw = w * 0.11f
        val c = Offset(w / 2, w / 2)
        if (man.contains("ROUNDABOUT")) drawCircle(color, w * 0.2f, c, style = Stroke(sw))
        drawLine(color, Offset(c.x, w * 0.92f), c, sw, StrokeCap.Round)
        rotate(ang, c) {
            drawLine(color, c, Offset(c.x, w * 0.14f), sw, StrokeCap.Round)
            drawLine(color, Offset(c.x, w * 0.14f), Offset(c.x - w * 0.18f, w * 0.34f), sw, StrokeCap.Round)
            drawLine(color, Offset(c.x, w * 0.14f), Offset(c.x + w * 0.18f, w * 0.34f), sw, StrokeCap.Round)
        }
    }
}

private fun minsText(ctx: android.content.Context, secs: Double): String {
    val m = (secs / 60).roundToInt()
    return if (m < 60) ctx.getString(R.string.drive_min, m) else ctx.getString(R.string.dur_hm, m / 60, m % 60)
}

@Composable
fun DriveScreen(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val kb = LocalSoftwareKeyboardController.current
    val focusMgr = LocalFocusManager.current
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

    var big by remember { mutableIntStateOf(0) }          // 0 split, 1 speed large, 2 map large
    var stage by remember { mutableIntStateOf(0) }        // 0 driving, 1 place chosen, 2 route preview, 3 navigating
    var showSet by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf(Drive.key(ctx)) }
    var auto by remember { mutableStateOf(Drive.auto(ctx)) }
    var follow by remember { mutableStateOf(true) }
    var fit by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var sugs by remember { mutableStateOf<List<Sug>>(emptyList()) }
    var recents by remember { mutableStateOf(Drive.recents(ctx)) }
    var place by remember { mutableStateOf<PlaceInfo?>(null) }
    var routes by remember { mutableStateOf<List<Route>>(emptyList()) }
    var sel by remember { mutableIntStateOf(0) }
    var tolls by remember { mutableStateOf(false) }
    var mute by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var lastFetch by remember { mutableLongStateOf(0L) }
    var avgSp by remember { mutableFloatStateOf(0f) }
    var limit by remember { mutableStateOf<Int?>(null) }
    var limitAt by remember { mutableLongStateOf(0L) }

    val fix = Drive.fix
    val live = fix != null && now - fix.ts < 6000
    val kmh by animateFloatAsState(if (fix != null && live) fix.speed * 3.6f else 0f, spring(dampingRatio = 0.8f, stiffness = 120f), label = "speed")
    val nb = (((fix?.bearing ?: 0f) % 360f) + 360f) % 360f
    LaunchedEffect(fix) { fix?.let { avgSp = avgSp * 0.9f + it.speed * 0.1f } }

    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    var ttsOk by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val t = TextToSpeech(app) { ttsOk = it == TextToSpeech.SUCCESS }
        tts[0] = t
        onDispose { t.stop(); t.shutdown() }
    }
    fun say(x: String) {
        if (!mute && ttsOk && x.isNotEmpty()) tts[0]?.speak(x, TextToSpeech.QUEUE_FLUSH, null, "nav")
    }

    fun fetch() {
        val d = place ?: return
        val o = Drive.fix?.let { it.lat to it.lon } ?: PlaceTracker.lastFix(ctx)
        if (o == null) { status = ctx.getString(R.string.drive_waiting); return }
        if (busy) return
        busy = true
        lastFetch = System.currentTimeMillis()
        val av = tolls
        val k = key
        scope.launch(Dispatchers.IO) {
            val r = Routing.routes(app, o.first, o.second, d, av, k)
            withContext(Dispatchers.Main) {
                busy = false
                if (place === d) {
                    if (r.isNotEmpty()) {
                        routes = r
                        sel = 0
                        status = if (Routing.keyBad) ctx.getString(R.string.drive_keybad) else ""
                        if (stage == 2) fit++
                    } else status = ctx.getString(R.string.drive_noroute)
                }
            }
        }
    }
    fun choose(p: PlaceInfo) {
        place = p; routes = emptyList(); sel = 0; stage = 1; follow = false; query = p.name; status = ""; fit++
        Drive.addRecent(ctx, p)
        recents = Drive.recents(ctx)
    }
    fun unfocus() { kb?.hide(); focusMgr.clearFocus() }
    fun gmaps(p: PlaceInfo) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + p.lat + "," + p.lon + "&mode=d")).setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&travelmode=driving&destination=" + p.lat + "," + p.lon)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.no_app), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val goBack: () -> Unit = {
        when {
            showSet -> showSet = false
            focused -> unfocus()
            stage == 3 -> { stage = 0; place = null; routes = emptyList(); query = ""; follow = true }
            stage == 2 -> stage = 1
            stage == 1 -> { stage = 0; place = null; query = ""; follow = true }
            else -> onClose()
        }
    }

    // Suggestions while typing.
    LaunchedEffect(query, focused) {
        if (!focused) return@LaunchedEffect
        val q = query.trim()
        if (q.length < 2) { sugs = emptyList(); return@LaunchedEffect }
        delay(350)
        val f = Drive.fix
        sugs = withContext(Dispatchers.IO) { Routing.suggest(app, q, f?.lat, f?.lon) }
        status = if (sugs.isEmpty()) ctx.getString(R.string.drive_noplace) else ""
    }
    LaunchedEffect(tolls) { if (stage >= 2) fetch() }
    val speak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (t != null) {
            query = t
            scope.launch {
                val f = Drive.fix
                val r = withContext(Dispatchers.IO) { Routing.suggest(app, t, f?.lat, f?.lon) }.firstOrNull()
                if (r != null) choose(PlaceInfo(r.main, r.sub, r.lat, r.lon)) else status = ctx.getString(R.string.drive_noplace)
            }
        }
    }

    val rt = routes.getOrNull(sel)
    val prog = remember(fix, rt) { if (rt != null && fix != null) rt.prog(fix.lat, fix.lon) else null }
    // Off the route, or the traffic picture is getting old: fetch again.
    LaunchedEffect(now) {
        if (stage == 3 && prog != null && ((prog.off > 150.0 && now - lastFetch > 20_000L) || now - lastFetch > 150_000L)) fetch()
    }
    LaunchedEffect(prog?.rem) {
        if (stage == 3 && prog != null && prog.rem < 40.0 && prog.off < 120.0) {
            Toast.makeText(ctx, ctx.getString(R.string.drive_arrived), Toast.LENGTH_LONG).show()
            say(ctx.getString(R.string.drive_arrived))
            stage = 0; place = null; routes = emptyList(); query = ""
        }
    }
    // Spoken directions: once on approach, once at the turn.
    val bucket = if (prog == null || stage != 3) 0 else if (prog.toNext < 80.0) 2 else if (prog.toNext < 800.0) 1 else 0
    LaunchedEffect(stage, prog?.next, bucket) {
        if (stage != 3 || prog == null || rt == null || bucket == 0) return@LaunchedEffect
        val text = if (prog.next >= 0) rt.steps[prog.next].text else ctx.getString(R.string.drive_arrive_at, place?.name ?: "")
        say(if (bucket == 1) ctx.getString(R.string.drive_in_m, (Math.round(prog.toNext / 50.0) * 50).toInt(), text) else text)
    }
    // Speed limit of the road you are on (OpenStreetMap), checked every 15 s while moving.
    LaunchedEffect(now / 15_000L) {
        val f = Drive.fix
        if (f != null && f.speed > 2f) {
            val l = withContext(Dispatchers.IO) { Routing.limit(f.lat, f.lon) }
            if (l != null) { limit = l; limitAt = System.currentTimeMillis() } else if (System.currentTimeMillis() - limitAt > 60_000L) limit = null
        }
    }
    val over = limit != null && kmh > (limit ?: 0) + 2
    val canFs = remember(now / 3000) { android.os.Build.VERSION.SDK_INT < 34 || ctx.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent() }
    val canOver = remember(now / 3000) { Settings.canDrawOverlays(ctx) }

    Dialog(onDismissRequest = goBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = false }
        }
        Box(Modifier.fillMaxSize().background(BG)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // ---------------- top: next turn while navigating, otherwise search ----------------
                if (stage == 3 && prog != null && rt != null) {
                    val st = rt.steps.getOrNull(prog.next)
                    Row(Modifier.fillMaxWidth().clip(SQ).background(LIME).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TurnGlyph(st?.man ?: "ARRIVE", ONACC)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Tx(distanceText(ctx, prog.toNext), 28, ONACC, cond = true, maxLines = 1)
                            Tx(st?.text ?: ctx.getString(R.string.drive_arrive_at, place?.name ?: ""), 18, ONACC, maxLines = 2)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().height(56.dp).clip(SQ).background(FIELD).padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clickable { if (focused || stage > 0) goBack() }, contentAlignment = Alignment.Center) {
                            SwapIcon(focused || stage > 0) { backIcon ->
                            ComposeCanvas(Modifier.size(22.dp)) {
                                val u = size.minDimension / 24f
                                if (backIcon) {
                                    drawLine(INK, Offset(20 * u, 12 * u), Offset(5 * u, 12 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                                    drawLine(INK, Offset(5 * u, 12 * u), Offset(11 * u, 6 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                                    drawLine(INK, Offset(5 * u, 12 * u), Offset(11 * u, 18 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                                } else {
                                    drawCircle(GREY, 7 * u, Offset(11 * u, 11 * u), style = Stroke(2 * u, cap = StrokeCap.Round))
                                    drawLine(GREY, Offset(16 * u, 16 * u), Offset(20 * u, 20 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                                }
                            }
                            }
                        }
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = INK, fontSize = 16.sp, fontFamily = INTER),
                            cursorBrush = SolidColor(ACCTXT),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { sugs.firstOrNull()?.let { sg -> unfocus(); choose(PlaceInfo(sg.main, sg.sub, sg.lat, sg.lon)) } }),
                            modifier = Modifier.weight(1f).height(44.dp).onFocusChanged { focused = it.isFocused },
                            decorationBox = { inner ->
                                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                                    if (query.isEmpty()) Tx(s(R.string.drive_search), 16, GREY)
                                    inner()
                                }
                            },
                        )
                        SwapIcon(query.isNotEmpty()) { hasText ->
                        if (hasText) {
                            Box(Modifier.size(44.dp).clickable { query = ""; if (stage in 1..2) { stage = 0; place = null; routes = emptyList() } }.semantics { contentDescription = ctx.getString(R.string.drive_clear) }, contentAlignment = Alignment.Center) {
                                ComposeCanvas(Modifier.size(18.dp)) {
                                    drawLine(INK, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                    drawLine(INK, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                }
                            }
                        } else {
                            Box(
                                Modifier.size(44.dp).clickable {
                                    try {
                                        speak.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, ctx.getString(R.string.drive_novoice), Toast.LENGTH_SHORT).show()
                                    }
                                }.semantics { contentDescription = ctx.getString(R.string.drive_voice) },
                                contentAlignment = Alignment.Center,
                            ) {
                                ComposeCanvas(Modifier.size(22.dp)) {
                                    val u = size.minDimension / 24f
                                    drawRect(INK, Offset(9 * u, 3 * u), Size(6 * u, 12 * u), style = Stroke(2 * u))
                                    drawArc(INK, 0f, 180f, false, Offset(5 * u, 4 * u), Size(14 * u, 14 * u), style = Stroke(2 * u, cap = StrokeCap.Round))
                                    drawLine(INK, Offset(12 * u, 18 * u), Offset(12 * u, 21 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
                                }
                            }
                        }
                        }
                    }
                }

                // ---------------- map ----------------
                Box((if (big == 1) Modifier.height(150.dp) else Modifier.weight(1f)).fillMaxWidth().clip(SQ).background(CARD)) {
                    DriveMap(
                        fix, routes, sel, stage >= 2, place, if (key.isNotEmpty()) key + "|" + (now / 180_000L) else "", stage == 3, follow, fit,
                        { follow = it },
                        { la, lo -> if (stage != 3) scope.launch { val n = withContext(Dispatchers.IO) { Routing.reverse(app, la, lo) }; choose(PlaceInfo(n, String.format(Locale.US, "%.5f, %.5f", la, lo), la, lo)) } },
                        Modifier.fillMaxSize(),
                    )
                    if (limit != null) {
                        Box(Modifier.align(Alignment.TopStart).padding(12.dp).size(64.dp).clip(CircleShape).background(Color.White).border(6.dp, RED, CircleShape), contentAlignment = Alignment.Center) {
                            Tx((limit ?: 0).toString(), 24, Color(0xFF101417), cond = true)
                        }
                    }
                    Row(Modifier.align(Alignment.BottomEnd).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MapBtn(s(if (big == 2) R.string.drive_smaller else R.string.drive_bigger), { big = if (big == 2) 0 else 2 }) { c ->
                            val w = size.width
                            val k = w * 0.32f
                            listOf(0f to 0f, w to 0f, 0f to w, w to w).forEach { (x, y) ->
                                drawLine(c, Offset(x, y), Offset(if (x == 0f) k else w - k, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(c, Offset(x, y), Offset(x, if (y == 0f) k else w - k), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                        MapBtn(s(R.string.drive_recenter), { follow = true }) { c ->
                            val m = Offset(size.width / 2, size.height / 2)
                            drawCircle(if (follow) ACCTXT else c, size.width * 0.17f, m, style = Stroke(2.dp.toPx()))
                            listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f).forEach { (dx, dy) ->
                                drawLine(c, Offset(m.x + dx * size.width * 0.3f, m.y + dy * size.width * 0.3f), Offset(m.x + dx * size.width * 0.48f, m.y + dy * size.width * 0.48f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    }
                    // Suggestions (or recent places) cover the map while you type.
                    val list = if (query.trim().length < 2) recents else sugs
                    if (focused) {
                        val ss = rememberScrollState()
                        Column(Modifier.fillMaxSize().background(BG).fadeEdges(false, ss.canScrollBackward, ss.canScrollForward).verticalScroll(ss).padding(horizontal = 14.dp)) {
                            if (query.trim().length < 2 && list.isNotEmpty()) Tx(s(R.string.drive_recent), 16, GREY, modifier = Modifier.padding(top = 12.dp))
                            list.forEach { sg ->
                                Row(Modifier.fillMaxWidth().clickable { unfocus(); choose(PlaceInfo(sg.main, sg.sub, sg.lat, sg.lon)) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Tx(sg.main, 16, INK, maxLines = 1)
                                        if (sg.sub.isNotEmpty()) Tx(sg.sub, 16, GREY, maxLines = 1)
                                    }
                                    if (sg.metres > 0) Tx(distanceText(ctx, sg.metres.toDouble()), 16, GREY, modifier = Modifier.padding(start = 8.dp))
                                }
                                Box(Modifier.fillMaxWidth().height(1.dp).background(LINE))
                            }
                        }
                    }
                }
                if (status.isNotEmpty()) Tx(status, 16, AMBER)

                // ---------------- speed and heading (double-tap to enlarge) ----------------
                if (big == 1) {
                    // Speed enlarged: one big centred number, sized to the space it has.
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) { detectTapGestures(onDoubleTap = { big = 0 }) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val digits = kmh.roundToInt().toString().length.coerceAtLeast(2)
                        val sizeSp = min(maxWidth.value / (digits * 0.62f), maxHeight.value * 0.62f).coerceIn(80f, 360f)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Tx(kmh.roundToInt().toString(), sizeSp.toInt(), if (over) RED else INK, cond = true, maxLines = 1, align = TextAlign.Center)
                            Tx(s(if (live || !granted) R.string.kmh_unit else R.string.drive_waiting), 20, GREY, align = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Tx(CARDS[(((nb + 22.5f) / 45f).toInt()) % 8], 40, INK, cond = true)
                                Spacer(Modifier.width(10.dp))
                                Tx(String.format(Locale.US, "%d\u00B0", nb.roundToInt() % 360), 20, GREY, modifier = Modifier.padding(bottom = 6.dp))
                            }
                        }
                    }
                } else Row(
                    Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures(onDoubleTap = { big = if (big == 1) 0 else 1 }) },
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Tx(kmh.roundToInt().toString(), if (stage in 1..2 || big == 2) 64 else 104, if (over) RED else INK, cond = true, maxLines = 1)
                    Tx(s(if (live || !granted) R.string.kmh_unit else R.string.drive_waiting), 16, GREY, modifier = Modifier.padding(start = 8.dp, bottom = 14.dp).weight(1f), maxLines = 1)
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
                        Tx(CARDS[(((nb + 22.5f) / 45f).toInt()) % 8], 28, INK, cond = true)
                        Tx(String.format(Locale.US, "%d°", nb.roundToInt() % 360), 16, GREY)
                    }
                }

                // ---------------- below: place card, route preview, or trip figures ----------------
                val p = place
                if (stage == 1 && p != null) {
                    Column(Modifier.fillMaxWidth().clip(SQ).background(FIELD).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Tx(p.name, 22, INK, cond = true, maxLines = 2)
                        if (p.addr.isNotEmpty()) Tx(p.addr, 16, GREY, maxLines = 2)
                        if (fix != null) Tx(distanceText(ctx, metresBetween(fix.lat, fix.lon, p.lat, p.lon)), 16, GREY)
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Btn(s(R.string.drive_directions), true, Modifier.weight(1f)) { stage = 2; fetch() }
                            Btn(s(R.string.drive_gmaps), false, Modifier.weight(1f)) { gmaps(p) }
                        }
                    }
                } else if (stage == 2 && p != null) {
                    Column(Modifier.fillMaxWidth().clip(SQ).background(FIELD).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (routes.isEmpty()) Tx(s(if (busy) R.string.drive_routing else R.string.drive_noroute), 16, INK)
                        Row(run { val hs = rememberScrollState(); Modifier.fadeEdges(true, hs.canScrollBackward, hs.canScrollForward).horizontalScroll(hs) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            routes.forEachIndexed { i, r ->
                                val on = i == sel
                                Column(Modifier.clip(SQ).background(if (on) LIME else BG).clickable { sel = i; fit++ }.padding(horizontal = 14.dp, vertical = 8.dp)) {
                                    Tx(minsText(ctx, r.secs), 22, if (on) ONACC else INK, cond = true)
                                    Tx(distanceText(ctx, r.metres) + (if (r.live && r.delay >= 60) "  +" + minsText(ctx, r.delay) else ""), 16, if (on) ONACC else GREY, maxLines = 1)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(s(R.string.drive_fastest_word), !tolls) { tolls = false }
                            Pill(s(R.string.drive_avoid_none), tolls) { tolls = true }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Btn(s(R.string.drive_start), true, Modifier.weight(1f)) {
                                if (rt != null) { stage = 3; follow = true; big = 0; say(rt.steps.firstOrNull()?.text ?: "") }
                            }
                            Btn(s(R.string.drive_gmaps), false, Modifier.weight(1f)) { gmaps(p) }
                        }
                    }
                } else {
                    if (big != 2) {
                        val avg = if (Drive.movingMs > 3000) (Drive.tripM / (Drive.movingMs / 1000.0) * 3.6).roundToInt() else 0
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(s(R.string.drive_avg) to avg.toString(), s(R.string.drive_top) to (Drive.maxMs * 3.6f).roundToInt().toString(), s(R.string.drive_trip) to distanceText(ctx, Drive.tripM)).forEach { (l, v) ->
                                Column(Modifier.weight(1f).clip(SQ).background(FIELD).padding(horizontal = 14.dp, vertical = 12.dp)) {
                                    Tx(l, 16, GREY, maxLines = 1)
                                    Tx(v, 24, INK, cond = true, maxLines = 1)
                                }
                            }
                        }
                    }
                    if (stage == 3 && rt != null && prog != null) {
                        val base = if (rt.metres > 0) rt.secs / rt.metres else 0.0
                        var remS = prog.rem * base
                        if (avgSp > 4f) remS = 0.5 * remS + 0.5 * (prog.rem / avgSp)
                        val free = (rt.secs - rt.delay).coerceAtLeast(1.0)
                        val ratio = rt.delay / free
                        val dm = (rt.delay / 60).roundToInt()
                        Row(Modifier.fillMaxWidth().clip(SQ).background(LIME).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Tx(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date(now + (remS * 1000).toLong())), 24, ONACC, cond = true)
                                Tx(s(R.string.drive_arrive_word), 16, ONACC)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Tx(minsText(ctx, remS) + "  /  " + distanceText(ctx, prog.rem), 18, ONACC, maxLines = 1)
                                Tx(
                                    if (!rt.live) s(R.string.drive_nolive) else if (ratio >= 0.2) ctx.getString(R.string.drive_heavy_adds, dm) else if (ratio >= 0.05) ctx.getString(R.string.drive_mod_adds, dm) else s(R.string.drive_clear_ahead),
                                    16, ONACC, maxLines = 1,
                                )
                            }
                        }
                    }
                    Row(run { val hs = rememberScrollState(); Modifier.fillMaxWidth().fadeEdges(true, hs.canScrollBackward, hs.canScrollForward).horizontalScroll(hs) }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (stage == 3) {
                            Pill(s(R.string.drive_exit), true) { goBack() }
                            Pill(s(if (mute) R.string.drive_unmute else R.string.drive_mute)) { mute = !mute; if (mute) tts[0]?.stop() }
                            Pill(s(R.string.drive_fastest_word), !tolls) { tolls = false }
                            Pill(s(R.string.drive_avoid_none), tolls) { tolls = true }
                            if (p != null) Pill(s(R.string.drive_gmaps)) { gmaps(p) }
                        }
                        Pill(s(R.string.drive_reset)) { Drive.resetTrip() }
                        Pill(s(R.string.drive_settings)) { showSet = true }
                        if (stage != 3) Pill(s(R.string.close)) { onClose() }
                    }
                }
            }

            // ---------------- settings ----------------
            AnimatedVisibility(showSet, Modifier.align(Alignment.Center).padding(horizontal = 20.dp)) {
                Column(Modifier.fillMaxWidth().clip(SQ).background(FIELD).border(2.dp, LINE, SQ).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!granted) {
                        Tx(s(R.string.drive_no_perm), 16, AMBER)
                        Pill(s(R.string.allow_btn), true) { ask.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tx(s(R.string.drive_auto), 16, INK, modifier = Modifier.weight(1f))
                        Switch(checked = auto, onCheckedChange = { auto = it; Drive.setAuto(ctx, it) })
                    }
                    if (!canFs) Tx(
                        s(R.string.drive_lock_perm), 16, ACCTXT,
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (android.os.Build.VERSION.SDK_INT >= 34) ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.padding(vertical = 8.dp),
                    )
                    if (!canOver) Tx(
                        s(R.string.drive_overlay), 16, ACCTXT,
                        modifier = Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.padding(vertical = 8.dp),
                    )
                    OutlinedTextField(value = key, onValueChange = { key = it; Drive.setKey(ctx, it) }, singleLine = true, label = { Text(s(R.string.drive_key)) }, modifier = Modifier.fillMaxWidth())
                    Tx(s(R.string.drive_key_hint), 16, GREY)
                    Pill(s(R.string.close), true) { showSet = false }
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

/** The drive map: follows the car, turns heading-up while navigating, press and hold to drop a destination. */
@Composable
private fun DriveMap(
    fix: Drive.Fix?, routes: List<Route>, sel: Int, show: Boolean, dest: PlaceInfo?, trafficKey: String, nav: Boolean, follow: Boolean, fit: Int,
    onFollow: (Boolean) -> Unit, onPick: (Double, Double) -> Unit, modifier: Modifier,
) {
    val ctx = LocalContext.current
    val pick by rememberUpdatedState(onPick)
    val fol by rememberUpdatedState(onFollow)
    val folNow by rememberUpdatedState(follow)
    val navNow by rememberUpdatedState(nav)
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
    var curBr by remember { mutableFloatStateOf(0f) }
    var curSet by remember { mutableStateOf(false) }
    LaunchedEffect(fix) {
        val f = fix ?: return@LaunchedEffect
        car.show = true
        car.bearing = f.bearing
        val fromLa = if (curSet) curLa.toDouble() else f.lat
        val fromLo = if (curSet) curLo.toDouble() else f.lon
        val fromBr = curBr
        val turn = ((f.bearing - fromBr + 540f) % 360f) - 180f
        val firstFix = !curSet
        curSet = true
        t.snapTo(0f)
        t.animateTo(1f, tween(900, easing = LinearEasing)) {
            val la = fromLa + (f.lat - fromLa) * value
            val lo = fromLo + (f.lon - fromLo) * value
            curLa = la.toFloat(); curLo = lo.toFloat(); curBr = fromBr + turn * value
            car.lat = la; car.lon = lo
            if (folNow || firstFix) map.controller.setCenter(GeoPoint(la, lo))
            // Heading-up while navigating: the road ahead points to the top.
            if (navNow && folNow) map.mapOrientation = -curBr
            map.invalidate()
        }
        curLa = f.lat.toFloat(); curLo = f.lon.toFloat(); curBr = f.bearing
    }
    LaunchedEffect(follow) {
        if (follow && curSet) map.controller.setCenter(GeoPoint(car.lat, car.lon))
        if (!follow) { map.mapOrientation = 0f; map.invalidate() }
    }
    LaunchedEffect(nav) {
        if (nav) map.controller.setZoom(17.5) else { map.mapOrientation = 0f; map.invalidate() }
    }
    LaunchedEffect(fit) {
        if (fit == 0) return@LaunchedEffect
        val r = routes.getOrNull(sel)
        if (r != null && r.lat.size > 1 && show) {
            val pts = r.lat.indices.map { GeoPoint(r.lat[it], r.lon[it]) } + (fix?.let { listOf(GeoPoint(it.lat, it.lon)) } ?: emptyList())
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), true, (48 * ctx.resources.displayMetrics.density).toInt())
        } else if (dest != null) {
            map.controller.setZoom(16.0)
            map.controller.animateTo(GeoPoint(dest.lat, dest.lon))
        }
    }
    val key = "$dark/${routes.hashCode()}/$sel/$show/$nav/${dest?.hashCode()}/$trafficKey"
    AndroidView({ map }, modifier) { m ->
        if (m.tag == key) return@AndroidView
        m.tag = key
        holder.traffic?.onDetach(m)
        holder.traffic = null
        m.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else multiplyFilter(BG))
        m.overlays.clear()
        m.overlays.add(object : Overlay() {
            override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {}
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
        if (show) {
            fun line(r: Route, color: Int, width: Float) = Polyline().apply {
                setPoints(r.lat.indices.map { GeoPoint(r.lat[it], r.lon[it]) })
                outlinePaint.color = color
                outlinePaint.strokeWidth = width
                outlinePaint.strokeCap = Paint.Cap.ROUND
            }
            // Other routes in grey underneath, the chosen one in the accent on top.
            if (!nav) routes.forEachIndexed { i, r -> if (i != sel && r.lat.size > 1) m.overlays.add(line(r, 0xFF8A9096.toInt(), 12f)) }
            routes.getOrNull(sel)?.let { r ->
                if (r.lat.size > 1) {
                    m.overlays.add(line(r, 0xCC101417.toInt(), 22f))
                    m.overlays.add(line(r, accent, 10f))
                }
            }
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
        c.rotate(bearing)   // the canvas already turns with the map
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
