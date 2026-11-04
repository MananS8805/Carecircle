package com.carecircle.domain

import java.time.LocalTime
import java.time.ZoneId

/**
 * Doses that went by while the phone was off or the app was stopped. Looks at today and yesterday only, so an old
 * medicine list never floods the history. Wall-clock times are rebuilt per day, so a time-zone change keeps "8:00".
 */
object MissedSweep {
    fun overdueSlots(
        times: List<LocalTime>, now: Long, zone: ZoneId, createdAt: Long, archivedAt: Long?, graceMs: Long = MissedDose.GRACE_MINUTES * 60_000L,
    ): List<Long> {
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return listOf(today.minusDays(1), today).flatMap { d -> times.map { d.atTime(it).atZone(zone).toInstant().toEpochMilli() } }
            .filter { it >= createdAt && now - it > graceMs && (archivedAt == null || it < archivedAt) }
            .sorted()
    }
}
