package com.carecircle.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.data.Medicine
import java.io.File
import java.text.DateFormat
import java.util.Date

/** One-page-per-~35-lines list of current medicines for a doctor visit. Generated and shared locally, in the app language. */
object PdfExport {
    fun create(base: Context, person: String, meds: List<Medicine>): File {
        val ctx = AppLocale.wrap(base)
        val doc = PdfDocument()
        val title = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val head = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 12f }
        val small = Paint().apply { textSize = 10f }
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM, AppLocale.locale(base)).format(Date())
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var y = 60f
        fun footer() { page.canvas.drawText(ctx.getString(R.string.disclaimer_medical), 40f, 820f, small) }
        fun header() {
            val t = if (person.isBlank()) ctx.getString(R.string.pdf_title) else ctx.getString(R.string.pdf_title_person, person)
            page.canvas.drawText(t, 40f, y, title); y += 22f
            page.canvas.drawText(ctx.getString(R.string.pdf_generated, date), 40f, y, body); y += 28f
            page.canvas.drawText(ctx.getString(R.string.pdf_col_medicine), 40f, y, head); page.canvas.drawText(ctx.getString(R.string.pdf_col_dose), 230f, y, head)
            page.canvas.drawText(ctx.getString(R.string.pdf_col_times), 330f, y, head); page.canvas.drawText(ctx.getString(R.string.pdf_col_generic), 440f, y, head); y += 18f
        }
        header()
        for (m in meds) {
            if (y > 780f) {
                footer(); doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); y = 60f; header()
            }
            page.canvas.drawText(m.name.take(26), 40f, y, body)
            page.canvas.drawText(m.doseText.take(14), 230f, y, body)
            page.canvas.drawText(m.timesCsv.take(16), 330f, y, body)
            page.canvas.drawText(m.salt.take(20), 440f, y, body)
            y += 18f
        }
        footer()
        doc.finishPage(page)
        val f = File(base.cacheDir, "current-medicines.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun share(base: Context, file: File) {
        val ctx = AppLocale.wrap(base)
        val uri = FileProvider.getUriForFile(base, "${base.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        base.startActivity(Intent.createChooser(send, ctx.getString(R.string.pdf_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
