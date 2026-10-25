package com.carecircle.domain

import java.net.URLEncoder

object Messages {
    /** Digits only, India default: 10-digit numbers get 91 prefix. Returns null if unusable. */
    fun normalizePhone(raw: String, defaultCountry: String = "91"): String? {
        val d = raw.filter { it.isDigit() }
        return when {
            d.length == 10 -> defaultCountry + d
            d.length in 11..15 -> d.trimStart('0').ifEmpty { null }
            else -> null
        }
    }

    fun missedDose(patient: String, medicine: String, timeLabel: String) =
        "CareCircle alert: $patient has not marked $medicine as taken (due $timeLabel). Please check in."

    fun reorder(medicine: String, daysLeft: Int, patient: String) =
        "Hello, I'd like to reorder $medicine for $patient. About $daysLeft day(s) of stock left. Please confirm availability."

    fun whatsAppUrl(phone: String?, text: String): String {
        val enc = URLEncoder.encode(text, "UTF-8")
        return if (phone != null) "https://wa.me/$phone?text=$enc" else "https://wa.me/?text=$enc"
    }

    fun smsUri(phone: String?) = "smsto:${phone ?: ""}"
}
