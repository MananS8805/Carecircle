package com.carecircle.smoke

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import com.carecircle.DebugReset
import com.carecircle.MainActivity
import com.carecircle.R
import com.carecircle.SmokeSupport
import com.carecircle.SmokeSupport.waitForText
import com.carecircle.data.Prefs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingSkipTest {
    @get:Rule(order = 0) val permissions = SmokeSupport.permissions()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()

    @Test fun aFreshInstallShowsTheGuideAndSkipLandsOnToday() {
        DebugReset.reset(SmokeSupport.context, guideDone = false)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText(SmokeSupport.str(R.string.guide_privacy_title))
            compose.onNodeWithText(SmokeSupport.str(R.string.guide_skip_all)).performClick()
            compose.waitForText(SmokeSupport.str(R.string.today_headline))
        }
        assertTrue(Prefs(SmokeSupport.context).guideDone)
    }

    @Test fun anExistingUserSkipsTheGuideAndSeesTodayDirectly() {
        DebugReset.reset(SmokeSupport.context, guideDone = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.waitForText(SmokeSupport.str(R.string.today_headline))
        }
    }
}
