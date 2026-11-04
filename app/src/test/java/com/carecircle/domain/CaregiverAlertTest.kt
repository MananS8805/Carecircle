package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CaregiverAlertTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val key = CaregiverAlert.key(today, "ABCD2345", "8:00 AM")

    @Test fun alertsOncePerSlotPerDay() {
        val links = setOf("ABCD2345")
        assertTrue(CaregiverAlert.shouldNotify(true, links, "ABCD2345", key, emptySet()))
        val after = CaregiverAlert.remember(emptySet(), key, today)
        assertFalse(CaregiverAlert.shouldNotify(true, links, "ABCD2345", key, after))
        // The same dose time tomorrow is a new alert.
        assertTrue(CaregiverAlert.shouldNotify(true, links, "ABCD2345", CaregiverAlert.key(today.plusDays(1), "ABCD2345", "8:00 AM"), after))
    }

    @Test fun noAlertWhenPushIsOffOrTheLinkIsNotFollowed() {
        assertFalse(CaregiverAlert.shouldNotify(false, setOf("ABCD2345"), "ABCD2345", key, emptySet()))
        assertFalse(CaregiverAlert.shouldNotify(true, setOf("OTHER234"), "ABCD2345", key, emptySet()))
        assertFalse(CaregiverAlert.shouldNotify(true, emptySet(), "ABCD2345", key, emptySet()))
    }

    @Test fun rememberedKeysOlderThanYesterdayAreForgotten() {
        val old = CaregiverAlert.key(today.minusDays(3), "ABCD2345", "8:00 AM")
        val yesterday = CaregiverAlert.key(today.minusDays(1), "ABCD2345", "8:00 AM")
        val kept = CaregiverAlert.remember(setOf(old, yesterday, "garbage"), key, today)
        assertEquals(setOf(yesterday, key), kept)
    }

    @Test fun labelsAreCheckedBeforeTheyAreShown() {
        assertEquals("8:00 AM", CaregiverAlert.sanitizeLabel("  8:00 AM "))
        assertNull(CaregiverAlert.sanitizeLabel(null))
        assertNull(CaregiverAlert.sanitizeLabel("   "))
        assertNull(CaregiverAlert.sanitizeLabel("x".repeat(21)))
        assertNull(CaregiverAlert.sanitizeLabel("8:00\u0000AM"))
    }

    @Test fun storedKeysSurviveEncoding() {
        val set = setOf(key, CaregiverAlert.key(today, "ZZZZ2222", "9:30 PM"))
        assertEquals(set, CaregiverAlert.decode(CaregiverAlert.encode(set)))
        assertEquals(emptySet<String>(), CaregiverAlert.decode(""))
    }
}

class PendingNotifyTest {
    private val now = 10 * 3600_000L
    private fun e(slot: String, at: Long = now, link: String = "ABCD2345") = PendingNotify(link, slot, "8:00 AM", at)

    @Test fun roundTripAndSurvivesOddLabels() {
        val list = listOf(e("1-5"), PendingNotify("ABCD2345", "2-5", "a|b\nc", now))
        val back = PendingNotifies.decode(PendingNotifies.encode(list))
        assertEquals(2, back.size)
        assertEquals("a b c", back[1].slotLabel)
        assertEquals(emptyList<PendingNotify>(), PendingNotifies.decode(""))
    }

    @Test fun theSameDoseIsHeldOncePerLink() {
        var l = PendingNotifies.add(emptyList(), e("1-5"))
        l = PendingNotifies.add(l, e("1-5"))
        l = PendingNotifies.add(l, e("1-5", link = "OTHER234"))
        assertEquals(2, l.size)
    }

    @Test fun entriesOlderThanTwoHoursAreDropped() {
        val l = listOf(e("fresh", now - 3600_000L), e("stale", now - 3 * 3600_000L))
        assertEquals(listOf("fresh"), PendingNotifies.prune(l, now).map { it.slotId })
        assertEquals(1, PendingNotifies.prune(listOf(e("edge", now - PendingNotifies.MAX_AGE_MS)), now).size)
    }

    @Test fun removeAndFilterByActiveLinks() {
        val l = listOf(e("1-5"), e("2-5", link = "GONE2222"))
        assertEquals(listOf("2-5"), PendingNotifies.remove(l, e("1-5")).map { it.slotId })
        assertEquals(listOf("1-5"), PendingNotifies.forLinks(l, setOf("ABCD2345")).map { it.slotId })
    }
}
