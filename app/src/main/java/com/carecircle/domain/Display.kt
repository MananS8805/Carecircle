package com.carecircle.domain

import kotlin.math.pow

/** Text size choice in Settings > Display. It multiplies the system font size, up to [Display.MAX_TOTAL_SCALE] in all. */
enum class TextSize(val key: String, val multiplier: Float) {
    NORMAL("normal", 1f), LARGE("large", 1.3f), EXTRA_LARGE("xl", 1.6f);

    companion object {
        fun fromKey(k: String?) = entries.firstOrNull { it.key == k } ?: NORMAL
    }
}

data class DisplayPrefs(val textSize: TextSize = TextSize.NORMAL, val highContrast: Boolean = false, val simpleMode: Boolean = false)

object Display {
    const val MAX_TOTAL_SCALE = 2.0f

    /** The font scale the app uses: the phone's own setting times our multiplier, never above 2.0x. */
    fun effectiveFontScale(systemScale: Float, size: TextSize): Float =
        (systemScale.coerceIn(0.5f, MAX_TOTAL_SCALE) * size.multiplier).coerceAtMost(MAX_TOTAL_SCALE)

    // ---- contrast (WCAG 2.x relative luminance) ----

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt(); val g = ((argb shr 8) and 0xFF).toInt(); val b = (argb and 0xFF).toInt()
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    fun contrastRatio(a: Long, b: Long): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** High-contrast colours. Text needs 7:1 on the light backgrounds, button and icon colours 4.5:1. */
    object HighContrast {
        const val CANVAS = 0xFFFFFFFFL
        const val SURFACE = 0xFFFFFFFFL
        const val INK = 0xFF000000L
        const val MUTED = 0xFF3D3D45L
        const val LINE = 0xFF5C5C66L
        const val ORANGE = 0xFF7A2800L
        const val GOOD = 0xFF14472FL
        const val WARN = 0xFF6B4000L
        const val BAD = 0xFF8A0E0EL
        const val ORANGE_SOFT = 0xFFFFE9DCL
        const val GOOD_SOFT = 0xFFE3F5EBL
        const val WARN_SOFT = 0xFFFFF3D1L
        const val BAD_SOFT = 0xFFFBE3E3L
    }
}
