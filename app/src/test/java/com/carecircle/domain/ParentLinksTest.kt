package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentLinksTest {
    private fun l(id: String, status: LinkStatus, person: Long = 1, at: Long = 0, label: String = id) = ParentLink(id, person, label, status, at)

    @Test fun threeIsTheLimitAndPendingCodesCount() {
        val two = listOf(l("A", LinkStatus.ACTIVE), l("B", LinkStatus.PENDING))
        assertTrue(ParentLinks.canAddLink(two, 1))
        assertFalse(ParentLinks.canAddLink(two + l("C", LinkStatus.PENDING), 1))
        assertFalse(ParentLinks.canAddLink(two + l("C", LinkStatus.UNKNOWN), 1)) // not knowing is treated as in use
    }

    @Test fun endedLinksFreeASlotAndOtherPeopleHaveTheirOwn() {
        val full = listOf(l("A", LinkStatus.ACTIVE), l("B", LinkStatus.ACTIVE), l("C", LinkStatus.REVOKED))
        assertTrue(ParentLinks.canAddLink(full, 1))
        assertTrue(ParentLinks.canAddLink(listOf(l("A", LinkStatus.ACTIVE), l("B", LinkStatus.ACTIVE), l("C", LinkStatus.ACTIVE)), 2))
    }

    @Test fun anUnclaimedCodeOlderThanTwentyMinutesIsDropped() {
        val now = 100 * 60_000L
        val links = listOf(l("old", LinkStatus.PENDING, at = now - 21 * 60_000L), l("fresh", LinkStatus.PENDING, at = now - 5 * 60_000L), l("active", LinkStatus.ACTIVE, at = 0))
        assertEquals(listOf("fresh", "active"), ParentLinks.pruneExpired(links, now).map { it.linkId })
    }

    @Test fun onlyActiveLinksGetPushes() {
        val links = listOf(l("A", LinkStatus.ACTIVE), l("B", LinkStatus.PENDING), l("C", LinkStatus.REVOKED))
        assertEquals(listOf("A"), ParentLinks.activeLinks(links).map { it.linkId })
    }

    @Test fun defaultNamesFillTheFirstFreeNumber() {
        val render = { n: Int -> "Caregiver $n" }
        assertEquals(1, ParentLinks.nextDefaultNumber(emptyList(), render))
        assertEquals(2, ParentLinks.nextDefaultNumber(listOf("Caregiver 1"), render))
        assertEquals(1, ParentLinks.nextDefaultNumber(listOf("caregiver 2", "Asha"), render))
        assertEquals(3, ParentLinks.nextDefaultNumber(listOf(" CAREGIVER 1 ", "Caregiver 2"), render))
    }

    @Test fun labelsAreUniqueIgnoringCaseAndShort() {
        assertEquals(LabelCheck.OK, ParentLinks.checkLabel("Asha", listOf("Ravi")))
        assertEquals(LabelCheck.EMPTY, ParentLinks.checkLabel("   ", emptyList()))
        assertEquals(LabelCheck.TOO_LONG, ParentLinks.checkLabel("x".repeat(21), emptyList()))
        assertEquals(LabelCheck.OK, ParentLinks.checkLabel("x".repeat(20), emptyList()))
        assertEquals(LabelCheck.DUPLICATE, ParentLinks.checkLabel(" ravi ", listOf("Ravi")))
    }
}
