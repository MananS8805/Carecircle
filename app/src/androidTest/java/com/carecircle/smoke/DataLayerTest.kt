package com.carecircle.smoke

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.carecircle.DebugReset
import com.carecircle.SmokeSupport
import com.carecircle.data.AppDb
import com.carecircle.data.Appointment
import com.carecircle.data.DoseEvent
import com.carecircle.data.Medicine
import com.carecircle.data.Prefs
import com.carecircle.data.Repository
import com.carecircle.data.SymptomLog
import com.carecircle.data.SyncLink
import com.carecircle.data.VitalLog
import com.carecircle.reminder.DoseActions
import com.carecircle.reminder.Notifier
import com.carecircle.reminder.TakenActionReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The data layer on a real phone: Room, the alarm manager and the receivers, with no screen involved. Each test starts from an empty app. */
class DataLayerTest {
    private val ctx get() = SmokeSupport.context
    private val db get() = AppDb.get(ctx)
    private val repo get() = Repository(ctx)
    private val pid get() = runBlocking { db.people().all().first().id }

    @Before fun fresh() { DebugReset.reset(ctx) }

    private fun med(name: String = "Test Med", times: String = "08:00", pills: Int = 10, perDose: Int = 1, person: Long = pid, createdAt: Long = 1L) = runBlocking {
        db.medicines().insert(Medicine(name = name, doseText = "", timesCsv = times, pillCount = pills, pillsPerDose = perDose, personId = person, createdAt = createdAt))
    }

    private fun dose(medicineId: Long, at: Long = System.currentTimeMillis() - 60_000L): Long = runBlocking {
        db.doses().insert(DoseEvent(medicineId = medicineId, scheduledAt = at)); db.doses().find(medicineId, at)!!.id
    }

    private fun systemAlarms(): Int {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys alarm")
        val text = ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        return text.lineSequence().count { it.contains("Alarm{") && it.contains(ctx.packageName) }
    }

    // ---------- the bug that ended every family link ----------

    @Test fun readingAdherenceNeverEndsAFamilyLink() = runBlocking {
        db.sync().putLink(SyncLink("TESTCODE", pid, System.currentTimeMillis()))
        repeat(5) { repo.adherence(pid, 1) }
        assertEquals(listOf("TESTCODE"), db.sync().links().map { it.linkId })
    }

    @Test fun readingAdherenceNeverCancelsAnAppointmentAlarm() = runBlocking {
        val before = systemAlarms()
        val id = repo.saveAppointment(Appointment(personId = pid, title = "Checkup", startsAt = System.currentTimeMillis() + 5 * 3_600_000L, remindBefore = 60))
        val armed = systemAlarms()
        assertTrue("saving an appointment should arm an alarm", armed > before)
        repeat(3) { repo.adherence(pid, 7) }
        assertEquals("reading adherence must leave the alarm alone", armed, systemAlarms())
        repo.deleteAppointment(id)
        assertEquals(before, systemAlarms())
    }

    // ---------- adherence only counts what could have been taken ----------

    @Test fun aDoseBeforeTheMedicineWasAddedDoesNotCount() = runBlocking {
        assumeTrue(LocalTime.now().isAfter(LocalTime.of(0, 40)))
        med(times = "00:01", createdAt = System.currentTimeMillis())
        val r = repo.adherence(pid, 1)
        assertEquals(0, r.taken + r.missed + r.skipped + r.pending)
    }

    @Test fun anOverdueUntakenDoseCountsAsMissed() = runBlocking {
        assumeTrue(LocalTime.now().isAfter(LocalTime.of(0, 40)))
        med(times = "00:01", createdAt = 1L)
        val r = repo.adherence(pid, 1)
        assertEquals(1, r.missed); assertEquals(0, r.taken)
    }

    // ---------- taking, skipping, pills ----------

    @Test fun takingADoseUsesExactlyThePillsPerDose() = runBlocking {
        val m = med(pills = 10, perDose = 2); val d = dose(m)
        DoseActions.markTaken(ctx, d)
        assertEquals(8, SmokeSupport.medicine(m).pillCount); assertNotNull(db.doses().get(d)!!.takenAt)
    }

    @Test fun pillsNeverGoBelowZero() = runBlocking {
        val m = med(pills = 1, perDose = 3); val d = dose(m)
        DoseActions.markTaken(ctx, d)
        assertEquals(0, SmokeSupport.medicine(m).pillCount)
    }

