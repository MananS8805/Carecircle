package com.carecircle.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 12-hour clock with Latin digits in every app language. */
object Fmt {
    private fun f() = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun time(epochMs: Long): String = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(f())

    fun time(t: java.time.LocalTime): String = t.format(f())
}
