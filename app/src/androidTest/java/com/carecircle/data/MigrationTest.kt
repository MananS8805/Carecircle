package com.carecircle.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Run on a device: `./gradlew :app:connectedDebugAndroidTest`. Schemas come from app/schemas. */
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDb::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate4To5KeepsMedicinesWithNullTagAndStampsCreatedAt() {
        helper.createDatabase("mig45", 4).apply {
            execSQL("INSERT INTO person (id, name, caregiverPhone, pharmacyPhone, sortOrder) VALUES (1, 'Me', '', '', 0)")
            execSQL("INSERT INTO medicine (id, name, doseText, timesCsv, pillCount, pillsPerDose, salt, patientName, active, foodTiming, personId) VALUES (1, 'Telma', '40 mg', '08:00', 30, 1, 'telmisartan', '', 1, 'NONE', 1)")
            execSQL("INSERT INTO medicine (id, name, doseText, timesCsv, pillCount, pillsPerDose, salt, patientName, active, foodTiming, personId) VALUES (2, 'Old', '', '09:00', 0, 1, '', '', 0, 'NONE', 1)")
            execSQL("INSERT INTO dose_event (medicineId, scheduledAt, takenAt, missedHandled) VALUES (1, 1000, 2000, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig45", 5, true, Migrations.M4_5)
        db.query("SELECT conditionTag, createdAt, archivedAt FROM medicine WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(true, it.isNull(0)); assertEquals(1000L, it.getLong(1)); assertEquals(true, it.isNull(2))
        }
        db.query("SELECT archivedAt FROM medicine WHERE id = 2").use { it.moveToFirst(); assertEquals(false, it.isNull(0)) }
        db.query("SELECT skipped FROM dose_event").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    @Test
    fun migrate3To4CreatesDefaultPersonWithOldContactsAndKeepsEverything() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("carecircle", android.content.Context.MODE_PRIVATE).edit()
            .putString("caregiver", "919876543210").putString("pharmacy", "919000000000").putString("patient", "").apply()
        helper.createDatabase("mig34", 3).apply {
            execSQL("INSERT INTO medicine (id, name, doseText, timesCsv, pillCount, pillsPerDose, salt, patientName, active, foodTiming) VALUES (1, 'Telma', '40 mg', '08:00', 30, 1, 'telmisartan', '', 1, 'NONE')")
            execSQL("INSERT INTO dose_event (medicineId, scheduledAt, takenAt, missedHandled) VALUES (1, 1000, 2000, 0)")
            execSQL("INSERT INTO policy_summary (savedAt, json) VALUES (5, '{}')")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig34", 4, true, Migrations.m3to4(ctx))
        db.query("SELECT id, name, caregiverPhone, pharmacyPhone FROM person").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0)); assertEquals("Me", it.getString(1))
            assertEquals("919876543210", it.getString(2)); assertEquals("919000000000", it.getString(3))
        }
        db.query("SELECT personId FROM medicine").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT personId FROM policy_summary").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM dose_event").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        assertEquals("", ctx.getSharedPreferences("carecircle", android.content.Context.MODE_PRIVATE).getString("caregiver", ""))
    }

    @Test
    fun migrate2To3AddsPolicyTableAndKeepsData() {
        helper.createDatabase("mig23", 2).apply {
            execSQL("INSERT INTO medicine (id, name, doseText, timesCsv, pillCount, pillsPerDose, salt, patientName, active, foodTiming) VALUES (1, 'Telma', '40 mg', '08:00', 30, 1, 'telmisartan', '', 1, 'AFTER_FOOD')")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig23", 3, true, Migrations.M2_3)
        db.query("SELECT foodTiming FROM medicine").use { it.moveToFirst(); assertEquals("AFTER_FOOD", it.getString(0)) }
        db.execSQL("INSERT INTO policy_summary (savedAt, json) VALUES (1, '{}')")
        db.query("SELECT COUNT(*) FROM policy_summary").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    @Test
    fun migrate1To2KeepsMedicinesAndHistory() {
        helper.createDatabase("mig", 1).apply {
            execSQL("INSERT INTO medicine (id, name, doseText, timesCsv, pillCount, pillsPerDose, salt, patientName, active) VALUES (1, 'Metformin', '500 mg', '08:00,20:00', 30, 1, 'metformin', 'Dad', 1)")
            execSQL("INSERT INTO dose_event (medicineId, scheduledAt, takenAt, missedHandled) VALUES (1, 1000, 2000, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate("mig", 2, true, Migrations.M1_2)
        db.query("SELECT name, foodTiming FROM medicine").use {
            it.moveToFirst()
            assertEquals("Metformin", it.getString(0))
            assertEquals("NONE", it.getString(1))
        }
        db.query("SELECT COUNT(*) FROM dose_event").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }
}
