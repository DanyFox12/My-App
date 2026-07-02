package com.devexplorer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's Room database. Room generates the concrete implementation at build
 * time (via KSP) and verifies every DAO query against the schema, so a typo in
 * SQL is a compile error, not a runtime crash.
 *
 * Version history:
 *  1 — recent_locations (milestone 9)
 *  2 — analysis_history + favorite_packages (update tracking & pinned apps);
 *      migrated explicitly in [DbModule] — there is no destructive fallback.
 */
@Database(
    entities = [
        RecentLocationEntity::class,
        AnalysisSnapshotEntity::class,
        FavoritePackageEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class DevExplorerDatabase : RoomDatabase() {
    abstract fun recentLocationDao(): RecentLocationDao
    abstract fun analysisSnapshotDao(): AnalysisSnapshotDao
    abstract fun favoritePackageDao(): FavoritePackageDao
}
