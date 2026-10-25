package com.carecircle.domain

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * How hard the app tries before it tells the family: a reminder at the dose time, repeats 10 and 20 minutes later
 * (the second one louder), and the family alert at 30 minutes (the existing missed-dose grace window).
 */
object Escalation {
    val REPEAT_AFTER_MINUTES = listOf(10L, 20L)
    const val ALERT_AFTER_MINUTES = MissedDose.GRACE_MINUTES
    val DAILY_SUMMARY_TIME: LocalTime = LocalTime.of(21, 0)

    /** Epoch times of the repeat reminders for a dose, index 0 is level 1. */
    fun repeatTimes(scheduledAt: Long): List<Long> = REPEAT_AFTER_MINUTES.map { scheduledAt + it * 60_000L }

    /** Repeats continue only while the dose is neither taken nor skipped. */
    fun shouldRemind(takenAt: Long?, skipped: Boolean): Boolean = takenAt == null && !skipped

    /** Minutes left before the family is told, as shown on a repeat reminder (level 1 or 2). */
    fun minutesUntilAlert(level: Int): Long = ALERT_AFTER_MINUTES - (REPEAT_AFTER_MINUTES.getOrNull(level - 1) ?: 0L)

    /** The next 9 pm strictly after [now], in the device zone. */
    fun nextDailySummary(now: ZonedDateTime): ZonedDateTime = Schedule.nextDose(listOf(DAILY_SUMMARY_TIME), now)!!
}
