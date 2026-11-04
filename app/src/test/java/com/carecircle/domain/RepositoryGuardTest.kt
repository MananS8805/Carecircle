package com.carecircle.domain

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard. Two lines that stop sharing a person and cancel their appointment alarms once sat inside
 * Repository.adherence(), which every status update calls. Each dose sync therefore ended the family link.
 * A read-only function must never contain a destructive call, so this reads the source and checks.
 */
class RepositoryGuardTest {
    private val source: String by lazy {
        val module = File(System.getProperty("user.dir") ?: ".")
        val root = if (File(module, "src/main").exists()) File(module, "src/main") else File(module, "app/src/main")
        File(root, "java/com/carecircle/data/Repository.kt").readText()
    }

    private fun body(name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue("$name not found", start >= 0)
        val next = Regex("\\n    (suspend )?fun ").find(source, start + 10)?.range?.first ?: source.length
        return source.substring(start, next)
    }

    @Test fun adherenceNeverEndsALinkOrCancelsAnAlarm() {
        val b = body("adherence")
        for (bad in listOf("unlinkOwner", "unlinkAll", "unfollow", "cancelAppointment", "ReminderScheduler.cancel", "deleteLink")) {
            assertFalse("adherence() must be read-only but contains $bad", b.contains(bad))
        }
    }

    @Test fun deletingAPersonStopsSharingThemAndCancelsTheirAppointments() {
        val b = body("deletePerson")
        assertTrue(b.contains("unlinkOwner"))
        assertTrue(b.contains("cancelAppointment"))
    }
}
