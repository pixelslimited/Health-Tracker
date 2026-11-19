package ng.pixelslimited.health.walk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import ng.pixelslimited.health.data.metresBetween
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

typealias LL = Pair<Double, Double>

/** A walkable route: the line to draw and its length in metres. */
class Plan(val line: List<LL>, val metres: Double)

/** Plans walks over footpaths and roads with the free OpenStreetMap foot router. All calls block, so run them off the main thread. */
object WalkPlanner {
    /** Walking pace used for time and arrival estimates, in metres per minute (5 km/h). */
    const val PACE = 5000.0 / 60.0

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

    /** Route through the points in order, or null when the router has none. */
    fun route(via: List<LL>): Plan? {
        if (via.size < 2) return null
        val coords = via.joinToString(";") { String.format(Locale.US, "%.6f,%.6f", it.second, it.first) }
        val body = get("https://routing.openstreetmap.de/routed-foot/route/v1/foot/$coords?overview=full&geometries=geojson") ?: return null
        return try {
            val r = JSONObject(body).optJSONArray("routes")?.optJSONObject(0) ?: return null
            val g = r.getJSONObject("geometry").getJSONArray("coordinates")
            Plan(List(g.length()) { i -> g.getJSONArray(i).let { it.getDouble(1) to it.getDouble(0) } }, r.getDouble("distance"))
        } catch (e: Exception) {
            null
        }
    }

    /** Straight lines between the points, for when the router cannot be reached. */
    fun straight(via: List<LL>): Plan {
        var m = 0.0
        for (i in 1 until via.size) m += metresBetween(via[i - 1].first, via[i - 1].second, via[i].first, via[i].second)
        return Plan(via, m)
    }

    private fun offset(p: LL, bearing: Double, metres: Double): LL {
        val d = metres / 6371000.0
        val b = Math.toRadians(bearing)
        val la = Math.toRadians(p.first)
        val lo = Math.toRadians(p.second)
        val la2 = asin(sin(la) * cos(d) + cos(la) * sin(d) * cos(b))
        val lo2 = lo + atan2(sin(b) * sin(d) * cos(la), cos(d) - sin(la) * sin(la2))
        return Math.toDegrees(la2) to Math.toDegrees(lo2)
    }

    private fun bearing(a: LL, b: LL): Double {
        val la1 = Math.toRadians(a.first)
        val la2 = Math.toRadians(b.first)
        val dl = Math.toRadians(b.second - a.second)
        val y = sin(dl) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** A round trip from start in one direction: a triangle of three legs, resized until it is close to the wanted length. */
    private fun loop(start: LL, target: Double, bearing: Double): Plan? {
        var side = target / 3.0 / 1.3
        var best: Plan? = null
        for (i in 0 until 3) {
            val p = route(listOf(start, offset(start, bearing, side), offset(start, bearing + 60.0, side), start)) ?: return best
            if (best == null || abs(p.metres - target) < abs(best.metres - target)) best = p
            if (abs(p.metres - target) <= target * 0.07) break
            side *= (target / p.metres).coerceIn(0.5, 2.0)
        }
        return best
    }

    /** Round trips that return to the start and add up to about target metres, best match first. */
    suspend fun loops(start: LL, target: Double): List<Plan> = coroutineScope {
        listOf(20.0, 110.0, 200.0, 290.0).map { b -> async(Dispatchers.IO) { loop(start, target, b) } }.awaitAll()
            .filterNotNull().sortedBy { abs(it.metres - target) }
    }

    /** A route to dest that is bent sideways until it is about target metres long; the direct route when that is already long enough. */
    suspend fun toward(start: LL, dest: LL, target: Double): Plan? = coroutineScope {
        val direct = async(Dispatchers.IO) { route(listOf(start, dest)) }.await() ?: return@coroutineScope null
        if (direct.metres >= target * 0.95) return@coroutineScope direct
        val gap = metresBetween(start.first, start.second, dest.first, dest.second)
        val brg = bearing(start, dest)
        val mid = offset(start, brg, gap / 2.0)
        val half = target / 1.3 / 2.0
        val sq = half * half - (gap / 2.0) * (gap / 2.0)
        val h0 = if (sq > 0) sqrt(sq) else 100.0
        val sides = listOf(90.0, -90.0).map { turn ->
            async(Dispatchers.IO) {
                var h = h0
                var best: Plan? = null
                for (i in 0 until 3) {
                    val p = route(listOf(start, offset(mid, brg + turn, h), dest)) ?: break
                    if (best == null || abs(p.metres - target) < abs(best.metres - target)) best = p
                    if (abs(p.metres - target) <= target * 0.07) break
                    h *= (target / p.metres).coerceIn(0.5, 2.0)
                }
                best
            }
        }.awaitAll().filterNotNull()
        (sides + direct).minByOrNull { abs(it.metres - target) }
    }
}
