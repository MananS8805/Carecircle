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

    /** One line of the monthly order: a medicine running low and a 30-day quantity. */
    data class OrderLine(val label: String, val daysLeft: Int, val quantity: Int)

    data class OrderMed(val name: String, val doseText: String, val pillCount: Int, val pillsPerDose: Int, val timesPerDay: Int, val active: Boolean)

    /**
     * Medicines with fewer than [thresholdDays] days left and what a [supplyDays]-day supply is. As-needed medicines (no
     * scheduled times) and removed ones are left out. The user decides whether and where to send it.
     */
    fun monthlyOrder(meds: List<OrderMed>, thresholdDays: Int = 10, supplyDays: Int = 30): List<OrderLine> =
        meds.filter { it.active && it.timesPerDay > 0 }.mapNotNull { m ->
            val days = Refill.daysLeft(m.pillCount, m.pillsPerDose, m.timesPerDay)
            if (days >= thresholdDays) null else OrderLine(medicineLabel(m.name, m.doseText), days, m.pillsPerDose.coerceAtLeast(1) * m.timesPerDay * supplyDays)
        }.sortedBy { it.daysLeft }

    /** The medicine as written in a message: "Metformin 500 mg". */
    fun medicineLabel(name: String, dose: String) = listOf(name, dose).filter { it.isNotBlank() }.joinToString(" ")
}
