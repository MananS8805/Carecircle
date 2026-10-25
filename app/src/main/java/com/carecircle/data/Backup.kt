package com.carecircle.data

import android.content.Context
import androidx.work.WorkManager
import androidx.core.app.NotificationManagerCompat
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
)

@Serializable
data class BackupDose(val medicineId: Long, val scheduledAt: Long, val takenAt: Long? = null, val missedHandled: Boolean = false)

/** A saved policy summary (the summary JSON only). Added in backup version 2. */
@Serializable
data class BackupPolicy(val savedAt: Long, val json: String)

@Serializable
data class BackupSettings(val caregiverPhone: String = "", val pharmacyPhone: String = "", val patientName: String = "")

/** Everything a user needs to move to a new phone. It never contains API keys, the app key or cached server data. */
@Serializable
data class BackupFile(
    val app: String = BackupCodec.APP_ID,
    val schemaVersion: Int,
    val exportedAt: Long,
    val settings: BackupSettings = BackupSettings(),
    val medicines: List<BackupMedicine> = emptyList(),
    val doses: List<BackupDose> = emptyList(),
    val policySummaries: List<BackupPolicy> = emptyList(),
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
    const val CURRENT_VERSION = 2
    const val HISTORY_DAYS = 90
    const val MAX_BYTES = 10 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun build(
        meds: List<Medicine>, doses: List<DoseEvent>, settings: BackupSettings, now: Long,
        policies: List<PolicySummaryEntity> = emptyList(),
    ): BackupFile {
        val from = now - HISTORY_DAYS * 24L * 3600_000L
        val ids = meds.map { it.id }.toSet()
        return BackupFile(
            schemaVersion = CURRENT_VERSION, exportedAt = now, settings = settings,
            medicines = meds.map {
                BackupMedicine(it.id, it.name, it.doseText, it.timesCsv, it.pillCount, it.pillsPerDose, it.salt, it.patientName, it.active, it.foodTiming)
            },
            doses = doses.filter { it.scheduledAt >= from && it.medicineId in ids }
                .map { BackupDose(it.medicineId, it.scheduledAt, it.takenAt, it.missedHandled) },
            policySummaries = policies.map { BackupPolicy(it.savedAt, it.json) },
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
        return true
    }
}

/** Database and alarm side of export, restore and delete-all. Never touches the network. */
class BackupService(private val ctx: Context) {
    private val db = AppDb.get(ctx)
    private val prefs = Prefs(ctx)

    suspend fun export(out: OutputStream, now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        val file = BackupCodec.build(
            db.medicines().allIncludingArchived(), db.doses().since(now - BackupCodec.HISTORY_DAYS * 24L * 3600_000L),
            BackupSettings(prefs.caregiverPhone, prefs.pharmacyPhone, prefs.patientName), now,
            db.policies().all(),
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
    suspend fun restore(file: BackupFile) = withContext(Dispatchers.IO) {
        cancelEverything()
        db.clearAllTables()
        db.medicines().insertAll(
            file.medicines.map {
                Medicine(it.id, it.name, it.doseText, it.timesCsv, it.pillCount, it.pillsPerDose, it.salt, it.patientName, it.active, it.foodTiming)
            },
        )
        db.doses().insertAll(file.doses.map { DoseEvent(medicineId = it.medicineId, scheduledAt = it.scheduledAt, takenAt = it.takenAt, missedHandled = it.missedHandled) })
        db.policies().insertAll(file.policySummaries.map { PolicySummaryEntity(savedAt = it.savedAt, json = it.json) })
        prefs.caregiverPhone = file.settings.caregiverPhone
        prefs.pharmacyPhone = file.settings.pharmacyPhone
        prefs.patientName = file.settings.patientName
        ReminderScheduler.scheduleAll(ctx)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        cancelEverything()
        db.clearAllTables()
        prefs.clearAll()
        ctx.cacheDir.listFiles()?.forEach { it.delete() }
    }

    private suspend fun cancelEverything() {
        db.medicines().allIncludingArchived().forEach { ReminderScheduler.cancel(ctx, it.id) }
        db.doses().allIds().forEach { ReminderScheduler.cancelSnooze(ctx, it) }
        WorkManager.getInstance(ctx).cancelAllWork()
        NotificationManagerCompat.from(ctx).cancelAll()
    }
}
