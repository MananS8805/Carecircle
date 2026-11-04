package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Random

class SyncTest {
    @Test fun generatedCodesAreValidAndAvoidLookAlikes() {
        val rnd = Random(7)
        repeat(200) {
            val c = PairingCode.generate(rnd)
            assertEquals(PairingCode.LENGTH, c.length)
            assertTrue(PairingCode.isValid(c))
            assertTrue(c.none { it in "01OIL" })
        }
    }

    @Test fun typedCodesAreNormalised() {
        assertEquals("ABCD2345", PairingCode.normalize(" abcd-2345 "))
        assertTrue(PairingCode.isValid(PairingCode.normalize("abcd 2345")))
        assertFalse(PairingCode.isValid("ABCD234"))
        assertFalse(PairingCode.isValid("ABCD234O"))
        assertEquals("ABCD-2345", PairingCode.display("ABCD2345"))
    }

    @Test fun codesDoNotRepeatOftenEnoughToBeGuessable() {
        val rnd = Random(1)
        assertEquals(1000, (1..1000).map { PairingCode.generate(rnd) }.toSet().size)
    }

    @Test fun eventIdsAreStableSoRetriesNeverDuplicate() {
        val e = SyncEvent(SyncType.MISSED, "Dad", "Metformin", 1000, 2000)
        assertEquals("missed-42", e.id(doseId = 42))
        assertEquals(e.id(42), e.id(42))
        assertEquals("taken-42", e.copy(type = SyncType.TAKEN).id(42))
        val s = SyncEvent(SyncType.SUMMARY, "Dad", at = 0, taken = 3, total = 4)
        assertEquals("summary-2026-10-26", s.id(day = LocalDate.of(2026, 10, 26)))
    }

    @Test fun typeKeysRoundTripAndUnknownIsNull() {
        SyncType.entries.forEach { assertEquals(it, SyncType.fromKey(it.key)) }
        assertNull(SyncType.fromKey("bogus")); assertNull(SyncType.fromKey(null))
    }
}

class SyncPrivacyAndLockoutTest {
    @org.junit.Test fun noNamesLeaveThePhoneByDefault() {
        org.junit.Assert.assertEquals("", SyncPrivacy.personField("Asha", false))
        org.junit.Assert.assertEquals("8:00 AM", SyncPrivacy.medicineField("Metformin 500 mg", "8:00 AM", false))
        org.junit.Assert.assertEquals("", SyncPrivacy.nextMedicineField("Metformin", false))
    }

    @org.junit.Test fun namesAreSentOnlyWhenTheUserTurnsThemOn() {
        org.junit.Assert.assertEquals("Asha", SyncPrivacy.personField("  Asha ", true))
        org.junit.Assert.assertEquals("Metformin 500 mg", SyncPrivacy.medicineField("Metformin 500 mg", "8:00 AM", true))
        org.junit.Assert.assertEquals("Metformin", SyncPrivacy.nextMedicineField("Metformin", true))
        org.junit.Assert.assertEquals(40, SyncPrivacy.personField("x".repeat(80), true).length)
    }

    @org.junit.Test fun fifthWrongCodeLocksForFifteenMinutes() {
        var s = CodeLockout.State()
        val t = 1_000_000L
        repeat(4) { s = CodeLockout.afterFailure(s, t); org.junit.Assert.assertFalse(CodeLockout.isLocked(s, t)) }
        s = CodeLockout.afterFailure(s, t)
        org.junit.Assert.assertTrue(CodeLockout.isLocked(s, t))
        org.junit.Assert.assertTrue(CodeLockout.isLocked(s, t + 14 * 60_000L))
        org.junit.Assert.assertFalse(CodeLockout.isLocked(s, t + 15 * 60_000L))
        org.junit.Assert.assertEquals(15, CodeLockout.minutesLeft(s, t))
    }

    @org.junit.Test fun aSuccessResetsTheCountAndANewRoundStartsAfterTheLock() {
        var s = CodeLockout.State()
        repeat(3) { s = CodeLockout.afterFailure(s, 1L) }
        s = CodeLockout.afterSuccess()
        org.junit.Assert.assertEquals(0, s.fails)
        // after a lock ends, the first wrong code counts as 1 again, not 5
        val locked = CodeLockout.State(0, 500L)
        val next = CodeLockout.afterFailure(locked, 600L)
        org.junit.Assert.assertEquals(1, next.fails)
        org.junit.Assert.assertFalse(CodeLockout.isLocked(next, 600L))
    }
}
