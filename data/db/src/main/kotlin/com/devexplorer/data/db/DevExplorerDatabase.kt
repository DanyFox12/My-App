package com.devexplorer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The app's Room database. Room generates the concrete implementation at build
 * time (via KSP) and verifies every DAO query against the schema, so a typo in
 * SQL is a compile error, not a runtime crash.
 */
@Database(
    entities = [RecentLocationEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class DevExplorerDatabase : RoomDatabase() {
    abstract fun recentLocationDao(): RecentLocationDao
}
