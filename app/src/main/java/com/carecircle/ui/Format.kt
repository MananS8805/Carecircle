package com.carecircle.ui

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.util.Locale

/** 12-hour clock with Latin digits in every app language. */
object Fmt {
    private fun f(locale: Locale) = DateTimeFormatter.ofPattern("h:mm a", locale).withDecimalStyle(DecimalStyle.STANDARD)

    fun time(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(f(locale))

    fun time(t: LocalTime, locale: Locale = Locale.getDefault()): String = t.format(f(locale))
}
