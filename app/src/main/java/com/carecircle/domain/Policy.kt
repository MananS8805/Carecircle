package com.carecircle.domain

import kotlinx.serialization.Serializable

/** One fact from a policy. It is only ever shown with its page, clause and the quote it was read from. */
@Serializable
data class PolicyItem(val value: String, val page: Int, val clause: String? = null, val quote: String)

@Serializable
data class PolicyWait(val illness: String, val period: String, val page: Int, val clause: String? = null, val quote: String)

@Serializable
data class PolicySummary(
    val sumInsured: PolicyItem? = null,
    val roomRentCap: PolicyItem? = null,
    val coPay: PolicyItem? = null,
    val waitingInitial: PolicyItem? = null,
    val waitingPreExisting: PolicyItem? = null,
    val waitingSpecific: List<PolicyWait> = emptyList(),
    val inclusions: List<PolicyItem> = emptyList(),
    val exclusions: List<PolicyItem> = emptyList(),
    val dayCare: PolicyItem? = null,
    val preHospitalisation: PolicyItem? = null,
    val postHospitalisation: PolicyItem? = null,
    val claimHelpline: PolicyItem? = null,
    val tpa: PolicyItem? = null,
) {
    val isEmpty: Boolean
        get() = listOf(sumInsured, roomRentCap, coPay, waitingInitial, waitingPreExisting, dayCare, preHospitalisation, postHospitalisation, claimHelpline, tpa)
            .all { it == null } && waitingSpecific.isEmpty() && inclusions.isEmpty() && exclusions.isEmpty()
}

/** A summary as saved on the phone: the summary only, never the source document or its text. */
@Serializable
data class SavedPolicy(val summary: PolicySummary, val unverified: List<String> = emptyList(), val mode: String = "", val savedAt: Long = 0)

/** What the user sees as the citation for an item: "Page 3, clause 4.2" (clause omitted when not printed). */
data class Citation(val page: Int, val clause: String?)

object PolicyLimits {
    const val MAX_PAGES = 40
    /** Below this many characters in total the document is treated as unreadable and nothing is sent. */
    const val MIN_TOTAL_CHARS = 200
    const val LONGEST_SIDE_PX = 1800

    fun hasEnoughText(pageTexts: List<String>): Boolean = pageTexts.sumOf { it.trim().length } >= MIN_TOTAL_CHARS

    /** Whether more pages exist than will be used. */
    fun isTruncated(totalPages: Int) = totalPages > MAX_PAGES

    /** Scale so the longest side becomes [LONGEST_SIDE_PX]; never upscale a small page by more than 3x. */
    fun renderScale(widthPt: Int, heightPt: Int): Float {
        val longest = maxOf(widthPt, heightPt).coerceAtLeast(1)
        return (LONGEST_SIDE_PX.toFloat() / longest).coerceAtMost(3f)
    }
}
