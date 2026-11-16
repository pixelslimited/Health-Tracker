package ng.pixelslimited.health.drive

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import ng.pixelslimited.health.App
import ng.pixelslimited.health.MainActivity
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.metresBetween

/** Live speed, heading and trip figures, plus the "you are driving" detector that opens the drive screen. */
object Drive {
    class Fix(val lat: Double, val lon: Double, val speed: Float, val bearing: Float, val acc: Float, val alt: Double, val ts: Long)

    var open by mutableStateOf(false)
    var fix by mutableStateOf<Fix?>(null)
    var tripM by mutableDoubleStateOf(0.0)
    var maxMs by mutableFloatStateOf(0f)
    var movingMs by mutableLongStateOf(0L)

    /** True while the app is on screen; set by MainActivity. */
    @Volatile var visible = false

    private const val DRIVE_MS = 2.78f   // 10 km/h
    private const val STILL_MS = 1.4f
    private var hits = 0
    private var armed = true
    private var slowSince = 0L

    fun auto(c: Context) = c.getSharedPreferences("drive", 0).getBoolean("auto", true)
    fun setAuto(c: Context, on: Boolean) = c.getSharedPreferences("drive", 0).edit().putBoolean("auto", on).apply()
    fun key(c: Context): String = c.getSharedPreferences("drive", 0).getString("tt", "") ?: ""
    fun setKey(c: Context, k: String) = c.getSharedPreferences("drive", 0).edit().putString("tt", k.trim()).apply()

    /** Places you searched for lately, newest first ("name, address, lat, lon" per line). */
    fun recents(c: Context): List<Sug> = (c.getSharedPreferences("drive", 0).getString("recent", "") ?: "").split("\n").mapNotNull {
        val p = it.split("\t")
        val la = p.getOrNull(2)?.toDoubleOrNull()
        val lo = p.getOrNull(3)?.toDoubleOrNull()
        if (p.size == 4 && la != null && lo != null) Sug("", p[0], p[1], 0, la, lo) else null
    }

    fun addRecent(c: Context, p: PlaceInfo) {
        fun clean(x: String) = x.replace("\t", " ").replace("\n", " ")
        val sp = c.getSharedPreferences("drive", 0)
        val name = clean(p.name)
        val line = name + "\t" + clean(p.addr) + "\t" + p.lat + "\t" + p.lon
        val old = (sp.getString("recent", "") ?: "").split("\n").filter { it.isNotEmpty() && !it.startsWith(name + "\t") }
        sp.edit().putString("recent", (listOf(line) + old).take(8).joinToString("\n")).apply()
    }

    fun openManual() { armed = false; open = true }
    fun dismiss() { armed = false; open = false }

    fun resetTrip() { tripM = 0.0; maxMs = 0f; movingMs = 0L }

    fun feed(c: Context, l: Location) {
        if (l.hasAccuracy() && l.accuracy > 100f) return
        val now = System.currentTimeMillis()
        val prev = fix
        val sp = if (l.hasSpeed()) l.speed.coerceAtLeast(0f) else 0f
        val br = if (l.hasBearing() && sp > 1.2f) l.bearing else (prev?.bearing ?: 0f)
        fix = Fix(l.latitude, l.longitude, sp, br, if (l.hasAccuracy()) l.accuracy else 0f, if (l.hasAltitude()) l.altitude else 0.0, now)
        if (prev != null) {
            val dt = now - prev.ts
            val d = metresBetween(prev.lat, prev.lon, l.latitude, l.longitude)
            if (sp > 1f && d in 2.0..300.0 && dt in 1..30_000) {
                tripM += d
                movingMs += dt
            }
        }
        if (sp > maxMs) maxMs = sp
        detect(c, sp, now)
        ng.pixelslimited.health.steps.StepService.pushSpeed()
    }

    private fun detect(c: Context, sp: Float, now: Long) {
        if (sp < STILL_MS) {
            if (slowSince == 0L) slowSince = now
            if (now - slowSince > 120_000L) armed = true
            hits = 0
            return
        }
        slowSince = 0L
        if (sp >= DRIVE_MS) hits++ else return
        if (hits >= 2 && armed && !open && auto(c)) {
            armed = false
            if (visible) open = true else notify(c.applicationContext)
        }
    }

    private fun notify(c: Context) {
        try {
            val i = Intent(c, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("drive", true)
            // With "display over other apps" allowed, Android lets us open the screen straight away.
            if (android.provider.Settings.canDrawOverlays(c)) {
                c.startActivity(i)
                return
            }
            val pi = PendingIntent.getActivity(c, 4101, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(c, App.CH_DRIVE)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(c.getString(R.string.drive_notif_title))
                .setContentText(c.getString(R.string.drive_notif_text))
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .build()
            c.getSystemService(android.app.NotificationManager::class.java).notify(4101, n)
        } catch (e: Exception) {
        }
    }

    // One-second GPS fixes while the drive screen is open.
    private var lm: LocationManager? = null
    private var app: Context? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            app?.let { feed(it, l) }
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Required below Android 11")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    fun listen(c: Context) {
        if (lm != null) return
        val a = c.applicationContext
        val m = a.getSystemService(LocationManager::class.java)
        try {
            if ("gps" in m.allProviders) {
                m.requestLocationUpdates("gps", 1000L, 0f, listener, Looper.getMainLooper())
                app = a
                lm = m
            }
        } catch (e: Exception) {
        }
    }

    fun unlisten() {
        try {
            lm?.removeUpdates(listener)
        } catch (e: Exception) {
        }
        lm = null
    }
}
