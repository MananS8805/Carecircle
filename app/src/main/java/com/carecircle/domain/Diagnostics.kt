package com.carecircle.domain

/**
 * What the local diagnostics log may record: a fixed list of event codes, never free text. Adding a code is a deliberate edit
 * here, and `DiagLogTest` fails if a log call passes a string instead of one of these.
 */
enum class DiagCode {
    APP_OPEN, REMINDERS_REARMED, STATUS_SENT, STATUS_FAILED, PUSH_REQUESTED, PUSH_FAILED, PUSH_RECEIVED, PUSH_HELD, PUSH_DROPPED,
    TOKEN_SAVED, ACK_SENT, RESTORE_DONE, BACKUP_EXPORTED, CAMERA_FAILED, CRASH,
}

/** [error] is an exception class name only; [frames] (a crash) are "package.Class.method" with no file, line or message. */
data class DiagEvent(val at: Long, val code: DiagCode, val error: String? = null, val frames: List<String> = emptyList())

object DiagBuffer {
    const val MAX_EVENTS = 50
    const val MAX_BYTES = 64 * 1024
    const val MAX_FRAMES = 5
    private val SAFE = Regex("[^A-Za-z0-9_.$<>]")

    /** Class and method names only: anything else (spaces, messages, paths) is removed, and the length is limited. */
    fun clean(raw: String?, max: Int = 80): String? = raw?.let { SAFE.replace(it, "").take(max) }?.takeIf { it.isNotEmpty() }

    fun simpleClassName(t: Throwable): String? = clean(t.javaClass.name)

    /** Package, class and method of the top frames of a crash. File names, line numbers and the message are left out. */
    fun framesOf(t: Throwable): List<String> = t.stackTrace.take(MAX_FRAMES).mapNotNull { clean("${it.className}.${it.methodName}", 120) }

    fun encode(list: List<DiagEvent>): String = list.joinToString("\n") { "${it.at}|${it.code.name}|${it.error.orEmpty()}|${it.frames.joinToString(";")}" }

    fun decode(raw: String): List<DiagEvent> = raw.lines().mapNotNull { l ->
        val p = l.split("|")
        if (p.size < 4) return@mapNotNull null
        val code = DiagCode.entries.firstOrNull { it.name == p[1] } ?: return@mapNotNull null
        p[0].toLongOrNull()?.let { DiagEvent(it, code, clean(p[2]), p[3].split(";").mapNotNull { f -> clean(f, 120) }.take(MAX_FRAMES)) }
    }

    /** Appends an event and drops the oldest ones until there are at most [MAX_EVENTS] and [MAX_BYTES]. */
    fun add(list: List<DiagEvent>, e: DiagEvent): List<DiagEvent> {
        var out = (list + e.copy(error = clean(e.error), frames = e.frames.mapNotNull { clean(it, 120) }.take(MAX_FRAMES))).takeLast(MAX_EVENTS)
        while (out.size > 1 && encode(out).toByteArray().size > MAX_BYTES) out = out.drop(1)
        return out
    }
}

object DiagReport {
    const val NOTE = "This report holds no medicine names, people, doses, notes or other health text."

    /** Plain English on purpose: it is read by whoever you send it to. [info] are label and value pairs. */
    fun build(info: List<Pair<String, String>>, events: List<DiagEvent>, time: (Long) -> String): String = buildString {
        appendLine("CareCircle diagnostics")
        appendLine(NOTE)
        appendLine()
        info.forEach { (k, v) -> appendLine("$k: $v") }
        appendLine()
        appendLine("Last ${events.size} events (newest last)")
        if (events.isEmpty()) appendLine("(none)")
        events.forEach { e ->
            append(time(e.at)).append("  ").append(e.code.name)
            e.error?.let { append("  ").append(it) }
            appendLine()
            e.frames.forEach { appendLine("    at $it") }
        }
    }
}
