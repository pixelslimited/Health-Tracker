package ng.pixelslimited.health.heart

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

data class PpgState(val finger: Boolean = false, val progress: Float = 0f, val bpm: Int? = null, val failed: Boolean = false, val wave: List<Float> = emptyList())

/**
 * Heart rate from the camera: with a fingertip over the lens and the flash on, each heartbeat
 * changes how much light passes through the finger. We time the brightness peaks.
 */
class Ppg(private val on: (PpgState) -> Unit) : ImageAnalysis.Analyzer {
    private val times = ArrayList<Long>()
    private val values = ArrayList<Float>()
    private var done = false

    override fun analyze(img: ImageProxy) {
        try {
            if (done) return
            val y = mean(img.planes[0], img.width, img.height)
            val red = if (img.planes.size > 2) mean(img.planes[2], img.width / 2, img.height / 2) else 0f
            if (red < 140f || y < 30f) {
                times.clear()
                values.clear()
                on(PpgState(finger = false))
                return
            }
            val now = System.currentTimeMillis()
            times.add(now)
            values.add(y)
            val elapsed = now - times[0]
            if (elapsed < DURATION) {
                on(PpgState(true, elapsed / DURATION.toFloat(), wave = wave()))
                return
            }
            done = true
            val bpm = bpm(times, values)
            on(PpgState(true, 1f, bpm, bpm == null))
        } catch (e: Exception) {
            done = true
            on(PpgState(failed = true))
        } finally {
            img.close()
        }
    }

    /** The last few seconds of the pulse signal with the slow drift removed, scaled to -1..1 for drawing. */
    private fun wave(): List<Float> {
        val n = values.size
        val from = maxOf(0, n - 150)
        fun avg(i: Int, w: Int): Float {
            var sum = 0f
            var c = 0
            for (j in maxOf(0, i - w)..minOf(n - 1, i + w)) {
                sum += values[j]
                c++
            }
            return sum / c
        }
        val d = (from until n).map { avg(it, 1) - avg(it, 20) }
        val m = d.maxOf { abs(it) }.coerceAtLeast(0.05f)
        return d.map { it / m }
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
                if (i < limit) {
                    sum += buf.get(i).toInt() and 0xFF
                    n++
                }
                c += 8
            }
            r += 8
        }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    companion object {
        const val DURATION = 20000L

        /** Returns null when the signal is too irregular to trust, so we never show a made-up number. */
        fun bpm(t: List<Long>, v: List<Float>): Int? {
            val n = v.size
            val start = t.indexOfFirst { it - t[0] >= 3000 }
            if (start < 1 || n < 60) return null
            fun avg(i: Int, w: Int): Float {
                var s = 0f
                var c = 0
                for (j in maxOf(0, i - w)..minOf(n - 1, i + w)) {
                    s += v[j]
                    c++
                }
                return s / c
            }
            val d = FloatArray(n) { avg(it, 2) - avg(it, 15) }
            val peaks = ArrayList<Long>()
            for (i in start until n - 1) {
                if (d[i] > 0 && d[i] > d[i - 1] && d[i] >= d[i + 1] && (peaks.isEmpty() || t[i] - peaks.last() > 300)) peaks.add(t[i])
            }
            if (peaks.size < 9) return null
            val gaps = peaks.zipWithNext { a, b -> (b - a).toInt() }.sorted()
            val med = gaps[gaps.size / 2]
            if (gaps.count { abs(it - med) <= med * 0.25 } < gaps.size * 0.7) return null
            val bpm = 60000 / med
            return if (bpm in 35..200) bpm else null
        }
    }
}
