package com.carecircle.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimAnswerTest {
    private val cite = listOf(ClaimCitation("p. 3, clause 5.2", "Cataract surgery is covered as a day care procedure"))

    @Test fun coveredWithACitationStaysCovered() {
        val v = ClaimAnswer.requireCitations("COVERED_PER_TEXT", "Covered.", cite, 0.9, "none")
        assertEquals(Verdict.LIKELY_COVERED, v.verdict)
        assertEquals(1, v.citations.size)
    }

    @Test fun noCitationOrLowConfidenceForcesUnclear() {
        assertEquals(Verdict.UNCLEAR, ClaimAnswer.requireCitations("COVERED_PER_TEXT", "Covered.", emptyList(), 0.99, "We could not find a matching clause.").verdict)
        assertEquals("We could not find a matching clause.", ClaimAnswer.requireCitations("EXCLUDED_PER_TEXT", "No.", emptyList(), 0.99, "We could not find a matching clause.").explanation)
        assertEquals(Verdict.UNCLEAR, ClaimAnswer.requireCitations("EXCLUDED_PER_TEXT", "No.", cite, 0.2, "x").verdict)
        assertEquals(Verdict.UNCLEAR, ClaimAnswer.requireCitations("WAITING_PERIOD", "Wait.", emptyList(), 0.9, "x").verdict)
    }

    @Test fun notFoundAndUnclearNeedNoCitation() {
        assertEquals(Verdict.NOT_FOUND, ClaimAnswer.requireCitations("NOT_FOUND", "n", cite, 0.0, "x").verdict)
        assertTrue(ClaimAnswer.requireCitations("NOT_FOUND", "n", cite, 0.0, "x").citations.isEmpty())
        assertEquals(Verdict.UNCLEAR, ClaimAnswer.requireCitations("SOMETHING_ELSE", "n", emptyList(), 0.0, "x").verdict)
    }

    @Test fun excerptsAreTruncatedAtTheLimit() {
        val long = "word ".repeat(200)
        val out = ClaimAnswer.truncateExcerpt(long)
        assertTrue(out.length <= ClaimAnswer.EXCERPT_MAX)
        assertTrue(out.endsWith("..."))
        assertEquals("short text", ClaimAnswer.truncateExcerpt("  short   text "))
    }

    @Test fun questionLengthIsValidated() {
        assertFalse(ClaimAnswer.validQuestion("hi"))
        assertTrue(ClaimAnswer.validQuestion("Is cataract surgery covered?"))
        assertFalse(ClaimAnswer.validQuestion("x".repeat(301)))
    }

    @Test fun clausesComeFromEveryCitedItemWithoutDuplicates() {
        val s = PolicySummary(
            sumInsured = PolicyItem("Rs. 5,00,000", 1, "2.1", "The Sum Insured is Rs. 5,00,000"),
            waitingSpecific = listOf(PolicyWait("cataract", "24 months", 2, "4.3", "Cataract surgery has a waiting period of 24 months")),
            exclusions = listOf(PolicyItem("Cosmetic surgery", 3, "7.1", "Cosmetic surgery is excluded"), PolicyItem("Cosmetic surgery", 3, "7.1", "cosmetic surgery is excluded")),
        )
        val c = ClaimAnswer.clausesFrom(s)
        assertEquals(3, c.size)
        assertEquals(listOf(1, 2, 3), c.map { it.page })
        assertTrue(ClaimAnswer.clausesFrom(PolicySummary()).isEmpty())
    }

    @Test fun neverMoreThanTheLimitOfClauses() {
        val many = (1..60).map { PolicyItem("v$it", it, null, "Quote number $it for exclusions") }
        assertEquals(ClaimAnswer.MAX_CLAUSES, ClaimAnswer.clausesFrom(PolicySummary(exclusions = many)).size)
    }
}
