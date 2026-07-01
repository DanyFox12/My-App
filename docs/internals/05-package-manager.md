# Milestone 5 — PackageManager & package visibility

The Packages tab lists installed apps and lets you open any of them in the same
APK Viewer built in milestone 4. It's a tour of `PackageManager` and the modern
**package-visibility** model.

---

## 1. `PackageManager` is your window into installed apps

`PackageManager` is the system service (an IPC proxy to `PackageManagerService`)
that knows every installed package. We call:

```kotlin
val installed: List<PackageInfo> = pm.getInstalledPackages(0)
```

Each `PackageInfo` carries an `applicationInfo` with the fields we surface:

- `loadLabel(pm)` → the app's display name (resolved from its resources),
- `flags and FLAG_SYSTEM` → whether it's a system app (pre-installed on the
  system image) vs a user-installed one,
- `minSdkVersion` / `targetSdkVersion`,
- `sourceDir` → the path to the app's **base APK** on disk,
- version code via `longVersionCode` (API 28+) with a `versionCode` fallback,
- `firstInstallTime` / `lastUpdateTime`.

We map each to our framework-free `InstalledPackage` and sort by label. Loading a
label per app touches that app's resources, so the whole list build runs on
`Dispatchers.IO` behind a spinner.

## 2. Package visibility (Android 11+) — why `QUERY_ALL_PACKAGES`

Before Android 11, any app could enumerate every other app. That was a privacy
leak (installed apps fingerprint a user), so **API 30 tightened visibility**: by
default your app only "sees" itself, apps it interacts with, and a few
categories. `getInstalledPackages()` now returns a *filtered* list unless you
declare visibility.

For a genuine *packages explorer*, the full list is the point, so
`:data:packages` declares:

```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
```

Declaring it in the data module (not the app) keeps the requirement next to the
code that needs it; the manifest merger folds it into the final app manifest.

> Trade-off worth knowing: Google Play restricts `QUERY_ALL_PACKAGES` to apps
> whose core function needs it. That's fine for a personal/learning build. A Play
> release would either justify it in the console or, for targeted needs, use a
> `<queries>` element to declare exactly which packages/intents it must see —
> the privacy-preserving alternative.

## 3. Reusing the APK Viewer for installed apps

The elegant payoff: tapping an installed package opens the **same** ApkViewer as
a file APK. We do it by unifying both sources behind `StorageRef`:

```kotlin
// AnalyzeApkUseCase
val input = when (source.kind) {
    StorageRef.Kind.InstalledPackage -> packages.openApk(source.raw)  // base.apk
    else -> storage.openInputStream(source)                          // SAF file
}
input.use { apk.analyze(it) }
```

`PackagesRepository.openApk(packageName)` opens `applicationInfo.sourceDir` as a
**read-only** `FileInputStream`. From there the analysis is byte-for-byte the
same pipeline as a file APK: enumerate the ZIP, parse the package. One viewer,
two sources, zero duplication — and still entirely read-only (the module holds
only `ReadCapability`).

Navigation just encodes a package ref:
`openApk(StorageRef.installedPackage(pkg))`, which `StorageRefArgs` turns into
the route-safe argument the viewer already understands.

## 4. UI: search + system filter as UDF

`PackagesUiState` holds the full loaded list plus a `query` and an
`includeSystem` flag. Search filtering is a **derived** property of the state
(`visible`), so typing filters instantly with no repository round-trip. Toggling
"show system apps" *does* reload, because whether to include system apps is
decided where the data is read. Both are plain events reduced by the ViewModel —
the same unidirectional pattern as every other screen.

System apps are hidden by default: the list is long, and user apps are what you
usually want. A `SYSTEM` badge marks the rest when you enable them.

---

## Try it / observe it

- Open the Packages tab → your user apps list, searchable; toggle "system apps"
  to include the platform packages (each badged `SYSTEM`).
- Tap any app → the ApkViewer opens on its base APK: same Overview / Permissions
  / Contents tabs as a file APK.
- On Android 11+ without `QUERY_ALL_PACKAGES`, you'd see a truncated list — the
  permission is what makes the full enumeration possible.

> Build note: `:core:model`, `:core:capability`, `:core:usecase` compile-verified
> here. `:data:packages` and `:app` need the Android SDK (unavailable in this
> env) and are validated by review plus your local build.
