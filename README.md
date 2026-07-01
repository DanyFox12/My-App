# DevExplorer

A developer's file & package explorer for Android, built as a **learning vehicle
for Android internals**. Read-only by design, sandboxed by default, network-off
unless you turn it on.

> This repo is a guided build: one comprehensive app that touches every major
> Android subsystem, implemented in 10 milestones, each with a write-up of the
> internals behind it in [`docs/internals/`](docs/internals).

## What it does

- **Explorer** — browse files via the Storage Access Framework (read-only),
  with a recent-folders history.
- **APK Viewer** — open any APK (or installed app) and inspect it read-only:
  overview, permissions, **signing certificates & fingerprints**, resource
  breakdown, and the raw ZIP contents.
- **Packages** — list installed apps via `PackageManager`; tap one to analyze it
  in the same viewer.
- **Workspace** — the app's *only* writable surface: an app-private sandbox you
  copy files into, with rename/delete.
- **Code Viewer** — syntax-highlighted, read-only text/code viewing.
- **Settings** — Material You dynamic color, per-app language (English/العربية),
  and a network opt-in.

## Safety model (enforced, not promised)

- **Capability types gate writes at compile time.** `WriteCapability`'s only
  implementation is `internal` to `:data:workspace`, so no analysis/browsing
  module can obtain one — "APK analysis is read-only" is a compile-time fact.
- **One writable directory** (`filesDir/workspace`), with directory-traversal
  defense (canonical-path containment).
- **Sources are opened read-only** via `ContentResolver`/SAF — the OS enforces
  access.
- **No network** code path runs unless you enable it in Settings.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full design.

## Architecture

Multi-module, Clean Architecture + MVVM + unidirectional data flow:

```
:app  →  feature screens (packages under :app)
      →  :core:model | :core:capability | :core:usecase   (pure Kotlin domain)
      →  :core:designsystem                                (Material 3 UI kit)
      →  :data:storage | :data:apk | :data:packages
         :data:workspace | :data:db | :data:work           (Android implementations)
```

Dependencies point downward only; the domain never imports Android.

## Tech stack

Kotlin · Jetpack Compose · Material 3 · Navigation-Compose (type-safe) ·
Coroutines/Flow · Room · WorkManager · SAF · single-Activity.

- **minSdk 24**, targetSdk/compileSdk 35 — runs on old and new phones; every
  newer-API feature (dynamic color, `longVersionCode`, per-app locales…) is
  gated at runtime.
- **Performance-aware**: a `DevicePerformanceTier`/`PerformanceBudget` detected
  at startup scales motion, blur, cache sizes, and list prefetch to the device.

## Building

Open in Android Studio (Ladybug or newer), or:

```bash
./gradlew :app:assembleDebug
```

Requires an Android SDK. The Gradle wrapper is pinned to 8.10.2; the build
targets AGP 8.7.3 / Kotlin 2.1.0.

## Milestones & internals

| # | Milestone | Internals write-up |
|---|-----------|--------------------|
| 1 | Skeleton, single-Activity, theme, nav | [01](docs/internals/01-skeleton-and-lifecycle.md) |
| 2 | Explorer + Storage Access Framework | [02](docs/internals/02-storage-access-framework.md) |
| 3 | Workspace sandbox + capability model | [03](docs/internals/03-workspace-sandbox.md) |
| 4 | APK Viewer (ZIP + package parsing) | [04](docs/internals/04-apk-parsing.md) |
| 5 | Packages (PackageManager) | [05](docs/internals/05-package-manager.md) |
| 6 | Digital signatures & certificates | [06](docs/internals/06-signatures.md) |
| 7 | Code Viewer & syntax highlighting | [07](docs/internals/07-text-rendering.md) |
| 8 | Resources (res/, arsc, assets) | [08](docs/internals/08-resources.md) |
| 9 | Room history + WorkManager | [09](docs/internals/09-persistence-and-background.md) |
| 10 | Localization, theming, motion | [10](docs/internals/10-localization-and-theming.md) |
