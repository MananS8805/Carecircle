package com.carecircle.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.carecircle.ui.theme.BigButton
import com.carecircle.ui.theme.CareCircleTheme
import com.carecircle.ui.theme.SoftButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Cheap semantics checks for the conventions in CLAUDE.md. Run on a device: `./gradlew :app:connectedDebugAndroidTest`. */
class AccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun doseRowReadsAsOneSentenceAndIsALiveRegion() {
        rule.setContent {
            CareCircleTheme {
                Column(Modifier.doseRowSemantics("Metformin 500 mg, 8:00 AM, not yet taken")) {
                    Text("8:00 AM"); Text("Metformin"); Text("500 mg")
                }
            }
        }
        val node = rule.onNodeWithContentDescription("Metformin 500 mg, 8:00 AM, not yet taken")
        node.assertExists()
        assertEquals(LiveRegionMode.Polite, node.fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
    }

    @Test fun takenButtonIsAtLeast56dpHighAndClickable() {
        var taken = 0
        rule.setContent { CareCircleTheme { BigButton("Taken", { taken++ }) } }
        rule.onNodeWithText("Taken").assertHeightIsAtLeast(56.dp).performClick()
        assertEquals(1, taken)
    }

    @Test fun secondaryButtonsAreAtLeast48dp() {
        rule.setContent { CareCircleTheme { SoftButton("Skip", {}) } }
        rule.onNodeWithText("Skip").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }
}
