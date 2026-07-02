package com.devexplorer.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Room entity for pinned packages in the Packages list. */
@Entity(tableName = "favorite_packages")
data class FavoritePackageEntity(
    @PrimaryKey val packageName: String,
    val addedAt: Long,
)

@Dao
interface FavoritePackageDao {

    @Query("SELECT * FROM favorite_packages")
    fun observe(): Flow<List<FavoritePackageEntity>>

    @Query("SELECT * FROM favorite_packages WHERE packageName = :packageName")
    suspend fun find(packageName: String): FavoritePackageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoritePackageEntity)

    @Query("DELETE FROM favorite_packages WHERE packageName = :packageName")
    suspend fun delete(packageName: String)
}
