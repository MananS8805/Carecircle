package com.carecircle.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "medicine")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val doseText: String,          // e.g. "500 mg"
    val timesCsv: String,          // "08:00,20:00"
    val pillCount: Int,
    val pillsPerDose: Int = 1,
    val salt: String = "",         // generic name(s) from strip scan or manual entry
    val patientName: String = "",
    val active: Boolean = true,
    @ColumnInfo(defaultValue = "NONE") val foodTiming: String = "NONE",
)

@Entity(tableName = "dose_event", indices = [Index("medicineId", "scheduledAt", unique = true)])
data class DoseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val scheduledAt: Long,         // epoch millis
    val takenAt: Long? = null,
    val missedHandled: Boolean = false,
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
@Entity(tableName = "policy_summary")
data class PolicySummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val savedAt: Long,
    val json: String,
)

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicine WHERE active = 1 ORDER BY name") fun observeAll(): Flow<List<Medicine>>
    @Query("SELECT * FROM medicine WHERE active = 1") suspend fun all(): List<Medicine>
    @Query("SELECT * FROM medicine") suspend fun allIncludingArchived(): List<Medicine>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(m: List<Medicine>)
    @Query("SELECT * FROM medicine WHERE id = :id") suspend fun get(id: Long): Medicine?
    @Insert suspend fun insert(m: Medicine): Long
    @Update suspend fun update(m: Medicine)
    @Query("UPDATE medicine SET active = 0 WHERE id = :id") suspend fun archive(id: Long)
    @Query("UPDATE medicine SET pillCount = MAX(0, pillCount - :n) WHERE id = :id") suspend fun decrementPills(id: Long, n: Int)
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
    @Query("SELECT * FROM dose_event WHERE scheduledAt >= :from ORDER BY scheduledAt") suspend fun since(from: Long): List<DoseEvent>
    @Query("SELECT id FROM dose_event") suspend fun allIds(): List<Long>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(e: List<DoseEvent>)
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
    @Query("SELECT * FROM policy_summary ORDER BY savedAt DESC") fun observeAll(): Flow<List<PolicySummaryEntity>>
    @Query("SELECT * FROM policy_summary ORDER BY savedAt DESC") suspend fun all(): List<PolicySummaryEntity>
    @Insert suspend fun insert(e: PolicySummaryEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(e: List<PolicySummaryEntity>)
    @Query("DELETE FROM policy_summary WHERE id = :id") suspend fun delete(id: Long)
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

    val ALL = arrayOf(M1_2, M2_3)
}

@Database(
    entities = [Medicine::class, DoseEvent::class, InfoCardEntity::class, InteractionCacheEntity::class, PolicySummaryEntity::class],
    version = 3, exportSchema = true,
)
abstract class AppDb : RoomDatabase() {
    abstract fun medicines(): MedicineDao
    abstract fun doses(): DoseDao
    abstract fun cards(): InfoCardDao
    abstract fun interactions(): InteractionDao
    abstract fun policies(): PolicyDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "carecircle.db")
                .addMigrations(*Migrations.ALL).build().also { inst = it }
        }
    }
}
