package ng.pixelslimited.health.ui

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var add by remember { mutableStateOf("") }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var alert by remember { mutableStateOf<Entry?>(null) }
    val appCtx = LocalContext.current.applicationContext
    LaunchedEffect(Unit) { release = Updater.check() }
    // Auto-detect new readings from wearables and connected devices each time the app opens.
    LaunchedEffect(data.active) {
        try {
            if (Hc.available(appCtx) && Hc.granted(appCtx)) Hc.sync(appCtx)
        } catch (e: Exception) {
        }
    }
    BackHandler(settings) { settings = false }
    val save: (Entry) -> Unit = { e ->
        Store.edit { it.copy(entries = it.entries + e) }
        if (level(e) == Level.DANGER) alert = e
        StepService.start(appCtx)
    }
    val titles = listOf(R.string.tab_today, R.string.tab_vitals, R.string.t_sleep, R.string.tab_reports)
    Scaffold(
        bottomBar = {
            if (!settings) BottomBar(titles.map { s(it) }, tab, s(R.string.add), { tab = it }, { add = "pick" })
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dateText() + "  /  " + me.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(s(if (settings) R.string.settings else titles[tab]), style = MaterialTheme.typography.headlineMedium)
                }
                Box(
                    Modifier.size(48.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)).clickable { settings = !settings },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (settings) Icons.Filled.Close else Icons.Filled.Settings, contentDescription = s(R.string.settings))
                }
            }
            if (settings) Settings(data, me) else when (tab) {
                0 -> Today(me, release, { tab = it }, save)
                1 -> Vitals(me) { add = it }
                2 -> SleepScreen(me, save) { add = it }
                else -> Reports(me) { add = it }
            }
        }
    }
    AddFlow(me, add, { add = it }, save)
    alert?.let { EmergencyDialog(me, it) { alert = null } }
}

@Composable
fun UpdateCard(release: Updater.Release) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf(-1f) }
    Section(s(R.string.update_available)) {
        if (release.notes.isNotBlank()) Text(release.notes.take(300), style = MaterialTheme.typography.bodyMedium)
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
                    !state.finger -> Text(s(R.string.hr_place_finger))
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
fun Reports(me: Profile, onAdd: (String) -> Unit) {
    val ctx = LocalContext.current
    var risk by remember { mutableStateOf(false) }
    val from = now() - 7 * 86400000L
    val today = LocalDate.now()
    val days = (0..6).map { me.steps[today.minusDays(it.toLong()).toString()] ?: 0 }
    fun avg(list: List<Double>): String = if (list.isEmpty()) ctx.getString(R.string.none) else String.format(Locale.US, "%.0f", list.average())
    val bp = me.since(T.BP, from)
    val sleep = me.since(T.SLEEP, from)
    Section(s(R.string.last_7_days)) {
        Stat(s(R.string.avg_steps), days.average().toInt().toString())
        Stat(s(R.string.goal_days), ctx.getString(R.string.of_total, days.count { it >= me.dailyGoal }, 7))
        Stat(s(R.string.streak), ctx.getString(R.string.f_days, streak(me, today)))
        Stat(s(R.string.t_hr), avg(me.since(T.HR, from).map { it.a }))
        Stat(s(R.string.t_bp), if (bp.isEmpty()) s(R.string.none) else avg(bp.map { it.a }) + "/" + avg(bp.map { it.b }))
        Stat(s(R.string.fasting_glu), avg(me.since(T.GLU, from).filter { it.b.toInt() == 0 }.map { it.a }))
        Stat(s(R.string.t_sleep), if (sleep.isEmpty()) s(R.string.none) else ctx.getString(R.string.f_hours, sleep.map { it.a }.average()))
        me.bmi()?.let { Stat(s(R.string.bmi), String.format(Locale.US, "%.1f", it)) }
    }
    LogSections(me, onAdd)
    Section(s(R.string.export)) {
        Text(s(R.string.export_desc), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { runCatching { Export.share(ctx, Export.pdf(ctx, me), "application/pdf") }.onFailure { toast(ctx, ctx.getString(R.string.export_failed)) } }) { Text("PDF") }
            OutlinedButton(onClick = { runCatching { Export.share(ctx, Export.csv(ctx, me), "text/csv") }.onFailure { toast(ctx, ctx.getString(R.string.export_failed)) } }) { Text("CSV") }
        }
    }
    Section(s(R.string.risk_title)) {
        Text(s(R.string.risk_desc), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { risk = true }) { Text(s(R.string.risk_start)) }
    }
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
