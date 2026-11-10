package ng.pixelslimited.health.export

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import ng.pixelslimited.health.R
import ng.pixelslimited.health.data.Profile
import ng.pixelslimited.health.data.typeName
import ng.pixelslimited.health.data.value
import ng.pixelslimited.health.data.whenText
import java.io.File

object Export {
    private fun dir(ctx: Context) = File(ctx.cacheDir, "exports").apply { mkdirs() }

    fun csv(ctx: Context, p: Profile): File {
        val sb = StringBuilder("type,time,value_a,value_b,note\n")
        p.entries.sortedBy { it.ts }.forEach {
            sb.append(it.type).append(',').append(whenText(it.ts).replace(",", "")).append(',')
                .append(it.a).append(',').append(it.b).append(',')
                .append('"').append(it.note.replace("\"", "'")).append('"').append('\n')
        }
        p.steps.toSortedMap().forEach { (day, n) -> sb.append("STEPS,").append(day).append(',').append(n).append(",0,\"\"\n") }
        return File(dir(ctx), "health-report.csv").apply { writeText(sb.toString()) }
    }

    /** A4 report for a doctor. All text is 12 pt or larger. */
    fun pdf(ctx: Context, p: Profile): File {
        val doc = PdfDocument()
        val body = Paint().apply { textSize = 12f; isAntiAlias = true }
        val head = Paint().apply { textSize = 18f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var y = 64f
        fun line(text: String, paint: Paint = body) {
            if (y > 790f) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                y = 64f
            }
            page.canvas.drawText(text, 48f, y, paint)
            y += paint.textSize + 8f
        }
        line(ctx.getString(R.string.report_title), head)
        line(p.name + "  |  " + whenText(System.currentTimeMillis()))
        line(ctx.getString(R.string.disclaimer_short))
        y += 12f
        p.entries.sortedByDescending { it.ts }.forEach {
            line(whenText(it.ts) + "   " + typeName(ctx, it.type) + ": " + it.value(ctx))
        }
        y += 12f
        line(ctx.getString(R.string.steps), head)
        p.steps.toSortedMap(reverseOrder()).entries.take(60).forEach { line(it.key + "   " + it.value) }
        doc.finishPage(page)
        val out = File(dir(ctx), "health-report.pdf")
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }

    fun share(ctx: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
