package com.carecircle.domain

enum class HealthIssue { NOTIFICATIONS_OFF, EXACT_ALARMS_OFF, BATTERY_RESTRICTED, DO_NOT_DISTURB, CHANNEL_OFF, NOTHING_SCHEDULED, NEVER_FIRED, NO_RECENT_RESCHEDULE, ALARM_COUNT_MISMATCH }

/** What the phone currently allows for reminders, as read by the Android layer. */
data class ReminderHealth(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val batteryUnrestricted: Boolean,
    val doNotDisturbOff: Boolean,
    val channelsOn: Boolean,
    val scheduledMedicines: Int,
    /** Epoch millis of the last reminder this app posted, or 0. */
    val lastReminderAt: Long,
    val firstMedicineAddedAt: Long,
    val now: Long,
    /** When alarms were last re-armed from the database, or 0. */
    val lastRescheduleAt: Long = 0,
    /** Alarms the database says should exist, and how many the last re-arm set; -1 = unknown. */
    val expectedAlarms: Int = -1,
    val armedAlarms: Int = -1,
) {
    /** Problems that can make a reminder late or missing, most serious first. Battery and Do Not Disturb are warnings only. */
    val issues: List<HealthIssue>
        get() = buildList {
            if (!notifications) add(HealthIssue.NOTIFICATIONS_OFF)
            if (!channelsOn) add(HealthIssue.CHANNEL_OFF)
            if (!exactAlarms) add(HealthIssue.EXACT_ALARMS_OFF)
            if (!batteryUnrestricted) add(HealthIssue.BATTERY_RESTRICTED)
            if (!doNotDisturbOff) add(HealthIssue.DO_NOT_DISTURB)
            if (scheduledMedicines == 0) add(HealthIssue.NOTHING_SCHEDULED)
            // A medicine added more than a day ago that has never produced a reminder deserves a look.
            if (scheduledMedicines > 0 && lastReminderAt == 0L && firstMedicineAddedAt > 0 && now - firstMedicineAddedAt > 26 * 3600_000L) add(HealthIssue.NEVER_FIRED)
            if (scheduledMedicines > 0 && (lastRescheduleAt == 0L || now - lastRescheduleAt > STALE_MS)) add(HealthIssue.NO_RECENT_RESCHEDULE)
            if (expectedAlarms >= 0 && armedAlarms >= 0 && expectedAlarms != armedAlarms) add(HealthIssue.ALARM_COUNT_MISMATCH)
        }

    /** Reminders will work as intended: nothing that blocks them, whatever the warnings. */
    val blocking: Boolean get() = issues.any { it == HealthIssue.NOTIFICATIONS_OFF || it == HealthIssue.CHANNEL_OFF || it == HealthIssue.EXACT_ALARMS_OFF }
}

private const val STALE_MS = 36 * 3600_000L
