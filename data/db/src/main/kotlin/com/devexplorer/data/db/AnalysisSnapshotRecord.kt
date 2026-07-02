package com.devexplorer.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Room entity for the analysis-history journal. Permissions are persisted as a
 * newline-joined string — a single sorted list, no relational table needed for
 * a display-and-diff feature.
 */
@Entity(tableName = "analysis_history")
data class AnalysisSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val analyzedAt: Long,
    val uncompressedBytes: Long,
    val entryCount: Int,
    val permissions: String,
    val dexMethodRefs: Int,
    val dexClasses: Int,
)

@Dao
interface AnalysisSnapshotDao {

    @Query("SELECT * FROM analysis_history WHERE packageName = :packageName ORDER BY analyzedAt DESC LIMIT 1")
    suspend fun latestFor(packageName: String): AnalysisSnapshotEntity?

    @Query(
        "SELECT * FROM analysis_history WHERE packageName = :packageName " +
            "AND versionCode != :versionCode ORDER BY analyzedAt DESC LIMIT 1",
    )
    suspend fun latestOtherVersion(packageName: String, versionCode: Long): AnalysisSnapshotEntity?

    @Insert
    suspend fun insert(entity: AnalysisSnapshotEntity)

    /** Keep only the [keep] newest rows for one package (change journal, not archive). */
    @Query(
        "DELETE FROM analysis_history WHERE packageName = :packageName AND id NOT IN " +
            "(SELECT id FROM analysis_history WHERE packageName = :packageName " +
            "ORDER BY analyzedAt DESC LIMIT :keep)",
    )
    suspend fun prune(packageName: String, keep: Int)
}
