# Milestone 9 — Room persistence & WorkManager

Two Android workhorses: **Room** (local database) gives the Explorer a reactive
"recent folders" history; **WorkManager** runs guaranteed background cleanup.

---

## 1. Room: a typed, compile-checked SQLite layer

Room generates a SQLite implementation from annotated Kotlin at build time (via
**KSP**). The three pieces:

- **Entity** — a table row (`@Entity RecentLocationEntity`).
- **DAO** — queries as methods (`@Query`, `@Insert`). Room **verifies the SQL
  against the schema at compile time**, so a typo in a column name is a build
  error, not a 2 a.m. crash.
- **Database** — ties entities + DAOs together (`@Database ... RoomDatabase`).

### Reactive queries

The key line:

```kotlin
@Query("SELECT * FROM recent_locations ORDER BY lastOpenedAt DESC LIMIT 20")
fun observe(): Flow<List<RecentLocationEntity>>
```

Returning a `Flow` hooks the query into Room's **invalidation tracker**. When any
write touches `recent_locations`, Room re-runs the query and emits a fresh list.
The Explorer just `collect`s it — open a folder, and it appears at the top of the
recents with **no manual refresh**. That's the reactive-persistence pattern the
milestone-3 Workspace's `ON_START` refresh was a stopgap for.

### One database instance

`DbModule` builds the database once behind a double-checked lock. Multiple
`RoomDatabase` instances would each have their *own* invalidation tracker and
connection pool — updates in one wouldn't notify the other. A process-wide
singleton is mandatory, not just tidy.

### Keeping the domain clean

The entity stores the encoded `StorageRef.raw` + kind name; the repository maps
`Entity ↔ RecentLocation` at the boundary, so the domain never sees Room. The
persisted URI permission taken back in milestone 2 keeps a recent folder's grant
valid across restarts, so reopening one just works.

## 2. WorkManager: guaranteed, constraint-aware background work

`CacheCleanupWorker` deletes stale analyzer temp files. As a `CoroutineWorker`,
its `doWork()` is a suspend function on a background executor:

```kotlin
class CacheCleanupWorker(...) : CoroutineWorker(...) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) { ... }
}
```

WorkManager persists the request in **its own SQLite DB**, so scheduled work
survives process death and reboot, and it chooses the right runtime under the
hood (`JobScheduler` on API 23+, alarms/`BroadcastReceiver` as fallback). We
schedule it periodically with a constraint:

```kotlin
PeriodicWorkRequestBuilder<CacheCleanupWorker>(1, TimeUnit.DAYS)
    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
```

and enqueue it uniquely with `ExistingPeriodicWorkPolicy.KEEP`, so calling it on
every app launch (from `Application.onCreate`) doesn't pile up duplicate
schedules. WorkManager itself is auto-initialized via its bundled `androidx.
startup` provider — no manual setup.

Two properties every Worker must respect, both satisfied here:
- **Idempotent** — deleting already-clean files is harmless, so a re-run (after a
  `Result.retry()`) is safe.
- **Deferrable** — nothing time-critical; the OS batches it into a good window
  (interacting with Doze) to save battery.

---

## Try it / observe it

- Open a folder in the Explorer, back out (or relaunch) → it's listed under
  **Recent**; tap to reopen instantly, or ✕ to remove. Open another → the list
  reorders live (reactive Room `Flow`).
- The cleanup worker runs about daily when the battery isn't low; it only ever
  removes the analyzer's own stale `analyze_*` temp files.

> Build note: `:core` modules compile-verified here. `:data:db` (Room/KSP),
> `:data:work` (WorkManager) and `:app` need the Android SDK and are validated by
> review plus your local build.
