package com.devexplorer.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.devexplorer.core.capability.AnalysisHistoryRepository
import com.devexplorer.core.capability.FavoritesRepository
import com.devexplorer.core.capability.RecentLocationsRepository

/**
 * Manual provider for the database and its repositories (milestone 9; Hilt takes
 * this over later). The [DevExplorerDatabase] is a process-wide singleton —
 * creating multiple instances would defeat Room's in-memory invalidation
 * tracker, so we build it once behind a double-checked lock.
 */
object DbModule {

    @Volatile
    private var database: DevExplorerDatabase? = null

    /**
     * v1 → v2: the analysis-history journal and pinned packages. Hand-written
     * DDL matching the entities exactly (column order, NOT NULL, PKs) — Room
     * validates the schema on open and there is no destructive fallback.
     */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `analysis_history` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`versionName` TEXT, " +
                    "`versionCode` INTEGER NOT NULL, " +
                    "`analyzedAt` INTEGER NOT NULL, " +
                    "`uncompressedBytes` INTEGER NOT NULL, " +
                    "`entryCount` INTEGER NOT NULL, " +
                    "`permissions` TEXT NOT NULL, " +
                    "`dexMethodRefs` INTEGER NOT NULL, " +
                    "`dexClasses` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorite_packages` (" +
                    "`packageName` TEXT NOT NULL, " +
                    "`addedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`packageName`))",
            )
        }
    }

    private fun database(context: Context): DevExplorerDatabase =
        database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                DevExplorerDatabase::class.java,
                "devexplorer.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { database = it }
        }

    fun recentLocationsRepository(context: Context): RecentLocationsRepository =
        RoomRecentLocationsRepository(database(context).recentLocationDao())

    fun analysisHistoryRepository(context: Context): AnalysisHistoryRepository =
        RoomAnalysisHistoryRepository(database(context).analysisSnapshotDao())

    fun favoritesRepository(context: Context): FavoritesRepository =
        RoomFavoritesRepository(database(context).favoritePackageDao())
}
