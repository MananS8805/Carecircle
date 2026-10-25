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
