package com.carecircle.domain

import kotlinx.serialization.Serializable

/** One clause sent with a question: the page, the clause number if printed, and the quote saved from the policy. */
@Serializable
data class ClauseDto(val page: Int, val clause: String? = null, val text: String)

/** How the answer is shown. Only [LIKELY_COVERED] and [LIKELY_NOT_COVERED] ever read as a verdict, and only with a source clause. */
enum class Verdict { LIKELY_COVERED, LIKELY_NOT_COVERED, WAITING_PERIOD, NOT_FOUND, UNCLEAR }

data class ClaimCitation(val clauseRef: String, val excerpt: String)

data class ClaimView(val verdict: Verdict, val explanation: String, val citations: List<ClaimCitation>)

object ClaimAnswer {
    const val MAX_QUESTION = 300
    const val MIN_QUESTION = 3
    const val MAX_CLAUSES = 40
    const val MAX_CLAUSE_CHARS = 2_000
    const val EXCERPT_MAX = 280
    /** Below this the server's confidence is not enough for a verdict. */
    const val MIN_CONFIDENCE = 0.4

    fun validQuestion(q: String): Boolean = q.trim().length in MIN_QUESTION..MAX_QUESTION

    /**
     * The clauses of the saved policy summary, each as its page, clause and exact quote. The policy document itself is
     * never kept on the phone, so these quotes are everything a question can be answered from.
     */
    fun clausesFrom(s: PolicySummary): List<ClauseDto> {
        val scalar = listOf(s.sumInsured, s.roomRentCap, s.coPay, s.waitingInitial, s.waitingPreExisting, s.dayCare, s.preHospitalisation, s.postHospitalisation)
            .filterNotNull().map { ClauseDto(it.page, it.clause, it.quote) }
        val waits = s.waitingSpecific.map { ClauseDto(it.page, it.clause, it.quote) }
        val lists = (s.inclusions + s.exclusions).map { ClauseDto(it.page, it.clause, it.quote) }
        return (scalar + waits + lists).filter { it.text.isNotBlank() }
            .distinctBy { it.page to it.text.trim().lowercase() }
            .map { it.copy(text = it.text.take(MAX_CLAUSE_CHARS)) }
            .take(MAX_CLAUSES)
    }

    fun normalizeVerdict(answer: String): Verdict = when (answer) {
        "COVERED_PER_TEXT" -> Verdict.LIKELY_COVERED
        "EXCLUDED_PER_TEXT" -> Verdict.LIKELY_NOT_COVERED
        "WAITING_PERIOD" -> Verdict.WAITING_PERIOD
        "NOT_FOUND" -> Verdict.NOT_FOUND
        else -> Verdict.UNCLEAR
    }

    fun truncateExcerpt(text: String, max: Int = EXCERPT_MAX): String {
        val t = text.trim().replace(Regex("\\s+"), " ")
        return if (t.length <= max) t else t.take(max - 3).trimEnd() + "..."
    }

    /**
     * Never a verdict without a source clause: a covered / not covered / waiting answer with no citation, or with low
     * confidence, becomes Unclear. The server already checks quotes; this is the second line of defence on the phone.
     */
    fun requireCitations(answer: String, explanation: String, citations: List<ClaimCitation>, confidence: Double, noClauseText: String): ClaimView {
        val v = normalizeVerdict(answer)
        val needsCite = v == Verdict.LIKELY_COVERED || v == Verdict.LIKELY_NOT_COVERED || v == Verdict.WAITING_PERIOD
        val shown = citations.map { ClaimCitation(it.clauseRef, truncateExcerpt(it.excerpt)) }.filter { it.excerpt.isNotBlank() }
        if (needsCite && (shown.isEmpty() || confidence < MIN_CONFIDENCE)) return ClaimView(Verdict.UNCLEAR, noClauseText, shown)
        return ClaimView(v, explanation, if (v == Verdict.NOT_FOUND) emptyList() else shown)
    }
}
