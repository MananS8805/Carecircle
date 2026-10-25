package com.carecircle.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 3600_000L

    private val people = listOf(
        Person(1, "Dad", "919876543210", "919000000000", 0),
        Person(2, "Mom", "", "919111111111", 1),
    )
    private val meds = listOf(
        Medicine(id = 1, name = "Metformin", doseText = "500 mg", timesCsv = "08:00,20:00", pillCount = 40, salt = "metformin", foodTiming = "AFTER_FOOD", personId = 1),
        Medicine(id = 2, name = "Old one", doseText = "", timesCsv = "09:00", pillCount = 0, active = false, personId = 2),
    )
    private val doses = listOf(
        DoseEvent(medicineId = 1, scheduledAt = now - 2 * day, takenAt = now - 2 * day + 60_000),
        DoseEvent(medicineId = 1, scheduledAt = now - 100 * day), // older than 90 days: left out
        DoseEvent(medicineId = 9, scheduledAt = now - day), // unknown medicine: left out
    )

    private fun built() = BackupCodec.build(people, meds, doses, now)

    @Test fun roundTripKeepsPeopleMedicinesTimesAndRecentHistory() {
        val parsed = BackupCodec.decode(BackupCodec.encode(built())) as BackupParse.Ok
        assertEquals(3, parsed.file.schemaVersion)
        assertEquals(listOf("Dad", "Mom"), parsed.file.people.map { it.name })
        assertEquals("919876543210", parsed.file.people[0].caregiverPhone)
        assertEquals(2, parsed.file.medicines.size)
        assertEquals("08:00,20:00", parsed.file.medicines[0].timesCsv)
        assertEquals("AFTER_FOOD", parsed.file.medicines[0].foodTiming)
        assertEquals(2L, parsed.file.medicines[1].personId)
        assertFalse(parsed.file.medicines[1].active)
        assertEquals(1, parsed.file.doses.size)
    }

    @Test fun exportHasNoSecrets() {
        val text = BackupCodec.encode(built()).lowercase()
        assertFalse(text.contains("apikey") || text.contains("appkey") || text.contains("x-app-key"))
    }

    @Test fun newerVersionIsRejected() {
        val text = BackupCodec.encode(built()).replace("\"schemaVersion\": 3", "\"schemaVersion\": 99")
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
        val text = BackupCodec.encode(built()).replaceFirst("{", "{\"extra\": true,")
        assertTrue(BackupCodec.decode(text) is BackupParse.Ok)
    }

    @Test fun savedPolicySummariesRoundTrip() {
        val policies = listOf(PolicySummaryEntity(id = 4, savedAt = now, json = "{\"summary\":{}}", personId = 2))
        val parsed = BackupCodec.decode(BackupCodec.encode(BackupCodec.build(people, meds, doses, now, policies))) as BackupParse.Ok
        assertEquals(1, parsed.file.policySummaries.size)
        assertEquals("{\"summary\":{}}", parsed.file.policySummaries[0].json)
        assertEquals(2L, parsed.file.policySummaries[0].personId)
    }

    @Test fun versionOneFileMapsEverythingToTheDefaultPerson() {
        val v1 = "{\"app\":\"carecircle\",\"schemaVersion\":1,\"exportedAt\":5," +
            "\"settings\":{\"caregiverPhone\":\"919876543210\",\"pharmacyPhone\":\"919000000000\",\"patientName\":\"Dad\"}," +
            "\"medicines\":[{\"id\":1,\"name\":\"Metformin\",\"timesCsv\":\"08:00\"}],\"doses\":[]}"
        val parsed = BackupCodec.decode(v1) as BackupParse.Ok
        assertEquals(emptyList<BackupPerson>(), parsed.file.people)
        val file = BackupCodec.withPeople(parsed.file)
        assertEquals(1, file.people.size)
        assertEquals("Dad", file.people[0].name)
        assertEquals("919876543210", file.people[0].caregiverPhone)
        assertEquals(file.people[0].id, file.medicines[0].personId)
    }

    @Test fun versionTwoFileWithoutPeopleGetsDefaultMe() {
        val v2 = "{\"app\":\"carecircle\",\"schemaVersion\":2,\"exportedAt\":5,\"medicines\":[{\"id\":3,\"name\":\"Telma\",\"timesCsv\":\"08:00\"}]," +
            "\"policySummaries\":[{\"savedAt\":1,\"json\":\"{}\"}]}"
        val file = BackupCodec.withPeople((BackupCodec.decode(v2) as BackupParse.Ok).file)
        assertEquals("Me", file.people.single().name)
        assertEquals(file.people.single().id, file.policySummaries.single().personId)
    }

    @Test fun unknownPersonReferencesFallBackToTheFirstPerson() {
        val f = BackupCodec.withPeople(built().let { b -> b.copy(medicines = b.medicines.map { it.copy(personId = 77) }) })
        assertTrue(f.medicines.all { it.personId == 1L })
    }

    @Test fun duplicatePersonIdsAreMalformed() {
        val text = BackupCodec.encode(BackupCodec.build(people + people[0], meds, doses, now))
        assertEquals(BackupParse.Malformed, BackupCodec.decode(text))
    }
}
