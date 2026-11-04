package com.carecircle.smoke

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import com.carecircle.DebugReset
import com.carecircle.MainActivity
import com.carecircle.R
import com.carecircle.SmokeSupport
import com.carecircle.SmokeSupport.waitForText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class AddMedicineAndTakenTest {
    @get:Rule(order = 0) val permissions = SmokeSupport.permissions()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Test fun aMedicineAddedByHandAppearsInTheList() {
        DebugReset.reset(SmokeSupport.context)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText(SmokeSupport.str(R.string.tab_medicines))
            compose.onNodeWithText(SmokeSupport.str(R.string.tab_medicines)).performClick()
            compose.waitForText(SmokeSupport.str(R.string.meds_add))
            compose.onNodeWithText(SmokeSupport.str(R.string.meds_add)).performClick()
            compose.waitForText(SmokeSupport.str(R.string.field_name))
            compose.onNodeWithText(SmokeSupport.str(R.string.field_name)).performTextInput("Smoke Tablet")
            compose.onNodeWithText(SmokeSupport.str(R.string.action_save)).performClick()
            compose.waitForText("Smoke Tablet")
        }
    }

    @Test fun tappingTakenMarksTheDoseAndUsesOnePill() {
        assumeTrue(SmokeSupport.safeToUseDueTime())
        DebugReset.reset(SmokeSupport.context)
        val id = SmokeSupport.insertDueMedicine(pills = 10, perDose = 1)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText("SmokeTest Med")
            compose.onNodeWithText(SmokeSupport.str(R.string.action_taken)).performClick()
            compose.waitUntil(10_000) { SmokeSupport.doses().any { it.takenAt != null } }
            // The status pill now reads Taken and the button is gone.
            compose.waitUntil(10_000) { compose.onAllNodesWithText(SmokeSupport.str(R.string.status_taken)).fetchSemanticsNodes().isNotEmpty() }
        }
        assertNotNull(SmokeSupport.doses().single().takenAt)
        assertEquals(9, SmokeSupport.medicine(id).pillCount)
    }
}
