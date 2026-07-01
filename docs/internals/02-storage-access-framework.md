# Milestone 2 — The Explorer & the Storage Access Framework

This milestone makes DevExplorer actually browse your files. It's also the first
time the full Clean-Architecture stack is exercised end to end:

```
ExplorerScreen (Compose)                       :app
   → ExplorerViewModel (state + events)        :app
      → ListDirectory / OpenDocumentTree       :core:usecase   (pure Kotlin)
         → StorageRepository (interface)        :core:capability (pure Kotlin)
            → SafStorageRepository (SAF impl)    :data:storage   (Android)
```

Below, each decision is tied to how Android actually works.

---

## 1. Why the Storage Access Framework (and not `File`)?

On modern Android you **cannot** just open `/storage/emulated/0/...` with
`java.io.File`. Since Android 10 (API 29), **scoped storage** removed broad
filesystem access: an app sees its own sandbox and media it owns, and *nothing
else* — no `READ_EXTERNAL_STORAGE` will give you the whole disk anymore.

The sanctioned way to let a user grant access to arbitrary folders is the
**Storage Access Framework (SAF)**. The user picks a folder in the system UI, and
the OS hands your app a **tree Uri** representing a grant to *that subtree only*.
This is exactly the behavior DevExplorer wants: **permission-honest by
construction** — we can't read anything the user didn't explicitly choose,
because the OS is the gatekeeper.

That's why our request needs **no storage permission in the manifest at all**.
The grant rides on the Uri, not on a manifest permission.

## 2. The picker: `ActivityResultContracts.OpenDocumentTree`

```kotlin
val pickFolder = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
) { uri: Uri? -> if (uri != null) onEvent(ExplorerEvent.TreePicked(uri.toString())) }
```

**Under the hood:** launching this fires an `Intent(ACTION_OPEN_DOCUMENT_TREE)`
that resolves to the system Documents UI (a different app/process). When it
returns, the result `Intent` carries a Uri plus **grant flags**
(`FLAG_GRANT_READ_URI_PERMISSION`, and a *persistable* flag). The Activity
Result API is the modern replacement for `onActivityResult` — it survives config
changes and process death because the framework registers the callback against
the Activity's saved-state registry.

## 3. Persisting the grant

A URI grant is normally tied to your process lifetime. To keep it across app
restarts:

```kotlin
resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
```

We deliberately take **only READ** — the picker also offers write, but the System
zone must stay read-only, so we drop the write grant on the floor. The OS stores
persisted grants per-app (there's a system limit, ~128–512 depending on
version); `contentResolver.persistedUriPermissions` can enumerate them (we'll use
that in milestone 9 to restore the last location).

## 4. Listing a directory with `ContentResolver` + `DocumentsContract`

We intentionally skip the `DocumentFile` helper and talk to the provider
directly, because that's where the learning is:

```kotlin
val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
resolver.query(childrenUri, projection, null, null, null)?.use { cursor -> ... }
```

- A **documents provider** is a `ContentProvider` that exposes files as rows.
- `buildChildDocumentsUriUsingTree` builds a content Uri whose query returns the
  *children* of a folder, addressed by the folder's **document id** *within the
  granted tree*. You can never build a valid child Uri outside the tree — the
  containment is structural.
- `resolver.query(...)` crosses a **Binder** boundary into the provider's process
  and streams rows back through a `Cursor` (backed by a shared-memory
  `CursorWindow`). We read the standard `Document.COLUMN_*` columns
  (id, name, mime, size, last-modified) and map each row to our domain `FileNode`.
- `MIME_TYPE_DIR` distinguishes folders from files.

**The two-part address problem.** To re-list a subfolder later, we need *both*
the granted tree Uri (which carries the permission) and the subfolder's document
id. Our domain `StorageRef` is a single opaque string, so `SafLocation` packs the
two together (separated by a control char) and unpacks them at the data boundary.
The domain layer stays framework-free and never learns this encoding — a small
but real example of keeping platform detail out of the core.

## 5. Threading & why the UI never jank-freezes

Every provider `query` is IO — it can block. So the repository wraps its work in
`withContext(Dispatchers.IO)`, and the ViewModel calls it from `viewModelScope`
(main-safe coroutines). The `Cursor` is always closed via Kotlin's `use { }`
(a `try/finally`), so we never leak a `CursorWindow`.

Result: the main thread only ever touches immutable `FileNode` snapshots; the
disk/Binder work happens on a background dispatcher.

## 6. Navigation modeled as a breadcrumb stack

Rather than push a nav destination per folder, the ViewModel keeps a
`List<Crumb>` in its state. Opening a folder pushes; "up" pops; tapping a
breadcrumb truncates. The system back button is wired via `BackHandler(enabled =
canNavigateUp)` so hardware/gesture back navigates *within* the Explorer before
leaving it. This keeps folder navigation as cheap in-state changes and makes the
whole path trivially restorable later.

## 7. State discipline (UDF)

`ExplorerUiState` is a single immutable snapshot; the screen renders it and emits
`ExplorerEvent`s; the ViewModel is the only place that mutates state (via
`MutableStateFlow.update { }`). Failures never crash: use-cases return
`Result`, and the ViewModel maps exceptions (e.g. `SecurityException` when a
grant was revoked) to a friendly message with a **Retry** action. The screen
collects with `collectAsStateWithLifecycle`, which stops collecting when the UI
is stopped — no wasted work, no leaks.

## 8. Insets correctness (a "no visual bugs" detail)

We run edge-to-edge with a top-level Scaffold (bottom nav) hosting per-screen
Scaffolds (top bar). Nested Scaffolds can each try to pad for the *same* system
bars, double-spacing content. The fix is to consume the insets once at the top:

```kotlin
Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)
```

so inner Scaffolds see zero remaining insets and lay out correctly on notched and
gesture-nav devices.

---

## Try it / observe it

- Tap the folder icon → pick a directory → see it listed, folders first.
- Drill into subfolders; use the breadcrumb or system back to go up.
- Revoke the app's access in system settings, return, tap Retry → you get the
  friendly "access denied" state, not a crash.
- Rotate mid-browse → the ViewModel retains your location and listing.

> Build note: `:core:model`, `:core:capability`, and `:core:usecase` were
> compile-verified in the authoring environment. `:data:storage` and `:app`
> require the Android SDK (unavailable here; Google Maven is blocked), so they're
> validated by review and by your local Android Studio / `./gradlew` build.
