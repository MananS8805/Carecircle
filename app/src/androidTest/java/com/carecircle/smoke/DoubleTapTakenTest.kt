package com.carecircle.smoke

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.carecircle.DebugReset
import com.carecircle.MainActivity
import com.carecircle.R
import com.carecircle.SmokeSupport
import com.carecircle.SmokeSupport.waitForText
import com.carecircle.reminder.DoseActions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** A second tap, a second notification action or the widget must never use up a second pill. */
class DoubleTapTakenTest {
    @get:Rule(order = 0) val permissions = SmokeSupport.permissions()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Test fun twoQuickTapsCountOnce() {
        assumeTrue(SmokeSupport.safeToUseDueTime())
        DebugReset.reset(SmokeSupport.context)
        val id = SmokeSupport.insertDueMedicine(pills = 10, perDose = 2)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText("SmokeTest Med")
            val taken = SmokeSupport.str(R.string.cd_taken_for, "SmokeTest Med")
            SmokeSupport.scrollToTaken(compose)
            compose.onAllNodesWithContentDescription(taken)[0].performClick()
            runCatching { compose.onAllNodesWithContentDescription(taken)[0].performClick() } // the button may already be gone
            compose.waitUntil(10_000) { SmokeSupport.doses().any { it.takenAt != null } }
        }
        assertEquals(8, SmokeSupport.medicine(id).pillCount)
    }

    @Test fun markingTheSameDoseTakenAgainFromAnotherPathIsIgnored() {
        assumeTrue(SmokeSupport.safeToUseDueTime())
        DebugReset.reset(SmokeSupport.context)
        val id = SmokeSupport.insertDueMedicine(pills = 10, perDose = 1)
        val ctx = SmokeSupport.context
        val db = com.carecircle.data.AppDb.get(ctx)
        runBlocking {
            val med = db.medicines().get(id)!!
            val at = java.time.LocalDate.now().atTime(java.time.LocalTime.parse(med.timesCsv)).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            db.doses().insert(com.carecircle.data.DoseEvent(medicineId = id, scheduledAt = at))
            val doseId = db.doses().find(id, at)!!.id
            DoseActions.markTaken(ctx, doseId) // what the notification button and the widget call
            DoseActions.markTaken(ctx, doseId)
            DoseActions.markTaken(ctx, doseId)
        }
        assertEquals(9, SmokeSupport.medicine(id).pillCount)
    }
}
