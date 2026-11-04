package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTest {
    private val hc = Display.HighContrast

    @Test fun fontScaleMultipliesTheSystemSettingAndNeverPassesTwo() {
        assertEquals(1f, Display.effectiveFontScale(1f, TextSize.NORMAL), 0.001f)
        assertEquals(1.3f, Display.effectiveFontScale(1f, TextSize.LARGE), 0.001f)
        assertEquals(1.6f, Display.effectiveFontScale(1f, TextSize.EXTRA_LARGE), 0.001f)
        assertEquals(2f, Display.effectiveFontScale(1.5f, TextSize.EXTRA_LARGE), 0.001f)
        assertEquals(2f, Display.effectiveFontScale(2f, TextSize.NORMAL), 0.001f)
        assertEquals(2f, Display.effectiveFontScale(3f, TextSize.NORMAL), 0.001f)
    }

    @Test fun unknownSavedValueFallsBackToNormal() {
        assertEquals(TextSize.NORMAL, TextSize.fromKey(null))
        assertEquals(TextSize.NORMAL, TextSize.fromKey("huge"))
        assertEquals(TextSize.EXTRA_LARGE, TextSize.fromKey("xl"))
    }

    @Test fun contrastRatioKnownValues() {
        assertEquals(21.0, Display.contrastRatio(0xFF000000L, 0xFFFFFFFFL), 0.01)
        assertEquals(1.0, Display.contrastRatio(0xFF777777L, 0xFF777777L), 0.001)
    }

    @Test fun highContrastTextMeetsSevenToOneOnItsBackgrounds() {
        val backgrounds = listOf(hc.CANVAS, hc.SURFACE, hc.ORANGE_SOFT, hc.GOOD_SOFT, hc.WARN_SOFT, hc.BAD_SOFT)
        val texts = listOf(hc.INK, hc.MUTED, hc.ORANGE, hc.GOOD, hc.WARN, hc.BAD)
        for (bg in backgrounds) for (fg in texts) {
            val r = Display.contrastRatio(fg, bg)
            assertTrue("${fg.toString(16)} on ${bg.toString(16)} is $r", r >= 7.0)
        }
    }

    @Test fun highContrastControlsMeetFourPointFiveToOne() {
        assertTrue(Display.contrastRatio(hc.INK, hc.SURFACE) >= 4.5)
        assertTrue(Display.contrastRatio(0xFFFFFFFFL, hc.INK) >= 4.5) // white text on the dark button
        assertTrue(Display.contrastRatio(0xFFFFFFFFL, hc.BAD) >= 4.5)
        assertTrue(Display.contrastRatio(hc.LINE, hc.SURFACE) >= 4.5) // borders and dividers
    }
}
