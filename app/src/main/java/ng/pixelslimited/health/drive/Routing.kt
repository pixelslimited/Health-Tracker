package ng.pixelslimited.health.drive

import android.content.Context
import android.location.Geocoder
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.metresBetween
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** One manoeuvre: what to do, its kind (TURN_LEFT...), and where it happens. */
class Step(val text: String, val man: String, val lat: Double, val lon: Double)

/** Where you are on a route: nearest point, metres left, metres off it, the next manoeuvre (-1 = destination) and metres to it. */
class Prog(val idx: Int, val rem: Double, val off: Double, val next: Int, val toNext: Double)

/** A driving route. jams holds [first point, last point, level] with level 1 slow, 2 jammed. */
class Route(
    val lat: DoubleArray, val lon: DoubleArray, val metres: Double, val secs: Double, val delay: Double, val live: Boolean,
    val steps: List<Step> = emptyList(), val label: String = "", val jams: List<IntArray> = emptyList(),
) {
    private val cum = DoubleArray(lat.size).also { for (i in 1 until lat.size) it[i] = it[i - 1] + metresBetween(lat[i - 1], lon[i - 1], lat[i], lon[i]) }
    private val stepIdx = IntArray(steps.size).also { a ->
        for (i in steps.indices) a[i] = maxOf(nearest(steps[i].lat, steps[i].lon).first, if (i > 0) a[i - 1] else 0)
    }
    val pathMetres: Double get() = if (cum.isEmpty()) 0.0 else cum.last()

    private fun nearest(la: Double, lo: Double): Pair<Int, Double> {
        var best = Double.MAX_VALUE
        var idx = 0
        for (i in lat.indices) {
            val d = metresBetween(la, lo, lat[i], lon[i])
            if (d < best) { best = d; idx = i }
        }
        return idx to best
    }

    fun prog(la: Double, lo: Double): Prog {
        val (i, d) = nearest(la, lo)
        var n = -1
        for (j in 1 until steps.size) if (stepIdx[j] > i) { n = j; break }
        val here = if (cum.isEmpty()) 0.0 else cum[i]
        val to = if (n >= 0) cum[stepIdx[n]] - here else pathMetres - here
        return Prog(i, (pathMetres - here).coerceAtLeast(0.0), d, n, to.coerceAtLeast(0.0))
    }
}

class Hit(val name: String, val lat: Double, val lon: Double)

/** A search suggestion. */
class Sug(val id: String, val main: String, val sub: String, val metres: Int, val lat: Double, val lon: Double)

class PlaceInfo(val name: String, val addr: String, val lat: Double, val lon: Double, val id: String = "")

/** Free services: Photon for search-as-you-type, OSRM for routes and turns; TomTom for live traffic when a key is set. */
object Routing {
    @Volatile var keyBad = false

