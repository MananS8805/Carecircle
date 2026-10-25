package com.carecircle.domain

import java.net.URLEncoder

/** Phone and link helpers. The message texts themselves are translated string resources. */
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

    fun whatsAppUrl(phone: String?, text: String): String {
        val enc = URLEncoder.encode(text, "UTF-8")
        return if (phone != null) "https://wa.me/$phone?text=$enc" else "https://wa.me/?text=$enc"
    }

    fun smsUri(phone: String?) = "smsto:${phone ?: ""}"

    /** The medicine as written in a message: "Metformin 500 mg". */
    fun medicineLabel(name: String, dose: String) = listOf(name, dose).filter { it.isNotBlank() }.joinToString(" ")
}
