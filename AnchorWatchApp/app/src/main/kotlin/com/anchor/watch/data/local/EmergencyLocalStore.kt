package com.anchor.watch.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "emergency_events")
data class EmergencyEventEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val userId: String,
    val type: String,
    val isSynced: Boolean,
)

@Dao
interface EmergencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EmergencyEventEntity)

    @Query("SELECT * FROM emergency_events WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun unsynced(): List<EmergencyEventEntity>

    @Query("UPDATE emergency_events SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}

@Database(
    entities = [EmergencyEventEntity::class, CheckInEntity::class, MedicationEntity::class, WaterEntity::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(IntListConverter::class)
abstract class AnchorDatabase : RoomDatabase() {
    abstract fun emergencyDao(): EmergencyDao
    abstract fun checkInDao(): CheckInDao
    abstract fun medicationDao(): MedicationDao
    abstract fun waterDao(): WaterDao

    companion object {
        @Volatile
        private var instance: AnchorDatabase? = null

        // Adds MedicationEntity.daysOfWeek (CSV via IntListConverter). Non-destructive
        // so queued offline emergency/check-in events survive the upgrade.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE medications ADD COLUMN daysOfWeek TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        // Adds the water_reminders table (mirrors medications' shape minus a name column).
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS water_reminders (
                        id TEXT NOT NULL PRIMARY KEY,
                        scheduledTime TEXT NOT NULL,
                        status TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        isSynced INTEGER NOT NULL,
                        statusTimestamp INTEGER,
                        daysOfWeek TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent(),
                )
            }
        }

        fun get(context: Context): AnchorDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnchorDatabase::class.java,
                    "anchor-watch.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}

interface EmergencyStore {
    suspend fun save(event: EmergencyEventEntity)
    suspend fun unsynced(): List<EmergencyEventEntity>
    suspend fun markSynced(id: String)
}

class EmergencyLocalStore(context: Context) : EmergencyStore {
    private val dao = AnchorDatabase.get(context).emergencyDao()
    override suspend fun save(event: EmergencyEventEntity) = dao.upsert(event)
    override suspend fun unsynced(): List<EmergencyEventEntity> = dao.unsynced()
    override suspend fun markSynced(id: String) = dao.markSynced(id)
}
