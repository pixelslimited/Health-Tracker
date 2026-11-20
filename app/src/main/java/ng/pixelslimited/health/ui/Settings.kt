package ng.pixelslimited.health.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ng.pixelslimited.health.BuildConfig
import ng.pixelslimited.health.MainActivity
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.AppData
import ng.pixelslimited.health.data.Profile
import ng.pixelslimited.health.data.Reminder
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.reminders.Reminders
import ng.pixelslimited.health.update.Updater
import java.util.Locale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization

private fun switchProfile(ctx: Context, id: String) {
    Reminders.cancelAll(ctx)
    Store.update { it.copy(active = id) }
    Reminders.scheduleAll(ctx)
}

/** Settings group: small label above, rounded card below. */
@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            Modifier.fillMaxWidth().clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f), shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun Settings(data: AppData, me: Profile) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dlg by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf<Profile?>(null) }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) { release = Updater.check() }

    SettingsCard(s(R.string.profiles)) {
        data.profiles.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = p.id == me.id, onClick = { switchProfile(ctx, p.id) })
                Text(p.name, Modifier.weight(1f))
                if (p.id != me.id) IconButton(onClick = { remove = p }) { Icon(Icons.Outlined.Delete, contentDescription = s(R.string.delete)) }
            }
        }
        OutlinedButton(onClick = { dlg = "profile" }) { Text(s(R.string.add_profile)) }
    }

    SettingsCard(s(R.string.appearance)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark).forEachIndexed { i, label ->
                FilterChip(selected = data.theme == i, onClick = { Store.update { it.copy(theme = i) } }, label = { Text(s(label)) })
            }
        }
    }

    SettingsCard(s(R.string.about_you)) {
        OutlinedTextField(me.name, { v -> Store.edit { it.copy(name = v) } }, label = { Text(s(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = me.male, onClick = { Store.edit { it.copy(male = true) } }, label = { Text(s(R.string.male)) })
            FilterChip(selected = !me.male, onClick = { Store.edit { it.copy(male = false) } }, label = { Text(s(R.string.female)) })
        }
        Ruler(me.birthYear, { v -> Store.edit { it.copy(birthYear = v) } }, 1920..2024, 1, s(R.string.birth_year))
        Ruler(me.heightCm, { v -> Store.edit { it.copy(heightCm = v) } }, 100..230, 1, s(R.string.height_cm))
    }

    SettingsCard(s(R.string.goals)) {
        Ruler(me.dailyGoal, { v -> Store.edit { it.copy(dailyGoal = v) } }, 1000..40000, 500, s(R.string.daily_steps))
        Ruler(me.weeklyGoal, { v -> Store.edit { it.copy(weeklyGoal = v) } }, 5000..280000, 1000, s(R.string.weekly_steps))
        Ruler(me.waterGoal, { v -> Store.edit { it.copy(waterGoal = v) } }, 750..6000, 750, s(R.string.water_ml))
        Ruler(me.sleepGoal, { v -> Store.edit { it.copy(sleepGoal = v) } }, 4..12, 1, s(R.string.sleep_hours))
    }

    SettingsCard(s(R.string.targets)) {
        Ruler(me.fastingMax, { v -> Store.edit { it.copy(fastingMax = v) } }, 80..200, 1, s(R.string.fasting_target))
        Ruler(me.hba1cTarget, { v -> Store.edit { it.copy(hba1cTarget = v) } }, 50..100, 1, s(R.string.hba1c_target)) { String.format(Locale.US, "%.1f %%", it / 10.0) }
        Text(s(R.string.targets_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    var batteryOk by remember { mutableStateOf(ctx.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)) }
    if (!batteryOk) SettingsCard(s(R.string.keep_alive)) {
        Text(s(R.string.keep_alive_desc), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = {
            try {
                ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
            batteryOk = true
        }) { Text(s(R.string.allow_btn)) }
    }

    SettingsCard(s(R.string.emergency)) {
        Text(s(R.string.emergency_desc), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(me.contactName, { v -> Store.edit { it.copy(contactName = v) } }, label = { Text(s(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
        OutlinedTextField(
            me.contactPhone, { v -> Store.edit { it.copy(contactPhone = v.filter { c -> c.isDigit() || c == '+' }) } },
            label = { Text(s(R.string.phone)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        )
    }

    SettingsCard(s(R.string.security)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s(R.string.app_lock), Modifier.weight(1f))
            Switch(checked = data.lock, onCheckedChange = { on ->
                if (on && BiometricManager.from(ctx).canAuthenticate(MainActivity.AUTH) != BiometricManager.BIOMETRIC_SUCCESS) toast(ctx, ctx.getString(R.string.need_screen_lock))
                else Store.update { it.copy(lock = on) }
            })
        }
        Text(s(R.string.security_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (Build.VERSION.SDK_INT >= 33) SettingsCard(s(R.string.language)) {
        OutlinedButton(onClick = {
            try {
                ctx.startActivity(Intent(SystemSettings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            } catch (e: Exception) {
                toast(ctx, ctx.getString(R.string.no_app))
            }
        }) { Text(s(R.string.change_language)) }
    }

    SettingsCard(s(R.string.updates)) {
        Text(ctx.getString(R.string.version, BuildConfig.VERSION_NAME))
        val found = release
        if (found != null) UpdateCard(found) else OutlinedButton(onClick = {
            scope.launch {
                release = Updater.check()
                if (release == null) toast(ctx, ctx.getString(R.string.up_to_date))
            }
        }) { Text(s(R.string.check_updates)) }
    }

    Text(s(R.string.disclaimer), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(s(R.string.made_by), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    when (dlg) {
        "profile" -> {
            var name by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { dlg = "" },
                title = { Text(s(R.string.add_profile)) },
                text = { OutlinedTextField(name, { name = it }, label = { Text(s(R.string.name)) }, singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done)) },
                confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = {
                        val p = Profile(name = name.trim())
                        Store.update { it.copy(profiles = it.profiles + p) }
                        switchProfile(ctx, p.id)
                        dlg = ""
                    }) { Text(s(R.string.save)) }
                },
                dismissButton = { TextButton(onClick = { dlg = "" }) { Text(s(R.string.cancel)) } },
            )
        }
    }

    remove?.let { p ->
        AlertDialog(
            onDismissRequest = { remove = null },
            title = { Text(s(R.string.delete_profile)) },
            text = { Text(ctx.getString(R.string.delete_profile_desc, p.name)) },
            confirmButton = {
                TextButton(onClick = {
                    Store.update { d -> d.copy(profiles = d.profiles.filter { it.id != p.id }) }
                    remove = null
                }) { Text(s(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { remove = null }) { Text(s(R.string.cancel)) } },
        )
    }
}
