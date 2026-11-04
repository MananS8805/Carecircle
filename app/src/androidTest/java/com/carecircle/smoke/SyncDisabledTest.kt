package com.carecircle.smoke

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import com.carecircle.DebugReset
import com.carecircle.MainActivity
import com.carecircle.R
import com.carecircle.SmokeSupport
import com.carecircle.SmokeSupport.waitForText
import com.carecircle.data.CareSync
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/** A build without google-services.json (CI, forks) must open Family sync and say it is not set up, not crash. */
class SyncDisabledTest {
    @get:Rule(order = 0) val permissions = SmokeSupport.permissions()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Test fun familySyncExplainsItIsNotSetUp() {
        assumeFalse("this build has Firebase configured", CareSync.isConfigured(SmokeSupport.context))
        DebugReset.reset(SmokeSupport.context)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText(SmokeSupport.str(R.string.tab_settings))
            compose.onNodeWithText(SmokeSupport.str(R.string.tab_settings)).performClick()
            compose.waitForText(SmokeSupport.str(R.string.family_open))
            compose.onNodeWithText(SmokeSupport.str(R.string.family_open)).performScrollTo().performClick()
            compose.waitForText(SmokeSupport.str(R.string.family_not_configured))
        }
    }
}
