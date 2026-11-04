package com.carecircle.smoke

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.carecircle.DebugReset
import com.carecircle.SmokeSupport
import com.carecircle.data.AppDb
import com.carecircle.data.Medicine
import com.carecircle.reminder.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Asks the system what it has scheduled (`dumpsys alarm`) instead of trusting our own bookkeeping. */
class ReminderSchedulingTest {
    private val ctx get() = SmokeSupport.context

    private fun alarmsOfThisApp(): Int {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys alarm")
        val text = ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        return text.lineSequence().count { it.contains("Alarm{") && it.contains(ctx.packageName) }
    }

    private fun addMedicine(times: String): Long = runBlocking {
        val db = AppDb.get(ctx)
        db.medicines().insert(Medicine(name = "Alarm Med", doseText = "", timesCsv = times, pillCount = 20, personId = db.people().all().first().id, createdAt = 1L))
    }

    @Test fun rescheduleAllArmsOneAlarmPerMedicineAndRemovingItCancelsIt() {
        DebugReset.reset(ctx)
        runBlocking { ReminderScheduler.rescheduleAll(ctx, "test") }
        val before = alarmsOfThisApp()
        val id = addMedicine("08:00,20:00")
        val armed = runBlocking { ReminderScheduler.rescheduleAll(ctx, "test") }
        assertEquals(1, armed)
        assertTrue("expected the system to list a new alarm for ${ctx.packageName}", alarmsOfThisApp() > before)

        ReminderScheduler.cancel(ctx, id)
        assertEquals(before, alarmsOfThisApp())
    }

    @Test fun runningItTwiceDoesNotDuplicateAlarms() {
        DebugReset.reset(ctx)
        addMedicine("08:00")
        runBlocking { ReminderScheduler.rescheduleAll(ctx, "first") }
        val once = alarmsOfThisApp()
        runBlocking { ReminderScheduler.rescheduleAll(ctx, "second") }
        assertEquals(once, alarmsOfThisApp())
    }

    @Test fun theRefreshIsWrittenToTheLog() {
        DebugReset.reset(ctx)
        runBlocking { ReminderScheduler.rescheduleAll(ctx, "logcheck") }
        val first = com.carecircle.data.Prefs(ctx).rescheduleLog.first()
        assertEquals("logcheck", first.reason)
    }
}
