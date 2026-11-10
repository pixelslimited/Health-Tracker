package ng.pixelslimited.health.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ng.pixelslimited.health.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** In-app updater: reads the latest GitHub release, downloads its APK and hands it to the system installer. */
object Updater {
    data class Release(val code: Int, val url: String, val notes: String)

    private const val REPO = "https://github.com/pixelslimited/Health-Tracker/"
    private const val API = "https://api.github.com/repos/pixelslimited/Health-Tracker/releases/latest"

    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        try {
            val c = URL(API).openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 10000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            if (c.responseCode != 200) return@withContext null
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val code = o.getString("tag_name").removePrefix("v").toIntOrNull() ?: return@withContext null
            val assets = o.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) url = a.getString("browser_download_url")
            }
            val found = url
            if (found != null && found.startsWith(REPO, ignoreCase = true) && code > BuildConfig.VERSION_CODE) Release(code, found, o.optString("body")) else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun download(ctx: Context, url: String, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "update.apk")
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 30000
            if (c.responseCode != 200) return@withContext null
            val total = c.contentLengthLong
            c.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
