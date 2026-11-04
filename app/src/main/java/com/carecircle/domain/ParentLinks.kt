package com.carecircle.domain

enum class LinkStatus { PENDING, ACTIVE, REVOKED, UNKNOWN }

/** One family member who can see a person's status. [label] is local to this phone and is never uploaded. */
data class ParentLink(val linkId: String, val personId: Long, val label: String, val status: LinkStatus, val createdAt: Long)

enum class LabelCheck { OK, EMPTY, TOO_LONG, DUPLICATE }

/** Rules for sharing one person with up to three family members. A person's links are independent of each other. */
object ParentLinks {
    const val MAX = 3
    const val MAX_LABEL = 20
    /** The server drops an unclaimed code after this long, so a pending link older than it is dead. */
    const val PENDING_TTL_MS = 20 * 60_000L

    /** Pending links count, because a code in someone's hands could still be used. Ended links do not. */
    fun countsTowardLimit(l: ParentLink) = l.status != LinkStatus.REVOKED

    fun canAddLink(links: List<ParentLink>, personId: Long): Boolean =
        links.count { it.personId == personId && countsTowardLimit(it) } < MAX

    fun pruneExpired(links: List<ParentLink>, now: Long): List<ParentLink> =
        links.filterNot { it.status == LinkStatus.PENDING && now - it.createdAt > PENDING_TTL_MS }

    fun activeLinks(links: List<ParentLink>): List<ParentLink> = links.filter { it.status == LinkStatus.ACTIVE }

    /** The smallest number 1..MAX whose rendered default name ("Caregiver 2") is not taken yet, ignoring case. */
    fun nextDefaultNumber(existingLabels: Collection<String>, render: (Int) -> String): Int {
        val taken = existingLabels.map { it.trim().lowercase() }.toSet()
        return (1..MAX).firstOrNull { render(it).lowercase() !in taken } ?: (existingLabels.size + 1)
    }

    /** [others] are the labels of the person's other links. Case-insensitive uniqueness, 1 to 20 characters after trimming. */
    fun checkLabel(raw: String, others: Collection<String>): LabelCheck {
        val t = raw.trim()
        return when {
            t.isEmpty() -> LabelCheck.EMPTY
            t.length > MAX_LABEL -> LabelCheck.TOO_LONG
            others.any { it.trim().equals(t, ignoreCase = true) } -> LabelCheck.DUPLICATE
            else -> LabelCheck.OK
        }
    }
}
