package com.anchor.watch.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val userId: String,
    val status: String,
    val isSynced: Boolean,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Dao
interface CheckInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CheckInEntity)

    @Query("SELECT * FROM check_ins WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun unsynced(): List<CheckInEntity>

    @Query("UPDATE check_ins SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}

interface CheckInStore {
    suspend fun save(entity: CheckInEntity)
    suspend fun unsynced(): List<CheckInEntity>
    suspend fun markSynced(id: String)
}

class CheckInLocalStore(context: Context) : CheckInStore {
    private val dao = AnchorDatabase.get(context).checkInDao()
    override suspend fun save(entity: CheckInEntity) = dao.upsert(entity)
    override suspend fun unsynced(): List<CheckInEntity> = dao.unsynced()
    override suspend fun markSynced(id: String) = dao.markSynced(id)
}
