package com.carecircle.domain

import java.util.Locale

enum class VitalKind(val key: String, val unit: String, val twoValues: Boolean = false) {
    BP("bp", "mmHg", twoValues = true),
    SUGAR("sugar", "mg/dL"),
    PULSE("pulse", "bpm"),
    WEIGHT("weight", "kg"),
    TEMP("temp", "°C");

    companion object {
        fun fromKey(k: String?): VitalKind? = entries.firstOrNull { it.key == k }
    }
}

data class VitalStat(val count: Int, val avg1: Double, val min1: Double, val max1: Double, val latest1: Double, val avg2: Double?, val latest2: Double?)

/**
 * Plain recording and trends of readings the person measured. It never calls a reading normal or abnormal; it only rejects
 * values that cannot be real (a typing slip) and always leaves interpretation to a doctor.
 */
object Vitals {
    /** "72,5" and "72.5" both read as 72.5; anything else is null. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    fun isPlausible(kind: VitalKind, v1: Double?, v2: Double? = null): Boolean = when (kind) {
        VitalKind.BP -> v1 != null && v2 != null && v1 in 70.0..260.0 && v2 in 40.0..160.0 && v1 > v2
        VitalKind.SUGAR -> v1 != null && v1 in 20.0..600.0
        VitalKind.PULSE -> v1 != null && v1 in 25.0..220.0
        VitalKind.WEIGHT -> v1 != null && v1 in 2.0..300.0
        VitalKind.TEMP -> v1 != null && v1 in 30.0..43.0
    }

    /** "120/80", "98", "72.5". */
    fun format(kind: VitalKind, v1: Double, v2: Double?): String {
        fun n(d: Double) = if (d % 1.0 == 0.0) d.toInt().toString() else String.format(Locale.US, "%.1f", d)
        return if (kind.twoValues && v2 != null) "${n(v1)}/${n(v2)}" else n(v1)
    }

    /** Statistics over readings in the order given (oldest first). Null when there are none. */
    fun stats(readings: List<Pair<Double, Double?>>): VitalStat? {
        if (readings.isEmpty()) return null
        val a = readings.map { it.first }
        val b = readings.mapNotNull { it.second }
        return VitalStat(
            count = a.size, avg1 = a.average(), min1 = a.min(), max1 = a.max(), latest1 = a.last(),
            avg2 = if (b.isEmpty()) null else b.average(), latest2 = readings.last().second,
        )
    }
}
