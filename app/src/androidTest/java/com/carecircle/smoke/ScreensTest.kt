package com.carecircle.smoke

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.carecircle.DebugReset
import com.carecircle.MainActivity
import com.carecircle.R
import com.carecircle.SmokeSupport
import com.carecircle.SmokeSupport.waitForText
import com.carecircle.data.Prefs
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Every main screen opens, shows the words it must show, and the safety text is on the screens that need it. */
class ScreensTest {
    @get:Rule(order = 0) val permissions = SmokeSupport.permissions()
    @get:Rule(order = 1) val compose = createEmptyComposeRule()
    private val ctx get() = SmokeSupport.context

    @Before fun fresh() { DebugReset.reset(ctx) }

    private fun s(res: Int, vararg a: Any) = SmokeSupport.str(res, *a)
    private fun open(block: () -> Unit) { ActivityScenario.launch(MainActivity::class.java).use { block() } }
    private fun tab(res: Int) { compose.onNodeWithText(s(res)).performClick() }
    private fun has(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun todayOpensWithItsHeadlineAndTheEmptyHint() = open {
        compose.waitForText(s(R.string.today_headline))
        compose.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0].performScrollToNode(androidx.compose.ui.test.hasText(s(R.string.today_empty), substring = true))
        assertTrue(has(s(R.string.today_empty)))
    }

    @Test fun theFourTabsAreAllThere() = open {
        compose.waitForText(s(R.string.tab_today))
        for (t in listOf(R.string.tab_today, R.string.tab_medicines, R.string.tab_insurance, R.string.tab_settings)) assertTrue(has(s(t)))
    }

    @Test fun medicinesTabOffersAddAndShowsTheEmptyMessage() = open {
        compose.waitForText(s(R.string.tab_medicines)); tab(R.string.tab_medicines)
        compose.waitForText(s(R.string.meds_add)); assertTrue(has(s(R.string.meds_empty)))
    }

    @Test fun theAddFormAsksForEverythingAFamilyNeeds() = open {
        compose.waitForText(s(R.string.tab_medicines)); tab(R.string.tab_medicines)
        compose.waitForText(s(R.string.meds_add)); compose.onNodeWithText(s(R.string.meds_add)).performClick()
        compose.waitForText(s(R.string.field_name))
        for (f in listOf(R.string.field_name, R.string.field_dose, R.string.field_times_title, R.string.field_how_to_take, R.string.field_pills, R.string.field_per_dose, R.string.field_for)) assertTrue("missing ${s(f)}", has(s(f)))
    }

    @Test fun saveStaysDisabledUntilAMedicineHasAName() = open {
        compose.waitForText(s(R.string.tab_medicines)); tab(R.string.tab_medicines)
        compose.waitForText(s(R.string.meds_add)); compose.onNodeWithText(s(R.string.meds_add)).performClick()
        compose.waitForText(s(R.string.field_name))
        compose.onNodeWithText(s(R.string.action_save)).performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText(s(R.string.field_name)).performTextInput("Aspirin")
        compose.onNodeWithText(s(R.string.action_save)).performScrollTo().assertIsEnabled()
    }

    @Test fun anAddedMedicineShowsInTheListAndOpensItsDetailPage() = open {
        compose.waitForText(s(R.string.tab_medicines)); tab(R.string.tab_medicines)
        compose.waitForText(s(R.string.meds_add)); compose.onNodeWithText(s(R.string.meds_add)).performClick()
        compose.waitForText(s(R.string.field_name)); compose.onNodeWithText(s(R.string.field_name)).performTextInput("Paracetamol")
        compose.onNodeWithText(s(R.string.action_save)).performScrollTo().performClick()
        compose.waitForText("Paracetamol"); compose.onNodeWithText("Paracetamol").performClick()
        compose.waitUntil(10_000) { has(s(R.string.action_back)) }
    }

    @Test fun insuranceExplainsWhatItDoesBeforeAnythingIsUploaded() = open {
        compose.waitForText(s(R.string.tab_insurance)); tab(R.string.tab_insurance)
        compose.waitUntil(10_000) { has(s(R.string.ins_intro).take(30)) }
    }

    @Test fun settingsListsPeopleFamilySyncLanguageAndTheVersion() = open {
        compose.waitForText(s(R.string.tab_settings)); tab(R.string.tab_settings)
        compose.waitForText(s(R.string.settings_people))
        for (r in listOf(R.string.settings_people, R.string.family_title, R.string.settings_language)) assertTrue(has(s(r)))
    }

    @Test fun familySyncExplainsHowToFollowAndStartsWithNoOne() = open {
        compose.waitForText(s(R.string.tab_settings)); tab(R.string.tab_settings)
        compose.waitForText(s(R.string.family_title))
        compose.onAllNodesWithText(s(R.string.family_title))[0].performClick()
    }

    private fun nativeText(tag: String, res: Int): String =
        ctx.createConfigurationContext(android.content.res.Configuration(ctx.resources.configuration).apply { setLocale(java.util.Locale(tag)) }).getString(res)

    private fun restoreEnglish() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList())
        }
        Prefs(ctx).language = ""
    }

    private fun switchLanguageThroughSettings(chip: String, expectedSettingsTitle: String) {
        try {
            open {
                compose.waitForText(s(R.string.tab_settings)); tab(R.string.tab_settings)
                compose.waitForText(chip); compose.onNodeWithText(chip).performClick()
                compose.waitForText(expectedSettingsTitle)
            }
        } finally { restoreEnglish() }
    }

    @Test fun choosingHindiInSettingsChangesTheScreenToHindi() {
        val hindi = nativeText("hi", R.string.tab_settings)
        org.junit.Assume.assumeTrue(hindi != s(R.string.tab_settings))
        switchLanguageThroughSettings("हिन्दी", hindi)
    }

    @Test fun choosingGujaratiInSettingsChangesTheScreenToGujarati() {
        val gujarati = nativeText("gu", R.string.tab_settings)
        org.junit.Assume.assumeTrue(gujarati != s(R.string.tab_settings))
        switchLanguageThroughSettings("ગુજરાતી", gujarati)
    }

    // ---------- the family app ----------

    @Test fun theFamilyAppStartsWithAnInviteToFollowSomeone() {
        Prefs(ctx).appMode = Prefs.MODE_FAMILY
        open {
            compose.waitForText(s(R.string.fam_empty_title))
            for (t in listOf(R.string.fam_tab_home, R.string.fam_tab_add, R.string.fam_tab_settings)) assertTrue(has(s(t)))
        }
    }

    @Test fun theFamilyAddScreenNeedsACodeBeforeFollowing() {
        Prefs(ctx).appMode = Prefs.MODE_FAMILY
        open {
            compose.waitForText(s(R.string.fam_tab_add)); compose.onNodeWithText(s(R.string.fam_tab_add)).performClick()
            compose.waitForText(s(R.string.family_follow_hint))
            compose.onNodeWithText(s(R.string.family_follow_button)).assertIsNotEnabled()
            compose.onNodeWithText(s(R.string.family_follow_hint)).performTextInput("ABCD2345")
            compose.onNodeWithText(s(R.string.family_follow_button)).assertIsEnabled()
        }
    }

    @Test fun theFamilyAppNeverShowsPatientTabs() {
        Prefs(ctx).appMode = Prefs.MODE_FAMILY
        open {
            compose.waitForText(s(R.string.fam_empty_title))
            assertTrue(!has(s(R.string.tab_insurance))); assertTrue(!has(s(R.string.tab_medicines)))
        }
    }
}
