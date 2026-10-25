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
