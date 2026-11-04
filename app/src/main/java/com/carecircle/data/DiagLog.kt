package com.carecircle.data

import android.content.Context
import com.carecircle.domain.DiagBuffer
import com.carecircle.domain.DiagCode
import com.carecircle.domain.DiagEvent
import java.io.File

/**
 * A small local log for support: event codes and exception class names, in a private file of at most 50 events and 64 KB.
 * It never holds health text, and it leaves the phone only when the user taps Share or Copy on the Diagnostics screen.
 * [log] takes a [DiagCode], never a string, so free text cannot get in.
 */
object DiagLog {
    private val lock = Any()
    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "diag.log")

    fun log(ctx: Context, code: DiagCode, error: Throwable? = null) {
        runCatching {
            synchronized(lock) {
                val f = file(ctx)
                val old = if (f.exists()) DiagBuffer.decode(f.readText()) else emptyList()
                f.writeText(DiagBuffer.encode(DiagBuffer.add(old, DiagEvent(System.currentTimeMillis(), code, error?.let(DiagBuffer::simpleClassName)))))
            }
        } // logging must never break the app
    }

    fun read(ctx: Context): List<DiagEvent> = runCatching { synchronized(lock) { file(ctx).takeIf { it.exists() }?.readText()?.let(DiagBuffer::decode).orEmpty() } }.getOrDefault(emptyList())

    fun clear(ctx: Context) { runCatching { synchronized(lock) { file(ctx).delete() } } }

    /**
     * Opt-in only. Records the exception class and the top five frames (package and method only), then hands over to the
     * handler that was there before, so a crash behaves exactly as it would without this.
     */
    fun installCrashHandlerIfEnabled(ctx: Context) {
        if (!Prefs(ctx).crashLogEnabled) return
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, t ->
            runCatching {
                synchronized(lock) {
                    val f = file(app)
                    val old = if (f.exists()) DiagBuffer.decode(f.readText()) else emptyList()
                    f.writeText(DiagBuffer.encode(DiagBuffer.add(old, DiagEvent(System.currentTimeMillis(), DiagCode.CRASH, DiagBuffer.simpleClassName(t), DiagBuffer.framesOf(t)))))
                }
            }
            previous?.uncaughtException(thread, t)
        }
    }
}
