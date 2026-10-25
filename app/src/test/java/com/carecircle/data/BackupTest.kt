package com.carecircle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 3600_000L

    private val meds = listOf(
        Medicine(id = 1, name = "Metformin", doseText = "500 mg", timesCsv = "08:00,20:00", pillCount = 40, salt = "metformin", foodTiming = "AFTER_FOOD"),
        Medicine(id = 2, name = "Old one", doseText = "", timesCsv = "09:00", pillCount = 0, active = false),
    )
    private val doses = listOf(
        DoseEvent(medicineId = 1, scheduledAt = now - 2 * day, takenAt = now - 2 * day + 60_000),
        DoseEvent(medicineId = 1, scheduledAt = now - 100 * day), // older than 90 days: left out
        DoseEvent(medicineId = 9, scheduledAt = now - day), // unknown medicine: left out
    )
    private val settings = BackupSettings("919876543210", "919000000000", "Dad")

    @Test fun roundTripKeepsMedicinesTimesAndRecentHistory() {
        val text = BackupCodec.encode(BackupCodec.build(meds, doses, settings, now))
        val parsed = BackupCodec.decode(text) as BackupParse.Ok
        assertEquals(2, parsed.file.medicines.size)
        assertEquals("08:00,20:00", parsed.file.medicines[0].timesCsv)
        assertEquals("AFTER_FOOD", parsed.file.medicines[0].foodTiming)
        assertFalse(parsed.file.medicines[1].active)
        assertEquals(1, parsed.file.doses.size)
        assertEquals(settings, parsed.file.settings)
    }

    @Test fun exportHasNoSecrets() {
        val text = BackupCodec.encode(BackupCodec.build(meds, doses, settings, now)).lowercase()
        assertFalse(text.contains("apikey") || text.contains("appkey") || text.contains("x-app-key"))
    }

    @Test fun newerVersionIsRejected() {
        val text = BackupCodec.encode(BackupCodec.build(meds, doses, settings, now)).replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        assertEquals(BackupParse.Newer, BackupCodec.decode(text))
    }

    @Test fun malformedFilesAreRejected() {
        assertEquals(BackupParse.Malformed, BackupCodec.decode(""))
        assertEquals(BackupParse.Malformed, BackupCodec.decode("not json"))
        assertEquals(BackupParse.Malformed, BackupCodec.decode("{\"hello\": 1}"))
        assertEquals(BackupParse.Malformed, BackupCodec.decode("{\"app\":\"carecircle\",\"schemaVersion\":\"x\"}"))
        assertEquals(BackupParse.Malformed, BackupCodec.decode("{\"app\":\"other\",\"schemaVersion\":1}"))
        // dose points at a medicine that is not in the file
        val bad = "{\"app\":\"carecircle\",\"schemaVersion\":1,\"exportedAt\":1,\"medicines\":[],\"doses\":[{\"medicineId\":5,\"scheduledAt\":1}]}"
        assertEquals(BackupParse.Malformed, BackupCodec.decode(bad))
    }

    @Test fun unknownFieldsAreIgnored() {
        val text = BackupCodec.encode(BackupCodec.build(meds, emptyList(), settings, now)).replaceFirst("{", "{\"extra\": true,")
        assertTrue(BackupCodec.decode(text) is BackupParse.Ok)
    }
}
