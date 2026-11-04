package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefillTest {
    private val day = 86_400_000L

    @Test fun quantityMustBeBetweenOneAndAThousand() {
        assertFalse(RefillRules.validQuantity(0))
        assertFalse(RefillRules.validQuantity(-5))
        assertTrue(RefillRules.validQuantity(1))
        assertTrue(RefillRules.validQuantity(1000))
        assertFalse(RefillRules.validQuantity(1001))
    }

    @Test fun undoOnlyWhenTheCountIsUnchanged() {
        assertTrue(RefillRules.canUndo(currentCount = 70, countRightAfterRefill = 70))
        assertFalse(RefillRules.canUndo(currentCount = 69, countRightAfterRefill = 70))
    }

    @Test fun averageNeedsThreeEventsAndIgnoresOrder() {
        assertNull(RefillRules.averageDaysBetween(listOf(0, 30 * day)))
        assertEquals(30, RefillRules.averageDaysBetween(listOf(60 * day, 0, 30 * day)))
        assertEquals(25, RefillRules.averageDaysBetween(listOf(0, 20 * day, 50 * day)))
    }

    private fun med(name: String, count: Int, perDose: Int = 1, times: Int = 2, active: Boolean = true) = Messages.OrderMed(name, "500 mg", count, perDose, times, active)

    @Test fun monthlyOrderListsOnlyLowStockWithAThirtyDaySupply() {
        val lines = Messages.monthlyOrder(listOf(med("Low", 10), med("Plenty", 100), med("Edge", 20)))
        assertEquals(listOf("Low 500 mg"), lines.map { it.label }) // 10 / 2 per day = 5 days; 20 / 2 = 10 days is not below 10
        assertEquals(5, lines.single().daysLeft)
        assertEquals(60, lines.single().quantity)
    }

    @Test fun monthlyOrderSkipsAsNeededAndRemovedMedicines() {
        val lines = Messages.monthlyOrder(listOf(med("As needed", 2, times = 0), med("Gone", 2, active = false), med("Two pills", 6, perDose = 2, times = 1)))
        assertEquals(listOf("Two pills 500 mg"), lines.map { it.label })
        assertEquals(60, lines.single().quantity)
    }

    @Test fun lowestStockComesFirst() {
        val lines = Messages.monthlyOrder(listOf(med("B", 12), med("A", 4)))
        assertEquals(listOf("A 500 mg", "B 500 mg"), lines.map { it.label })
    }
}
