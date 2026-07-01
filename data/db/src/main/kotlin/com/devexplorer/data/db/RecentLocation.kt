package com.devexplorer.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room entity for the recents history. We persist the encoded [refRaw] and the
 * [StorageRef.Kind] name so we can rebuild the exact domain ref later; the
 * persisted URI permission (taken in :data:storage) keeps the grant valid across
 * restarts.
 */
@Entity(tableName = "recent_locations")
data class RecentLocationEntity(
    @PrimaryKey val refRaw: String,
    val kind: String,
    val label: String,
    val lastOpenedAt: Long,
)

@Dao
interface RecentLocationDao {

    // A Flow query: Room re-emits automatically whenever the table changes.
    @Query("SELECT * FROM recent_locations ORDER BY lastOpenedAt DESC LIMIT 20")
    fun observe(): Flow<List<RecentLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentLocationEntity)

    @Query("DELETE FROM recent_locations WHERE refRaw = :refRaw")
    suspend fun delete(refRaw: String)
}
