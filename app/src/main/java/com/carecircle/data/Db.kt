package com.carecircle.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** One family member whose medicines are tracked. At least one person always exists. */
@Entity(tableName = "person")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val caregiverPhone: String = "",
    val pharmacyPhone: String = "",
    val sortOrder: Int = 0,
)

@Entity(tableName = "medicine", indices = [Index("personId")])
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val doseText: String,          // e.g. "500 mg"
    val timesCsv: String,          // "08:00,20:00"
    val pillCount: Int,
    val pillsPerDose: Int = 1,
    val salt: String = "",         // generic name(s) from strip scan or manual entry
    val patientName: String = "",  // legacy free-text name from before people existed
    val active: Boolean = true,
    @ColumnInfo(defaultValue = "NONE") val foodTiming: String = "NONE",
    @ColumnInfo(defaultValue = "1") val personId: Long = 0, // 0 = "not set yet"; the view model fills in the selected person
    val conditionTag: String? = null,                      // what it is for: a built-in chip label or free text, max 60 chars
    @ColumnInfo(defaultValue = "0") val createdAt: Long = 0, // when it was added; doses before this never count as missed
    val archivedAt: Long? = null,                           // when it was removed; doses after this do not count
    val expiry: String? = null,                             // "2028-02": printed expiry month of the strip in hand
    val doctorNote: String? = null,                         // the doctor's own instructions, shown above the generic card
)

/** One side effect the person noticed. Severity: 1 mild, 2 moderate, 3 severe. Stays on the phone. */
@Entity(tableName = "symptom_log", indices = [Index("medicineId")])
data class SymptomLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val loggedAt: Long,
    val symptom: String,
    val severity: Int = 1,
    val note: String = "",
)

@Entity(tableName = "dose_event", indices = [Index("medicineId", "scheduledAt", unique = true)])
data class DoseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val scheduledAt: Long,         // epoch millis
    val takenAt: Long? = null,
    val missedHandled: Boolean = false,
    @ColumnInfo(defaultValue = "0") val skipped: Boolean = false,
)

@Entity(tableName = "info_card")
data class InfoCardEntity(
    @PrimaryKey val saltKey: String,
    val json: String,
    val fetchedAt: Long,
)

/** Last interaction-check result for a set of generic names. [key] is the sorted, joined name list. */
@Entity(tableName = "interaction_cache")
data class InteractionCacheEntity(
    @PrimaryKey val key: String,
    val json: String,
    val fetchedAt: Long,
)

/** A saved policy summary. Only the summary JSON is stored: no source file, no full text. */
@Entity(tableName = "policy_summary", indices = [Index("personId")])
data class PolicySummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedAt: Long,
    val json: String,
    @ColumnInfo(defaultValue = "1") val personId: Long = 0,
)

@Dao
interface PersonDao {
    @Query("SELECT * FROM person ORDER BY sortOrder, id") fun observeAll(): Flow<List<Person>>
    @Query("SELECT * FROM person ORDER BY sortOrder, id") suspend fun all(): List<Person>
    @Query("SELECT * FROM person WHERE id = :id") suspend fun get(id: Long): Person?
    @Query("SELECT COUNT(*) FROM person") suspend fun count(): Int
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM person") suspend fun maxSort(): Int
    @Insert suspend fun insert(p: Person): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(p: List<Person>)
    @Update suspend fun update(p: Person)
    @Query("DELETE FROM person WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicine WHERE active = 1 ORDER BY name") fun observeAll(): Flow<List<Medicine>>
    @Query("SELECT * FROM medicine WHERE active = 1") suspend fun all(): List<Medicine>
    @Query("SELECT * FROM medicine") suspend fun allIncludingArchived(): List<Medicine>
    @Query("SELECT * FROM medicine WHERE personId = :personId") suspend fun forPersonIncludingArchived(personId: Long): List<Medicine>
    @Query("SELECT COUNT(*) FROM medicine WHERE personId = :personId AND active = 1") suspend fun activeCount(personId: Long): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(m: List<Medicine>)
    @Query("SELECT * FROM medicine WHERE id = :id") suspend fun get(id: Long): Medicine?
    @Insert suspend fun insert(m: Medicine): Long
    @Update suspend fun update(m: Medicine)
    @Query("UPDATE medicine SET active = 0, archivedAt = :now WHERE id = :id") suspend fun archive(id: Long, now: Long)
    @Query("UPDATE medicine SET pillCount = MAX(0, pillCount - :n) WHERE id = :id") suspend fun decrementPills(id: Long, n: Int)
    @Query("DELETE FROM medicine WHERE personId = :personId") suspend fun deleteForPerson(personId: Long)
}

