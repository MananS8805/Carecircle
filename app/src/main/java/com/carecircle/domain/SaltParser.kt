package com.carecircle.domain

/**
 * Extracts candidate generic names from OCR text of an Indian medicine strip, e.g.
 * "Each film coated tablet contains: Paracetamol IP 500 mg" -> ["paracetamol"].
 * The backend re-normalizes, so this only needs to be good enough to pre-fill the field for user confirmation.
 */
object SaltParser {
    private val strength = Regex("""([A-Za-z][A-Za-z\- ]{2,40}?)\s*(?:I\.?P\.?|B\.?P\.?|U\.?S\.?P\.?)?\s*\.?\s*(\d+(?:[.,]\d+)?)\s*(mg|mcg|µg|g|ml|iu)\b""", RegexOption.IGNORE_CASE)
    private val stop = setOf(
        "mfd", "exp", "batch", "price", "mrp", "store", "keep", "schedule", "each", "contains", "tablet", "tablets",
        "capsule", "capsules", "film", "coated", "net", "strip", "of", "per", "and", "the", "date", "manufactured", "marketed",
    )

    fun parse(ocr: String): List<String> {
        val out = linkedSetOf<String>()
        for (line in ocr.lines()) {
            for (m in strength.findAll(line)) {
                val words = m.groupValues[1]
                    .replace(Regex("""\b(contains|each|film|coated|tablets?|capsules?)\b""", RegexOption.IGNORE_CASE), " ")
                    .trim().split(Regex("""\s+""")).filter { it.length > 1 && it.lowercase() !in stop }
                if (words.isEmpty()) continue
                out += words.joinToString(" ").lowercase()
            }
        }
        return out.toList()
    }
}
