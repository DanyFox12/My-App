package com.devexplorer.data.db

import android.content.Context
import androidx.room.Room
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

    private fun database(context: Context): DevExplorerDatabase =
        database ?: synchronized(this) {
            database ?: Room.databaseBuilder(
                context.applicationContext,
                DevExplorerDatabase::class.java,
                "devexplorer.db",
            ).build().also { database = it }
        }

    fun recentLocationsRepository(context: Context): RecentLocationsRepository =
        RoomRecentLocationsRepository(database(context).recentLocationDao())
}
