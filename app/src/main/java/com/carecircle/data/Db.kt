package com.carecircle.data

import android.content.Context
import androidx.room.*
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

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicine WHERE active = 1 ORDER BY name") fun observeAll(): Flow<List<Medicine>>
    @Query("SELECT * FROM medicine WHERE active = 1") suspend fun all(): List<Medicine>
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
}

@Dao
interface InfoCardDao {
    @Query("SELECT * FROM info_card WHERE saltKey = :k") suspend fun get(k: String): InfoCardEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(e: InfoCardEntity)
}

@Database(entities = [Medicine::class, DoseEvent::class, InfoCardEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun medicines(): MedicineDao
    abstract fun doses(): DoseDao
    abstract fun cards(): InfoCardDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "carecircle.db").build().also { inst = it }
        }
    }
}
