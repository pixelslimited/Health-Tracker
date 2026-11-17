package ng.pixelslimited.health.ui

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Entry
import ng.pixelslimited.health.data.Level
import ng.pixelslimited.health.data.Profile
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.data.T
import ng.pixelslimited.health.data.bmi
import ng.pixelslimited.health.data.dateText
import ng.pixelslimited.health.data.distanceText
import ng.pixelslimited.health.data.metres
import ng.pixelslimited.health.data.dayStart
import ng.pixelslimited.health.data.timeText
import ng.pixelslimited.health.data.findrisc
import ng.pixelslimited.health.data.last
import ng.pixelslimited.health.data.level
import ng.pixelslimited.health.data.series
import ng.pixelslimited.health.data.since
import ng.pixelslimited.health.data.streak
import ng.pixelslimited.health.data.typeName
import ng.pixelslimited.health.data.value
import ng.pixelslimited.health.data.weekSteps
import ng.pixelslimited.health.data.whenText
import ng.pixelslimited.health.export.Export
import ng.pixelslimited.health.heart.Hc
import ng.pixelslimited.health.heart.Ppg
import ng.pixelslimited.health.heart.PpgState
import ng.pixelslimited.health.steps.StepService
import ng.pixelslimited.health.update.Updater
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.Executors

internal fun now() = System.currentTimeMillis()

@Composable
fun AppRoot() {
    val data by Store.data.collectAsState()
    val me = data.me
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // 0 Home, 1 Activity (Steps, Workouts, Places), 2 Health (Vitals, Sleep, Food), 3 More. A page (reports, reminders, settings) sits on top.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var actSub by rememberSaveable { mutableIntStateOf(0) }
    var healthSub by rememberSaveable { mutableIntStateOf(0) }
    var page by rememberSaveable { mutableStateOf("") }
    var add by remember { mutableStateOf("") }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var alert by remember { mutableStateOf<Entry?>(null) }
    val appCtx = ctx.applicationContext
    LaunchedEffect(Unit) { release = Updater.check() }
    // Auto-detect new readings from wearables and connected devices each time the app opens.
    LaunchedEffect(data.active) {
        try {
            if (Hc.available(appCtx) && Hc.granted(appCtx)) Hc.sync(appCtx)
        } catch (e: Exception) {
        }
    }
    BackHandler(page.isNotEmpty() || tab != 0) { if (page.isNotEmpty()) page = "" else tab = 0 }
    val save: (Entry) -> Unit = { e ->
        Store.edit { it.copy(entries = it.entries + e) }
        if (level(e) == Level.DANGER) alert = e
        StepService.start(appCtx)
    }
    val go: (Int, Int) -> Unit = { t, sub ->
        page = ""
        tab = t
        if (t == 1) actSub = sub else if (t == 2) healthSub = sub
    }
    val titles = listOf(R.string.tab_home, R.string.tab_activity, R.string.tab_health, R.string.tab_more)
    val target = if (page.isNotEmpty()) page else tab.toString() + (if (tab == 1) actSub else if (tab == 2) healthSub else 0)
    val scroll = rememberScrollState()
    LaunchedEffect(target) { scroll.scrollTo(0) }
    Scaffold(
        bottomBar = { BottomBar(titles.map { s(it) }, if (page.isNotEmpty()) 3 else tab, s(R.string.add), { page = ""; tab = it }, { add = "pick" }) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().imePadding().fadeEdges(false, scroll.canScrollBackward, scroll.canScrollForward, 28.dp).verticalScroll(scroll)
                .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (page.isNotEmpty()) {
                Column {
                    Text(s(R.string.back), Modifier.clickable { page = "" }.padding(vertical = 10.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(s(when (page) { "reports" -> R.string.tab_reports; "reminders" -> R.string.reminders; else -> R.string.profile_title }), style = MaterialTheme.typography.headlineMedium)
                }
            } else if (tab == 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(dateText() + "  /  " + me.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(s(R.string.tab_today), style = MaterialTheme.typography.headlineMedium)
                    }
                    Box(
                        Modifier.size(52.dp).border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(2.dp)).clickable { page = "settings" },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Outlined.Person, contentDescription = s(R.string.profile_title)) }
                }
            } else {
                Text(s(titles[tab]), style = MaterialTheme.typography.headlineMedium)
            }
            if (page.isEmpty() && tab == 1) Seg(listOf(s(R.string.steps), s(R.string.workouts), s(R.string.places)), actSub) { actSub = it }
            if (page.isEmpty() && tab == 2) Seg(listOf(s(R.string.tab_vitals), s(R.string.t_sleep), s(R.string.tab_food)), healthSub) { healthSub = it }
            AnimatedContent(
                targetState = target,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(120)) },
                label = "screen",
            ) { k ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (k) {
                        "settings" -> Settings(data, me)
                        "reports" -> Reports(me)
                        "reminders" -> RemindersPage(me)
                        "00" -> Home(me, go) { page = it }
                        "10" -> StepsPage(me)
                        "11" -> WorkoutsPage(me, save)
                        "12" -> PlacesPage(me)
                        "20" -> VitalsPage(me) { add = it }
                        "21" -> {
                            SleepScreen(me, save) { add = it }
                            SleepAuto()
                        }
                        "22" -> FoodPage(me, { add = it }, save)
                        else -> MorePage(go, { page = it }) {
                            scope.launch {
                                val r = Updater.check()
                                if (r == null) toast(ctx, ctx.getString(R.string.up_to_date)) else { release = r; dismissed = false }
                            }
                        }
                    }
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = release != null && !dismissed,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
            exit = androidx.compose.animation.slideOutVertically(tween(200)) { it } + fadeOut(tween(200)),
        ) {
            release?.let { FloatingUpdate(it) { dismissed = true } }
        }
        }
    }
    AddFlow(me, add, { add = it }, save)
    alert?.let { EmergencyDialog(me, it) { alert = null } }
}

