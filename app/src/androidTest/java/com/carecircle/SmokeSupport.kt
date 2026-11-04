package com.carecircle

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.carecircle.data.AppDb
import com.carecircle.data.DoseEvent
import com.carecircle.data.Medicine
import kotlinx.coroutines.runBlocking
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Shared helpers for the emulator smoke tests. They never sleep: they wait for a condition with a limit. */
object SmokeSupport {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Notifications allowed, so no permission dialog covers the app. A no-op before Android 13. */
    fun permissions(): GrantPermissionRule =
        if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    fun str(res: Int, vararg args: Any): String = context.getString(res, *args)

    /** True from five minutes past midnight, so "one minute ago" is still today. Tests assume this and skip otherwise. */
    fun safeToUseDueTime(): Boolean = LocalTime.now().isAfter(LocalTime.of(0, 5))

    /** A medicine whose only dose was one minute ago, so Today shows it as due with a Taken button. Returns its id. */
    fun insertDueMedicine(name: String = "SmokeTest Med", pills: Int = 10, perDose: Int = 1): Long = runBlocking {
        val db = AppDb.get(context)
        val time = LocalTime.now().minusMinutes(1).withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("HH:mm"))
        val personId = db.people().all().first().id
        db.medicines().insert(Medicine(name = name, doseText = "100 mg", timesCsv = time, pillCount = pills, pillsPerDose = perDose, personId = personId, createdAt = 1L))
    }

    /** The Today list is lazy: the dose card is below the organ view, so scroll the main list to the Taken button first. */
    fun scrollToTaken(rule: ComposeTestRule, name: String = "SmokeTest Med") {
        rule.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0]
            .performScrollToNode(androidx.compose.ui.test.hasContentDescription(str(R.string.cd_taken_for, name)))
    }

    fun medicine(id: Long): Medicine = runBlocking { AppDb.get(context).medicines().get(id)!! }
    fun doses(): List<DoseEvent> = runBlocking { AppDb.get(context).doses().since(0) }

    fun ComposeTestRule.waitForText(text: String, timeoutMs: Long = 10_000) =
        waitUntil(timeoutMs) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    fun ComposeTestRule.waitForGone(text: String, timeoutMs: Long = 10_000) =
        waitUntil(timeoutMs) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
}
