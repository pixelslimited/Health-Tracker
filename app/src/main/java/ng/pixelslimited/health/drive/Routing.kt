package ng.pixelslimited.health.drive

import android.content.Context
import android.location.Geocoder
import org.json.JSONArray
import org.json.JSONObject
import ng.pixelslimited.health.data.metresBetween
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** A driving route with its length, normal travel time and the extra delay traffic adds (zero without a traffic key). */
class Route(val lat: DoubleArray, val lon: DoubleArray, val metres: Double, val secs: Double, val delay: Double, val live: Boolean) {
    private val cum = DoubleArray(lat.size).also { for (i in 1 until lat.size) it[i] = it[i - 1] + metresBetween(lat[i - 1], lon[i - 1], lat[i], lon[i]) }
    val pathMetres: Double get() = if (cum.isEmpty()) 0.0 else cum.last()

    /** Metres still to go along the route, and how far off it the position is. */
    fun progress(la: Double, lo: Double): Pair<Double, Double> {
        var best = Double.MAX_VALUE
        var idx = 0
        for (i in lat.indices) {
            val d = metresBetween(la, lo, lat[i], lon[i])
            if (d < best) { best = d; idx = i }
        }
        return (pathMetres - cum[idx]).coerceAtLeast(0.0) to best
    }
}

class Hit(val name: String, val lat: Double, val lon: Double)

class Incident(val text: String, val lat: Double, val lon: Double)

object Routing {
    class Result(val route: Route?, val keyBad: Boolean)

