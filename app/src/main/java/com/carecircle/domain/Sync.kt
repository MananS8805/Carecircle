package com.carecircle.domain

import java.time.LocalDate

/** What the family can see. Names and times only: no notes, insurance data or contacts. */
enum class SyncType(val key: String) {
    TAKEN("taken"), MISSED("missed"), SKIPPED("skipped"), SUMMARY("summary");

    companion object {
        fun fromKey(k: String?): SyncType? = entries.firstOrNull { it.key == k }
    }
}

data class SyncEvent(
    val type: SyncType,
    val personName: String,
    val medicine: String = "",
    val scheduledAt: Long = 0,
    val at: Long,
    /** For [SyncType.SUMMARY]: doses taken and doses scheduled today. */
    val taken: Int = 0,
    val total: Int = 0,
) {
    /** Stable, so sending the same event twice (a retry, a re-run worker) never creates a duplicate. */
    fun id(doseId: Long = 0, day: LocalDate? = null): String = when (type) {
        SyncType.SUMMARY -> "summary-${day ?: LocalDate.ofEpochDay(at / 86_400_000L)}"
        else -> "${type.key}-$doseId"
    }
}

/** Short code a patient shows to a family member. No look-alike characters (0/O, 1/I/L). */
object PairingCode {
    const val LENGTH = 8
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val VALID_MINUTES = 15L

    fun generate(random: java.util.Random = java.security.SecureRandom()): String =
        buildString { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** What the user typed to what is stored: upper case, spaces and dashes removed. */
    fun normalize(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    /** Groups for reading aloud: ABCD-2345. */
    fun display(code: String): String = if (code.length == LENGTH) code.substring(0, 4) + "-" + code.substring(4) else code
}

/**
 * What goes into the shared fields. By default no person name and no medicine name leave the phone: the "medicine" field of an
 * event then carries only the time of the dose. The user can switch names on, with a separate switch.
 */
object SyncPrivacy {
    fun personField(name: String, shareNames: Boolean): String = if (shareNames) name.trim().take(40) else ""
    fun medicineField(label: String, timeLabel: String, shareNames: Boolean): String = (if (shareNames) label else timeLabel).take(80)
    fun nextMedicineField(name: String, shareNames: Boolean): String = if (shareNames) name.take(80) else ""
}

/** Five wrong codes in a row lock code entry for 15 minutes on this phone. Pure, so the rule is tested. */
object CodeLockout {
    const val MAX_FAILS = 5
    const val LOCK_MS = 15 * 60_000L

    data class State(val fails: Int = 0, val lockedUntil: Long = 0)

    fun isLocked(s: State, now: Long) = now < s.lockedUntil

    /** After a wrong code. The fifth one starts the lock and resets the count. */
    fun afterFailure(s: State, now: Long): State {
        val fails = (if (now >= s.lockedUntil && s.lockedUntil != 0L) 0 else s.fails) + 1
        return if (fails >= MAX_FAILS) State(0, now + LOCK_MS) else State(fails, 0)
    }

    fun afterSuccess() = State()

    fun minutesLeft(s: State, now: Long): Int = (((s.lockedUntil - now).coerceAtLeast(0) + 59_999) / 60_000).toInt()
}
