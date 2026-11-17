package ng.pixelslimited.health.heart

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

data class PpgState(val finger: Boolean = false, val progress: Float = 0f, val bpm: Int? = null, val failed: Boolean = false, val wave: List<Float> = emptyList())

/**
 * Heart rate from the camera: with a fingertip over the lens and the flash on, each heartbeat changes how much
 * light passes through the finger. Every second we find the repeating period in the last 8 seconds of that
 * brightness (autocorrelation), and finish as soon as three estimates in a row agree.
 */
class Ppg(private val on: (PpgState) -> Unit) : ImageAnalysis.Analyzer {
    private val times = ArrayList<Long>()
    private val luma = ArrayList<Float>()
    private val red = ArrayList<Float>()
    private val est = ArrayList<Pair<Double, Double>>()
    private var done = false
    private var startAt = 0L
    private var lastEst = 0L
    private var misses = 0

    private fun reset() {
        times.clear(); luma.clear(); red.clear(); est.clear()
        startAt = 0L
        lastEst = 0L
    }

    override fun analyze(img: ImageProxy) {
        try {
            if (done) return
            val y = mean(img.planes[0], img.width, img.height)
            val v = if (img.planes.size > 2) mean(img.planes[2], img.width / 2, img.height / 2) else 0f
            val now = System.currentTimeMillis()
            if (v < 140f || y < 30f) {
                // A finger that slips for a moment does not throw the reading away; a lifted finger does.
                if (++misses > 12) {
                    reset()
                    on(PpgState(finger = false))
                }
                return
            }
            misses = 0
            if (startAt == 0L) startAt = now
            // The first second and a half is the flash and exposure settling: not used.
            if (now - startAt < SETTLE) {
                on(PpgState(true, 0.02f))
                return
            }
            times.add(now); luma.add(y); red.add(v)
            while (times.size > 2 && now - times[0] > WINDOW + 500) { times.removeAt(0); luma.removeAt(0); red.removeAt(0) }
            val elapsed = now - startAt - SETTLE
            if (now - lastEst >= 1000 && elapsed >= 6000) {
                lastEst = now
                val a = estimate(times, luma)
                val b = estimate(times, red)
                val best = listOfNotNull(a, b).maxByOrNull { it.second }
                if (best != null) est.add(best)
                val last = est.takeLast(3)
                val agree = last.size == 3 && last.maxOf { it.first } - last.minOf { it.first } <= 4.0
                if (agree && last.minOf { it.second } >= 0.45) return finish(last)
                if (elapsed >= LIMIT) {
                    val loose = last.size == 3 && last.maxOf { it.first } - last.minOf { it.first } <= 6.0 && last.minOf { it.second } >= 0.25
                    return if (loose) finish(last) else fail()
                }
            }
            on(PpgState(true, (elapsed / EXPECTED.toFloat()).coerceAtMost(0.95f), wave = wave(if (signal(red) > signal(luma)) red else luma)))
        } catch (e: Exception) {
            fail()
        } finally {
            img.close()
        }
    }

    private fun finish(last: List<Pair<Double, Double>>) {
        done = true
        val bpm = last.map { it.first }.sorted()[1].let { Math.round(it).toInt() }
        if (bpm in 35..200) on(PpgState(true, 1f, bpm)) else fail()
    }

    private fun fail() {
        done = true
        on(PpgState(true, 1f, failed = true))
    }

    /** How much the beat moves the channel (a saturated channel barely moves). */
    private fun signal(v: List<Float>): Float {
        if (v.size < 10) return 0f
        val tail = v.takeLast(75)
        val m = tail.average().toFloat()
        return tail.sumOf { ((it - m) * (it - m)).toDouble() }.toFloat()
    }

