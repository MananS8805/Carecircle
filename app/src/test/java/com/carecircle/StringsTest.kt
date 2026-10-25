package com.carecircle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the real string resources: every key exists in English, Hindi and Gujarati with the same placeholders,
 * and the message templates are non-empty and placeholder-complete in each language.
 */
class StringsTest {
    private fun load(dir: String): Map<String, String> {
        val f = File("src/main/res/$dir/strings.xml")
        assertTrue("missing ${f.path}", f.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        val out = linkedMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as? Element ?: continue
            when (n.tagName) {
                "string" -> out[n.getAttribute("name")] = n.textContent
                "plurals" -> {
                    val items = n.getElementsByTagName("item")
                    for (j in 0 until items.length) {
                        val it = items.item(j) as Element
                        out[n.getAttribute("name") + "#" + it.getAttribute("quantity")] = it.textContent
                    }
                }
            }
        }
        return out
    }

    private val en = load("values")
    private val hi = load("values-hi")
    private val gu = load("values-gu")
    private val placeholder = Regex("""%(\d+\$)?[sd]""")

    private fun placeholders(s: String) = placeholder.findAll(s).map { it.value }.sorted().toList()

    @Test fun everyEnglishKeyIsTranslated() {
        for ((lang, map) in listOf("hi" to hi, "gu" to gu)) {
            val missing = en.keys.filter { it !in map }
            assertTrue("$lang is missing: $missing", missing.isEmpty())
            val extra = map.keys.filter { it !in en }
            assertTrue("$lang has keys English lacks: $extra", extra.isEmpty())
        }
    }

    @Test fun placeholdersMatchEnglish() {
        for ((lang, map) in listOf("hi" to hi, "gu" to gu)) {
            for ((k, v) in en) {
                val t = map[k] ?: continue
                if (k.contains('#')) continue // plural items differ in how they count (one vs other)
                assertEquals("$lang placeholders for $k", placeholders(v), placeholders(t))
            }
        }
    }

    @Test fun messageTemplatesAreCompleteInEveryLanguage() {
        for ((lang, map) in listOf("en" to en, "hi" to hi, "gu" to gu)) {
            val missed = map.getValue("msg_missed")
            val reorder = map.getValue("msg_reorder")
            assertTrue("$lang missed", missed.isNotBlank() && listOf("%1\$s", "%2\$s", "%3\$s").all { missed.contains(it) })
            assertTrue("$lang reorder", reorder.isNotBlank() && listOf("%1\$s", "%2\$s", "%3\$d").all { reorder.contains(it) })
            assertTrue("$lang disclaimer", map.getValue("disclaimer_medical").isNotBlank() && map.getValue("disclaimer_insurance").isNotBlank())
        }
    }

    @Test fun noEmptyTranslations() {
        for ((lang, map) in listOf("hi" to hi, "gu" to gu)) {
            val empty = map.filterValues { it.isBlank() }.keys
            assertTrue("$lang has empty strings: $empty", empty.isEmpty())
        }
    }
}
