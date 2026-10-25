package com.carecircle.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.carecircle.domain.PolicyLimits
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device text reading for insurance documents: PDF pages are rendered with the platform PdfRenderer and read
 * by ML Kit; photos are read directly. Pages go one at a time off the main thread and bitmaps are recycled.
 * The extracted text is returned to the caller only; it is never written to disk or logged.
 */
object PolicyOcr {
    data class Outcome(val pages: List<PolicyPageText>, val truncated: Boolean)

    class PasswordProtectedException : Exception()
    class UnreadableException : Exception()

    /** Reads every given document in order (PDF or image). Throws [PasswordProtectedException] / [UnreadableException]. */
    suspend fun read(ctx: Context, uris: List<Uri>, onProgress: (done: Int, total: Int) -> Unit): Outcome = withContext(Dispatchers.Default) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val out = mutableListOf<PolicyPageText>()
            var truncated = false
            val plan = uris.map { it to ctx.contentResolver.getType(it) }
            // Count work up front so the progress bar is honest: PDF page counts, one per image.
            val counts = plan.map { (uri, type) -> if (type == "application/pdf") pdfPageCount(ctx, uri) else 1 }
            val total = minOf(PolicyLimits.MAX_PAGES, counts.sum())
            var done = 0
            onProgress(0, total)
            for ((i, p) in plan.withIndex()) {
                val (uri, type) = p
                if (type == "application/pdf") {
                    truncated = truncated || readPdf(ctx, uri, recognizer, out) { done++; onProgress(done, total) }
                } else if (out.size < PolicyLimits.MAX_PAGES) {
                    coroutineContext.ensureActive()
                    val text = recognize(recognizer, InputImage.fromFilePath(ctx, uri))
                    out += PolicyPageText(out.size + 1, text)
                    done++; onProgress(done, total)
                } else truncated = true
                if (counts[i] > 0 && out.size >= PolicyLimits.MAX_PAGES && i < plan.lastIndex) truncated = true
            }
            Outcome(out, truncated)
        } finally {
            recognizer.close()
        }
    }

    private fun pdfPageCount(ctx: Context, uri: Uri): Int = try {
        ctx.contentResolver.openFileDescriptor(uri, "r")?.use { PdfRenderer(it).use { r -> r.pageCount } } ?: 0
    } catch (e: SecurityException) { throw PasswordProtectedException() } catch (e: IOException) { throw UnreadableException() }

    /** Returns true when the PDF had more pages than were used. */
    private suspend fun readPdf(
        ctx: Context, uri: Uri, recognizer: com.google.mlkit.vision.text.TextRecognizer,
        out: MutableList<PolicyPageText>, onPage: () -> Unit,
    ): Boolean {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: throw UnreadableException()
        val renderer = try { PdfRenderer(pfd) } catch (e: SecurityException) { pfd.close(); throw PasswordProtectedException() } catch (e: IOException) { pfd.close(); throw UnreadableException() }
        renderer.use { r ->
            pfd.use {
                var i = 0
                while (i < r.pageCount && out.size < PolicyLimits.MAX_PAGES) {
                    coroutineContext.ensureActive()
                    r.openPage(i).use { page ->
                        val scale = PolicyLimits.renderScale(page.width, page.height)
                        val bmp = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        try {
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            out += PolicyPageText(out.size + 1, recognize(recognizer, InputImage.fromBitmap(bmp, 0)))
                        } finally {
                            bmp.recycle()
                        }
                    }
                    onPage()
                    i++
                }
                return i < r.pageCount
            }
        }
    }

    private suspend fun recognize(recognizer: com.google.mlkit.vision.text.TextRecognizer, image: InputImage): String =
        recognizer.process(image).await().text

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
}
