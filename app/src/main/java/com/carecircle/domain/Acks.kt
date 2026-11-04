package com.carecircle.domain

import java.time.LocalDate

/**
 * "Mark as handled": a family member tells the others (without saying who) that a missed dose has been dealt with.
 * A slot is a dose time, so two medicines due at the same minute share one acknowledgement.
 */
object Acks {
    const val KEEP_DAYS = 14L
    const val MAX_KEYS = 200
    private const val DAY_MS = 86_400_000L
    private val SLOT_ID = Regex("^[0-9]{10,14}$")
    private val KEY = Regex("^([0-9]{4}-[0-9]{2}-[0-9]{2})_([0-9]{10,14})$")

    /** The slot id the server and the push carry: the dose time in epoch milliseconds, digits only. */
    fun slotId(scheduledAt: Long): String = scheduledAt.toString()

    fun validSlotId(raw: String?): Boolean = raw != null && SLOT_ID.matches(raw)

    /** "2026-11-04_1793775600000": the UTC day of the dose, then the dose time. Stable on every phone, in any time zone or language. */
    fun slotKey(scheduledAt: Long): String = "${LocalDate.ofEpochDay(Math.floorDiv(scheduledAt, DAY_MS))}_$scheduledAt"

    fun slotKeyFromId(slotId: String): String? = slotId.takeIf { validSlotId(it) }?.toLongOrNull()?.let { slotKey(it) }

    fun validKey(key: String) = KEY.matches(key)

    /** The UTC day inside a key, or null for a malformed one. */
    fun dayOf(key: String): LocalDate? = KEY.matchEntire(key)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Every acknowledgement from every link of one person, once each, malformed ones dropped. */
    fun mergeAcks(lists: Collection<Collection<String>>): List<String> = lists.flatten().filter(::validKey).distinct().sorted()

    /** Forgets anything older than [KEEP_DAYS] days. */
    fun pruneOld(keys: Collection<String>, today: LocalDate): List<String> {
        val cutoff = today.minusDays(KEEP_DAYS)
        return keys.filter { k -> dayOf(k)?.let { !it.isBefore(cutoff) } ?: false }
    }

    /** What the parent writes into every link's status: merged, pruned, newest [MAX_KEYS] only. */
    fun handledSlots(keys: Collection<String>, today: LocalDate): List<String> = pruneOld(keys.distinct(), today).sorted().takeLast(MAX_KEYS)

    fun isHandled(handled: Collection<String>, scheduledAt: Long): Boolean = slotKey(scheduledAt) in handled
}

/** The parent phone's memory of acknowledgements per person, so a status write never loses ones it cannot re-read. */
object HandledCache {
    fun decode(raw: String): Map<Long, List<String>> = raw.lines().mapNotNull { l ->
        val i = l.indexOf('|'); if (i <= 0) null else l.substring(0, i).toLongOrNull()?.let { it to l.substring(i + 1) }
    }.filter { Acks.validKey(it.second) }.groupBy({ it.first }, { it.second })

    fun encode(map: Map<Long, Collection<String>>): String = map.entries.flatMap { (p, keys) -> keys.map { "$p|$it" } }.joinToString("\n")

    fun with(map: Map<Long, List<String>>, personId: Long, keys: Collection<String>, today: LocalDate): Map<Long, List<String>> =
        map + (personId to Acks.handledSlots(map[personId].orEmpty() + keys, today))
}
