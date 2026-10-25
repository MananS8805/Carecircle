package com.carecircle.domain

import java.time.LocalDate
import java.time.YearMonth

enum class ExpiryStatus { EXPIRED, SOON, OK }

/** Reads and judges the expiry date printed on a medicine strip. A strip is valid through the last day of its expiry month. */
object Expiry {
    const val SOON_DAYS = 60L

    private val months = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )
    private val label = Regex("""(?:exp(?:iry)?\.?\s*(?:date)?|best\s*before|use\s*before)""", RegexOption.IGNORE_CASE)
    private val numeric = Regex("""\s*[:.\-]?\s*(\d{1,2})\s*[/\-.]\s*(\d{2}|\d{4})""")
    private val named = Regex("""\s*[:.\-]?\s*([A-Za-z]{3,4})\s*[.,/\- ]\s*(\d{2}|\d{4})""")

    private fun year(s: String): Int = if (s.length == 2) 2000 + s.toInt() else s.toInt()

    /** The expiry month printed after an "EXP" label, or null. Manufacturing dates are never read. */
    fun parse(ocr: String): YearMonth? {
        for (m in label.findAll(ocr)) {
            val tail = ocr.substring(m.range.last + 1).take(24)
            numeric.find(tail)?.takeIf { it.range.first <= 3 }?.let {
                val mo = it.groupValues[1].toInt()
                val y = year(it.groupValues[2])
                if (mo in 1..12 && y in 2000..2100) return YearMonth.of(y, mo)
            }
            named.find(tail)?.takeIf { it.range.first <= 3 }?.let {
                val mo = months[it.groupValues[1].lowercase()]
                val y = year(it.groupValues[2])
                if (mo != null && y in 2000..2100) return YearMonth.of(y, mo)
            }
        }
        return null
    }

    /** "02/2028" or "2028-02" as typed by the user, or null. */
    fun parseTyped(raw: String): YearMonth? {
        val t = raw.trim()
        Regex("""^(\d{1,2})\s*[/\-.]\s*(\d{2}|\d{4})$""").matchEntire(t)?.let {
            val mo = it.groupValues[1].toInt(); val y = year(it.groupValues[2])
            return if (mo in 1..12 && y in 2000..2100) YearMonth.of(y, mo) else null
        }
        return runCatching { YearMonth.parse(t) }.getOrNull()
    }

    fun status(expiry: YearMonth, today: LocalDate): ExpiryStatus {
        val lastDay = expiry.atEndOfMonth()
        return when {
            today.isAfter(lastDay) -> ExpiryStatus.EXPIRED
            !today.plusDays(SOON_DAYS).isBefore(lastDay) -> ExpiryStatus.SOON
            else -> ExpiryStatus.OK
        }
    }

    /** Stored form, "2028-02". */
    fun format(ym: YearMonth): String = ym.toString()
}
