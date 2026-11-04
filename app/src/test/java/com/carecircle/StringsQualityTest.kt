package com.carecircle

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Goes beyond "the key exists": the translations must be real text, the disclaimers must be present, nothing placeholder-looking ships. */
class StringsQualityTest {
    private fun load(dir: String): Map<String, String> {
        val f = File("src/main/res/$dir/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val out = linkedMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let { if (it.tagName == "string") out[it.getAttribute("name")] = it.textContent }
        return out
    }
    private val en = load("values")
    private val hi = load("values-hi")
    private val gu = load("values-gu")

    @Test fun mostHindiAndGujaratiStringsAreActuallyTranslated() {
        for ((lang, map) in listOf("hi" to hi, "gu" to gu)) {
            val long = en.filter { it.value.length > 12 && !it.value.contains("http") }
            val same = long.filter { (k, v) -> map[k] == v }.keys
            assertTrue("$lang: ${same.size} of ${long.size} long strings are still English: ${same.take(8)}", same.size <= long.size * 0.08)
        }
    }

    @Test fun hindiUsesDevanagariAndGujaratiUsesGujaratiScript() {
        fun share(s: Iterable<String>, from: Char, to: Char): Double {
            val letters = s.joinToString("").filter { it.isLetter() }; if (letters.isEmpty()) return 0.0
            return letters.count { it in from..to }.toDouble() / letters.length
        }
        assertTrue("hi should be mostly Devanagari", share(hi.values, 'ऀ', 'ॿ') > 0.6)
        assertTrue("gu should be mostly Gujarati", share(gu.values, '઀', '૿') > 0.6)
    }

    @Test fun theMedicalDisclaimerIsPresentInEveryLanguageAndSaysInformationalOnlyInEnglish() {
        assertTrue(en.getValue("disclaimer_medical").contains("Informational only", ignoreCase = true))
        for (m in listOf(hi, gu)) assertTrue(m.getValue("disclaimer_medical").length > 20)
    }

    @Test fun theInsuranceAndInteractionDisclaimersArePresentEverywhere() {
        for (key in listOf("disclaimer_insurance", "disclaimer_interactions")) for (m in listOf(en, hi, gu)) assertTrue("$key missing or short", (m[key] ?: "").length > 15)
        assertTrue(en.getValue("disclaimer_insurance").contains("Verify with your insurer", ignoreCase = true))
    }

    @Test fun noStringContainsPlaceholderText() {
        for ((lang, m) in listOf("en" to en, "hi" to hi, "gu" to gu)) for ((k, v) in m) {
            for (bad in listOf("TODO", "FIXME", "lorem", "xxx", "TBD")) assertFalse("$lang/$k contains $bad", v.contains(bad, ignoreCase = true))
            assertFalse("$lang/$k has leading or trailing spaces", v != v.trim())
        }
    }

    @Test fun noStringClaimsToDiagnoseOrPrescribe() {
        val forbidden = listOf("you have ", "you should take", "diagnos", "prescribe", "cure")
        for ((k, v) in en) if (!k.startsWith("disclaimer") && !k.startsWith("diag")) for (w in forbidden) assertFalse("en/$k says \"$w\": $v", v.contains(w, ignoreCase = true) && !v.contains("not ", ignoreCase = true))
    }

    @Test fun theBrandNameIsSpelledTheSameEverywhereItAppears() {
        for (m in listOf(en, hi, gu)) for ((k, v) in m) assertFalse("$k misspells the app name", Regex("care ?circl(?!e)", RegexOption.IGNORE_CASE).containsMatchIn(v) || v.contains("Care Circle"))
    }

    @Test fun everyPlaceholderStyleIsPositionalWhenThereIsMoreThanOne() {
        for ((lang, m) in listOf("en" to en, "hi" to hi, "gu" to gu)) for ((k, v) in m) {
            val plain = Regex("%[sd]").findAll(v).count()
            if (plain > 1) assertTrue("$lang/$k has several unnumbered placeholders: $v", false)
        }
    }
}
