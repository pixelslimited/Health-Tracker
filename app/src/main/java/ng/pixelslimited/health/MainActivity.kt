package ng.pixelslimited.health

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.reminders.Reminders
import ng.pixelslimited.health.ui.AppRoot
import ng.pixelslimited.health.ui.AppTheme

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var authBusy = false

    override fun onResume() {
        super.onResume()
        // Add steps taken while the service was stopped, and detect last night's sleep from screen times.
        ng.pixelslimited.health.steps.StepSync.catchUp(applicationContext)
        ng.pixelslimited.health.sleep.SleepDetector.detect(applicationContext)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("drive", false)) ng.pixelslimited.health.drive.Drive.openManual()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra("drive", false) == true) ng.pixelslimited.health.drive.Drive.openManual()
        Store.init(applicationContext)
        Reminders.scheduleAll(this)
        locked = Store.data.value.lock
        setContent {
            val data by Store.data.collectAsState()
            AppTheme(data.theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LaunchedEffect(ng.pixelslimited.health.drive.Drive.open) { showOverLock(ng.pixelslimited.health.drive.Drive.open) }
                    if (locked) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(getString(R.string.locked), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { unlock() }) { Text(getString(R.string.unlock)) }
                        }
                    } else {
                        AppRoot()
                    }
                    // The speedometer shows even over the lock screen and the app lock; the rest of the app stays hidden.
                    if (ng.pixelslimited.health.drive.Drive.open) ng.pixelslimited.health.ui.DriveScreen { ng.pixelslimited.health.drive.Drive.dismiss() }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ng.pixelslimited.health.drive.Drive.visible = true
        if (locked && !authBusy && !ng.pixelslimited.health.drive.Drive.open) unlock()
    }

    @Suppress("DEPRECATION")
    private fun showOverLock(on: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(on)
            setTurnScreenOn(on)
        } else if (on) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    override fun onStop() {
        super.onStop()
        ng.pixelslimited.health.drive.Drive.visible = false
        Store.flush()
        if (Store.data.value.lock && !authBusy) locked = true
    }

    private fun unlock() {
        if (BiometricManager.from(this).canAuthenticate(AUTH) != BiometricManager.BIOMETRIC_SUCCESS) {
            // The phone's screen lock was removed; there is nothing to check against.
            locked = false
            return
        }
        authBusy = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                authBusy = false
                locked = false
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                authBusy = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock))
                .setAllowedAuthenticators(AUTH)
                .build()
        )
    }

    companion object {
        const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