    @Test fun takingTheSameDoseTwiceUsesOnlyOnePill() = runBlocking {
        val m = med(pills = 10); val d = dose(m)
        repeat(3) { DoseActions.markTaken(ctx, d) }
        assertEquals(9, SmokeSupport.medicine(m).pillCount)
    }

    @Test fun skippingUsesNoPillAndIsRemembered() = runBlocking {
        val m = med(pills = 10); val d = dose(m)
        DoseActions.skip(ctx, d)
        assertEquals(10, SmokeSupport.medicine(m).pillCount); assertTrue(db.doses().get(d)!!.skipped); assertNull(db.doses().get(d)!!.takenAt)
    }

    @Test fun aTakenDoseCannotBeSkippedAfterwards() = runBlocking {
        val m = med(); val d = dose(m)
        DoseActions.markTaken(ctx, d); DoseActions.skip(ctx, d)
        assertFalse(db.doses().get(d)!!.skipped)
    }

    @Test fun theSameDoseSlotCannotBeStoredTwice() = runBlocking {
        val m = med(); val at = System.currentTimeMillis() - 120_000L
        db.doses().insert(DoseEvent(medicineId = m, scheduledAt = at)); db.doses().insert(DoseEvent(medicineId = m, scheduledAt = at))
        assertEquals(1, db.doses().since(0).count { it.medicineId == m })
    }

