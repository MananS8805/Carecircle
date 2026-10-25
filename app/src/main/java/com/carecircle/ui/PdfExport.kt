package com.carecircle.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.carecircle.AppLocale
import com.carecircle.R
import com.carecircle.data.Medicine
import com.carecircle.data.SymptomLog
import com.carecircle.domain.AdherenceResult
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Doctor-visit PDF: current medicines (with what each is for) and 30 days of adherence for one person.
 * Generated and shared on the phone only, in the app language.
 */
object PdfExport {
    fun create(base: Context, person: String, meds: List<Medicine>, adherence: AdherenceResult? = null, symptoms: List<SymptomLog> = emptyList(), vitals: List<com.carecircle.data.VitalLog> = emptyList()): File {
        val ctx = AppLocale.wrap(base)
        val locale = AppLocale.locale(base)
        val doc = PdfDocument()
        val title = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val head = Paint().apply { textSize = 12f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 12f }
        val small = Paint().apply { textSize = 10f }
        val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
        val date = fmt.format(Date())
        val dfmt = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(locale).withDecimalStyle(java.time.format.DecimalStyle.STANDARD)
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var y = 60f
        fun footer() { page.canvas.drawText(ctx.getString(R.string.disclaimer_medical), 40f, 820f, small) }
        fun newPageIfNeeded(needed: Float = 20f) {
            if (y + needed > 800f) {
                footer(); doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); y = 60f
            }
        }
        fun header() {
            val t = if (person.isBlank()) ctx.getString(R.string.pdf_title) else ctx.getString(R.string.pdf_title_person, person)
            page.canvas.drawText(t, 40f, y, title); y += 22f
            page.canvas.drawText(ctx.getString(R.string.pdf_generated, date), 40f, y, body); y += 28f
            page.canvas.drawText(ctx.getString(R.string.pdf_col_medicine), 40f, y, head); page.canvas.drawText(ctx.getString(R.string.pdf_col_dose), 200f, y, head)
            page.canvas.drawText(ctx.getString(R.string.pdf_col_times), 290f, y, head); page.canvas.drawText(ctx.getString(R.string.pdf_col_for), 390f, y, head); y += 18f
        }
        header()
        for (m in meds) {
            if (y > 780f) {
                footer(); doc.finishPage(page); pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create()); y = 60f; header()
            }
            page.canvas.drawText(m.name.take(24), 40f, y, body)
            page.canvas.drawText(m.doseText.take(14), 200f, y, body)
            page.canvas.drawText(m.timesCsv.take(18), 290f, y, body)
            page.canvas.drawText((m.conditionTag ?: m.salt).take(24), 390f, y, body)
            y += 18f
        }

        if (adherence != null) {
            y += 18f; newPageIfNeeded(80f)
            page.canvas.drawText(ctx.getString(R.string.pdf_adherence_title), 40f, y, title); y += 20f
            page.canvas.drawText(
                ctx.getString(R.string.pdf_adherence_range, adherence.from.format(dfmt), adherence.to.format(dfmt)) +
                    "  -  " + ctx.getString(R.string.pdf_generated, date),
                40f, y, small,
            ); y += 22f
            val pct = adherence.percent
            page.canvas.drawText(
                if (pct == null) ctx.getString(R.string.hist_no_data)
                else ctx.getString(R.string.hist_summary, adherence.taken, adherence.decided, pct),
                40f, y, head,
            ); y += 20f
            for (m in adherence.perMedicine) {
                newPageIfNeeded()
                page.canvas.drawText(
                    listOf(m.name, m.doseText).filter { it.isNotBlank() }.joinToString(" ").take(40) + "  -  " +
                        ctx.getString(R.string.pdf_med_counts, m.taken, m.missed, m.skipped),
                    40f, y, body,
                ); y += 16f
            }
        }
        val byKind = vitals.groupBy { it.kind }
        if (byKind.isNotEmpty()) {
            y += 18f; newPageIfNeeded(60f)
            page.canvas.drawText(ctx.getString(R.string.pdf_vitals_title), 40f, y, title); y += 22f
            for (k in com.carecircle.domain.VitalKind.entries) {
                val list = byKind[k.key]?.sortedBy { it.measuredAt } ?: continue
                val st = com.carecircle.domain.Vitals.stats(list.map { it.v1 to it.v2 }) ?: continue
                newPageIfNeeded()
                val name = ctx.getString(when (k) { com.carecircle.domain.VitalKind.BP -> R.string.vital_bp; com.carecircle.domain.VitalKind.SUGAR -> R.string.vital_sugar; com.carecircle.domain.VitalKind.PULSE -> R.string.vital_pulse; com.carecircle.domain.VitalKind.WEIGHT -> R.string.vital_weight; com.carecircle.domain.VitalKind.TEMP -> R.string.vital_temp })
                page.canvas.drawText(
                    ctx.getString(R.string.pdf_vital_line, name, com.carecircle.domain.Vitals.format(k, st.latest1, st.latest2), com.carecircle.domain.Vitals.format(k, st.avg1, st.avg2), st.count, k.unit),
                    40f, y, body,
                ); y += 16f
            }
        }
        val own = symptoms.filter { s -> meds.any { it.id == s.medicineId } }
        if (own.isNotEmpty()) {
            y += 18f; newPageIfNeeded(60f)
            page.canvas.drawText(ctx.getString(R.string.pdf_diary_title), 40f, y, title); y += 22f
            for (s in own.take(40)) {
                newPageIfNeeded()
                val name = meds.firstOrNull { it.id == s.medicineId }?.name.orEmpty()
                val sev = ctx.getString(when (s.severity) { 3 -> R.string.sev_severe; 2 -> R.string.sev_moderate; else -> R.string.sev_mild })
                page.canvas.drawText(
                    listOf(fmt.format(Date(s.loggedAt)), name, symptomText(ctx, s.symptom), sev, s.note).filter { it.isNotBlank() }.joinToString("  -  ").take(90),
                    40f, y, body,
                ); y += 16f
            }
        }
        footer()
        doc.finishPage(page)
        val f = File(base.cacheDir, "current-medicines.pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }

    private fun symptomText(ctx: Context, key: String): String = when (key) {
        "nausea" -> ctx.getString(R.string.sym_nausea)
        "dizziness" -> ctx.getString(R.string.sym_dizziness)
        "headache" -> ctx.getString(R.string.sym_headache)
        "rash" -> ctx.getString(R.string.sym_rash)
        "sleepy" -> ctx.getString(R.string.sym_sleepy)
        "stomach" -> ctx.getString(R.string.sym_stomach)
        else -> key
    }

    fun share(base: Context, file: File) {
        val ctx = AppLocale.wrap(base)
        val uri = FileProvider.getUriForFile(base, "${base.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        base.startActivity(Intent.createChooser(send, ctx.getString(R.string.pdf_share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
