# Milestone 1 — Skeleton, and the Android internals behind it

This is the companion write-up for the first buildable slice of DevExplorer:
the multi-module skeleton, the single-Activity + Compose host, the Material 3
theme with dynamic-color fallback, and the bottom-nav navigation graph.

Everything below maps a decision in the code to *why Android works that way*.

---

## 1. Why a single Activity?

`MainActivity` is the only `Activity` in the app. Every "screen" is a Composable
reached through the `NavHost`, not a separate Activity.

**Under the hood:** an `Activity` is a heavyweight, system-managed component with
its own window, task/back-stack entry, and lifecycle driven by the framework via
Binder calls from `ActivityManagerService`. Historically each screen was an
Activity, which meant many lifecycles, `Intent` plumbing between them, and
`startActivityForResult` dances. The modern model collapses that into **one**
Activity whose content is swapped in-process by Compose Navigation. You get one
lifecycle to understand deeply — exactly what we want for learning — and screen
transitions become cheap function recompositions instead of process-level
Activity launches.

Key line — `MainActivity.onCreate`:
```kotlin
enableEdgeToEdge()
setContent { DevExplorerTheme { DevExplorerApp() } }
```
`setContent` installs a `ComposeView` as the Activity's content and bridges the
Compose runtime to the Activity/Window lifecycle.

## 2. Configuration changes & the lifecycle

Rotate the device and the framework **destroys and recreates** the Activity
(`onDestroy` → `onCreate`) so it can load configuration-specific resources
(e.g. `values-land`, a different density bucket). This is the classic Android
gotcha: naïve state held in the Activity is lost.

Two mechanisms save us (wired up more in later milestones, but the plumbing
lands now):
- **`ViewModel`** lives in a `ViewModelStore` that is *retained* across the
  config-change recreation, so state survives rotation without serialization.
- **`SavedStateHandle` / `rememberSaveable`** persist small state into the
  `onSaveInstanceState` `Bundle`, which also survives **process death** (when the
  OS reclaims your app in the background and later rebuilds it).

The distinction — retained-across-config-change vs survives-process-death — is
one of the most important things to internalize about Android, and this app is
structured to make both observable.

## 3. Compose: the three phases

`DevExplorerApp` and every screen are `@Composable` functions. Compose renders in
three phases:

1. **Composition** — run the composable functions to build/patch the UI tree.
2. **Layout** — measure and place nodes.
3. **Drawing** — paint to the canvas.

The performance rule that follows: keep *composition* small and cheap. We do that
by hoisting state up and passing plain values + lambdas down (our screens take a
`UiState` and emit events), so when state changes, only the composables that read
that state recompose — not the whole tree. `LazyColumn` (used from milestone 2)
recycles by `key`, reusing slots instead of recreating them.

## 4. The module graph is an architecture, not just folders

```
:app → :core:designsystem → :core:model
:app → :core:model
```

- **`:core:model`** is a *pure Kotlin/JVM* module. It has no Android dependency,
  so it compiles without AAPT2 or a manifest (fast), and its types are unit-
  testable on a plain JVM. This is why our domain types (`StorageRef`, `Zone`,
  `FileNode`, `DevicePerformanceTier`) deliberately avoid `android.net.Uri` /
  `java.io.File`.
- **`:core:designsystem`** is an Android *library* module: it needs `Context`
  (for dynamic color and capability detection) and Compose.
- **Non-transitive R classes** (`android.nonTransitiveRClass=true`) mean each
  module gets its own `R` with only its resources — smaller, faster, and it
  forces clean dependencies.

At build time Gradle compiles modules in parallel and R8 can shrink per-module;
at package time the manifest merger stitches each module's `AndroidManifest.xml`
into one. Our library manifests are empty (`<manifest/>`) because they contribute
no components yet.

## 5. Navigation: type-safe routes and the back stack

Routes are `@Serializable` objects (`Explorer`, `Packages`, `Workspace`,
`Settings`) instead of string templates. The compiler now checks navigation
arguments, and kotlinx-serialization encodes/decodes them (hence the ProGuard
keep rules — R8 must not strip the generated serializers in release).

The bottom bar uses Material's **multiple back stack** pattern:
```kotlin
navController.navigate(dest.route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```
- `saveState`/`restoreState` preserve each tab's own back stack and scroll state.
- `launchSingleTop` prevents pushing a duplicate of a destination already on top.

**Under the hood:** every `NavBackStackEntry` is itself a `LifecycleOwner` with
its *own* `ViewModelStore` and `SavedStateRegistry`. Navigation persists the back
stack to a `Bundle`, so it (and each entry's saved state) survives process death.
That's why a ViewModel scoped to a nav entry is cleared exactly when that entry
is popped — not before.

## 6. Making it beautiful *and* correct on every phone

Two explicit goals: gorgeous on new hardware, flawless on old.

- **Dynamic color with a fallback** (`DevExplorerTheme`): on Android 12+ we pull
  `dynamicLightColorScheme`/`dynamicDarkColorScheme` from the wallpaper palette;
  below API 31 we fall back to the curated brand scheme. The single
  `Build.VERSION.SDK_INT >= S` branch is the whole compatibility story — old
  phones never call an API they don't have.
- **Edge-to-edge** with transparent system bars, and a `Scaffold` that consumes
  window insets so content never hides under the status/navigation bars —
  correct on notched and gesture-nav devices alike.
- **A pre-Compose host theme** (`values/` + `values-night/`) whose window
  background matches the Compose background, so there's no white flash on launch,
  in light or dark.

## 7. Respecting device power (the performance budget)

`DeviceCapabilities.detect()` classifies the device once at startup using cheap,
always-available signals:
- `ActivityManager.isLowRamDevice` — the OS's own "please be frugal" flag; if set
  we immediately choose the `Low` tier.
- total RAM (`ActivityManager.MemoryInfo.totalMem`), CPU core count, and API level.

The result is a `DevicePerformanceTier` → `PerformanceBudget` that centralizes
every expensive knob (rich motion on/off, blur on/off, crossfade duration, cache
size, list prefetch distance). It's provided to the whole UI via
`LocalPerformanceBudget`. From here on, no screen hard-codes "is this an old
phone?" — it reads the budget. That keeps the budget phone smooth and lets the
flagship show off, from one codebase.

**Why this matters internally:** effects like `RenderEffect` blur are GPU-bound
and can tank frame times on weak GPUs; unbounded caches invite `OutOfMemoryError`
on low-heap devices (the per-app heap limit is set by the device's RAM class).
Budgeting these is how you avoid jank and crashes on the long tail of Android
hardware.

---

## Try it / observe it (once built with the Android SDK)

- Rotate the device on a screen → Activity recreates; nav position is retained.
- Switch tabs, scroll, switch back → each tab restored its own state.
- Toggle system dark mode → theme + host window background follow, no flash.
- Run on an emulator configured as a low-RAM device → confirm the `Low` budget is
  selected (a debug log hook is added in a later milestone).

> Build note: this repo pins the Gradle wrapper to 8.10.2 and targets AGP 8.7.3.
> Open it in Android Studio (Ladybug or newer) or run `./gradlew :app:assembleDebug`
> with an Android SDK installed. The environment used to author this milestone had
> no Android SDK, so only the pure-Kotlin `:core:model` module was compile-checked
> here; the Android modules are validated by review and by your local build.