@Composable
fun SleepAuto() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(ng.pixelslimited.health.sleep.SleepDetector.auto(ctx)) }
    Block {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(s(R.string.sleep_auto), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Switch(checked = on, onCheckedChange = { on = it; ng.pixelslimited.health.sleep.SleepDetector.setAuto(ctx, it) })
        }
    }
}

/** Floating pill above the tab bar: new version, one tap to download and install, or dismiss for now. */
@Composable
fun FloatingUpdate(release: Updater.Release, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(-1f) }
    val fg = MaterialTheme.colorScheme.onSurface
    val bg = MaterialTheme.colorScheme.surface
    Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(8.dp, RoundedCornerShape(8.dp)).background(fg, RoundedCornerShape(8.dp)).padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s(R.string.update_available), color = bg, style = MaterialTheme.typography.titleSmall)
                Text("v" + release.code, color = bg.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(enabled = progress < 0f, onClick = {
                progress = 0f
                scope.launch {
                    val apk = Updater.download(ctx, release.url) { progress = it }
                    progress = -1f
                    if (apk == null) toast(ctx, ctx.getString(R.string.update_failed)) else Updater.install(ctx, apk)
                }
            }) { Text(s(R.string.update_short), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = s(R.string.close), tint = bg) }
        }
        if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 4.dp))
    }
}

@Composable
fun UpdateCard(release: Updater.Release) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(-1f) }
    Section(s(R.string.update_available)) {
        // Hide GitHub's automatic "Full Changelog" link line; show only real notes.
        val notes = release.notes.lines().filterNot { it.contains("Full Changelog", ignoreCase = true) }.joinToString("\n").trim()
        if (notes.isNotBlank()) Text(notes.take(300), style = MaterialTheme.typography.bodyMedium)
        if (progress >= 0f) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        Button(enabled = progress < 0f, onClick = {
            progress = 0f
            scope.launch {
                val apk = Updater.download(ctx, release.url) { progress = it }
                progress = -1f
                if (apk == null) toast(ctx, ctx.getString(R.string.update_failed)) else Updater.install(ctx, apk)
            }
        }) { Text(s(R.string.update_now)) }
    }
}

@Composable
fun MeasureHr(onDone: (Int) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var state by remember { mutableStateOf(PpgState()) }
    var attempt by remember { mutableIntStateOf(0) }
    var cameraFailed by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    DisposableEffect(granted, attempt) {
        var provider: ProcessCameraProvider? = null
        val exec = Executors.newSingleThreadExecutor()
        if (granted) {
            state = PpgState()
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                try {
                    val p = future.get()
                    provider = p
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(exec, Ppg { state = it })
                    p.unbindAll()
                    val camera = p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
                    camera.cameraControl.enableTorch(true)
                } catch (e: Exception) {
                    cameraFailed = true
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose {
            provider?.unbindAll()
            exec.shutdown()
        }
    }

    val bpm = state.bpm
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s(R.string.measure_hr)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    !granted -> Text(s(R.string.camera_needed))
                    cameraFailed -> Text(s(R.string.camera_failed))
                    bpm != null -> Text(ctx.getString(R.string.f_hr, bpm), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    state.failed -> Text(s(R.string.hr_failed))
                    !state.finger -> {
                        Text(s(R.string.hr_place_finger))
                        PulseWave(emptyList())
                    }
                    else -> {
                        Text(s(R.string.hr_hold))
                        PulseWave(state.wave)
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
                Text(s(R.string.hr_disclaimer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            if (bpm != null) TextButton(onClick = { onDone(bpm) }) { Text(s(R.string.save)) }
            else if (state.failed) TextButton(onClick = { attempt++ }) { Text(s(R.string.retry)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(s(R.string.close)) } },
    )
}

@Composable
fun EmergencyDialog(me: Profile, e: Entry, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val reading = typeName(ctx, e.type) + ": " + e.value(ctx)
    val open: (Intent) -> Unit = {
        try {
            ctx.startActivity(it)
        } catch (ex: Exception) {
            toast(ctx, ctx.getString(R.string.no_app))
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s(R.string.danger_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(reading, fontWeight = FontWeight.Bold)
                Text(s(R.string.status_danger))
                if (me.contactPhone.isBlank()) Text(s(R.string.no_contact))
            }
        },
        confirmButton = {
            if (me.contactPhone.isNotBlank()) Row {
                TextButton(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + me.contactPhone))) }) { Text(s(R.string.call)) }
                TextButton(onClick = {
                    open(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + me.contactPhone)).putExtra("sms_body", ctx.getString(R.string.sms_body, me.name, reading)))
                }) { Text(s(R.string.message)) }
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(s(R.string.close)) } },
    )
}

