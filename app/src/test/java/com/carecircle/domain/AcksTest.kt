package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AcksTest {
    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 11, 4)
    private fun ms(d: LocalDate, h: Int, m: Int = 0) = d.atTime(h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun theSlotKeyIsTheUtcDayAndTheDoseTime() {
        val t = ms(day, 8)
        assertEquals("2026-11-04_$t", Acks.slotKey(t))
        assertEquals(Acks.slotKey(t), Acks.slotKeyFromId(Acks.slotId(t)))
        assertTrue(Acks.validKey(Acks.slotKey(t)))
    }

    @Test fun anInvalidSlotIdGivesNoKey() {
        assertNull(Acks.slotKeyFromId("abc")); assertNull(Acks.slotKeyFromId("../1")); assertNull(Acks.slotKeyFromId(""))
        assertFalse(Acks.validSlotId(null)); assertFalse(Acks.validSlotId("12"))
    }

    @Test fun acksFromEveryLinkAreMergedOnceEach() {
        val a = Acks.slotKey(ms(day, 8)); val b = Acks.slotKey(ms(day, 20))
        assertEquals(listOf(a, b).sorted(), Acks.mergeAcks(listOf(listOf(a), listOf(a, b), emptyList())))
        assertEquals(listOf(a), Acks.mergeAcks(listOf(listOf(a, "garbage", "../x"))))
    }

    @Test fun keysOlderThanFourteenDaysAreForgotten() {
        val old = Acks.slotKey(ms(day.minusDays(15), 8)); val edge = Acks.slotKey(ms(day.minusDays(14), 8)); val fresh = Acks.slotKey(ms(day, 8))
        assertEquals(listOf(edge, fresh).sorted(), Acks.pruneOld(listOf(old, edge, fresh), day).sorted())
    }

    @Test fun theHandledListIsCappedAtTheNewest() {
        val keys = (0 until 300).map { Acks.slotKey(ms(day, 8) + it * 60_000L) }
        val out = Acks.handledSlots(keys, day)
        assertEquals(Acks.MAX_KEYS, out.size)
        assertEquals(keys.last(), out.last())
    }

    @Test fun handledMeansTheSameDoseTime() {
        val t = ms(day, 8)
        val handled = listOf(Acks.slotKey(t))
        assertTrue(Acks.isHandled(handled, t)); assertFalse(Acks.isHandled(handled, t + 60_000L)); assertFalse(Acks.isHandled(emptyList(), t))
    }

    @Test fun theParentsCacheKeepsEachPersonSeparate() {
        val a = Acks.slotKey(ms(day, 8)); val b = Acks.slotKey(ms(day, 20))
        var map = HandledCache.with(emptyMap(), 1, listOf(a), day)
        map = HandledCache.with(map, 2, listOf(b), day)
        map = HandledCache.with(map, 1, listOf(a, b), day)
        assertEquals(setOf(a, b), map[1]!!.toSet()); assertEquals(listOf(b), map[2])
        assertEquals(map.mapValues { it.value.sorted() }, HandledCache.decode(HandledCache.encode(map)).mapValues { it.value.sorted() })
        assertEquals(emptyMap<Long, List<String>>(), HandledCache.decode(""))
    }
}
