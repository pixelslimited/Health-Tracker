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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
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
import ng.pixelslimited.health.data.dayStart
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

private fun now() = System.currentTimeMillis()

@Composable
fun AppRoot() {
    val data by Store.data.collectAsState()
    val me = data.me
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
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
    val tabs = listOf(
        R.string.tab_today to Icons.Filled.Home,
        R.string.tab_vitals to Icons.Filled.Favorite,
        R.string.tab_log to Icons.Filled.Create,
        R.string.tab_reports to Icons.Filled.DateRange,
    )
    Scaffold(
        bottomBar = {
            if (!settings) NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, contentDescription = null) }, label = { Text(s(label)) })
                }
            }
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s(if (settings) R.string.settings else tabs[tab].first), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(me.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { settings = !settings }) {
                    Icon(if (settings) Icons.Filled.Close else Icons.Filled.Settings, contentDescription = s(R.string.settings))
                }
            }
            if (settings) Settings(data, me) else when (tab) {
                0 -> Today(me, release, save)
                1 -> Vitals(me, save)
                2 -> Log(me, save)
                else -> Reports(me)
            }
        }
    }
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
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Today(me: Profile, release: Updater.Release?, save: (Entry) -> Unit) {
    val ctx = LocalContext.current
    val today = LocalDate.now()
    val steps = me.steps[today.toString()] ?: 0
    val week = weekSteps(me, today)
    var granted by remember { mutableStateOf(StepService.canRun(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = StepService.canRun(ctx) }
    LaunchedEffect(granted) { if (granted) StepService.start(ctx) }

    if (release != null) UpdateCard(release)
    if (!StepService.hasSensor(ctx)) {
        Section(s(R.string.steps)) { Text(s(R.string.no_sensor)) }
    } else if (!granted) {
        Section(s(R.string.steps)) {
            Text(s(R.string.steps_permission))
            Button(onClick = { ask.launch(StepService.perms()) }) { Text(s(R.string.enable)) }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(steps.toFloat() / me.dailyGoal) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(steps.toString(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(ctx.getString(R.string.of_goal, me.dailyGoal), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Section(s(R.string.this_week)) {
        Bars(week, me.dailyGoal, DayOfWeek.values().map { it.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()) })
        Stat(s(R.string.week_total), ctx.getString(R.string.of_total, week.sum(), me.weeklyGoal))
        LinearProgressIndicator(progress = { (week.sum().toFloat() / me.weeklyGoal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Stat(s(R.string.streak), ctx.getString(R.string.f_days, streak(me, today)))
    }
    val water = me.since(T.WATER, dayStart(today)).sumOf { it.a }.toInt()
    Section(s(R.string.t_water)) {
        Stat(s(R.string.today), ctx.getString(R.string.of_total, water, me.waterGoal))
        LinearProgressIndicator(progress = { (water.toFloat() / me.waterGoal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { save(Entry(T.WATER, now(), 250.0)) }) { Text(s(R.string.add_glass)) }
    }
    SleepSection(me, save)
    Section(s(R.string.latest)) {
        listOf(T.HR, T.BP, T.GLU).forEach { type ->
            val e = me.last(type)
            Stat(typeName(ctx, type), e?.value(ctx) ?: s(R.string.none))
            if (e != null && level(e) != Level.OK) LevelText(level(e))
        }
    }
}

@Composable
fun SleepSection(me: Profile, save: (Entry) -> Unit) {
    val ctx = LocalContext.current
    Section(s(R.string.t_sleep)) {
        val last = me.last(T.SLEEP)
        Stat(s(R.string.last_sleep), last?.value(ctx) ?: s(R.string.none))
        Stat(s(R.string.goal), ctx.getString(R.string.f_hours, me.sleepGoal.toDouble()))
        LineChart(me.series(T.SLEEP))
        if (me.sleepStart == 0L) {
            Button(onClick = { Store.edit { it.copy(sleepStart = now()) } }) { Text(s(R.string.sleep_start)) }
        } else {
            Text(ctx.getString(R.string.sleeping_since, whenText(me.sleepStart)))
            Button(onClick = {
                val hours = (now() - me.sleepStart) / 3600000.0
                if (hours in 0.25..20.0) save(Entry(T.SLEEP, now(), hours))
                Store.edit { it.copy(sleepStart = 0) }
            }) { Text(s(R.string.sleep_stop)) }
        }
    }
}

@Composable
fun Vitals(me: Profile, save: (Entry) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dlg by remember { mutableStateOf("") }
    var glucoseWhen by remember { mutableIntStateOf(0) }
    val runImport: () -> Unit = {
        scope.launch {
            try {
                toast(ctx, ctx.getString(R.string.imported, Hc.sync(ctx)))
            } catch (e: Exception) {
                toast(ctx, ctx.getString(R.string.hc_failed))
            }
        }
    }
    val hcAsk = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { got ->
        if (got.isNotEmpty()) runImport()
    }

    Section(s(R.string.t_hr)) {
        val e = me.last(T.HR)
        Stat(s(R.string.latest), e?.value(ctx) ?: s(R.string.none))
        if (e != null) LevelText(level(e))
        LineChart(me.series(T.HR))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dlg = "hr" }) { Text(s(R.string.measure)) }
            OutlinedButton(onClick = { dlg = "hrm" }) { Text(s(R.string.add)) }
        }
    }
    Section(s(R.string.t_bp)) {
        val e = me.last(T.BP)
        Stat(s(R.string.latest), e?.value(ctx) ?: s(R.string.none))
        if (e != null) LevelText(level(e))
        LineChart(me.series(T.BP))
        Text(s(R.string.bp_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { dlg = "bp" }) { Text(s(R.string.add)) }
    }
    Section(s(R.string.t_glu)) {
        val e = me.last(T.GLU)
        Stat(s(R.string.latest), e?.value(ctx) ?: s(R.string.none))
        if (e != null) LevelText(level(e))
        if (e != null && e.b.toInt() == 0 && e.a > me.fastingMax) Text(ctx.getString(R.string.above_target, me.fastingMax), color = MaterialTheme.colorScheme.primary)
        LineChart(me.series(T.GLU))
        Stat(s(R.string.t_hba1c), me.last(T.HBA1C)?.value(ctx) ?: s(R.string.none))
        Text(s(R.string.glu_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dlg = "glu" }) { Text(s(R.string.add)) }
            OutlinedButton(onClick = { dlg = "a1c" }) { Text(s(R.string.t_hba1c)) }
        }
    }
    Section(s(R.string.wearable)) {
        Text(s(R.string.hc_desc), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {
            if (!Hc.available(ctx)) toast(ctx, ctx.getString(R.string.hc_missing)) else scope.launch {
                try {
                    if (Hc.granted(ctx)) runImport() else hcAsk.launch(Hc.perms)
                } catch (e: Exception) {
                    toast(ctx, ctx.getString(R.string.hc_failed))
                }
            }
        }) { Text(s(R.string.hc_import)) }
    }

    when (dlg) {
        "hr" -> MeasureHr(onDone = { save(Entry(T.HR, now(), it.toDouble())); dlg = "" }, onClose = { dlg = "" })
        "hrm" -> RulerDialog(s(R.string.t_hr), listOf(RulerSpec(s(R.string.unit_bpm), 30..220, 1, 72)), onClose = { dlg = "" }) { v, _ ->
            save(Entry(T.HR, now(), v[0].toDouble())); dlg = ""
        }
        "bp" -> RulerDialog(
            s(R.string.t_bp),
            listOf(RulerSpec(s(R.string.systolic), 60..260, 1, 120), RulerSpec(s(R.string.diastolic), 30..160, 1, 80)),
            onClose = { dlg = "" },
        ) { v, _ -> save(Entry(T.BP, now(), v[0].toDouble(), v[1].toDouble())); dlg = "" }
        "glu" -> RulerDialog(
            s(R.string.t_glu),
            listOf(RulerSpec(s(R.string.unit_mgdl), 20..600, 1, 100) { "$it  (" + String.format(Locale.US, "%.1f", it / 18.0) + " mmol/L)" }),
            extra = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.fasting, R.string.after_meal, R.string.random).forEachIndexed { i, label ->
                        FilterChip(selected = glucoseWhen == i, onClick = { glucoseWhen = i }, label = { Text(s(label)) })
                    }
                }
            },
            onClose = { dlg = "" },
        ) { v, _ -> save(Entry(T.GLU, now(), v[0].toDouble(), glucoseWhen.toDouble())); dlg = "" }
        "a1c" -> RulerDialog(s(R.string.t_hba1c), listOf(RulerSpec("%", 40..150, 1, 60) { String.format(Locale.US, "%.1f", it / 10.0) }), onClose = { dlg = "" }) { v, _ ->
            save(Entry(T.HBA1C, now(), v[0] / 10.0)); dlg = ""
        }
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
fun Log(me: Profile, save: (Entry) -> Unit) {
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf("") }
    Section(s(R.string.add)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dlg = "meal" }, modifier = Modifier.weight(1f)) { Text(s(R.string.t_meal)) }
            Button(onClick = { dlg = "water" }, modifier = Modifier.weight(1f)) { Text(s(R.string.t_water)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { dlg = "weight" }, modifier = Modifier.weight(1f)) { Text(s(R.string.t_weight)) }
            Button(onClick = { dlg = "sleep" }, modifier = Modifier.weight(1f)) { Text(s(R.string.t_sleep)) }
        }
    }
    Section(s(R.string.t_weight)) {
        Stat(s(R.string.latest), me.last(T.WEIGHT)?.value(ctx) ?: s(R.string.none))
        me.bmi()?.let { Stat(s(R.string.bmi), String.format(Locale.US, "%.1f", it)) }
        LineChart(me.series(T.WEIGHT))
    }
    val carbs = me.since(T.MEAL, dayStart()).sumOf { it.a }.toInt()
    Section(s(R.string.t_meal)) { Stat(s(R.string.carbs_today), ctx.getString(R.string.f_carbs, carbs)) }
    Section(s(R.string.recent)) {
        val recent = me.entries.sortedByDescending { it.ts }.take(40)
        if (recent.isEmpty()) Text(s(R.string.none))
        recent.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(typeName(ctx, e.type) + ": " + e.value(ctx), style = MaterialTheme.typography.bodyLarge)
                    Text(whenText(e.ts), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { Store.edit { p -> p.copy(entries = p.entries - e) } }) { Icon(Icons.Filled.Delete, contentDescription = s(R.string.delete)) }
            }
        }
    }
    when (dlg) {
        "meal" -> RulerDialog(s(R.string.t_meal), listOf(RulerSpec(s(R.string.carbs_g), 0..300, 5, 45)), note = true, onClose = { dlg = "" }) { v, note ->
            save(Entry(T.MEAL, now(), v[0].toDouble(), note = note)); dlg = ""
        }
        "water" -> RulerDialog(s(R.string.t_water), listOf(RulerSpec("ml", 50..2000, 50, 250)), onClose = { dlg = "" }) { v, _ ->
            save(Entry(T.WATER, now(), v[0].toDouble())); dlg = ""
        }
        "weight" -> RulerDialog(
            s(R.string.t_weight),
            listOf(RulerSpec("kg", 200..2500, 5, ((me.last(T.WEIGHT)?.a ?: 70.0) * 10).toInt() / 5 * 5) { String.format(Locale.US, "%.1f", it / 10.0) }),
            onClose = { dlg = "" },
        ) { v, _ -> save(Entry(T.WEIGHT, now(), v[0] / 10.0)); dlg = "" }
        "sleep" -> RulerDialog(s(R.string.t_sleep), listOf(RulerSpec(s(R.string.hours), 1..32, 1, 14) { String.format(Locale.US, "%.1f", it / 2.0) }), onClose = { dlg = "" }) { v, _ ->
            save(Entry(T.SLEEP, now(), v[0] / 2.0)); dlg = ""
        }
    }
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
