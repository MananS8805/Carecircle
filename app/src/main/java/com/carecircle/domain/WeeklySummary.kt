package com.carecircle.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/** A medicine's stock as the weekly summary needs it. [timesPerDay] 0 means as-needed, which is never counted as low. */
data class StockMed(val name: String, val doseText: String, val pillCount: Int, val pillsPerDose: Int, val timesPerDay: Int)

data class LowStock(val label: String, val daysLeft: Int)

/**
 * What the weekly summary says, as plain numbers and dates. The wording lives in string resources (English, Hindi, Gujarati);
 * this holds only counts, so it is the same in every language. It never contains a condition tag.
 */
data class WeeklySummary(
    /** Whole-number percentage of decided doses that were taken, or null when nothing was decided yet. */
    val percent: Int?,
    val taken: Int,
    val missed: Int,
    val skipped: Int,
    val decided: Int,
    /** Days in the range that had at least one scheduled dose. Fewer than 7 means "so far". */
    val daysWithDoses: Int,
    val bestDay: LocalDate?,
    val worstDay: LocalDate?,
    /** Longest run of days with doses and none missed. */
    val longestStreak: Int,
    /** Up to [WeeklySummaries.MAX_LISTED], fewest days left first. */
    val lowStock: List<LowStock>,
    /** How many more were low but not listed. */
    val lowStockMore: Int,
) {
    val isPartialWeek get() = daysWithDoses in 1..6
}

object WeeklySummaries {
    const val MAX_LISTED = 5
    const val LOW_DAYS = 7

    fun compute(r: AdherenceResult, stock: List<StockMed>): WeeklySummary {
        val withDoses = r.days.filter { it.total > 0 }
        val ratio = { d: DayStat -> if (d.taken + d.missed + d.skipped == 0) null else d.taken.toDouble() / (d.taken + d.missed + d.skipped) }
        val judged = withDoses.mapNotNull { d -> ratio(d)?.let { d to it } }
        val best = judged.maxWithOrNull(compareBy({ it.second }, { it.first.date }))?.first?.date
        val worst = judged.filter { it.first.missed > 0 }.minWithOrNull(compareBy({ it.second }, { it.first.date }))?.first?.date?.takeIf { it != best }

        var run = 0; var longest = 0
        for (d in r.days) {
            if (d.total > 0 && d.missed == 0 && (d.taken > 0 || d.skipped > 0)) { run++; longest = maxOf(longest, run) } else if (d.total > 0) run = 0
        }

        val low = stock.filter { it.timesPerDay > 0 }
            .map { LowStock(Messages.medicineLabel(it.name, it.doseText), Refill.daysLeft(it.pillCount, it.pillsPerDose, it.timesPerDay)) }
            .filter { it.daysLeft < LOW_DAYS }
            .sortedBy { it.daysLeft }

        return WeeklySummary(
            percent = r.percent, taken = r.taken, missed = r.missed, skipped = r.skipped, decided = r.decided,
            daysWithDoses = withDoses.size, bestDay = best, worstDay = worst, longestStreak = longest,
            lowStock = low.take(MAX_LISTED), lowStockMore = (low.size - MAX_LISTED).coerceAtLeast(0),
        )
    }
}

/** The optional Sunday-evening nudge to share the weekly summary. Uses local dates, so a clock change never moves it. */
object WeeklyReminder {
    const val HOUR = 18

    /** The next Sunday at [HOUR]:00 local time strictly after [now]. */
    fun next(now: ZonedDateTime, hour: Int = HOUR): ZonedDateTime {
        var d = now.toLocalDate()
        while (d.dayOfWeek != DayOfWeek.SUNDAY || !d.atTime(hour, 0).atZone(now.zone).isAfter(now)) d = d.plusDays(1)
        return d.atTime(hour, 0).atZone(now.zone)
    }
}
