package ng.pixelslimited.health.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** All app data lives in one AES-256-GCM encrypted file; the key never leaves the Android Keystore. */
object Store {
    private const val ALIAS = "health_data_key"
    private lateinit var file: File
    private val state = MutableStateFlow(AppData.fresh())
    val data: StateFlow<AppData> = state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var saveJob: Job? = null

    @Volatile
    private var ready = false

    @Synchronized
    fun init(ctx: Context) {
        if (ready) return
        file = File(ctx.filesDir, "data.bin")
        if (file.exists()) {
            try {
                state.value = AppData.of(JSONObject(String(decrypt(file.readBytes()), Charsets.UTF_8)))
            } catch (e: Exception) {
                // Keep the unreadable file instead of overwriting it.
                file.renameTo(File(ctx.filesDir, "data.bad." + System.currentTimeMillis()))
            }
        }
        ready = true
    }

    fun update(f: (AppData) -> AppData) {
        state.update(f)
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1500)
            save()
        }
    }

    /** Edit the active profile. */
    fun edit(f: (Profile) -> Profile) = update { d -> d.copy(profiles = d.profiles.map { if (it.id == d.me.id) f(it) else it }) }

    fun addSteps(day: String, hour: Int, n: Int) = edit { p ->
        val hours = (p.hourly[day] ?: List(24) { 0 }).toMutableList()
        hours[hour] += n
        val kept = (p.hourly + (day to hours)).toSortedMap().entries.toList().takeLast(14).associate { it.key to it.value }
        p.copy(steps = p.steps + (day to ((p.steps[day] ?: 0) + n)), hourly = kept)
    }

    fun flush() {
        saveJob?.cancel()
        scope.launch { save() }
    }

    @Synchronized
    private fun save() {
        if (!ready) return
        try {
            val tmp = File(file.parentFile, "data.tmp")
            tmp.writeBytes(encrypt(state.value.json().toString().toByteArray(Charsets.UTF_8)))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        } catch (e: Exception) {
            // Next change retries the save.
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return c.iv + c.doFinal(plain)
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12))
        return c.doFinal(blob, 12, blob.size - 12)
    }
}
