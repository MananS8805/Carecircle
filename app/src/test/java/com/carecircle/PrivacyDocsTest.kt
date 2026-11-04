package com.carecircle

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The privacy policy must describe every endpoint the app can call, and the store text must fit its limits. */
class PrivacyDocsTest {
    private val module = File(System.getProperty("user.dir") ?: ".")
    private val root = if (File(module, "docs").exists()) module else module.parentFile

    private val api = File(root, "app/src/main/java/com/carecircle/data/Api.kt").readText()
    private val policy = File(root, "docs/privacy-policy.md").readText()

    @Test fun everyCareApiEndpointIsInThePrivacyPolicy() {
        val endpoints = Regex("""@(?:GET|POST)\("(api/[a-z-]+)"\)""").findAll(api).map { it.groupValues[1] }.toList()
        assertTrue("no endpoints found in Api.kt", endpoints.isNotEmpty())
        for (e in endpoints) assertTrue("$e is missing from docs/privacy-policy.md", policy.contains("/$e"))
    }

    @Test fun privacyScreenAndDataSafetyMentionEveryEndpoint() {
        val data = File(root, "docs/play-store/data-safety-answers.md").readText()
        assertTrue(data.contains("policy"))
        val strings = File(root, "app/src/main/res/values/strings.xml").readText()
        for (key in listOf("privacy_leave_cards", "privacy_leave_policy", "privacy_leave_question", "privacy_leave_sync", "privacy_leave_voice"))
            assertTrue("$key missing", strings.contains("name=\"$key\""))
    }

    @Test fun shortStoreDescriptionsAreAtMostEightyCharacters() {
        val lines = File(root, "docs/play-store/store-listing.md").readLines().filter { it.trimStart().startsWith("- Short") }
        assertTrue(lines.size >= 3)
        for (l in lines) {
            val text = l.substringAfter("):", "").trim()
            assertTrue("short description too long (${text.codePointCount(0, text.length)}): $text", text.codePointCount(0, text.length) in 1..80)
        }
    }
}
