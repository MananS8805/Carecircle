package com.carecircle.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

enum class AlertDecision { SHOW, HOLD, DROP }

/** Family phone only. [start] and [end] may cross midnight (22:00 to 07:00). [start] == [end] is not a valid range. */
data class QuietSettings(val enabled: Boolean, val start: LocalTime = LocalTime.of(22, 0), val end: LocalTime = LocalTime.of(7, 0), val snoozeUntil: Long = 0)

enum class SnoozeChoice { ONE_HOUR, EIGHT_HOURS, TOMORROW_MORNING }

object QuietHours {
    /** A push that arrives this long after it was sent is no longer news. */
    const val STALE_PUSH_MS = 12 * 3600_000L
    const val MORNING = 7

    fun validRange(start: LocalTime, end: LocalTime) = start != end

    fun isSnoozed(nowMs: Long, s: QuietSettings) = nowMs < s.snoozeUntil

    fun isQuiet(now: ZonedDateTime, s: QuietSettings): Boolean {
        if (!s.enabled || !validRange(s.start, s.end)) return false
        val t = now.toLocalTime()
        return if (s.start < s.end) t >= s.start && t < s.end else t >= s.start || t < s.end
    }

    /** Whether an alert that just arrived is shown, held for the digest, or ignored as stale. [sentAtMs] null when unknown. */
    fun shouldShow(nowMs: Long, zone: ZoneId, s: QuietSettings, sentAtMs: Long? = null): AlertDecision {
        if (sentAtMs != null && sentAtMs > 0 && nowMs - sentAtMs > STALE_PUSH_MS) return AlertDecision.DROP
        return if (isSnoozed(nowMs, s) || isQuiet(Instant.ofEpochMilli(nowMs).atZone(zone), s)) AlertDecision.HOLD else AlertDecision.SHOW
    }

    /** The next time quiet hours end, or null when it is not quiet now. */
    fun nextQuietEnd(now: ZonedDateTime, s: QuietSettings): ZonedDateTime? {
        if (!isQuiet(now, s)) return null
        var d = now.toLocalDate()
        var end = d.atTime(s.end).atZone(now.zone)
        while (!end.isAfter(now)) { d = d.plusDays(1); end = d.atTime(s.end).atZone(now.zone) }
        return end
    }

    /** When held alerts should be released: after any snooze and any quiet hours that follow it. Null when they can go now. */
    fun releaseAt(nowMs: Long, zone: ZoneId, s: QuietSettings): Long? {
        var t = nowMs
        var moved = false
        repeat(4) {
            if (isSnoozed(t, s)) { t = s.snoozeUntil; moved = true }
            else {
                val end = nextQuietEnd(Instant.ofEpochMilli(t).atZone(zone), s) ?: return if (moved) t else null
                t = end.toInstant().toEpochMilli(); moved = true
            }
        }
        return t
    }

    fun snoozeUntil(choice: SnoozeChoice, nowMs: Long, zone: ZoneId): Long = when (choice) {
        SnoozeChoice.ONE_HOUR -> nowMs + 3600_000L
        SnoozeChoice.EIGHT_HOURS -> nowMs + 8 * 3600_000L
        SnoozeChoice.TOMORROW_MORNING -> {
            val now = Instant.ofEpochMilli(nowMs).atZone(zone)
            var d = now.toLocalDate()
            var at = d.atTime(MORNING, 0).atZone(zone)
            // Tomorrow morning: today's 7:00 only counts when it is still ahead and it is the small hours.
            while (!at.isAfter(now) || at.toLocalDate() == now.toLocalDate() && now.hour >= 12) { d = d.plusDays(1); at = d.atTime(MORNING, 0).atZone(zone) }
            at.toInstant().toEpochMilli()
        }
    }
}

/** A missed-dose alert held back during quiet hours or a snooze. Only the day, the dose time and the link: no names. */
data class HeldAlert(val date: LocalDate, val slotLabel: String, val linkId: String)

object HeldAlerts {
    const val MAX = 20

    fun encode(list: List<HeldAlert>): String = list.joinToString("\n") { "${it.date}|${it.linkId}|${it.slotLabel.replace('|', ' ').replace('\n', ' ')}" }

    fun decode(raw: String): List<HeldAlert> = raw.lines().mapNotNull { l ->
        val p = l.split("|")
        if (p.size != 3) null else runCatching { HeldAlert(LocalDate.parse(p[0]), p[2], p[1]) }.getOrNull()
    }

    /** One entry per day, link and dose time; at most [MAX], the oldest dropped first. */
    fun add(list: List<HeldAlert>, e: HeldAlert): List<HeldAlert> = (if (e in list) list else list + e).takeLast(MAX)
}
