package com.carecircle.domain

/** An appointment as the reminder logic sees it. */
data class ApptLite(val id: Long, val startsAt: Long, val remindBeforeMinutes: Int)

object Appointments {
    /** Choices offered in the form, in minutes before the appointment. */
    val REMIND_CHOICES = listOf(60, 180, 1440)
    const val DEFAULT_REMIND = 60

    /** When the reminder should fire. */
    fun remindAt(a: ApptLite): Long = a.startsAt - a.remindBeforeMinutes.coerceAtLeast(0) * 60_000L

    /** A reminder still worth arming: it is in the future. A reminder time already past but an appointment still ahead is not armed. */
    fun shouldArm(a: ApptLite, now: Long): Boolean = remindAt(a) > now

    /** Upcoming ones first (soonest first), then past ones newest first. */
    fun order(all: List<ApptLite>, now: Long): List<ApptLite> =
        all.filter { it.startsAt >= now }.sortedBy { it.startsAt } + all.filter { it.startsAt < now }.sortedByDescending { it.startsAt }

    fun isUpcoming(a: ApptLite, now: Long) = a.startsAt >= now
}
