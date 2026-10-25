package com.carecircle.data

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.carecircle.reminder.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream

/** [personId] 0 (older files) means "the default person". */
@Serializable
data class BackupMedicine(
    val id: Long,
    val name: String,
    val doseText: String = "",
    val timesCsv: String,
    val pillCount: Int = 0,
    val pillsPerDose: Int = 1,
    val salt: String = "",
    val patientName: String = "",
    val active: Boolean = true,
    val foodTiming: String = "NONE",
    val personId: Long = 0,
    val conditionTag: String? = null, // added in backup version 4
    val createdAt: Long = 0,
    val archivedAt: Long? = null,
    val expiry: String? = null,       // added in backup version 5
    val doctorNote: String? = null,
)

/** A side-effect diary entry. Added in backup version 5. */
@Serializable
data class BackupSymptom(val medicineId: Long, val loggedAt: Long, val symptom: String, val severity: Int = 1, val note: String = "")

@Serializable
data class BackupDose(val medicineId: Long, val scheduledAt: Long, val takenAt: Long? = null, val missedHandled: Boolean = false, val skipped: Boolean = false)

/** A saved policy summary (the summary JSON only). Added in backup version 2. */
@Serializable
data class BackupPolicy(val savedAt: Long, val json: String, val personId: Long = 0)

/** A family member with their own contacts. Added in backup version 3. */
@Serializable
data class BackupPerson(val id: Long, val name: String, val caregiverPhone: String = "", val pharmacyPhone: String = "", val sortOrder: Int = 0)

/** Contacts of the single implicit person in version 1 and 2 files. Version 3 stores them on each person instead. */
@Serializable
data class BackupSettings(val caregiverPhone: String = "", val pharmacyPhone: String = "", val patientName: String = "")

/** Everything a user needs to move to a new phone. It never contains API keys, the app key or cached server data. */
@Serializable
data class BackupFile(
    val app: String = BackupCodec.APP_ID,
    val schemaVersion: Int,
    val exportedAt: Long,
    val settings: BackupSettings = BackupSettings(),
    val people: List<BackupPerson> = emptyList(),
    val medicines: List<BackupMedicine> = emptyList(),
    val doses: List<BackupDose> = emptyList(),
    val policySummaries: List<BackupPolicy> = emptyList(),
    val symptoms: List<BackupSymptom> = emptyList(),
)

sealed interface BackupParse {
    data class Ok(val file: BackupFile) : BackupParse
    /** Written by a newer version of CareCircle than this one understands. */
    data object Newer : BackupParse
    data object Malformed : BackupParse
}

/** Pure JSON encode / decode / validation, so it can be unit-tested on the JVM. */
object BackupCodec {
    const val APP_ID = "carecircle"
    const val CURRENT_VERSION = 5
    const val HISTORY_DAYS = 90
    const val MAX_BYTES = 10 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun build(
        people: List<Person>, meds: List<Medicine>, doses: List<DoseEvent>, now: Long,
        policies: List<PolicySummaryEntity> = emptyList(),
        symptoms: List<SymptomLog> = emptyList(),
    ): BackupFile {
        val from = now - HISTORY_DAYS * 24L * 3600_000L
        val ids = meds.map { it.id }.toSet()
        return BackupFile(
            schemaVersion = CURRENT_VERSION, exportedAt = now,
            people = people.map { BackupPerson(it.id, it.name, it.caregiverPhone, it.pharmacyPhone, it.sortOrder) },
            medicines = meds.map {
                BackupMedicine(it.id, it.name, it.doseText, it.timesCsv, it.pillCount, it.pillsPerDose, it.salt, it.patientName, it.active, it.foodTiming, it.personId, it.conditionTag, it.createdAt, it.archivedAt, it.expiry, it.doctorNote)
            },
            doses = doses.filter { it.scheduledAt >= from && it.medicineId in ids }
                .map { BackupDose(it.medicineId, it.scheduledAt, it.takenAt, it.missedHandled, it.skipped) },
            policySummaries = policies.map { BackupPolicy(it.savedAt, it.json, it.personId) },
            symptoms = symptoms.filter { it.medicineId in ids }.map { BackupSymptom(it.medicineId, it.loggedAt, it.symptom, it.severity, it.note) },
        )
    }

    /**
     * Older files had no people: their contacts become the default person and every medicine and summary is attached to it.
     * The result always has at least one person, and every reference points at one of them.
     */
    fun withPeople(f: BackupFile): BackupFile {
        if (f.people.isNotEmpty()) {
            val first = f.people.first().id
            val known = f.people.map { it.id }.toSet()
            return f.copy(
                medicines = f.medicines.map { if (it.personId in known) it else it.copy(personId = first) },
                policySummaries = f.policySummaries.map { if (it.personId in known) it else it.copy(personId = first) },
            )
        }
        val s = f.settings
        val me = BackupPerson(1, s.patientName.trim().take(Persons.MAX_NAME).ifEmpty { Persons.DEFAULT_NAME }, s.caregiverPhone, s.pharmacyPhone, 0)
        return f.copy(
            people = listOf(me),
            medicines = f.medicines.map { it.copy(personId = me.id) },
            policySummaries = f.policySummaries.map { it.copy(personId = me.id) },
        )
    }

