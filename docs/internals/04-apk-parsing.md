# Milestone 4 — Reading an APK: ZIP structure & package parsing

The APK Viewer is the first "analysis" feature. It reads an APK two ways — as a
raw **ZIP archive** and as an **Android package** — and shows both, entirely
read-only.

```
Explorer taps an .apk → ApkViewer(refArg)
  → ApkViewerViewModel → AnalyzeApkUseCase(ref)
      storage.openInputStream(ref)         // SAF, read-only
        → ApkRepository.analyze(stream)     // :data:apk  (ReadCapability)
            → ApkSummary  (immutable)
```

---

## 1. An APK *is* a ZIP

An `.apk` is a plain ZIP with a specific layout:

```
AndroidManifest.xml      ← binary-encoded XML (not text!)
classes.dex, classes2.dex…← Dalvik bytecode
resources.arsc           ← compiled resource table
res/…                    ← compiled resources (drawables, layouts…)
lib/<abi>/…              ← native libraries
assets/…                 ← raw assets
META-INF/                ← signatures (MANIFEST.MF, *.SF, *.RSA/.DSA/.EC)
```

We enumerate the **ZIP central directory** with `java.util.zip.ZipFile`: for each
entry we read its name, uncompressed size, **compressed** size, and compression
**method** (`STORED` vs `DEFLATED`). That's the archive's own table of contents —
no Android APIs involved, just the ZIP format. It's why the Contents tab can show
`resources.arsc` sitting `STORED` (uncompressed, so it can be `mmap`ed at runtime)
while code and XML are `DEFLATED`.

## 2. Why we stage a temp copy

The platform's package parser, `PackageManager.getPackageArchiveInfo(path,
flags)`, needs a real filesystem **path** — it can't read a SAF `content://`
Uri. Our source is a content Uri, so we:

1. copy the read-only stream into our **own cache** (`cacheDir`) as a temp file,
2. run `ZipFile` + `getPackageArchiveInfo` against that path,
3. `delete()` it in a `finally`.

That temp copy is a private implementation detail in the app's own sandbox; the
**user's APK is only ever read**. And `:data:apk` holds only `ReadCapability` —
it can't obtain a `WriteCapability`, so it cannot touch the Workspace or any user
storage. Staging to `cacheDir` needs no permission (app-private).

## 3. What the platform parser gives us

`getPackageArchiveInfo` parses the **binary AndroidManifest.xml** and the
resource table for us and returns a `PackageInfo`:

- `packageName`, `versionName`, and the version code
  (`longVersionCode` on API 28+, `versionCode` below — handled with a compat
  helper),
- `applicationInfo.minSdkVersion` / `targetSdkVersion` / `compileSdkVersion`
  (the last is API 31+, so it's read behind a version check),
- `requestedPermissions` (with the `GET_PERMISSIONS` flag).

To load the app's display **label**, we point `applicationInfo.sourceDir` /
`publicSourceDir` at our temp file and call `loadLabel(pm)` — the documented
trick for reading resources out of an un-installed archive. It's wrapped in
`runCatching`, so a weird/missing label degrades to the package name instead of
throwing.

If the file isn't a valid package at all (say, a normal `.zip`),
`getPackageArchiveInfo` returns `null`; we still show every ZIP entry and simply
mark the package fields as unknown. **No crash, useful output either way.**

## 4. Binary XML — noted for later

We rely on the framework to decode `AndroidManifest.xml`. That file is *binary*
XML (a string pool + typed nodes), not text — opening it raw shows gibberish.
Writing a from-scratch AXML decoder (string pool → resource map → start/end
tags) is a great deeper exercise and a natural companion to the `resources.arsc`
work in milestone 8; this milestone deliberately leans on the platform parser so
the feature is robust first.

## 5. Navigation with a non-trivial argument

Tapping an APK pushes a real detail destination. The argument is a `StorageRef`
whose `raw` can contain a control-character separator — not something to shove
into a route string. So `StorageRefArgs` JSON-serializes the ref and URL-safe
Base64-encodes it; the destination decodes it back to the exact same ref. The
route itself stays a clean `@Serializable data class ApkViewer(val refArg:
String)`, and the back stack persists it across process death like any other
type-safe arg.

## 6. UI: tabs, monospace, and cheap highlighting

- `TabRow` with a `rememberSaveable` selected index (survives rotation).
- **Overview** cards list package/version/SDK facts and archive stats.
- **Contents** renders entry names in a monospaced style (they're paths, and
  monospace makes structure scan-able), highlighting the notable ones
  (`AndroidManifest.xml`, `*.dex`, `resources.arsc`, `META-INF/…`) and tagging
  each with its `STORED`/`DEFLATED` method.
- Everything is a `LazyColumn`, so an APK with thousands of `res/` entries scrolls
  without building thousands of composables at once.

---

## Try it / observe it

- Point the Explorer at a folder with an `.apk`, tap it → Overview shows package,
  versions and SDK levels; Contents lists the ZIP entries with sizes/methods.
- Note `resources.arsc` is usually `STORED` while code/XML are `DEFLATED`.
- Try a non-APK `.zip` (rename one) → you still get a full Contents listing with
  the package fields marked unknown, and no crash.

> Build note: `:core:model`, `:core:capability`, `:core:usecase` compile-verified
> here. `:data:apk` and `:app` need the Android SDK (unavailable in this env) and
> are validated by review plus your local build.
