package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class ExpiryTest {
    @Test fun readsTypicalIndianStripLines() {
        assertEquals(YearMonth.of(2028, 2), Expiry.parse("Mfg. Date: 03/2026 Exp. Date: 02/2028"))
        assertEquals(YearMonth.of(2028, 2), Expiry.parse("MFG 03/26  EXP 02/28"))
        assertEquals(YearMonth.of(2027, 11), Expiry.parse("Expiry: NOV 2027"))
        assertEquals(YearMonth.of(2028, 2), Expiry.parse("EXP: FEB.28"))
        assertEquals(YearMonth.of(2029, 1), Expiry.parse("Best before 01-2029"))
    }

    @Test fun neverReadsTheManufacturingDate() {
        assertNull(Expiry.parse("Mfg. Date: 03/2026"))
        assertNull(Expiry.parse("Paracetamol IP 500 mg MRP 30.00"))
        assertNull(Expiry.parse("EXP 13/2028"))
    }

    @Test fun typedValues() {
        assertEquals(YearMonth.of(2028, 2), Expiry.parseTyped("02/2028"))
        assertEquals(YearMonth.of(2028, 2), Expiry.parseTyped(" 2/28 "))
        assertEquals(YearMonth.of(2028, 2), Expiry.parseTyped("2028-02"))
        assertNull(Expiry.parseTyped("soon")); assertNull(Expiry.parseTyped("00/2028"))
    }

    @Test fun statusUsesTheLastDayOfTheMonth() {
        val ym = YearMonth.of(2026, 10)
        assertEquals(ExpiryStatus.OK, Expiry.status(ym, LocalDate.of(2026, 7, 1)))
        assertEquals(ExpiryStatus.SOON, Expiry.status(ym, LocalDate.of(2026, 9, 1)))
        assertEquals(ExpiryStatus.SOON, Expiry.status(ym, LocalDate.of(2026, 10, 31)))
        assertEquals(ExpiryStatus.EXPIRED, Expiry.status(ym, LocalDate.of(2026, 11, 1)))
    }
}
