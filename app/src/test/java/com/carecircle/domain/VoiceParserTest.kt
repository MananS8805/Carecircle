package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class VoiceParserTest {
    private fun t(vararg h: Int) = h.map { LocalTime.of(it, 0) }
    private fun parse(s: String) = VoiceParser.parseVoiceMedicine(s)

    @Test fun metforminMorningAndNight() {
        val d = parse("Metformin 500 morning and night")
        assertEquals("Metformin", d.name); assertEquals("500 mg", d.dose); assertEquals(t(8, 21), d.times); assertNull(d.foodTiming)
    }

    @Test fun telmaMilligramOnceADayAfterBreakfast() {
        val d = parse("Telma 40 milligram once a day after breakfast")
        assertEquals("Telma", d.name); assertEquals("40 mg", d.dose); assertEquals(t(8), d.times); assertEquals(FoodTiming.AFTER_FOOD, d.foodTiming)
    }

    @Test fun hinglishSubahShaam() {
        val d = parse("Dolo 650 subah shaam")
        assertEquals("Dolo", d.name); assertEquals("650 mg", d.dose); assertEquals(t(8, 18), d.times)
    }

    @Test fun threeTimesADay() {
        val d = parse("Azithromycin 500 mg three times a day")
        assertEquals("Azithromycin", d.name); assertEquals("500 mg", d.dose); assertEquals(t(8, 14, 20), d.times)
    }

    @Test fun twiceADay() {
        val d = parse("Amlodipine 5 mg twice a day before food")
        assertEquals(t(8, 20), d.times); assertEquals(FoodTiming.BEFORE_FOOD, d.foodTiming); assertEquals("5 mg", d.dose)
    }

    @Test fun englishNumberWords() {
        val d = parse("Crocin five hundred milligram morning")
        assertEquals("Crocin", d.name); assertEquals("500 mg", d.dose); assertEquals(t(8), d.times)
        assertEquals("Metformin 850", VoiceParser.wordsToDigits("Metformin eight hundred and fifty"))
        assertEquals("40", VoiceParser.wordsToDigits("forty"))
    }

    @Test fun afternoonAndEvening() {
        val d = parse("Vitamin D 60000 IU afternoon evening")
        assertEquals(t(14, 18), d.times)
    }

    @Test fun hinglishDopaharRaatKhaneKeBaad() {
        val d = parse("Glycomet 500 dopahar raat khane ke baad")
        assertEquals("Glycomet", d.name); assertEquals(t(14, 21), d.times); assertEquals(FoodTiming.AFTER_FOOD, d.foodTiming)
    }

    @Test fun devanagariWords() {
        val d = parse("मेटफॉर्मिन 500 सुबह रात")
        assertEquals("मेटफॉर्मिन", d.name); assertEquals("500 mg", d.dose); assertEquals(t(8, 21), d.times)
    }

    @Test fun unknownUnitLeavesDoseEmpty() {
        val d = parse("Ecosprin 2 tablets morning")
        assertEquals("Ecosprin", d.name); assertEquals("", d.dose); assertEquals(t(8), d.times)
    }

    @Test fun mlIsRecognised() {
        val d = parse("Cough syrup 10 ml night")
        assertEquals("10 ml", d.dose); assertEquals(t(21), d.times)
    }

    @Test fun neverInventsAName() {
        val d = parse("500 mg morning and night")
        assertEquals("", d.name); assertEquals("500 mg", d.dose)
        assertEquals("", parse("").name)
        assertEquals("", parse("twice a day").name)
    }

    @Test fun fillerWordsAreDropped() {
        assertEquals("Metformin", parse("Add medicine Metformin 500 morning").name)
        assertEquals("Metformin", parse("I take Metformin 500 mg twice a day").name)
    }

    @Test fun emptyStomachAndBedtime() {
        assertEquals(FoodTiming.EMPTY_STOMACH, parse("Thyronorm 50 mcg morning empty stomach").foodTiming)
        assertEquals("50 mcg", parse("Thyronorm 50 mcg morning empty stomach").dose)
        assertEquals(FoodTiming.BEDTIME, parse("Melatonin 3 mg at bedtime").foodTiming)
    }

    @Test fun withFood() {
        assertEquals(FoodTiming.WITH_FOOD, parse("Iron 100 mg once a day with food").foodTiming)
    }

    @Test fun noTimesHeardGivesEmptyList() {
        assertEquals(emptyList<LocalTime>(), parse("Metformin 500 mg").times)
    }
}
