package ng.pixelslimited.health.places

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import ng.pixelslimited.health.data.Entry
import ng.pixelslimited.health.data.Store
import ng.pixelslimited.health.data.T
import ng.pixelslimited.health.data.metresBetween
import ng.pixelslimited.health.steps.StepService
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Logs stops and routes in the background while the step service runs.
 * A stop is 2+ minutes within 70 m of one spot; distance is the path actually travelled between stops.
 */
object PlaceTracker : LocationListener {
    private const val STOP_MS = 120_000L
    private const val RADIUS = 70.0
    private const val STEP = 15.0
    private const val BEST = 80f

    private class St {
        var hasPrev = false
        var pLat = 0.0
        var pLon = 0.0
        var leg = 0.0
        var aSince = 0L
        var aLast = 0L
        var aLat = 0.0
        var aLon = 0.0
        var legA = 0.0
        var fLat = 0.0
        var fLon = 0.0
        var fTs = 0L
        var dmDay = ""
        var dm = 0.0
    }

    private var app: Context? = null
    private var mgr: LocationManager? = null
    private var st = St()
    private val worker = Executors.newSingleThreadExecutor()
    private val buf = StringBuilder()
    private var bufN = 0
    private var bufDay = ""

    private fun sp(c: Context) = c.getSharedPreferences("places", Context.MODE_PRIVATE)
    private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    fun enabled(c: Context) = sp(c).getBoolean("on", false)
    fun setEnabled(c: Context, on: Boolean) = sp(c).edit().putBoolean("on", on).apply()
    fun hasPermission(c: Context) = granted(c, Manifest.permission.ACCESS_FINE_LOCATION) || granted(c, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun hasBackground(c: Context) = Build.VERSION.SDK_INT < 29 || granted(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    fun canRun(c: Context) = enabled(c) && hasPermission(c)

    /** Metres travelled so far today, updated on every fix. */
    fun todayMetres(c: Context): Double {
        val p = sp(c)
        return if (p.getString("dmDay", "") == java.time.LocalDate.now().toString()) p.getString("dm", "0")?.toDoubleOrNull() ?: 0.0 else 0.0
    }

    /** Newest known position: our own last fix or any provider's last known location. Instant, no waiting for GPS. */
    @SuppressLint("MissingPermission")
    fun lastFix(c: Context): Pair<Double, Double>? {
        val p = sp(c)
        var lat = p.getString("fLat", "")?.toDoubleOrNull()
        var lon = p.getString("fLon", "")?.toDoubleOrNull()
        var ts = p.getLong("fTs", 0L)
        if (hasPermission(c)) {
            try {
                val m = c.getSystemService(LocationManager::class.java)
                for (name in listOf("fused", "gps", "network", "passive")) {
                    if (name !in m.allProviders) continue
                    val l = m.getLastKnownLocation(name) ?: continue
                    if (l.time > ts) { ts = l.time; lat = l.latitude; lon = l.longitude }
                }
            } catch (e: Exception) {
            }
        }
        return if (lat != null && lon != null) lat to lon else null
    }

    /** Arrival time if you are standing at a stop right now, else 0. */
    fun hereSince(c: Context): Long {
        val p = sp(c)
        val since = p.getLong("aSince", 0L)
        val last = p.getLong("aLast", 0L)
        val now = System.currentTimeMillis()
        return if (since > 0 && last - since >= STOP_MS && now - last < 15 * 60_000L) since else 0L
    }

    /** The service type must include location, so restart it after the permission is granted. */
    fun restartService(c: Context) {
        c.stopService(Intent(c, StepService::class.java))
        StepService.start(c)
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    fun start(c: Context) {
        if (!canRun(c) || mgr != null) return
        val a = c.applicationContext
        app = a
        load(a)
        val m = a.getSystemService(LocationManager::class.java)
        if (register(m, false)) mgr = m
    }

    private var fast = false
    private var lastMove = 0L

    /** Slow (battery-friendly) while still; fast, dense fixes while moving so the line follows the road. */
    @SuppressLint("MissingPermission")
    private fun register(m: LocationManager, quick: Boolean): Boolean {
        try { m.removeUpdates(this) } catch (e: Exception) { }
        val have = m.allProviders
        val plan = when {
            quick && "gps" in have -> listOf(Triple("gps", 4_000L, 8f))
            quick && "fused" in have -> listOf(Triple("fused", 4_000L, 8f))
            quick -> listOf(Triple("network", 5_000L, 8f))
            "fused" in have -> listOf(Triple("fused", 60_000L, 0f))
            else -> listOf(Triple("network", 60_000L, 0f), Triple("gps", 120_000L, 0f))
        }
        var any = false
        plan.forEach { (p, every, dist) ->
            if (p in have) {
                try {
                    m.requestLocationUpdates(p, every, dist, this, Looper.getMainLooper())
                    any = true
                } catch (e: Exception) {
                }
            }
        }
        if (any) fast = quick
        return any
    }

    private fun adapt(now: Long, moved: Boolean) {
        val m = mgr ?: return
        if (moved) lastMove = now
        if (moved && !fast) register(m, true)
        else if (fast && now - lastMove > 180_000L) register(m, false)
    }

    @Synchronized
    fun stop() {
        try {
            mgr?.removeUpdates(this)
        } catch (e: Exception) {
        }
        mgr = null
        flushRoute()
        app?.let { save(it) }
    }

    override fun onLocationChanged(l: Location) {
        val c = app ?: return
        if (l.hasAccuracy() && l.accuracy > BEST) return
        handle(c, l)
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Required below Android 11")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    @Synchronized
    private fun handle(c: Context, l: Location) {
        val now = System.currentTimeMillis()
        val lat = l.latitude
        val lon = l.longitude
        val s = st
        s.fLat = lat; s.fLon = lon; s.fTs = now
        val today = java.time.LocalDate.now().toString()
        if (s.dmDay != today) { s.dmDay = today; s.dm = 0.0 }
        if (!s.hasPrev) {
            s.hasPrev = true
            s.pLat = lat
            s.pLon = lon
            s.leg = 0.0
            route(lat, lon, now)
        } else {
            val d = metresBetween(s.pLat, s.pLon, lat, lon)
            adapt(now, d >= STEP * 2)
            if (d >= STEP) {
                s.dm += d
                s.leg += d
                s.pLat = lat
                s.pLon = lon
                route(lat, lon, now)
            }
        }
        if (s.aSince == 0L) {
            s.aLat = lat; s.aLon = lon; s.aSince = now; s.aLast = now; s.legA = s.leg
        } else if (metresBetween(s.aLat, s.aLon, lat, lon) <= RADIUS) {
            s.aLast = now
        } else {
            if (s.aLast - s.aSince >= STOP_MS) {
                saveStop(c, s.aSince, s.aLast, s.aLat, s.aLon, s.legA)
                s.leg = metresBetween(s.aLat, s.aLon, lat, lon)
            }
            s.aLat = lat; s.aLon = lon; s.aSince = now; s.aLast = now; s.legA = s.leg
        }
        save(c)
    }

    private fun route(lat: Double, lon: Double, ts: Long) {
        val day = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        if (bufDay.isNotEmpty() && bufDay != day) flushRoute()
        bufDay = day
        buf.append(String.format(Locale.US, "%.5f,%.5f,%d;", lat, lon, ts / 1000))
        bufN++
        if (bufN >= 5) flushRoute()
    }

    private fun flushRoute() {
        if (buf.isEmpty()) return
        val d = bufDay
        val s = buf.toString()
        buf.setLength(0)
        bufN = 0
        Store.addRoute(d, s)
    }

    /** Looks the address up off the main thread; offline it is saved with coordinates only. */
    private fun saveStop(c: Context, arrive: Long, depart: Long, lat: Double, lon: Double, metres: Double) {
        worker.execute {
            val name = geocode(c, lat, lon)
            Store.edit { it.copy(entries = it.entries + Entry(T.STOP, arrive, lat, lon, "$depart|${metres.toLong()}|$name")) }
        }
    }

    @Suppress("DEPRECATION")
    private fun geocode(c: Context, lat: Double, lon: Double): String = try {
        val a = if (Geocoder.isPresent()) Geocoder(c, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull() else null
        if (a == null) "" else listOfNotNull(
            a.featureName?.takeIf { f -> f.any { ch -> ch.isLetter() } },
            a.thoroughfare,
            a.subLocality ?: a.locality,
        ).distinct().take(2).joinToString(", ").replace("|", " ")
    } catch (e: Exception) {
        ""
    }

    private fun load(c: Context) {
        val p = sp(c)
        val s = St()
        s.hasPrev = p.getBoolean("hasPrev", false)
        s.pLat = p.getString("pLat", "0")?.toDoubleOrNull() ?: 0.0
        s.pLon = p.getString("pLon", "0")?.toDoubleOrNull() ?: 0.0
        s.leg = p.getString("leg", "0")?.toDoubleOrNull() ?: 0.0
        s.aSince = p.getLong("aSince", 0L)
        s.aLast = p.getLong("aLast", 0L)
        s.aLat = p.getString("aLat", "0")?.toDoubleOrNull() ?: 0.0
        s.aLon = p.getString("aLon", "0")?.toDoubleOrNull() ?: 0.0
        s.legA = p.getString("legA", "0")?.toDoubleOrNull() ?: 0.0
        s.fLat = p.getString("fLat", "0")?.toDoubleOrNull() ?: 0.0
        s.fLon = p.getString("fLon", "0")?.toDoubleOrNull() ?: 0.0
        s.fTs = p.getLong("fTs", 0L)
        s.dmDay = p.getString("dmDay", "") ?: ""
        s.dm = p.getString("dm", "0")?.toDoubleOrNull() ?: 0.0
        st = s
    }

    private fun save(c: Context) {
        val s = st
        sp(c).edit().putBoolean("hasPrev", s.hasPrev).putString("pLat", s.pLat.toString()).putString("pLon", s.pLon.toString())
            .putString("leg", s.leg.toString()).putLong("aSince", s.aSince).putLong("aLast", s.aLast)
            .putString("aLat", s.aLat.toString()).putString("aLon", s.aLon.toString()).putString("legA", s.legA.toString())
            .putString("fLat", s.fLat.toString()).putString("fLon", s.fLon.toString()).putLong("fTs", s.fTs)
            .putString("dmDay", s.dmDay).putString("dm", s.dm.toString()).apply()
    }
}