@Dao
interface DoseDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: DoseEvent): Long
    @Query("SELECT * FROM dose_event WHERE id = :id") suspend fun get(id: Long): DoseEvent?
    @Query("SELECT * FROM dose_event WHERE medicineId = :mid AND scheduledAt = :at") suspend fun find(mid: Long, at: Long): DoseEvent?
    @Query("SELECT * FROM dose_event WHERE scheduledAt >= :from AND scheduledAt < :to ORDER BY scheduledAt")
    fun observeBetween(from: Long, to: Long): Flow<List<DoseEvent>>
    @Query("UPDATE dose_event SET takenAt = :now WHERE id = :id AND takenAt IS NULL") suspend fun markTaken(id: Long, now: Long): Int
    @Query("UPDATE dose_event SET missedHandled = 1 WHERE id = :id") suspend fun markMissedHandled(id: Long)
    @Query("UPDATE dose_event SET skipped = 1 WHERE id = :id AND takenAt IS NULL") suspend fun markSkipped(id: Long): Int
    @Query("SELECT * FROM dose_event WHERE scheduledAt >= :from ORDER BY scheduledAt") suspend fun since(from: Long): List<DoseEvent>
    @Query("SELECT id FROM dose_event") suspend fun allIds(): List<Long>
    @Query("SELECT id FROM dose_event WHERE medicineId IN (SELECT id FROM medicine WHERE personId = :personId)") suspend fun idsForPerson(personId: Long): List<Long>
    @Query("DELETE FROM dose_event WHERE medicineId IN (SELECT id FROM medicine WHERE personId = :personId)") suspend fun deleteForPerson(personId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(e: List<DoseEvent>)
}

@Dao
interface SymptomDao {
    @Query("SELECT * FROM symptom_log WHERE medicineId = :medicineId ORDER BY loggedAt DESC") fun observeFor(medicineId: Long): Flow<List<SymptomLog>>
    @Query("SELECT * FROM symptom_log WHERE loggedAt >= :from ORDER BY loggedAt DESC") suspend fun since(from: Long): List<SymptomLog>
    @Query("SELECT * FROM symptom_log") suspend fun all(): List<SymptomLog>
    @Insert suspend fun insert(e: SymptomLog): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(e: List<SymptomLog>)
    @Query("DELETE FROM symptom_log WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM symptom_log WHERE medicineId IN (SELECT id FROM medicine WHERE personId = :personId)") suspend fun deleteForPerson(personId: Long)
}

@Dao
interface InfoCardDao {
    @Query("SELECT * FROM info_card WHERE saltKey = :k") suspend fun get(k: String): InfoCardEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: InfoCardEntity)
}

@Dao
interface InteractionDao {
    @Query("SELECT * FROM interaction_cache WHERE `key` = :k") suspend fun get(k: String): InteractionCacheEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: InteractionCacheEntity)
}

@Dao
interface PolicyDao {
    @Query("SELECT * FROM policy_summary WHERE personId = :personId ORDER BY savedAt DESC") fun observeForPerson(personId: Long): Flow<List<PolicySummaryEntity>>
    @Query("SELECT * FROM policy_summary WHERE personId = :personId ORDER BY savedAt DESC") suspend fun forPerson(personId: Long): List<PolicySummaryEntity>
    @Query("SELECT * FROM policy_summary ORDER BY savedAt DESC") suspend fun all(): List<PolicySummaryEntity>
    @Insert suspend fun insert(e: PolicySummaryEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(e: List<PolicySummaryEntity>)
    @Query("DELETE FROM policy_summary WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM policy_summary WHERE personId = :personId") suspend fun deleteForPerson(personId: Long)
}

object Migrations {
    /** v0.2 -> v0.3: food timing on medicines and the interaction-check cache. */
    val M1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medicine ADD COLUMN foodTiming TEXT NOT NULL DEFAULT 'NONE'")
            db.execSQL("CREATE TABLE IF NOT EXISTS interaction_cache (`key` TEXT NOT NULL, json TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
        }
    }

