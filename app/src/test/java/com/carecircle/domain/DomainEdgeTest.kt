package com.carecircle.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Boundaries and bad input for the pure rules. Each case is one sentence of behaviour the family depends on. */
class DomainEdgeTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int, day: Int = 4) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, ist)

    // ---------- Schedule ----------
    @Test fun parseTimesDropsGarbageBlanksAndDuplicatesAndSorts() {
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), Schedule.parseTimes("20:00, 08:00,xx,,08:00"))
    }
    @Test fun parseTimesOfNothingIsEmpty() { assertTrue(Schedule.parseTimes("").isEmpty()); assertTrue(Schedule.parseTimes(" , ").isEmpty()) }
    @Test fun anImpossibleClockTimeIsIgnored() { assertTrue(Schedule.parseTimes("12:60,99:99,ab:cd").isEmpty()) }
    @Test fun formatTimesSortsAndPadsTwoDigits() {
        assertEquals("06:05,21:00", Schedule.formatTimes(listOf(LocalTime.of(21, 0), LocalTime.of(6, 5))))
    }
    @Test fun nextDoseIsStrictlyAfterTheGivenMoment() {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
        assertEquals(at(20, 0), Schedule.nextDose(times, at(8, 0)))
        assertEquals(at(8, 0), Schedule.nextDose(times, at(7, 59)))
    }
    @Test fun afterTheLastDoseOfTheDayTheNextOneIsTomorrowMorning() {
        assertEquals(at(8, 0, day = 5), Schedule.nextDose(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), at(20, 1)))
    }
    @Test fun noTimesMeansNoNextDose() { assertNull(Schedule.nextDose(emptyList(), at(9, 0))) }
    @Test fun nextDoseCrossesTheEndOfAMonth() {
        val t = Schedule.nextDose(listOf(LocalTime.of(8, 0)), ZonedDateTime.of(2026, 11, 30, 9, 0, 0, 0, ist))!!
        assertEquals(LocalDate.of(2026, 12, 1), t.toLocalDate())
    }

    // ---------- Refill ----------
    @Test fun daysLeftIsWholeDays() { assertEquals(15, Refill.daysLeft(30, 1, 2)); assertEquals(7, Refill.daysLeft(30, 2, 2)) }
    @Test fun daysLeftNeverGoesNegativeOrDividesByZero() {
        assertEquals(0, Refill.daysLeft(-5, 1, 1)); assertEquals(30, Refill.daysLeft(30, 0, 0)); assertEquals(0, Refill.daysLeft(0, 1, 3))
    }
    @Test fun reorderStartsAtFiveDaysLeft() { assertTrue(Refill.needsReorder(5)); assertTrue(Refill.needsReorder(0)); assertFalse(Refill.needsReorder(6)) }
    @Test fun refillQuantityMustBeBetweenOneAndAThousand() {
        assertFalse(RefillRules.validQuantity(0)); assertTrue(RefillRules.validQuantity(1))
        assertTrue(RefillRules.validQuantity(1000)); assertFalse(RefillRules.validQuantity(1001)); assertFalse(RefillRules.validQuantity(-3))
    }
    @Test fun aRefillCanBeUndoneOnlyWhileTheCountIsUnchanged() { assertTrue(RefillRules.canUndo(40, 40)); assertFalse(RefillRules.canUndo(39, 40)) }
    @Test fun averageRefillGapNeedsThreePurchases() {
        val day = 86_400_000L
        assertNull(RefillRules.averageDaysBetween(listOf(0, 30 * day)))
        assertEquals(30, RefillRules.averageDaysBetween(listOf(0, 30 * day, 60 * day)))
        assertEquals(30, RefillRules.averageDaysBetween(listOf(60 * day, 0, 30 * day)))
    }

    // ---------- Missed dose and snooze ----------
    @Test fun aDoseIsMissedExactlyThirtyMinutesAfterItsTime() {
        val t = 1_000_000_000L
        assertFalse(MissedDose.isMissed(t, t + 30 * 60_000L - 1, null))
        assertTrue(MissedDose.isMissed(t, t + 30 * 60_000L, null))
    }
    @Test fun aTakenDoseIsNeverMissed() { assertFalse(MissedDose.isMissed(0, Long.MAX_VALUE / 2, takenAtMs = 5)) }
    @Test fun theGraceCanBeChanged() { assertTrue(MissedDose.isMissed(0, 5 * 60_000L, null, graceMinutes = 5)) }
    @Test fun snoozeIsTenMinutes() { assertEquals(1_600_000L + 600_000L, Snooze.fireAt(1_600_000L)) }
    @Test fun escalationRepeatsAtTenAndTwentyMinutesThenAlertsAtThirty() {
        assertEquals(listOf(600_000L, 1_200_000L), Escalation.repeatTimes(0))
        assertEquals(30L, Escalation.ALERT_AFTER_MINUTES)
        assertEquals(30L, Escalation.minutesUntilAlert(0)); assertEquals(20L, Escalation.minutesUntilAlert(1)); assertEquals(10L, Escalation.minutesUntilAlert(2))
    }
    @Test fun noRemindersOnceTakenOrSkipped() {
        assertTrue(Escalation.shouldRemind(null, false)); assertFalse(Escalation.shouldRemind(5L, false)); assertFalse(Escalation.shouldRemind(null, true))
    }
    @Test fun theDailySummaryIsAtNinePmTodayOrTomorrow() {
        assertEquals(at(21, 0), Escalation.nextDailySummary(at(20, 59)))
        assertEquals(at(21, 0, day = 5), Escalation.nextDailySummary(at(21, 0)))
    }

    // ---------- Messages ----------
    @Test fun aTenDigitIndianNumberGetsTheCountryCode() {
        assertEquals("919876543210", Messages.normalizePhone("98765 43210"))
        assertEquals("919876543210", Messages.normalizePhone("98765-43210"))
    }
    @Test fun aNumberWithTheCountryCodeIsKept() {
        assertEquals("919876543210", Messages.normalizePhone("+91 98765 43210"))
        assertEquals("447911123456", Messages.normalizePhone("+44 7911 123456"))
    }
    @Test fun unusableNumbersAreRejected() {
        assertNull(Messages.normalizePhone("")); assertNull(Messages.normalizePhone("123")); assertNull(Messages.normalizePhone("abc"))
        assertNull(Messages.normalizePhone("1".repeat(16)))
    }
    @Test fun theWhatsAppLinkEncodesTheTextAndCarriesTheNumber() {
        val url = Messages.whatsAppUrl("919876543210", "Asha missed 8:00 & more")
        assertTrue(url.startsWith("https://wa.me/919876543210?text="))
        assertFalse(url.substringAfter("text=").contains(' ')); assertTrue(url.contains("%26"))
    }
    @Test fun withoutANumberTheLinkOpensAChooser() { assertEquals("https://wa.me/?text=Hi", Messages.whatsAppUrl(null, "Hi")) }
    @Test fun theMedicineLabelSkipsBlanks() {
        assertEquals("Metformin 500 mg", Messages.medicineLabel("Metformin", "500 mg")); assertEquals("Metformin", Messages.medicineLabel("Metformin", " "))
    }

    // ---------- Pairing code and lockout ----------
    @Test fun everyGeneratedCodeIsValidEightCharactersFromTheSafeAlphabet() {
        repeat(300) { val c = PairingCode.generate(); assertEquals(8, c.length); assertTrue(PairingCode.isValid(c)) }
    }
    @Test fun theAlphabetLeavesOutLookAlikeCharacters() { for (c in "IL01O") assertFalse(PairingCode.ALPHABET.contains(c)) }
    @Test fun codesAreNotAllTheSame() { assertTrue((1..50).map { PairingCode.generate() }.toSet().size > 40) }
    @Test fun typingACodeWithSpacesDashesAndLowerCaseStillMatches() { assertEquals("AB2C3DEF", PairingCode.normalize(" ab2c-3def ")) }
    @Test fun aCodeWithALookAlikeCharacterIsInvalid() { assertFalse(PairingCode.isValid("AB1C3DEF")); assertFalse(PairingCode.isValid("ABC")) }
    @Test fun theCodeIsShownInTwoGroupsOfFour() { assertEquals("ABCD-EFGH", PairingCode.display("ABCDEFGH")); assertEquals("ABC", PairingCode.display("ABC")) }
    @Test fun fourWrongCodesDoNotLockButTheFifthDoes() {
        var s = CodeLockout.State()
        repeat(4) { s = CodeLockout.afterFailure(s, 1000L); assertFalse(CodeLockout.isLocked(s, 1000L)) }
        s = CodeLockout.afterFailure(s, 1000L)
        assertTrue(CodeLockout.isLocked(s, 1000L)); assertEquals(0, s.fails)
    }
    @Test fun theLockLastsFifteenMinutes() {
        var s = CodeLockout.State(); repeat(5) { s = CodeLockout.afterFailure(s, 0L) }
        assertTrue(CodeLockout.isLocked(s, 15 * 60_000L - 1)); assertFalse(CodeLockout.isLocked(s, 15 * 60_000L))
        assertEquals(15, CodeLockout.minutesLeft(s, 0)); assertEquals(1, CodeLockout.minutesLeft(s, 14 * 60_000L + 1))
    }
    @Test fun aSuccessClearsTheCount() { assertEquals(CodeLockout.State(), CodeLockout.afterSuccess()) }
    @Test fun afterTheLockEndsTheCountStartsAgain() {
        var s = CodeLockout.State(); repeat(5) { s = CodeLockout.afterFailure(s, 0L) }
        val later = CodeLockout.afterFailure(s, 16 * 60_000L)
        assertEquals(1, later.fails); assertFalse(CodeLockout.isLocked(later, 16 * 60_000L))
    }

    // ---------- What leaves the phone ----------
    @Test fun withoutConsentToShareNamesNoNameLeaves() {
        assertEquals("", SyncPrivacy.personField("Asha Devi", false))
        assertEquals("8:00 am dose", SyncPrivacy.medicineField("Metformin 500 mg", "8:00 am dose", false))
        assertEquals("", SyncPrivacy.nextMedicineField("Metformin", false))
    }
    @Test fun withConsentNamesAreTrimmedAndLimited() {
        assertEquals("Asha", SyncPrivacy.personField("  Asha  ", true))
        assertEquals(40, SyncPrivacy.personField("x".repeat(100), true).length)
        assertEquals(80, SyncPrivacy.medicineField("y".repeat(200), "t", true).length)
    }

    // ---------- Expiry ----------
    @Test fun typedExpiryAcceptsSeveralSeparators() {
        assertEquals(YearMonth.of(2028, 2), Expiry.parseTyped("02/2028")); assertEquals(YearMonth.of(2028, 2), Expiry.parseTyped("2-2028"))
        assertEquals(YearMonth.of(2028, 2), Expiry.parseTyped("2028-02"))
    }
    @Test fun typedExpiryRejectsImpossibleMonthsAndYears() {
        assertNull(Expiry.parseTyped("13/2028")); assertNull(Expiry.parseTyped("00/2028")); assertNull(Expiry.parseTyped("02/1999")); assertNull(Expiry.parseTyped("soon"))
    }
    @Test fun aStripIsValidThroughTheLastDayOfItsMonth() {
        val ym = YearMonth.of(2028, 2)
        assertEquals(ExpiryStatus.SOON, Expiry.status(ym, LocalDate.of(2028, 2, 29)))
        assertEquals(ExpiryStatus.EXPIRED, Expiry.status(ym, LocalDate.of(2028, 3, 1)))
        assertEquals(ExpiryStatus.OK, Expiry.status(ym, LocalDate.of(2027, 1, 1)))
    }
    @Test fun soonStartsSixtyDaysBefore() {
        val ym = YearMonth.of(2028, 2); val last = LocalDate.of(2028, 2, 29)
        assertEquals(ExpiryStatus.SOON, Expiry.status(ym, last.minusDays(60))); assertEquals(ExpiryStatus.OK, Expiry.status(ym, last.minusDays(61)))
    }
    @Test fun expiryIsStoredAsYearDashMonth() { assertEquals("2028-02", Expiry.format(YearMonth.of(2028, 2))) }

    // ---------- Vitals ----------
    @Test fun aCommaDecimalIsRead() { assertEquals(72.5, Vitals.parse("72,5")!!, 0.0); assertEquals(72.5, Vitals.parse(" 72.5 ")!!, 0.0) }
    @Test fun notANumberIsNull() { for (s in listOf("abc", "", "NaN", "Infinity", "1,2,3")) assertNull(s, Vitals.parse(s)) }
    @Test fun bloodPressureNeedsBothValuesAndSystolicAboveDiastolic() {
        assertTrue(Vitals.isPlausible(VitalKind.BP, 120.0, 80.0)); assertFalse(Vitals.isPlausible(VitalKind.BP, 80.0, 120.0))
        assertFalse(Vitals.isPlausible(VitalKind.BP, 120.0, null)); assertFalse(Vitals.isPlausible(VitalKind.BP, 300.0, 80.0))
    }
    @Test fun otherReadingsHavePlausibleRanges() {
        assertFalse(Vitals.isPlausible(VitalKind.SUGAR, 19.0)); assertTrue(Vitals.isPlausible(VitalKind.SUGAR, 20.0)); assertFalse(Vitals.isPlausible(VitalKind.SUGAR, 601.0))
        assertTrue(Vitals.isPlausible(VitalKind.PULSE, 72.0)); assertFalse(Vitals.isPlausible(VitalKind.PULSE, 10.0))
        assertTrue(Vitals.isPlausible(VitalKind.TEMP, 36.6)); assertFalse(Vitals.isPlausible(VitalKind.TEMP, 43.1))
        assertTrue(Vitals.isPlausible(VitalKind.WEIGHT, 62.0)); assertFalse(Vitals.isPlausible(VitalKind.WEIGHT, 1.0))
    }
    @Test fun readingsAreShownWithoutPointlessDecimals() {
        assertEquals("120/80", Vitals.format(VitalKind.BP, 120.0, 80.0)); assertEquals("98", Vitals.format(VitalKind.SUGAR, 98.0, null)); assertEquals("72.5", Vitals.format(VitalKind.PULSE, 72.5, null))
    }
    @Test fun noInterpretationWordsAreEverProduced() {
        val shown = listOf(Vitals.format(VitalKind.BP, 190.0, 110.0), Vitals.format(VitalKind.SUGAR, 400.0, null))
        for (s in shown) for (w in listOf("high", "low", "normal", "abnormal", "danger")) assertFalse(s.contains(w, ignoreCase = true))
    }

    // ---------- Appointments ----------
    @Test fun theReminderIsTheChosenNumberOfMinutesBeforeTheAppointment() {
        assertEquals(1_000_000L - 60 * 60_000L, Appointments.remindAt(ApptLite(1, 1_000_000L, 60)))
    }
    @Test fun anAppointmentWhoseReminderTimeHasPassedIsNotArmed() {
        val a = ApptLite(1, 10_000_000L, 60)
        assertTrue(Appointments.shouldArm(a, 10_000_000L - 61 * 60_000L)); assertFalse(Appointments.shouldArm(a, 10_000_000L - 59 * 60_000L))
    }
    @Test fun anAppointmentIsUpcomingUntilItStarts() {
        val a = ApptLite(1, 5_000L, 0); assertTrue(Appointments.isUpcoming(a, 5_000L)); assertFalse(Appointments.isUpcoming(a, 5_001L))
    }
    @Test fun aNegativeReminderOffsetIsTreatedAsZero() { assertEquals(5_000L, Appointments.remindAt(ApptLite(1, 5_000L, -10))) }

    // ---------- Sanity across rules ----------
    @Test fun theCodeAlphabetHasNoDuplicates() { assertEquals(PairingCode.ALPHABET.length, PairingCode.ALPHABET.toSet().size) }
    @Test fun twoDifferentCodesAreNotEqualAfterNormalising() { assertNotEquals(PairingCode.normalize("AAAA-BBBB"), PairingCode.normalize("AAAA-BBBC")) }
}