    private fun fetch(u: String): Pair<Int, String?> {
        val c = URL(u).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("User-Agent", "ng.pixelslimited.health")
            val code = c.responseCode
            code to (if (code in 200..299) c.inputStream.bufferedReader().use { it.readText() } else null)
        } catch (e: Exception) {
            -1 to null
        } finally {
            c.disconnect()
        }
    }

    private fun get(u: String): String? = fetch(u).second

    /** Best route first, then alternatives. With a TomTom key the times count live traffic. */
    fun routes(ctx: Context, fa: Double, fo: Double, d: PlaceInfo, avoidTolls: Boolean, key: String): List<Route> {
        if (key.isNotEmpty()) {
            val lang = if (Locale.getDefault().language == "ar") "ar" else "en-GB"
            val u = String.format(Locale.US, "https://api.tomtom.com/routing/1/calculateRoute/%.6f,%.6f:%.6f,%.6f/json?key=%s&traffic=true&travelMode=car&maxAlternatives=2&instructionsType=text&language=%s", fa, fo, d.lat, d.lon, key, lang) + (if (avoidTolls) "&avoid=tollRoads" else "")
            val (code, body) = fetch(u)
            keyBad = code == 401 || code == 403
            val out = ArrayList<Route>()
            if (body != null) try {
                val rs = JSONObject(body).getJSONArray("routes")
                for (k in 0 until rs.length()) {
                    val r = rs.getJSONObject(k)
                    val sum = r.getJSONObject("summary")
                    val la = ArrayList<Double>()
                    val lo = ArrayList<Double>()
                    val legs = r.getJSONArray("legs")
                    for (i in 0 until legs.length()) {
                        val p = legs.getJSONObject(i).getJSONArray("points")
                        for (j in 0 until p.length()) { la.add(p.getJSONObject(j).getDouble("latitude")); lo.add(p.getJSONObject(j).getDouble("longitude")) }
                    }
                    val steps = ArrayList<Step>()
                    val ins = r.optJSONObject("guidance")?.optJSONArray("instructions")
                    if (ins != null) for (i in 0 until ins.length()) {
                        val o = ins.getJSONObject(i)
                        val pt = o.optJSONObject("point") ?: continue
                        steps.add(Step(o.optString("message", ""), o.optString("maneuver", ""), pt.optDouble("latitude", 0.0), pt.optDouble("longitude", 0.0)))
                    }
                    if (la.size > 1) out.add(Route(la.toDoubleArray(), lo.toDoubleArray(), sum.getDouble("lengthInMeters"), sum.getDouble("travelTimeInSeconds"), sum.optDouble("trafficDelayInSeconds", 0.0), true, steps))
                }
            } catch (e: Exception) {
            }
            if (out.isNotEmpty()) return out
        } else keyBad = false
        val u = String.format(Locale.US, "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&alternatives=true&steps=true", fo, fa, d.lon, d.lat) + (if (avoidTolls) "&exclude=toll" else "")
        val body = get(u) ?: return emptyList()
        val out = ArrayList<Route>()
        try {
            val rs = JSONObject(body).getJSONArray("routes")
            for (k in 0 until rs.length()) {
                val r = rs.getJSONObject(k)
                val co: JSONArray = r.getJSONObject("geometry").getJSONArray("coordinates")
                val la = DoubleArray(co.length())
                val lo = DoubleArray(co.length())
                for (i in 0 until co.length()) { lo[i] = co.getJSONArray(i).getDouble(0); la[i] = co.getJSONArray(i).getDouble(1) }
                val steps = ArrayList<Step>()
                val legs = r.optJSONArray("legs")
                if (legs != null) for (i in 0 until legs.length()) {
                    val st = legs.getJSONObject(i).optJSONArray("steps") ?: continue
                    for (j in 0 until st.length()) {
                        val o = st.getJSONObject(j)
                        val m = o.optJSONObject("maneuver") ?: continue
                        val loc = m.optJSONArray("location") ?: continue
                        val (man, text) = phrase(ctx, m.optString("type", ""), m.optString("modifier", ""), m.optInt("exit", 0), o.optString("name", ""))
                        steps.add(Step(text, man, loc.getDouble(1), loc.getDouble(0)))
                    }
                }
                if (la.size > 1) out.add(Route(la, lo, r.getDouble("distance"), r.getDouble("duration"), 0.0, false, steps))
            }
        } catch (e: Exception) {
        }
        return out
    }

    /** Turns OSRM's manoeuvre codes into a sentence and a glyph code. */
    private fun phrase(ctx: Context, type: String, mod: String, exit: Int, road: String): Pair<String, String> {
        val side = when (mod) {
            "left" -> "TURN_LEFT" to R.string.nav_left
            "right" -> "TURN_RIGHT" to R.string.nav_right
            "slight left" -> "SLIGHT_LEFT" to R.string.nav_keep_left
            "slight right" -> "SLIGHT_RIGHT" to R.string.nav_keep_right
            "sharp left" -> "SHARP_LEFT" to R.string.nav_sharp_left
            "sharp right" -> "SHARP_RIGHT" to R.string.nav_sharp_right
            "uturn" -> "UTURN" to R.string.nav_uturn
            else -> "STRAIGHT" to R.string.nav_straight
        }
        val (man, base) = when (type) {
            "depart" -> "DEPART" to ctx.getString(R.string.nav_depart)
            "arrive" -> "ARRIVE" to ctx.getString(R.string.drive_arrived)
            "roundabout", "rotary" -> "ROUNDABOUT" to (if (exit > 0) ctx.getString(R.string.nav_roundabout, exit) else ctx.getString(R.string.nav_roundabout_any))
            "merge" -> side.first to ctx.getString(R.string.nav_merge)
            "on ramp", "off ramp" -> side.first to ctx.getString(R.string.nav_ramp)
            else -> side.first to ctx.getString(side.second)
        }
        return man to (if (road.isNotBlank() && type != "arrive") ctx.getString(R.string.nav_onto, base, road) else base)
    }

    /** Suggestions while typing: Photon (OpenStreetMap), nearest first; the phone's geocoder if it returns nothing. */
    fun suggest(ctx: Context, q: String, la: Double?, lo: Double?): List<Sug> {
        val out = ArrayList<Sug>()
        val u = "https://photon.komoot.io/api/?limit=6&q=" + URLEncoder.encode(q, "UTF-8") + (if (la != null && lo != null) String.format(Locale.US, "&lat=%.5f&lon=%.5f", la, lo) else "")
        try {
            val body = get(u)
            if (body != null) {
                val fs = JSONObject(body).getJSONArray("features")
                for (i in 0 until fs.length()) {
                    val f = fs.getJSONObject(i)
                    val p = f.getJSONObject("properties")
                    val c = f.getJSONObject("geometry").getJSONArray("coordinates")
                    val street = listOf(p.optString("housenumber", ""), p.optString("street", "")).filter { it.isNotBlank() }.joinToString(" ")
                    val name = p.optString("name", "").ifBlank { street }
                    if (name.isBlank()) continue
                    val sub = listOf(if (street != name) street else "", p.optString("district", ""), p.optString("city", ""), p.optString("state", ""), p.optString("country", "")).filter { it.isNotBlank() }.distinct().joinToString(", ")
                    val y = c.getDouble(1)
                    val x = c.getDouble(0)
                    out.add(Sug("", name, sub, if (la != null && lo != null) metresBetween(la, lo, y, x).toInt() else 0, y, x))
                }
            }
        } catch (e: Exception) {
        }
        if (out.isEmpty()) search(ctx, q, la, lo).forEach { h ->
            val parts = h.name.split(",", limit = 2)
            out.add(Sug("", parts[0].trim(), parts.getOrElse(1) { "" }.trim(), if (la != null && lo != null) metresBetween(la, lo, h.lat, h.lon).toInt() else 0, h.lat, h.lon))
        }
        return out
    }

    /** Street address of a dropped pin; coordinates when the phone cannot look it up. */
    @Suppress("DEPRECATION")
    fun reverse(ctx: Context, la: Double, lo: Double): String {
        try {
            if (Geocoder.isPresent()) {
                val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(la, lo, 1)?.firstOrNull()
                val line = a?.getAddressLine(0)
                if (!line.isNullOrBlank()) return line
            }
        } catch (e: Exception) {
        }
        return String.format(Locale.US, "%.5f, %.5f", la, lo)
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
}
