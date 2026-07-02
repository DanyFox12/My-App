# Milestone 3 — The Workspace sandbox & the capability model

This milestone adds the app's **only writable surface** and turns the safety
promise ("APK analysis is read-only") into a **compile-time guarantee**. It also
introduces the read→write bridge: copying a System file into the sandbox.

---

## 1. Where writes are allowed — and why it needs no permission

The sandbox is `context.filesDir/workspace`. `filesDir` is **app-private
internal storage**: every app gets its own directory under
`/data/data/<pkg>/files`, owned by the app's Linux UID. Because it's yours,
**no permission is required** to read or write it — that's the flip side of
scoped storage. Uninstalling the app removes it; other apps can't see it.

This is deliberately the *entire* writable footprint of DevExplorer. Nothing
else in the app writes anywhere.

## 2. The capability model made real

```kotlin
// :core:capability
interface WriteCapability            // marker: the *type* is the permission

// :data:workspace
internal class WorkspaceWriteToken : WriteCapability   // the ONLY implementor
object WorkspaceModule { fun writeCapability(): WriteCapability = WorkspaceWriteToken() }
```

`WorkspaceWriteToken` has an **`internal`** visibility, so it can be constructed
only inside `:data:workspace`. Every write use-case demands a `WriteCapability`:

```kotlin
class CopyIntoWorkspaceUseCase(
    private val storage: StorageRepository,     // reads (SAF)
    private val workspace: WorkspaceRepository,  // writes (sandbox)
    private val capability: WriteCapability,     // proof, injected at construction
)
```

The consequence is structural: a module like `:data:apk` (milestone 4) **cannot
obtain a `WriteCapability`**, so it cannot even *construct* a write use-case, let
alone call one. "Read-only analysis" isn't a code-review rule you have to
remember — it's enforced by the type system and the module graph. The single
place a token is born (`WorkspaceModule.writeCapability()`) is trivially
auditable.

> This is the Kotlin/Gradle-idiomatic version of a capability. `internal` is
> per-module (enforced by the compiler across Gradle modules), so "only this
> module can mint the token" is a real boundary, not a naming convention.

## 3. Directory-traversal defense

A file explorer that writes is a classic place for path-traversal bugs. If a
source were named `../../databases/app.db`, a naïve `File(dir, name)` would
resolve *outside* the sandbox. Two layers stop that:

```kotlin
private fun sanitize(rawName: String): String =
    rawName.substringAfterLast('/').substringAfterLast('\\').trim()
        .ifBlank { "file" }.let { if (it == "." || it == "..") "file" else it }

private fun requireInside(file: File) {
    val root = workspaceDir.canonicalPath
    val path = file.canonicalPath                 // resolves .., symlinks, etc.
    if (path != root && !path.startsWith(root + File.separator))
        throw SecurityException("Path escapes the Workspace sandbox")
}
```

`canonicalPath` is the key: it resolves `..` segments and symlinks to a real
absolute path, which we then require to be *within* the sandbox root. Every
create/rename/delete calls `requireInside` before touching the disk. This is a
small, self-contained study of how traversal attacks work and how to neutralize
them.

## 4. The read → write bridge (one use-case, two repositories)

Copying a System file into the Workspace is the only path where reading meets
writing, and it's modeled cleanly:

```
Explorer (file row) → CopyToWorkspace(node)
  → CopyIntoWorkspaceUseCase(node)
      storage.openInputStream(node.ref)   // SAF read-only stream (ContentResolver)
        .use { input -> workspace.importStream(node.name, input, capability) }
```

- `StorageRepository.openInputStream` returns a **read-only** stream from the
  documents provider — we never open the source for write.
- `WorkspaceRepository.importStream` streams the bytes into a unique, sanitized
  file in the sandbox (`InputStream.copyTo`, both sides closed via `use { }`).
- The use-case orchestrates two decoupled repositories; neither depends on the
  other. This is Clean Architecture doing its job: the *policy* (read here, write
  there, only with a token) lives in one small, testable place.

Note the stream copy runs on `Dispatchers.IO`, so a large file never blocks the
UI, and the source is opened read-only, so the original is provably untouched.

## 5. State-driven dialogs & one-shot messages

The Workspace rename/delete dialogs are **driven entirely by state**
(`renameTarget`/`deleteTarget` in `WorkspaceUiState`). Rendering a dialog is just
"is this field non-null?", so dialogs survive recomposition and rotation for
free — no `DialogFragment`, no manual show/dismiss bookkeeping.

Transient feedback ("Copied to Workspace", "Deleted …") is a nullable `message`
in state, surfaced via a `Snackbar` in a `LaunchedEffect` and then **consumed**
(set back to null) so it fires exactly once, not again after a config change.

## 6. Keeping the two tabs in sync without a database (yet)

Copy a file in the Explorer, switch to the Workspace tab — it's there. Because
we don't have Room until milestone 9, the Workspace re-lists itself on
`Lifecycle.Event.ON_START` (`LifecycleEventEffect`), i.e. every time the tab
becomes visible. It's simple and correct, and the Workspace still uses it:
milestone 9 brings the reactive Room `Flow` pattern (recents, favorites), but
the Workspace lists a plain directory — there is no table to observe, so the
ON_START re-list remains the honest implementation.

---

## Try it / observe it

- In Explorer, tap the "move to inbox" icon on a file → snackbar confirms → open
  the Workspace tab and see it, badged with the amber Workspace zone.
- Rename and delete via the row's overflow menu; confirm dialogs survive rotation.
- Copy the same file twice → the second becomes `name (1).ext` (unique naming).
- The original file in the System zone is never modified — we only ever opened it
  for reading.

> Build note: `:core:model`, `:core:capability`, `:core:usecase` are
> compile-verified here. `:data:workspace`, `:data:storage`, and `:app` require
> the Android SDK (unavailable in this environment) and are validated by review
> plus your local build.
