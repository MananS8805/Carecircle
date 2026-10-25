package com.carecircle.domain

enum class CoverageKind { WAITING_SPECIFIC, PRE_EXISTING, EXCLUSION, INCLUSION }

/** One place where the saved policy mentions the medicine's condition. Always carries page, quote and (when printed) clause. */
data class CoverageMatch(val kind: CoverageKind, val text: String, val page: Int, val clause: String?, val quote: String)

/** Words a policy may use for each built-in condition. One object so it can be extended in one place. */
object ConditionSynonyms {
    private val map: Map<Condition, List<String>> = mapOf(
        Condition.DIABETES to listOf("diabetes", "diabetic", "diabetes mellitus", "dm", "type 2", "type ii", "type 1", "sugar"),
        Condition.BLOOD_PRESSURE to listOf("blood pressure", "hypertension", "hypertensive", "bp", "high blood pressure"),
        Condition.CHOLESTEROL to listOf("cholesterol", "lipid", "lipids", "dyslipidemia", "hyperlipidemia", "hypercholesterolemia"),
        Condition.THYROID to listOf("thyroid", "hypothyroidism", "hyperthyroidism", "goitre", "goiter"),
        Condition.HEART to listOf("heart", "cardiac", "coronary", "cardiovascular", "angina", "angioplasty", "bypass", "myocardial"),
        Condition.ASTHMA to listOf("asthma", "copd", "bronchial", "respiratory", "bronchitis"),
        Condition.ARTHRITIS to listOf("arthritis", "osteoarthritis", "rheumatoid", "joint replacement", "knee replacement", "joint"),
        Condition.KIDNEY to listOf("kidney", "renal", "dialysis", "nephritis", "calculi", "kidney stone"),
        Condition.BRAIN to listOf("brain", "neuro", "neurological", "stroke", "epilepsy", "parkinson", "paralysis"),
        Condition.INFECTION to listOf("infection", "infectious", "sepsis", "tuberculosis"),
        Condition.PAIN to listOf("pain", "migraine", "headache"),
    )

    /** Search words for a tag: the chip's synonyms, or the tag text itself for a custom tag. */
    fun termsFor(tag: String): List<String> {
        val t = Coverage.normalize(tag)
        if (t.isEmpty()) return emptyList()
        val condition = Conditions.fromTag(tag)
        return when {
            condition == null -> listOf(t)
            condition == Condition.OTHER -> emptyList()
            else -> (map[condition].orEmpty() + Coverage.normalize(condition.label)).distinct()
        }
    }
}

object Coverage {
    /** Lower case, punctuation to spaces, single spaces. "Type-2 Diabetes!" becomes "type 2 diabetes". */
    fun normalize(s: String): String =
        s.lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("").replace(Regex("\\s+"), " ").trim()

    private fun mentions(text: String, terms: List<String>): Boolean {
        val padded = " " + normalize(text) + " "
        return terms.any { padded.contains(" $it ") || padded.contains(" ${it}s ") }
    }

    private fun verified(page: Int, quote: String) = page > 0 && quote.isNotBlank()

    /**
     * What the saved policy text says about the medicine's condition. Never states that something is covered or not:
     * it only returns the policy's own lines (with page, clause and quote) that mention the condition. Entries without a
     * page and quote are ignored.
     */
    fun matchCoverage(conditionTag: String?, summary: PolicySummary?): List<CoverageMatch> {
        val tag = conditionTag?.trim().orEmpty()
        if (tag.isEmpty() || summary == null) return emptyList()
        val terms = ConditionSynonyms.termsFor(tag)
        if (terms.isEmpty()) return emptyList()
        val out = mutableListOf<CoverageMatch>()
        summary.waitingSpecific.forEach {
            if (verified(it.page, it.quote) && mentions("${it.illness} ${it.quote}", terms))
                out += CoverageMatch(CoverageKind.WAITING_SPECIFIC, "${it.illness}: ${it.period}", it.page, it.clause, it.quote)
        }
        summary.waitingPreExisting?.let {
            if (verified(it.page, it.quote) && mentions("${it.value} ${it.quote}", terms))
                out += CoverageMatch(CoverageKind.PRE_EXISTING, it.value, it.page, it.clause, it.quote)
        }
        summary.exclusions.forEach {
            if (verified(it.page, it.quote) && mentions("${it.value} ${it.quote}", terms))
                out += CoverageMatch(CoverageKind.EXCLUSION, it.value, it.page, it.clause, it.quote)
        }
        summary.inclusions.forEach {
            if (verified(it.page, it.quote) && mentions("${it.value} ${it.quote}", terms))
                out += CoverageMatch(CoverageKind.INCLUSION, it.value, it.page, it.clause, it.quote)
        }
        return out
    }

    /** Suggests a built-in condition from the info card text ("why you take it"), only when a known chip clearly matches. */
    fun suggestFromText(text: String): Condition? {
        if (text.isBlank()) return null
        return Condition.entries.filter { it != Condition.OTHER }.firstOrNull { mentions(text, ConditionSynonyms.termsFor(it.label)) }
    }
}

/** Resolves what a medicine is for: the user's tag first, then a guess from the name. */
fun Conditions.resolve(tag: String?, name: String, salt: String): Condition? =
    fromTag(tag) ?: if (tag.isNullOrBlank()) infer(name, salt) else Condition.OTHER
