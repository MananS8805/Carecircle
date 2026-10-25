package com.carecircle.domain

import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Pure scheduling logic (no Android imports) so it can be unit-tested on the JVM. */
object Schedule {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    fun parseTimes(csv: String): List<LocalTime> =
        csv.split(",").mapNotNull { runCatching { LocalTime.parse(it.trim(), fmt) }.getOrNull() }.distinct().sorted()

    fun formatTimes(times: List<LocalTime>): String = times.sorted().joinToString(",") { it.format(fmt) }

    /** Next dose strictly after [after] (today or tomorrow). Null if no times. */
    fun nextDose(times: List<LocalTime>, after: ZonedDateTime): ZonedDateTime? {
        if (times.isEmpty()) return null
        val sorted = times.sorted()
        val today = sorted.map { after.toLocalDate().atTime(it).atZone(after.zone) }.firstOrNull { it.isAfter(after) }
        return today ?: after.toLocalDate().plusDays(1).atTime(sorted.first()).atZone(after.zone)
    }
}

object Refill {
    const val REORDER_THRESHOLD_DAYS = 5

    fun dosesPerDay(timesPerDay: Int) = timesPerDay.coerceAtLeast(1)

    /** Whole days of medicine left. */
    fun daysLeft(pillCount: Int, pillsPerDose: Int, timesPerDay: Int): Int {
        val perDay = pillsPerDose.coerceAtLeast(1) * dosesPerDay(timesPerDay)
        return (pillCount.coerceAtLeast(0) / perDay)
    }

    fun needsReorder(daysLeft: Int) = daysLeft <= REORDER_THRESHOLD_DAYS
}

object MissedDose {
    const val GRACE_MINUTES = 30L

    fun isMissed(scheduledAtMs: Long, nowMs: Long, takenAtMs: Long?, graceMinutes: Long = GRACE_MINUTES): Boolean =
        takenAtMs == null && nowMs >= scheduledAtMs + graceMinutes * 60_000L
}
