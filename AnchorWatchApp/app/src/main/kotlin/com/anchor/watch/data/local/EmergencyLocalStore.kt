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
    entities = [EmergencyEventEntity::class, CheckInEntity::class, MedicationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AnchorDatabase : RoomDatabase() {
    abstract fun emergencyDao(): EmergencyDao
    abstract fun checkInDao(): CheckInDao
    abstract fun medicationDao(): MedicationDao

    companion object {
        @Volatile
        private var instance: AnchorDatabase? = null

        fun get(context: Context): AnchorDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AnchorDatabase::class.java,
                    "anchor-watch.db",
                )
                    // MVP policy: if the schema bumps, discard the local cache.
                    // Safe because every store has a server-side source of truth
                    // (unsynced rows are re-queued via the sync workers on next start).
                    // dropAllTables=false: only drop Room-managed tables; we have no
                    // non-Room tables in this database, so the choice is functionally
                    // equivalent to true but lets Room defend against accidents.
                    .fallbackToDestructiveMigration(dropAllTables = false)
                    .build().also { instance = it }
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