    private fun get(u: String): String? {
        val c = URL(u).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "ng.pixelslimited.health")
            if (c.responseCode in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null
        } catch (e: Exception) {
            null
        } finally {
            c.disconnect()
        }
    }

    private fun code(u: String): Int = try {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.responseCode.also { c.disconnect() }
    } catch (e: Exception) {
        -1
    }

    /** With a TomTom key the route counts live traffic; without one (or if the key is refused) plain road routing is used. */
    fun route(fa: Double, fo: Double, ta: Double, to: Double, key: String, avoidTolls: Boolean = false): Result {
        var keyBad = false
        if (key.isNotEmpty()) {
            val u = String.format(Locale.US, "https://api.tomtom.com/routing/1/calculateRoute/%.6f,%.6f:%.6f,%.6f/json?key=%s&traffic=true&travelMode=car" + (if (avoidTolls) "&avoid=tollRoads" else ""), fa, fo, ta, to, key)
            val body = get(u)
            if (body != null) {
                try {
                    val r = JSONObject(body).getJSONArray("routes").getJSONObject(0)
                    val sum = r.getJSONObject("summary")
                    val pts = ArrayList<Pair<Double, Double>>()
                    val legs = r.getJSONArray("legs")
                    for (i in 0 until legs.length()) {
                        val p = legs.getJSONObject(i).getJSONArray("points")
                        for (j in 0 until p.length()) pts.add(p.getJSONObject(j).getDouble("latitude") to p.getJSONObject(j).getDouble("longitude"))
                    }
                    if (pts.size > 1) {
                        return Result(
                            Route(pts.map { it.first }.toDoubleArray(), pts.map { it.second }.toDoubleArray(),
                                sum.getDouble("lengthInMeters"), sum.getDouble("travelTimeInSeconds"), sum.optDouble("trafficDelayInSeconds", 0.0), true),
                            false,
                        )
                    }
                } catch (e: Exception) {
                }
            } else {
                val cd = code(u)
                keyBad = cd == 401 || cd == 403
            }
        }
        val u = String.format(Locale.US, "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson" + (if (avoidTolls) "&exclude=toll" else ""), fo, fa, to, ta)
        val body = get(u) ?: return Result(null, keyBad)
        return try {
            val r = JSONObject(body).getJSONArray("routes").getJSONObject(0)
            val co: JSONArray = r.getJSONObject("geometry").getJSONArray("coordinates")
            val la = DoubleArray(co.length())
            val lo = DoubleArray(co.length())
            for (i in 0 until co.length()) { lo[i] = co.getJSONArray(i).getDouble(0); la[i] = co.getJSONArray(i).getDouble(1) }
            Result(Route(la, lo, r.getDouble("distance"), r.getDouble("duration"), 0.0, false), keyBad)
        } catch (e: Exception) {
            Result(null, keyBad)
        }
    }

    /** Phone geocoder first (same one that names your stops); OpenStreetMap search if it finds nothing. */
    @Suppress("DEPRECATION")
    fun search(ctx: Context, q: String, nearLat: Double?, nearLon: Double?): List<Hit> {
        val out = ArrayList<Hit>()
        try {
            if (Geocoder.isPresent()) {
                val g = Geocoder(ctx, Locale.getDefault())
                val l = if (nearLat != null && nearLon != null) g.getFromLocationName(q, 5, nearLat - 0.6, nearLon - 0.6, nearLat + 0.6, nearLon + 0.6) else g.getFromLocationName(q, 5)
                l?.forEach { a ->
                    val name = (0..a.maxAddressLineIndex).mapNotNull { a.getAddressLine(it) }.firstOrNull() ?: a.featureName ?: q
                    out.add(Hit(name, a.latitude, a.longitude))
                }
            }
        } catch (e: Exception) {
        }
        if (out.isEmpty()) {
            val body = get("https://nominatim.openstreetmap.org/search?format=json&limit=5&q=" + URLEncoder.encode(q, "UTF-8"))
            try {
                if (body != null) {
                    val a = JSONArray(body)
                    for (i in 0 until a.length()) {
                        val o = a.getJSONObject(i)
                        out.add(Hit(o.getString("display_name"), o.getString("lat").toDouble(), o.getString("lon").toDouble()))
                    }
                }
            } catch (e: Exception) {
            }
        }
        return out
    }

    /** Posted speed limit (km/h) of the road at this spot, from OpenStreetMap; null when unknown. */
    fun limit(lat: Double, lon: Double): Int? {
        val q = String.format(Locale.US, "[out:json][timeout:8];way(around:25,%.6f,%.6f)[highway][maxspeed];out tags 3;", lat, lon)
        val body = get("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8")) ?: return null
        return try {
            val a = JSONObject(body).getJSONArray("elements")
            for (i in 0 until a.length()) {
                val v = a.getJSONObject(i).getJSONObject("tags").optString("maxspeed", "")
                val n = Regex("\\d+").find(v)?.value?.toIntOrNull() ?: continue
                return if (v.contains("mph")) Math.round(n * 1.609).toInt() else n
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** Crashes, jams, closures and road works near here (needs the TomTom key). Nearest first. */
    fun incidents(lat: Double, lon: Double, key: String): List<Incident> {
        if (key.isEmpty()) return emptyList()
        val fields = URLEncoder.encode("{incidents{geometry{type,coordinates},properties{iconCategory,events{description}}}}", "UTF-8")
        val u = String.format(Locale.US, "https://api.tomtom.com/traffic/services/5/incidentDetails?key=%s&bbox=%.5f,%.5f,%.5f,%.5f&fields=%s&language=en-GB&timeValidityFilter=present", key, lon - 0.08, lat - 0.08, lon + 0.08, lat + 0.08, fields)
        val body = get(u) ?: return emptyList()
        val out = ArrayList<Incident>()
        try {
            val a = JSONObject(body).getJSONArray("incidents")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val pr = o.getJSONObject("properties")
                val cat = pr.optInt("iconCategory", 0)
                if (cat !in setOf(1, 6, 7, 8, 9, 14)) continue
                val g = o.getJSONObject("geometry").getJSONArray("coordinates")
                val pt = if (g.get(0) is JSONArray) g.getJSONArray(0) else g
                val name = pr.optJSONArray("events")?.optJSONObject(0)?.optString("description", "") ?: ""
                val label = name.ifEmpty { when (cat) { 1 -> "Accident"; 6 -> "Traffic jam"; 7 -> "Lane closed"; 8 -> "Road closed"; 9 -> "Road works"; else -> "Broken-down vehicle" } }
                out.add(Incident(label, pt.getDouble(1), pt.getDouble(0)))
            }
        } catch (e: Exception) {
        }
        return out.sortedBy { metresBetween(lat, lon, it.lat, it.lon) }.take(2)
    }
}