    /** v0.3 -> v0.4: saved insurance policy summaries. */
    val M2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS policy_summary (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, savedAt INTEGER NOT NULL, json TEXT NOT NULL)")
        }
    }

    /**
     * v0.4 -> v0.5: people. Creates the default person (id 1) with the caregiver and pharmacy numbers that used to
     * live in preferences, attaches every medicine and saved summary to it, then removes the numbers from preferences.
     */
    fun m3to4(ctx: Context) = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val sp = ctx.applicationContext.getSharedPreferences("carecircle", Context.MODE_PRIVATE)
            val caregiver = sp.getString("caregiver", "") ?: ""
            val pharmacy = sp.getString("pharmacy", "") ?: ""
            val name = (sp.getString("patient", "") ?: "").trim().take(Persons.MAX_NAME).ifEmpty { Persons.DEFAULT_NAME }
            db.execSQL("CREATE TABLE IF NOT EXISTS person (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, caregiverPhone TEXT NOT NULL, pharmacyPhone TEXT NOT NULL, sortOrder INTEGER NOT NULL)")
            db.execSQL("INSERT INTO person (id, name, caregiverPhone, pharmacyPhone, sortOrder) VALUES (1, ?, ?, ?, 0)", arrayOf<Any?>(name, caregiver, pharmacy))
            db.execSQL("ALTER TABLE medicine ADD COLUMN personId INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE policy_summary ADD COLUMN personId INTEGER NOT NULL DEFAULT 1")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_medicine_personId ON medicine (personId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_policy_summary_personId ON policy_summary (personId)")
            sp.edit().remove("caregiver").remove("pharmacy").remove("patient").apply()
        }
    }

    /** v0.5 -> v0.6: condition tag, when a medicine was added or removed (for adherence), and skipped doses. */
    val M4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.execSQL("ALTER TABLE medicine ADD COLUMN conditionTag TEXT")
            db.execSQL("ALTER TABLE medicine ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE medicine ADD COLUMN archivedAt INTEGER")
            db.execSQL("ALTER TABLE dose_event ADD COLUMN skipped INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE medicine SET createdAt = COALESCE((SELECT MIN(scheduledAt) FROM dose_event WHERE dose_event.medicineId = medicine.id), ?)", arrayOf<Any?>(now))
            db.execSQL("UPDATE medicine SET archivedAt = ? WHERE active = 0", arrayOf<Any?>(now))
        }
    }

    /** v0.6 -> v0.7: printed expiry month, the doctor's note, and the side-effect diary. */
    val M5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE medicine ADD COLUMN expiry TEXT")
            db.execSQL("ALTER TABLE medicine ADD COLUMN doctorNote TEXT")
            db.execSQL("CREATE TABLE IF NOT EXISTS symptom_log (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, medicineId INTEGER NOT NULL, loggedAt INTEGER NOT NULL, symptom TEXT NOT NULL, severity INTEGER NOT NULL, note TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_symptom_log_medicineId ON symptom_log (medicineId)")
        }
    }

    fun all(ctx: Context): Array<Migration> = arrayOf(M1_2, M2_3, m3to4(ctx), M4_5, M5_6)
}

object Persons {
    const val DEFAULT_NAME = "Me"
    const val MAX_NAME = 40
    fun cleanName(raw: String): String = raw.trim().take(MAX_NAME)
}

@Database(
    entities = [Person::class, Medicine::class, DoseEvent::class, InfoCardEntity::class, InteractionCacheEntity::class, PolicySummaryEntity::class, SymptomLog::class],
    version = 6, exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
    abstract fun people(): PersonDao
    abstract fun medicines(): MedicineDao
    abstract fun doses(): DoseDao
    abstract fun cards(): InfoCardDao
    abstract fun interactions(): InteractionDao
    abstract fun policies(): PolicyDao
    abstract fun symptoms(): SymptomDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "carecircle.db")
                .addMigrations(*Migrations.all(ctx))
                .addCallback(object : Callback() {
                    // Fresh install: the default person exists from the start.
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("INSERT INTO person (id, name, caregiverPhone, pharmacyPhone, sortOrder) VALUES (1, '${Persons.DEFAULT_NAME}', '', '', 0)")
                    }
                })
                .build().also { inst = it }
        }
    }
}
