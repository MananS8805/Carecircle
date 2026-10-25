package com.carecircle.domain

/** Decides whether a strip scan is good enough to fill the generic-name field. */
object ScanQuality {
    /** Below this average ML Kit element confidence the result is flagged for a careful check. */
    const val LOW_CONFIDENCE = 0.45f

    enum class Verdict { GOOD, CHECK, RETRY }

    /** No salt candidate means retry. A candidate with weak OCR confidence is accepted but must be double-checked. */
    fun assess(candidates: List<String>, avgConfidence: Float?): Verdict = when {
        candidates.isEmpty() -> Verdict.RETRY
        avgConfidence != null && avgConfidence < LOW_CONFIDENCE -> Verdict.CHECK
        else -> Verdict.GOOD
    }
}
