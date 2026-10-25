package com.carecircle.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.carecircle.data.Medicine
import java.io.File
import java.time.LocalDate

/** One-page-per-~35-lines list of current medicines for a doctor visit. Generated and shared locally. */
object PdfExport {
    fun create(ctx: Context, patient: String, meds: List<Medicine>): File {
        val doc = PdfDocument()
        val title = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val head = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 12f }
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var y = 60f
        fun header() {
            page.canvas.drawText("Current medicines${if (patient.isNotBlank()) " - $patient" else ""}", 40f, y, title); y += 22f
            page.canvas.drawText("Generated ${LocalDate.now()} by CareCircle. Bring to your doctor visit.", 40f, y, body); y += 28f
            page.canvas.drawText("Medicine", 40f, y, head); page.canvas.drawText("Dose", 230f, y, head)
            page.canvas.drawText("Times", 330f, y, head); page.canvas.drawText("Generic", 440f, y, head); y += 18f
        }
        header()
        for (m in meds) {
            if (y > 790f) {
                doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); y = 60f; header()
            }
            page.canvas.drawText(m.name.take(26), 40f, y, body)
            page.canvas.drawText(m.doseText.take(14), 230f, y, body)
            page.canvas.drawText(m.timesCsv.take(16), 330f, y, body)
            page.canvas.drawText(m.salt.take(20), 440f, y, body)
            y += 18f
        }
        doc.finishPage(page)
        val f = File(ctx.cacheDir, "current-medicines.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, "Share medicine list").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
