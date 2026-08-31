package com.anchor.watch.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "water_reminders")
data class WaterEntity(
    @PrimaryKey val id: String,
    val scheduledTime: String,
    val status: String,
    val userId: String,
    val isSynced: Boolean = true,
    val statusTimestamp: Long? = null,
    // Backend day codes 0=Sun..6=Sat. Empty = every day.
    val daysOfWeek: List<Int> = emptyList(),
)

@Dao
interface WaterDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WaterEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<WaterEntity>)

    @Query("SELECT * FROM water_reminders WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): WaterEntity?

    @Query("SELECT * FROM water_reminders")
    suspend fun all(): List<WaterEntity>

    @Query("SELECT * FROM water_reminders WHERE isSynced = 0 ORDER BY statusTimestamp ASC")
    suspend fun unsynced(): List<WaterEntity>

    @Query("UPDATE water_reminders SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}

interface WaterStore {
    suspend fun upsert(entity: WaterEntity)
    suspend fun upsertAll(entities: List<WaterEntity>)
    suspend fun byId(id: String): WaterEntity?
    suspend fun all(): List<WaterEntity>
    suspend fun unsynced(): List<WaterEntity>
    suspend fun markSynced(id: String)
}

class WaterLocalStore(context: Context) : WaterStore {
    private val dao = AnchorDatabase.get(context).waterDao()
    override suspend fun upsert(entity: WaterEntity) = dao.upsert(entity)
    override suspend fun upsertAll(entities: List<WaterEntity>) = dao.upsertAll(entities)
    override suspend fun byId(id: String): WaterEntity? = dao.byId(id)
    override suspend fun all(): List<WaterEntity> = dao.all()
    override suspend fun unsynced(): List<WaterEntity> = dao.unsynced()
    override suspend fun markSynced(id: String) = dao.markSynced(id)
}

object WaterStatus {
    const val PENDING = "pending"
    const val TAKEN = "taken"
    const val MISSED = "missed"
}