    fun decode(text: String): BackupParse {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return BackupParse.Malformed
        if (root["app"]?.jsonPrimitive?.contentOrNull != APP_ID) return BackupParse.Malformed
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: return BackupParse.Malformed
        if (version > CURRENT_VERSION) return BackupParse.Newer
        if (version < 1) return BackupParse.Malformed
        val file = runCatching { json.decodeFromJsonElement<BackupFile>(root) }.getOrNull() ?: return BackupParse.Malformed
        return if (isConsistent(file)) BackupParse.Ok(file) else BackupParse.Malformed
    }

    private fun isConsistent(f: BackupFile): Boolean {
        val ids = f.medicines.map { it.id }
        if (ids.any { it <= 0 } || ids.toSet().size != ids.size) return false
        if (f.medicines.any { it.name.isBlank() || it.pillCount < 0 || it.pillsPerDose < 1 }) return false
        if (f.doses.any { it.medicineId !in ids }) return false
        if (f.symptoms.any { it.medicineId !in ids || it.severity !in 1..3 }) return false
        val pids = f.people.map { it.id }
        if (pids.any { it <= 0 } || pids.toSet().size != pids.size) return false
        if (f.people.any { it.name.isBlank() }) return false
        return true
    }
}

/** Database and alarm side of export, restore and delete-all. Never touches the network. */
class BackupService(private val ctx: Context) {
    private val db = AppDb.get(ctx)
    private val prefs = Prefs(ctx)

    suspend fun export(out: OutputStream, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val file = BackupCodec.build(
            db.people().all(), db.medicines().allIncludingArchived(), db.doses().since(now - BackupCodec.HISTORY_DAYS * 24L * 3600_000L), now,
            db.policies().all(), db.symptoms().all(),
        )
        out.use { it.write(BackupCodec.encode(file).toByteArray(Charsets.UTF_8)) }
    }

    suspend fun read(input: InputStream): BackupParse = withContext(Dispatchers.IO) {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var tooBig = false
        input.use {
            while (true) {
                val n = it.read(chunk)
                if (n < 0) break
                if (buf.size() + n > BackupCodec.MAX_BYTES) { tooBig = true; break }
                buf.write(chunk, 0, n)
            }
        }
        if (tooBig) BackupParse.Malformed else BackupCodec.decode(buf.toString(Charsets.UTF_8.name()))
    }

    /** Replaces everything (after the user confirmed). Alarms are re-armed for the restored medicines. */
    suspend fun restore(raw: BackupFile) = withContext(Dispatchers.IO) {
        val file = BackupCodec.withPeople(raw)
        cancelEverything()
        db.clearAllTables()
        db.people().insertAll(file.people.map { Person(it.id, it.name, it.caregiverPhone, it.pharmacyPhone, it.sortOrder) })
        db.medicines().insertAll(
            file.medicines.map {
                Medicine(
                    it.id, it.name, it.doseText, it.timesCsv, it.pillCount, it.pillsPerDose, it.salt, it.patientName, it.active, it.foodTiming,
                    it.personId, it.conditionTag?.trim()?.take(60)?.ifEmpty { null }, it.createdAt, it.archivedAt,
                    it.expiry, it.doctorNote?.take(500)?.ifBlank { null },
                )
            },
        )
        db.doses().insertAll(file.doses.map { DoseEvent(medicineId = it.medicineId, scheduledAt = it.scheduledAt, takenAt = it.takenAt, missedHandled = it.missedHandled, skipped = it.skipped) })
        db.symptoms().insertAll(file.symptoms.map { SymptomLog(medicineId = it.medicineId, loggedAt = it.loggedAt, symptom = it.symptom, severity = it.severity, note = it.note) })
        db.policies().insertAll(file.policySummaries.map { PolicySummaryEntity(savedAt = it.savedAt, json = it.json, personId = it.personId) })
        prefs.selectedPersonId = Prefs.UNSET
        ReminderScheduler.scheduleAll(ctx)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        cancelEverything()
        db.clearAllTables()
        val language = prefs.language
        prefs.clearAll()
        prefs.language = language // a language choice is not health data
        db.people().insert(Person(id = 1, name = Persons.DEFAULT_NAME))
        ctx.cacheDir.listFiles()?.forEach { it.delete() }
    }

    private suspend fun cancelEverything() {
        db.medicines().allIncludingArchived().forEach { ReminderScheduler.cancel(ctx, it.id) }
        db.doses().allIds().forEach { ReminderScheduler.cancelSnooze(ctx, it); ReminderScheduler.cancelRepeats(ctx, it) }
        WorkManager.getInstance(ctx).cancelAllWork()
        NotificationManagerCompat.from(ctx).cancelAll()
    }
}
