# Enhancement — extracting APK entries & editing in the sandbox

Two additions that go *beyond reading* without weakening the safety model: pull
a single entry out of an APK, and edit a Workspace file in place. Both writes go
through the same capability gate as every other mutation — a `WriteCapability`
mintable only in `:data:workspace` (see [Milestone 3](03-workspace-sandbox.md)).
The source APK is still never modified; the only thing that ever changes is a
file inside `filesDir/workspace`.

```
APK Viewer › Contents › [extract]
  → ApkViewerViewModel.extract(entryName)
      → ExtractEntryUseCase(source, entryName)          // holds a WriteCapability
          storage/packages.open(source)                 // READ-ONLY source
            → ZipInputStream → find entry
              → workspace.importStream(leaf, zip, cap)   // the sole writer

Workspace › tap item  /  ⋮ › Edit
  → WorkspaceEditorViewModel
      readText(item)                                     // read, no token
      saveText(item, text)  → workspace.writeText(item, text, cap)  // gated write
```

---

## 1. Extract: read one entry, write one file

`ExtractEntryUseCase` opens the archive **read-only** — a SAF document via
`StorageRepository`, or an installed package's base APK via `PackagesRepository`,
exactly like `AnalyzeApkUseCase`. It then streams the ZIP central directory with
`ZipInputStream` until the requested entry is found and hands that stream
straight to `WorkspaceRepository.importStream`. Because `ZipInputStream` reports
EOF at the end of the current entry, only that entry's bytes are copied; the leaf
name is sanitized by the sandbox writer (so `res/layout/main.xml` lands as
`main.xml`, and traversal names are rejected).

The use-case is constructed with an injected `WriteCapability`, so — like
`CopyIntoWorkspaceUseCase` — it can only be *built* by code holding a token. The
APK Viewer's ViewModel obtains one from `WorkspaceModule` for this single action.

## 2. Edit: the one in-place write

`WorkspaceRepository` gains two methods: `readText` (read-only, no token) and
`writeText` (gated by `WriteCapability`). `writeText` resolves the target inside
the sandbox root, re-checks containment (`requireInside`), and overwrites the
file's UTF-8 contents — the only in-place edit path in the app. The editor
ViewModel loads text through `ReadWorkspaceTextUseCase`, tracks an `isDirty`
flag, and saves through `SaveWorkspaceTextUseCase`.

## 3. Why the guarantee still holds

Nothing here can touch a source APK: analysis modules still can't obtain a
`WriteCapability`, and the two new writes are just more callers of the existing
sandbox writer. The domain logic is Android-free and unit-tested —
`ExtractEntryUseCaseTest` builds a real in-memory ZIP and asserts the extracted
leaf name and bytes (including the installed-package path and the not-found
failure); `WorkspaceTextEditTest` round-trips a save/read edit.
