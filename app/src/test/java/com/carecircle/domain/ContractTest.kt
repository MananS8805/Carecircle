package com.carecircle.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The app, the Firestore rules and the backend must agree on field names, or a write is silently refused on a real phone.
 * These tests read the actual sources and compare them, so a change on one side fails here instead of in the family's hands.
 */
class ContractTest {
    private val repo: File by lazy {
        var d = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(d, "app/src/main").exists() && d.parentFile != null) d = d.parentFile
        d
    }
    private fun read(path: String) = File(repo, path).readText()
    private val careSync by lazy { read("app/src/main/java/com/carecircle/data/CareSync.kt") }
    private val rules by lazy { read("firebase/firestore.rules") }

    /** The text of a function from "fun name(" to the matching closing brace. */
    private fun body(src: String, name: String): String {
        val start = src.indexOf("fun $name(")
        assertTrue("function $name not found", start >= 0)
        var i = src.indexOf('{', start)
        var depth = 0
        val from = i
        while (i < src.length) {
            when (src[i]) { '{' -> depth++; '}' -> { depth--; if (depth == 0) return src.substring(from, i + 1) } }
            i++
        }
        return src.substring(from)
    }

    private fun keysOfMap(text: String): Set<String> = Regex("\"(\\w+)\"\\s+to\\b").findAll(text).map { it.groupValues[1] }.toSet()

    /** The keys a rules block allows: the first hasOnly([...]) after [marker]. */
    private fun rulesKeys(marker: String): Set<String> {
        val at = rules.indexOf(marker)
        assertTrue("rules marker not found: $marker", at >= 0)
        val m = Regex("hasOnly\\(\\[([^\\]]*)\\]\\)").find(rules, at)!!
        return Regex("'(\\w+)'").findAll(m.groupValues[1]).map { it.groupValues[1] }.toSet()
    }

    @Test fun theStatusDocumentTheAppWritesIsExactlyWhatTheRulesAllow() {
        val sent = keysOfMap(body(careSync, "publishStatusLocked"))
        assertEquals(rulesKeys("match /status/{doc}"), sent)
    }

    @Test fun theEventDocumentTheAppWritesIsExactlyWhatTheRulesAllow() {
        val sent = keysOfMap(body(careSync, "publish"))
        assertEquals(rulesKeys("match /events/{eventId}"), sent)
    }

    @Test fun theNewPairingTheAppCreatesIsExactlyWhatTheRulesAllow() {
        val sent = keysOfMap(body(careSync, "createPairing"))
        assertEquals(rulesKeys("allow create: if signedIn()"), sent)
    }

    @Test fun theAcknowledgementTheAppWritesIsExactlyWhatTheRulesAllow() {
        assertEquals(rulesKeys("match /acks/{slotKey}"), keysOfMap(body(careSync, "writeAck")))
    }

    @Test fun theFamilyPhoneClaimAndTokenUpdatesMatchTheRules() {
        assertTrue(careSync.contains("mapOf(\"caregiverUid\" to me, \"linkedAt\" to"))
        assertTrue(rules.contains("hasOnly(['caregiverUid', 'linkedAt'])"))
        assertTrue(careSync.contains("\"caregiverFcmToken\" to token, \"tokenUpdatedAt\" to"))
        assertTrue(rules.contains("hasOnly(['caregiverFcmToken', 'tokenUpdatedAt'])"))
    }

    @Test fun everyEventTypeTheAppCanSendIsAcceptedByTheRules() {
        val allowed = Regex("type in \\[([^\\]]*)\\]").find(rules)!!.groupValues[1]
        val rulesTypes = Regex("'(\\w+)'").findAll(allowed).map { it.groupValues[1] }.toSet()
        assertEquals(SyncType.entries.map { it.key }.toSet(), rulesTypes)
    }

    @Test fun thePushRequestHasExactlyTheFieldsTheBackendAccepts() {
        val dto = read("app/src/main/java/com/carecircle/data/Api.kt")
        val line = dto.lines().first { it.contains("class NotifyMissedRequest") }
        val appFields = Regex("val (\\w+):").findAll(line).map { it.groupValues[1] }.toSet()
        val backend = read("backend/lib/notifyMissed.ts")
        val allowed = Regex("ALLOWED_KEYS = \\[([^\\]]*)\\]").find(backend)!!.groupValues[1]
        val backendFields = Regex("\"(\\w+)\"").findAll(allowed).map { it.groupValues[1] }.toSet()
        assertEquals(backendFields, appFields)
    }

    @Test fun theBackendEndpointsTheAppCallsExist() {
        val api = read("app/src/main/java/com/carecircle/data/Api.kt")
        val endpoints = Regex("@(?:GET|POST)\\(\"api/([a-z-]+)\"\\)").findAll(api).map { it.groupValues[1] }.toSet()
        assertTrue(endpoints.isNotEmpty())
        for (e in endpoints) assertTrue("backend/api/$e.ts is missing", File(repo, "backend/api/$e.ts").exists())
    }

    // ---- the rule that nothing which only reads may delete ----

    private val readOnlyNames = Regex("^(observe\\w*|fetch\\w*|read\\w*|adherence|publishStatus\\w*|linkState|describe\\w*|refreshStatuses|daysLeft|medicineCount)$")
    private val destructive = listOf("unlinkOwner", "unlinkAll", "unfollow(", "deleteLink", "deleteFollowing", "deleteAllData", "deletePerson", ".deleteFor", "DELETE FROM", "ref.delete()", ".delete().await")

    @Test fun noFunctionThatOnlyReadsContainsADeletingCall() {
        val dirs = listOf("app/src/main/java/com/carecircle/data", "app/src/main/java/com/carecircle/ui", "app/src/main/java/com/carecircle/reminder")
        var checked = 0
        for (dir in dirs) File(repo, dir).walkTopDown().filter { it.extension == "kt" && it.name != "Db.kt" }.forEach { f -> // Db.kt holds DAO interfaces: functions without bodies
            val text = f.readText()
            Regex("fun (?:<[^>]*> )?(?:[A-Za-z.]+\\.)?(\\w+)\\(").findAll(text).forEach { m ->
                val name = m.groupValues[1]
                if (!readOnlyNames.matches(name)) return@forEach
                // From this declaration to the next one: works for block bodies and for "= expression" bodies alike.
                val next = Regex("\\n\\s{0,8}(?:(?:private|internal|suspend|override|inline) )*fun ").find(text, m.range.last)?.range?.first ?: text.length
                val b = text.substring(m.range.first, next)
                checked++
                for (bad in destructive) assertFalse("${f.name}: $name() only reads but contains \"$bad\"", b.contains(bad))
            }
        }
        assertTrue("expected to check some read-only functions, checked $checked", checked >= 5)
    }

    @Test fun theFirebaseSignInIsStartedOnlyFromFamilyScreensNeverAtAppStart() {
        val callers = File(repo, "app/src/main/java").walkTopDown().filter { it.extension == "kt" && it.readText().contains("warmUp(") }.map { it.name }.toSet()
        assertEquals(setOf("CareSync.kt", "FamilyScreen.kt", "FamilyApp.kt"), callers)
    }

    @Test fun nothingSignsInToFirebaseFromTheApplicationClassOrTheMainActivity() {
        for (name in listOf("CareCircleApp.kt", "MainActivity.kt")) {
            val f = File(repo, "app/src/main/java").walkTopDown().firstOrNull { it.name == name } ?: continue
            val t = f.readText()
            assertFalse("$name must not sign in at start", t.contains("signInAnonymously") || t.contains("CareSync.warmUp"))
        }
    }

    // ---- release hygiene ----

    @Test fun theChangelogTopEntryMatchesTheAppVersion() {
        val props = read("gradle.properties")
        val name = Regex("carecircle.versionName=(\\S+)").find(props)!!.groupValues[1]
        val top = Regex("^## (\\d+\\.\\d+\\.\\d+)", RegexOption.MULTILINE).find(read("CHANGELOG.md"))!!.groupValues[1]
        assertEquals(name, top)
    }

    @Test fun theVersionCodeIsAPositiveNumberThatGrowsWithTheVersion() {
        val props = read("gradle.properties")
        val code = Regex("carecircle.versionCode=(\\d+)").find(props)!!.groupValues[1].toInt()
        val (maj, min, pat) = Regex("carecircle.versionName=(\\d+)\\.(\\d+)\\.(\\d+)").find(props)!!.destructured
        assertTrue(code >= 1)
        assertTrue("versionCode $code is too low for ${maj}.${min}.${pat}", code >= maj.toInt() * 10 + min.toInt())
    }

    @Test fun theLatestRoomSchemaFileExistsForTheDeclaredDatabaseVersion() {
        val db = read("app/src/main/java/com/carecircle/data/Db.kt")
        val version = Regex("version = (\\d+)").find(db)!!.groupValues[1].toInt()
        val dir = File(repo, "app/schemas").walkTopDown().filter { it.isDirectory && it.listFiles()?.any { f -> f.name.endsWith(".json") } == true }.first()
        val files = dir.listFiles()!!.map { it.name.removeSuffix(".json").toInt() }.sorted()
        assertEquals("a schema JSON for every version 1..$version", (1..version).toList(), files.filter { it in 1..version })
    }

    @Test fun everyManifestPermissionIsExplainedInThePlayStoreNotes() {
        val manifest = read("app/src/main/AndroidManifest.xml")
        val notes = read("docs/play-store/permissions-justification.md")
        val perms = Regex("uses-permission[^>]*android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertTrue(perms.isNotEmpty())
        for (p in perms) assertTrue("$p is not in permissions-justification.md", notes.contains(p.substringAfterLast('.')))
    }

    @Test fun noSecretLooksCommittedInTheSources() {
        val risky = listOf(Regex("AIza[0-9A-Za-z_-]{30,}"), Regex("-----BEGIN (RSA |EC )?PRIVATE KEY-----"), Regex("\"private_key\"\\s*:"))
        val roots = listOf("app/src", "backend/api", "backend/lib", "docs", "firebase")
        for (r in roots) File(repo, r).walkTopDown().filter { it.isFile && it.extension in setOf("kt", "ts", "md", "rules", "json", "xml", "js") && !it.path.contains("node_modules") }.forEach { f ->
            val t = f.readText()
            for (rx in risky) assertFalse("${f.path} looks like it holds a secret (${rx.pattern})", rx.containsMatchIn(t))
        }
    }

    @Test fun googleServicesAndKeystoresAreGitIgnored() {
        val ignore = read(".gitignore")
        for (s in listOf("google-services.json", "local.properties")) assertTrue("$s must be in .gitignore", ignore.contains(s))
        assertTrue(ignore.contains(".jks") || ignore.contains("*.keystore") || ignore.contains("keystore"))
    }
}
