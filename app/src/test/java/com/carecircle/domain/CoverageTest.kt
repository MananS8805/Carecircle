package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageTest {
    private val summary = PolicySummary(
        sumInsured = PolicyItem("Rs. 5,00,000", 1, "2.1", "The Sum Insured is Rs. 5,00,000"),
        waitingPreExisting = PolicyItem("36 months", 2, "4.2", "Pre-existing diseases are covered after 36 months of continuous coverage"),
        waitingSpecific = listOf(
            PolicyWait("Diabetes mellitus (type 2)", "2 years", 3, "4.5", "Diabetes 2 year waiting period applies to all insured persons"),
            PolicyWait("Cataract", "24 months", 3, "4.3", "Cataract surgery has a waiting period of 24 months"),
        ),
        exclusions = listOf(PolicyItem("Cosmetic surgery", 5, "7.1", "Cosmetic or plastic surgery is excluded")),
        inclusions = listOf(PolicyItem("Hypertension management", 4, null, "Treatment of hypertension is included after the waiting period")),
    )

    @Test fun diabetesTagFindsTheWaitingPeriodWithPageClauseAndQuote() {
        val m = Coverage.matchCoverage("Diabetes", summary)
        assertEquals(1, m.size)
        assertEquals(CoverageKind.WAITING_SPECIFIC, m[0].kind)
        assertEquals(3, m[0].page)
        assertEquals("4.5", m[0].clause)
        assertTrue(m[0].quote.contains("Diabetes 2 year waiting period"))
    }

    @Test fun synonymsAreMatched() {
        // "Blood pressure" chip finds the line that says hypertension.
        val m = Coverage.matchCoverage("Blood pressure", summary)
        assertEquals(listOf(CoverageKind.INCLUSION), m.map { it.kind })
        assertNull(m[0].clause)
    }

    @Test fun punctuationAndCaseDoNotMatter() {
        val s = PolicySummary(exclusions = listOf(PolicyItem("Excluded", 2, null, "Type-2 DIABETES, and its complications; are excluded.")))
        assertEquals(1, Coverage.matchCoverage("  diabetes ", s).size)
        assertEquals(1, Coverage.matchCoverage("DIABETES!", s).size)
    }

    @Test fun customTagsAreMatchedByTheirOwnWords() {
        val m = Coverage.matchCoverage("cataract", summary)
        assertEquals(1, m.size)
        assertEquals(CoverageKind.WAITING_SPECIFIC, m[0].kind)
    }

    @Test fun noRelatedItemGivesAnEmptyListNotAClaim() {
        assertTrue(Coverage.matchCoverage("Thyroid", summary).isEmpty())
        assertTrue(Coverage.matchCoverage("Asthma", summary).isEmpty())
    }

    @Test fun emptySummaryOrTagGivesNothing() {
        assertTrue(Coverage.matchCoverage("Diabetes", PolicySummary()).isEmpty())
        assertTrue(Coverage.matchCoverage("Diabetes", null).isEmpty())
        assertTrue(Coverage.matchCoverage("", summary).isEmpty())
        assertTrue(Coverage.matchCoverage(null, summary).isEmpty())
        assertTrue(Coverage.matchCoverage("Other", summary).isEmpty())
    }

    @Test fun itemsWithoutAVerifiedPageAndQuoteAreIgnored() {
        val s = PolicySummary(
            waitingSpecific = listOf(PolicyWait("Diabetes", "2 years", 0, null, "Diabetes waiting period of 2 years")),
            exclusions = listOf(PolicyItem("Diabetes complications", 4, null, "")),
        )
        assertTrue(Coverage.matchCoverage("Diabetes", s).isEmpty())
    }

    @Test fun preExistingLineIsMatchedOnlyWhenItMentionsTheCondition() {
        assertTrue(Coverage.matchCoverage("Diabetes", summary).none { it.kind == CoverageKind.PRE_EXISTING })
        val s = PolicySummary(waitingPreExisting = PolicyItem("48 months", 2, "3.1", "Pre-existing conditions such as diabetes and hypertension: 48 months"))
        assertEquals(CoverageKind.PRE_EXISTING, Coverage.matchCoverage("Diabetes", s).single().kind)
    }

    @Test fun shortSynonymsDoNotMatchInsideOtherWords() {
        // "bp" and "dm" must be whole words.
        val s = PolicySummary(exclusions = listOf(PolicyItem("Admission", 2, null, "Admission formalities and bpm readings are not covered here")))
        assertTrue(Coverage.matchCoverage("Diabetes", s).isEmpty())
        assertTrue(Coverage.matchCoverage("Blood pressure", s).isEmpty())
    }

    @Test fun suggestionFromInfoCardTextOnlyForClearMatches() {
        assertEquals(Condition.DIABETES, Coverage.suggestFromText("Used with diet and exercise to improve blood sugar in type 2 diabetes."))
        assertNull(Coverage.suggestFromText("Temporarily relieves minor aches."))
        assertNull(Coverage.suggestFromText(""))
    }

    @Test fun resolvePrefersTheUsersTag() {
        assertEquals(Condition.ASTHMA, Conditions.resolve("Asthma", "Metformin", "metformin"))
        assertEquals(Condition.DIABETES, Conditions.resolve(null, "Glycomet", "metformin"))
        assertEquals(Condition.OTHER, Conditions.resolve("my own tag", "Glycomet", "metformin"))
    }
}
