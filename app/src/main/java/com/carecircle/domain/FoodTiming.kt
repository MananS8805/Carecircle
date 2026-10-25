package com.carecircle.domain

import java.time.LocalTime

/** When to take a medicine relative to food. [key] is what is stored in the database and in backups. */
enum class FoodTiming(val key: String) {
    NONE("NONE"),
    BEFORE_FOOD("BEFORE_FOOD"),
    WITH_FOOD("WITH_FOOD"),
    AFTER_FOOD("AFTER_FOOD"),
    EMPTY_STOMACH("EMPTY_STOMACH"),
    BEDTIME("BEDTIME");

    companion object {
        /** Unknown or missing values read as [NONE], so old or hand-edited files never crash. */
        fun fromKey(key: String?): FoodTiming = entries.firstOrNull { it.key == key?.trim()?.uppercase() } ?: NONE
    }
}

/** Editing helpers for the "08:00,20:00" time list stored on a medicine. All results are sorted and unique. */
object TimeList {
    fun parse(csv: String): List<LocalTime> = Schedule.parseTimes(csv)

    fun format(times: List<LocalTime>): String = Schedule.formatTimes(times.distinct())

    /** Adds a time; a duplicate is ignored silently. */
    fun add(csv: String, time: LocalTime): String = format(parse(csv) + time)

    fun remove(csv: String, time: LocalTime): String = format(parse(csv) - time)

    /** At least one time is required. */
    fun isValid(csv: String): Boolean = parse(csv).isNotEmpty()
}
