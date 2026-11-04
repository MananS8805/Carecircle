package com.carecircle.domain

import java.time.LocalDate

/** Rules for the missed-dose push on the family phone. The visible notification is always built locally from these. */
object CaregiverAlert {
    const val MAX_LABEL = 20

    /** One alert per link, dose time and day, however many pushes arrive. */
    fun key(date: LocalDate, linkId: String, slotLabel: String): String = "$date|$linkId|${slotLabel.trim().take(MAX_LABEL)}"

    /** The label from the push, or null if it is missing, empty, too long or contains control characters. */
    fun sanitizeLabel(raw: String?): String? {
        val t = raw?.trim() ?: return null
        return t.takeIf { it.isNotEmpty() && it.length <= MAX_LABEL && t.none { c -> c.isISOControl() } }
    }

    fun shouldNotify(pushEnabled: Boolean, followedLinks: Set<String>, linkId: String, key: String, alerted: Set<String>): Boolean =
        pushEnabled && linkId in followedLinks && key !in alerted

    /** Adds [key] and forgets anything from before yesterday, so the list never grows. */
    fun remember(alerted: Set<String>, key: String, today: LocalDate): Set<String> {
        val cutoff = today.minusDays(1)
        return (alerted + key).filter { k -> runCatching { LocalDate.parse(k.substringBefore('|')) }.getOrNull()?.let { !it.isBefore(cutoff) } ?: false }.toSet()
    }

    fun encode(keys: Set<String>): String = keys.joinToString("\n")
    fun decode(raw: String): Set<String> = raw.lines().filter { it.isNotBlank() }.toSet()
}

/** A missed-dose push the parent phone still has to send. Held for at most [PendingNotifies.MAX_AGE_MS]. */
data class PendingNotify(val linkId: String, val slotId: String, val slotLabel: String, val createdAt: Long)

object PendingNotifies {
    const val MAX_AGE_MS = 2 * 3600_000L

    fun encode(list: List<PendingNotify>): String =
        list.joinToString("\n") { "${it.linkId}|${it.slotId}|${it.slotLabel.replace('|', ' ').replace('\n', ' ')}|${it.createdAt}" }

    fun decode(raw: String): List<PendingNotify> = raw.lines().mapNotNull { l ->
        val p = l.split("|")
        if (p.size != 4) null else p[3].toLongOrNull()?.let { PendingNotify(p[0], p[1], p[2], it) }
    }

    /** The same dose for the same link is held once. */
    fun add(list: List<PendingNotify>, e: PendingNotify): List<PendingNotify> =
        if (list.any { it.linkId == e.linkId && it.slotId == e.slotId }) list else list + e

    fun prune(list: List<PendingNotify>, now: Long): List<PendingNotify> = list.filter { now - it.createdAt <= MAX_AGE_MS }

    fun remove(list: List<PendingNotify>, e: PendingNotify): List<PendingNotify> = list.filterNot { it.linkId == e.linkId && it.slotId == e.slotId }

    fun forLinks(list: List<PendingNotify>, activeLinks: Set<String>): List<PendingNotify> = list.filter { it.linkId in activeLinks }
}