    @Test fun theTakenButtonOnANotificationMarksTheDoseTaken() {
        val m = med(pills = 5); val d = dose(m)
        ctx.sendBroadcast(Intent(ctx, TakenActionReceiver::class.java).putExtra(Notifier.EXTRA_DOSE_ID, d))
        val until = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < until && runBlocking { db.doses().get(d)!!.takenAt } == null) Thread.sleep(100)
        assertNotNull(runBlocking { db.doses().get(d)!!.takenAt }); assertEquals(4, SmokeSupport.medicine(m).pillCount)
    }

    // ---------- refills ----------

    @Test fun aRefillAddsPillsAndRecordsThePurchase() = runBlocking {
        val m = med(pills = 4)
        val out = repo.addRefill(m, 30)!!
        assertEquals(34, out.countAfter); assertEquals(34, SmokeSupport.medicine(m).pillCount); assertEquals(1, db.refills().all().size)
    }

    @Test fun aRefillCanBeUndoneWhileTheCountIsUnchanged() = runBlocking {
        val m = med(pills = 4); val out = repo.addRefill(m, 30)!!
        assertTrue(repo.undoRefill(out)); assertEquals(4, SmokeSupport.medicine(m).pillCount); assertEquals(0, db.refills().all().size)
    }

    @Test fun aRefillCannotBeUndoneOnceADoseWasTaken() = runBlocking {
        val m = med(pills = 4); val out = repo.addRefill(m, 30)!!
        DoseActions.markTaken(ctx, dose(m))
        assertFalse(repo.undoRefill(out)); assertEquals(33, SmokeSupport.medicine(m).pillCount)
    }

    @Test fun badRefillQuantitiesAreRefused() = runBlocking {
        val m = med(pills = 4)
        assertNull(repo.addRefill(m, 0)); assertNull(repo.addRefill(m, -5)); assertNull(repo.addRefill(m, 1001)); assertNull(repo.addRefill(9999, 10))
        assertEquals(4, SmokeSupport.medicine(m).pillCount)
    }

    // ---------- people ----------

    @Test fun deletingAPersonRemovesOnlyThatPersonsRecords() = runBlocking {
        val keep = pid
        val other = repo.addPerson("Papa")
        val mine = med("Mine", person = keep); val theirs = med("Theirs", person = other)
        dose(mine); dose(theirs)
        db.vitals().insert(VitalLog(personId = other, kind = "pulse", v1 = 70.0, measuredAt = 1L)); db.vitals().insert(VitalLog(personId = keep, kind = "pulse", v1 = 71.0, measuredAt = 2L))
        repo.addAllergy(other, "Penicillin")
        assertTrue(repo.deletePerson(other))
        assertEquals(listOf("Mine"), db.medicines().all().map { it.name })
        assertEquals(1, db.doses().since(0).size); assertEquals(1, db.vitals().all().size); assertTrue(repo.allergyNames(other).isEmpty())
        assertEquals(listOf(keep), db.people().all().map { it.id })
    }

    @Test fun theLastPersonCanNeverBeDeleted() = runBlocking {
        assertFalse(repo.deletePerson(pid)); assertEquals(1, db.people().all().size)
    }

    @Test fun deletingAPersonStopsSharingThem() = runBlocking {
        val other = repo.addPerson("Papa")
        db.sync().putLink(SyncLink("PAPALINK", other, 1L))
        repo.deletePerson(other)
        assertTrue("their link must go with them", db.sync().links().none { it.linkId == "PAPALINK" })
    }

    @Test fun personNamesAreTrimmedAndLimited() = runBlocking {
        val id = repo.addPerson("   " + "x".repeat(80) + "  ")
        assertEquals(40, db.people().all().first { it.id == id }.name.length)
    }

    // ---------- the smaller records ----------

    @Test fun vitalsAreSavedFilteredByDateAndDeleted() = runBlocking {
        val a = repo.logVital(VitalLog(personId = pid, kind = "bp", v1 = 120.0, v2 = 80.0, measuredAt = 1_000L))
        repo.logVital(VitalLog(personId = pid, kind = "bp", v1 = 118.0, v2 = 79.0, measuredAt = 5_000L))
        assertEquals(1, repo.vitalsSince(pid, 2_000L).size)
        repo.deleteVital(a); assertEquals(1, repo.vitalsSince(pid, 0L).size)
    }

    @Test fun allergiesAreAddedAndRemoved() = runBlocking {
        repo.addAllergy(pid, "Penicillin")
        assertTrue(repo.allergyNames(pid).any { it.equals("Penicillin", ignoreCase = true) })
        db.allergies().listFor(pid).forEach { repo.removeAllergy(it.id) }
        assertTrue(repo.allergyNames(pid).isEmpty())
    }

    @Test fun symptomsAreLoggedAndFoundByDate() = runBlocking {
        val m = med()
        repo.logSymptom(SymptomLog(medicineId = m, loggedAt = 9_000L, symptom = "Nausea", severity = 2))
        assertEquals(1, repo.symptomsSince(5_000L).size); assertEquals(0, repo.symptomsSince(10_000L).size)
    }

    @Test fun anAppointmentArmsAnAlarmAndDeletingItCancelsIt() = runBlocking {
        val before = systemAlarms()
        val id = repo.saveAppointment(Appointment(personId = pid, title = "Eye test", startsAt = System.currentTimeMillis() + 8 * 3_600_000L))
        assertTrue(systemAlarms() > before)
        repo.deleteAppointment(id); assertEquals(before, systemAlarms())
    }

    @Test fun anAppointmentWhoseReminderTimeHasPassedArmsNothing() = runBlocking {
        val before = systemAlarms()
        repo.saveAppointment(Appointment(personId = pid, title = "Soon", startsAt = System.currentTimeMillis() + 10 * 60_000L, remindBefore = 60))
        assertEquals(before, systemAlarms())
    }

    // ---------- privacy defaults ----------

    @Test fun nothingPersonalIsSharedByDefault() {
        val p = Prefs(ctx)
        assertFalse("names must be off until the user opts in", p.shareNames)
        assertFalse("the local crash log must be off until the user opts in", p.crashLogEnabled)
    }

    @Test fun deleteAllLeavesOneEmptyDefaultPerson() = runBlocking {
        med(); dose(med("Other"))
        db.vitals().insert(VitalLog(personId = pid, kind = "pulse", v1 = 70.0, measuredAt = 1L))
        DebugReset.reset(ctx)
        assertEquals(0, db.medicines().all().size); assertEquals(0, db.doses().since(0).size); assertEquals(0, db.vitals().all().size)
        assertEquals(1, db.people().all().size)
    }

    @Test fun todayIsComputedInTheLocalTimezone() = runBlocking {
        val zone = ZoneId.systemDefault()
        val m = med(times = "00:01", createdAt = 1L)
        assumeTrue(LocalTime.now().isAfter(LocalTime.of(0, 40)))
        val r = repo.adherence(pid, 1)
        assertEquals(LocalDate.now(zone), r.to)
        assertNotNull(m)
    }
}
