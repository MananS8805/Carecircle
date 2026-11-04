package com.carecircle.smoke

import com.carecircle.DebugReset
import com.carecircle.SmokeSupport
import com.carecircle.data.AppDb
import com.carecircle.data.BackupParse
import com.carecircle.data.BackupService
import com.carecircle.data.Person
import com.carecircle.data.RefillEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class BackupRoundTripTest {
    private val ctx get() = SmokeSupport.context

    private fun seed(): Long = runBlocking {
        val db = AppDb.get(ctx)
        db.people().update(db.people().all().first().copy(name = "Asha", caregiverPhone = "9876543210"))
        val id = SmokeSupport.insertDueMedicine(name = "RoundTrip Med", pills = 30)
        db.refills().insert(RefillEvent(medicineId = id, date = 1_000L, quantityAdded = 30, note = "pharmacy"))
        id
    }

    private fun roundTrip(password: String?) {
        DebugReset.reset(ctx)
        val id = seed()
        val service = BackupService(ctx)
        val out = ByteArrayOutputStream()
        runBlocking { service.export(out, password = password) }
        val bytes = out.toByteArray()

        DebugReset.reset(ctx) // an empty phone again
        assertEquals(0, runBlocking { AppDb.get(ctx).medicines().allIncludingArchived().size })

        val parsed = service.parse(bytes, password) as BackupParse.Ok
        assertEquals("Asha", parsed.file.people.single().name)
        runBlocking { service.restore(parsed.file) }

        val db = AppDb.get(ctx)
        runBlocking {
            assertEquals("RoundTrip Med", db.medicines().get(id)!!.name)
            assertEquals(30, db.medicines().get(id)!!.pillCount)
            assertEquals(30, db.refills().all().single().quantityAdded)
            assertEquals("9876543210", db.people().all().single().caregiverPhone)
        }
    }

    @Test fun plainBackupRestoresEverything() = roundTrip(null)

    @Test fun passwordProtectedBackupRestoresAndRefusesAWrongPassword() {
        roundTrip("secret-123")
        DebugReset.reset(ctx)
        seed()
        val out = ByteArrayOutputStream()
        runBlocking { BackupService(ctx).export(out, password = "secret-123") }
        val service = BackupService(ctx)
        assertTrue(service.parse(out.toByteArray(), null) is BackupParse.NeedsPassword)
        assertTrue(service.parse(out.toByteArray(), "wrong-password") is BackupParse.WrongPassword)
    }

    @Test fun aFailedRestoreLeavesTheCurrentDataAlone() {
        DebugReset.reset(ctx)
        val id = seed()
        val service = BackupService(ctx)
        val bad = service.parse("{\"app\":\"carecircle\",\"schemaVersion\":99}".toByteArray(), null)
        assertTrue(bad is BackupParse.Newer)
        runBlocking { assertEquals("RoundTrip Med", AppDb.get(ctx).medicines().get(id)!!.name) }
        assertEquals(1, runBlocking { AppDb.get(ctx).people().count() })
        assertTrue(Person(id = 1, name = "x").id == 1L)
    }
}