@Composable
fun Reports(me: Profile) {
    val ctx = LocalContext.current
    var risk by remember { mutableStateOf(false) }
    val from = now() - 7 * 86400000L
    val today = LocalDate.now()
    val days = (0..6).map { me.steps[today.minusDays(it.toLong()).toString()] ?: 0 }
    fun avg(list: List<Double>): String = if (list.isEmpty()) ctx.getString(R.string.none) else String.format(Locale.US, "%.0f", list.average())
    val bp = me.since(T.BP, from)
    val sleep = me.since(T.SLEEP, from)
    Block(hero = true) {
        Text(s(R.string.last_7_days), style = MaterialTheme.typography.titleMedium)
        Stat(s(R.string.avg_steps), days.average().toInt().toString())
        Stat(s(R.string.distance), distanceText(ctx, me.metres(days.sum())))
        Stat(s(R.string.goal_days), ctx.getString(R.string.of_total, days.count { it >= me.dailyGoal }, 7))
        Stat(s(R.string.streak), ctx.getString(R.string.f_days, streak(me, today)))
        Stat(s(R.string.t_hr), avg(me.since(T.HR, from).map { it.a }))
        Stat(s(R.string.t_bp), if (bp.isEmpty()) s(R.string.none) else avg(bp.map { it.a }) + "/" + avg(bp.map { it.b }))
        Stat(s(R.string.fasting_glu), avg(me.since(T.GLU, from).filter { it.b.toInt() == 0 }.map { it.a }))
        Stat(s(R.string.t_sleep), if (sleep.isEmpty()) s(R.string.none) else ctx.getString(R.string.f_hours, sleep.map { it.a }.average()))
        me.bmi()?.let { Stat(s(R.string.bmi), String.format(Locale.US, "%.1f", it)) }
    }
    Block {
        Text(s(R.string.export), style = MaterialTheme.typography.titleMedium)
        Text(s(R.string.export_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { runCatching { Export.share(ctx, Export.pdf(ctx, me), "application/pdf") }.onFailure { toast(ctx, ctx.getString(R.string.export_failed)) } }, modifier = Modifier.weight(1f)) { Text("PDF") }
            OutlinedButton(onClick = { runCatching { Export.share(ctx, Export.csv(ctx, me), "text/csv") }.onFailure { toast(ctx, ctx.getString(R.string.export_failed)) } }, modifier = Modifier.weight(1f)) { Text("CSV") }
        }
    }
    Block(Modifier.clickable { risk = true }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s(R.string.risk_title), style = MaterialTheme.typography.titleMedium)
                Text(s(R.string.risk_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(s(R.string.risk_start), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    RecentEntries(me)
    Text(s(R.string.disclaimer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (risk) RiskDialog(me) { risk = false }
}

@Composable
private fun YesNo(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
fun RiskDialog(me: Profile, onClose: () -> Unit) {
    var waist by remember { mutableIntStateOf(90) }
    var active by remember { mutableStateOf(true) }
    var veg by remember { mutableStateOf(true) }
    var meds by remember { mutableStateOf(false) }
    var high by remember { mutableStateOf(false) }
    var family by remember { mutableIntStateOf(0) }
    var score by remember { mutableStateOf<Int?>(null) }
    val bmi = me.bmi()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(s(R.string.risk_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val sc = score
                if (sc == null) {
                    if (bmi == null) Text(s(R.string.risk_need_weight), color = MaterialTheme.colorScheme.primary)
                    Ruler(waist, { waist = it }, 50..170, 1, s(R.string.waist_cm))
                    YesNo(s(R.string.q_active), active) { active = it }
                    YesNo(s(R.string.q_veg), veg) { veg = it }
                    YesNo(s(R.string.q_meds), meds) { meds = it }
                    YesNo(s(R.string.q_high), high) { high = it }
                    Text(s(R.string.q_family))
                    listOf(R.string.fam_none, R.string.fam_far, R.string.fam_near).forEachIndexed { i, label ->
                        FilterChip(selected = family == i, onClick = { family = i }, label = { Text(s(label)) })
                    }
                } else {
                    Text(sc.toString() + " / 26", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                    Text(
                        s(
                            when {
                                sc < 7 -> R.string.risk_low
                                sc < 12 -> R.string.risk_slight
                                sc < 15 -> R.string.risk_moderate
                                sc < 21 -> R.string.risk_high
                                else -> R.string.risk_very_high
                            }
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(s(R.string.risk_note))
                }
            }
        },
        confirmButton = {
            if (score == null) TextButton(onClick = {
                score = findrisc(LocalDate.now().year - me.birthYear, bmi ?: 0.0, waist, me.male, active, veg, meds, high, family)
            }) { Text(s(R.string.calculate)) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text(s(R.string.close)) } },
    )
}
