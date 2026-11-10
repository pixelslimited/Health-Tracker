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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(applicationContext)
        Reminders.scheduleAll(this)
        locked = Store.data.value.lock
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (locked) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(getString(R.string.locked), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { unlock() }) { Text(getString(R.string.unlock)) }
                        }
                    } else {
                        AppRoot()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (locked && !authBusy) unlock()
    }

    override fun onStop() {
        super.onStop()
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