    /** The last few seconds of the pulse with the slow drift removed, scaled for drawing. */
    private fun wave(values: List<Float>): List<Float> {
        val n = values.size
        if (n < 3) return emptyList()
        val from = maxOf(0, n - 150)
        fun avg(i: Int, w: Int): Float {
            var sum = 0f
            var c = 0
            for (j in maxOf(0, i - w)..minOf(n - 1, i + w)) { sum += values[j]; c++ }
            return sum / c
        }
        // Inverted: more blood means less light, so the beat points up.
        val d = (from until n).map { avg(it, 15) - avg(it, 1) }
        val sorted = d.map { abs(it) }.sorted()
        val scale = sorted[(sorted.size * 0.95f).toInt().coerceAtMost(sorted.size - 1)].coerceAtLeast(0.05f)
        return d.map { (it / scale).coerceIn(-1f, 1f) }
    }

    private fun mean(plane: ImageProxy.PlaneProxy, w: Int, h: Int): Float {
        val buf = plane.buffer
        val rs = plane.rowStride
        val ps = plane.pixelStride
        val limit = buf.limit()
        var sum = 0L
        var n = 0
        var r = 0
        while (r < h) {
            var c = 0
            while (c < w) {
                val i = r * rs + c * ps
                if (i < limit) { sum += buf.get(i).toInt() and 0xFF; n++ }
                c += 8
            }
            r += 8
        }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    companion object {
        const val SETTLE = 1500L
        const val WINDOW = 8000L
        const val EXPECTED = 10000L
        const val LIMIT = 25000L

        /**
         * Beats per minute and a quality score (0 to 1) from the repeating period of the signal, or null.
         * Resampled to 50 per second, drift removed, then the shortest strong repeat between 40 and 180 bpm wins
         * (so a beat with a second small bump is not counted twice, and two beats are not counted as one).
         */
        fun estimate(t: List<Long>, v: List<Float>): Pair<Double, Double>? {
            if (t.size < 40) return null
            val t0 = t.last() - WINDOW
            var s = t.indexOfFirst { it >= t0 }.coerceAtLeast(0)
            if (t.last() - t[s] < 6000) return null
            val fs = 50.0
            val step = 1000.0 / fs
            val n = ((t.last() - t[s]) / step).toInt()
            val r = DoubleArray(n)
            var j = s
            for (i in 0 until n) {
                val x = t[s] + i * step
                while (j < t.size - 2 && t[j + 1] < x) j++
                val a = t[j]; val b = t[j + 1]
                val f = if (b == a) 0.0 else ((x - a) / (b - a)).coerceIn(0.0, 1.0)
                r[i] = v[j] + (v[j + 1] - v[j]) * f
            }
            val w = fs.toInt() / 2
            val d = DoubleArray(n)
            for (i in 0 until n) {
                var sum = 0.0
                var c = 0
                for (k in maxOf(0, i - w)..minOf(n - 1, i + w)) { sum += r[k]; c++ }
                d[i] = r[i] - sum / c
            }
            val sm = DoubleArray(n) { (d[maxOf(0, it - 1)] + d[it] + d[minOf(n - 1, it + 1)]) / 3 }
            val m = sm.average()
            for (i in 0 until n) sm[i] -= m
            val e = sm.sumOf { it * it }
            if (e <= 1e-9) return null
            val lo = (fs * 60 / 180).toInt()
            val hi = (fs * 60 / 40).toInt()
            if (hi + 2 >= n) return null
            val ac = DoubleArray(hi + 2)
            for (lag in lo - 1..hi + 1) {
                var acc = 0.0
                for (i in 0 until n - lag) acc += sm[i] * sm[i + lag]
                ac[lag] = acc / e * n / (n - lag)
            }
            var best = -1.0
            for (lag in lo..hi) if (ac[lag] > best) best = ac[lag]
            var pick = -1
            for (lag in lo..hi) if (ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1] && ac[lag] >= 0.8 * best) { pick = lag; break }
            if (pick < 0 || best <= 0) return null
            val a = ac[pick - 1]; val b = ac[pick]; val c = ac[pick + 1]
            val den = a - 2 * b + c
            val off = if (den != 0.0) 0.5 * (a - c) / den else 0.0
            val lagS = (pick + off) / fs
            return 60.0 / lagS to b
        }
    }
}
