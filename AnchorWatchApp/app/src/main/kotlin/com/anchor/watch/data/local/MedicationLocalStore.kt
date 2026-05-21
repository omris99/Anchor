package com.anchor.watch.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val scheduledTime: String,
    val status: String,
    val userId: String,
    val isSynced: Boolean = true,
    val statusTimestamp: Long? = null,
)

@Dao
interface MedicationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MedicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<MedicationEntity>)

    @Query("SELECT * FROM medications WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): MedicationEntity?

    @Query("SELECT * FROM medications")
    suspend fun all(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE isSynced = 0 ORDER BY statusTimestamp ASC")
    suspend fun unsynced(): List<MedicationEntity>

    @Query("UPDATE medications SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}

interface MedicationStore {
    suspend fun upsert(entity: MedicationEntity)
    suspend fun upsertAll(entities: List<MedicationEntity>)
    suspend fun byId(id: String): MedicationEntity?
    suspend fun all(): List<MedicationEntity>
    suspend fun unsynced(): List<MedicationEntity>
    suspend fun markSynced(id: String)
}

class MedicationLocalStore(context: Context) : MedicationStore {
    private val dao = AnchorDatabase.get(context).medicationDao()
    override suspend fun upsert(entity: MedicationEntity) = dao.upsert(entity)
    override suspend fun upsertAll(entities: List<MedicationEntity>) = dao.upsertAll(entities)
    override suspend fun byId(id: String): MedicationEntity? = dao.byId(id)
    override suspend fun all(): List<MedicationEntity> = dao.all()
    override suspend fun unsynced(): List<MedicationEntity> = dao.unsynced()
    override suspend fun markSynced(id: String) = dao.markSynced(id)
}

object MedicationStatus {
    const val PENDING = "pending"
    const val TAKEN = "taken"
    const val MISSED = "missed"
}
