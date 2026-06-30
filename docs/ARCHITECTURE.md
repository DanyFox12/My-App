# DevExplorer — System Architecture

> A developer's file & package explorer for Android, built as a learning vehicle
> for Android internals. **Read-only by design**, sandboxed by default, network-off
> unless explicitly configured.

This document is the blueprint we'll implement against, module by module. Each
section ends with an **"Internals you'll meet here"** callout so the architecture
doubles as a syllabus.

---

## 0. Design Principles

These constraints are load-bearing — they shape every decision below.

| Principle | Architectural consequence |
|---|---|
| **Permission-honest** | We never touch a byte the OS hasn't granted us. All file access goes through the Storage Access Framework (SAF) or scoped app dirs — no raw `/data` walking, no root assumptions. |
| **Read-only on the outside** | APK/file *analysis* code physically cannot write. We enforce this with type-level separation: analysis returns immutable models; only the `workspace` module holds write capabilities. |
| **Sandbox is the only writable surface** | All mutation happens inside app-private storage clearly labeled "Workspace". The UI renders a hard visual boundary between *System* (read-only) and *Workspace* (read-write). |
| **Network-off by default** | No `INTERNET`-dependent code path runs unless the user enables a feature in Settings. The dependency graph makes this auditable: only one opt-in module may depend on a network client. |
| **Explain-as-you-go** | Each subsystem maps to a documented Android internal. The code is the textbook. |

**Capability model (enforced, not advisory):**

```
ReadCapability   → granted to: explorer, apk-analyzer, signature, resources
WriteCapability  → granted to: workspace ONLY
NetworkCapability→ granted to: sync (opt-in) ONLY, gated behind a runtime flag
```

A module without a capability cannot even *import* the API that uses it. This is
how we turn "read-only" from a promise into a compile-time guarantee.

---

## 1. The 30,000-Foot View

DevExplorer is a single-Activity, Compose-first app organized as a **multi-module
Gradle project** with a strict **Clean Architecture / MVVM** layering inside each
feature.

```
┌──────────────────────────────────────────────────────────────────────┐
│                          :app  (single Activity)                       │
│  Hosts NavHost · DI graph root · Theme · Process-level config          │
└───────────────┬──────────────────────────────────────────────────────┘
                │ depends on
   ┌────────────┴───────────────────────────────────────────────────┐
   │                      FEATURE MODULES                             │
   │  :feature:explorer  :feature:apkviewer  :feature:codeviewer      │
   │  :feature:workspace :feature:packages   :feature:settings        │
   └────────────┬───────────────────────────────────────────────────┘
                │ depend on
   ┌────────────┴───────────────────────────────────────────────────┐
   │                      DOMAIN MODULES (pure Kotlin, no Android)    │
   │  :core:model   :core:usecase   :core:capability                 │
   └────────────┬───────────────────────────────────────────────────┘
                │ implemented by
   ┌────────────┴───────────────────────────────────────────────────┐
   │                       DATA MODULES                               │
   │  :data:storage (SAF)   :data:apk    :data:packages (PM)         │
   │  :data:signature       :data:resources   :data:db (Room)        │
   │  :data:work (WorkManager)   :data:sync (opt-in, network)        │
   └────────────┬───────────────────────────────────────────────────┘
                │ build on
   ┌────────────┴───────────────────────────────────────────────────┐
   │                       PLATFORM / LIBS                            │
   │  AndroidX · Compose · Material3 · Room · Hilt · Coroutines      │
   └──────────────────────────────────────────────────────────────┘
```

**Dependency rule:** arrows point downward only. `:app` → feature → domain →
data → platform. Domain never imports Android. Features never import each other
(they communicate via the nav graph and shared domain models). Data modules
never import features.

> **Internals you'll meet here:** Gradle's module graph and how it maps to the
> APK's DEX/class layout; why Google moved toward multi-module (build
> parallelism, R8 per-module shrinking, `:feature` isolation); how the manifest
> merger combines per-module `AndroidManifest.xml` files into the final one.

---

## 2. Module Catalog

### 2.1 `:app`
The composition root. Owns the single `MainActivity`, the top-level `NavHost`,
the Hilt application graph, the Material 3 theme, and edge-to-edge window setup.
Contains almost no logic — it *wires*.

### 2.2 Domain (`:core:*`) — pure Kotlin/JVM modules
- **`:core:model`** — immutable data classes: `FileNode`, `ApkSummary`,
  `Manifest`, `CertificateInfo`, `ResourceEntry`, `InstalledPackage`,
  `WorkspaceItem`. No framework types leak here (no `Uri`, no `File` — we use
  our own `StorageRef` value type).
- **`:core:usecase`** — orchestration: `OpenDocumentTreeUseCase`,
  `AnalyzeApkUseCase`, `VerifySignatureUseCase`, `ListInstalledPackagesUseCase`,
  `CopyIntoWorkspaceUseCase`. Each is a thin, testable function object that
  depends only on repository *interfaces*.
- **`:core:capability`** — the `ReadCapability` / `WriteCapability` /
  `NetworkCapability` marker types and the repository interfaces that require
  them. This is the keystone of the safety model.

### 2.3 Data (`:data:*`)
Each implements one or more domain repository interfaces.

| Module | Backing Android API | Capability |
|---|---|---|
| `:data:storage` | Storage Access Framework, `DocumentFile`, `ContentResolver` | Read (+ Write only for workspace URIs) |
| `:data:apk` | `java.util.zip`, `AssetManager`, `PackageManager.getPackageArchiveInfo` | Read |
| `:data:packages` | `PackageManager`, `ApplicationInfo`, `PackageInfo` | Read |
| `:data:signature` | `PackageManager` signing info, `java.security.cert.X509Certificate`, `apksig` concepts | Read |
| `:data:resources` | `AssetManager.openXmlResourceParser`, ARSC concepts | Read |
| `:data:db` | Room (history, bookmarks, analysis cache) | local only |
| `:data:work` | WorkManager | local only |
| `:data:sync` | (opt-in) network client | **Network, gated** |

### 2.4 Features (`:feature:*`)
Each is a self-contained Compose feature: screen(s) + ViewModel(s) + a nav
sub-graph it contributes to the host. Listed in §5.

> **Internals you'll meet here:** how `R` classes are namespaced per module
> (`com.devexplorer.feature.explorer.R`); resource merging order and override
> precedence; why `:core` JVM modules build faster (no AAPT2, no manifest).

---

## 3. Layered Architecture (inside a feature)

We use **MVVM + Clean Architecture + UDF (unidirectional data flow)**.

```
        ┌──────────────────────────────────────────────┐
        │  Composable Screen  (stateless, dumb)         │
        │   renders UiState, emits UiEvent              │
        └───────────────┬───────────────▲──────────────┘
              UiEvent ↓  │               │ ↑ UiState (StateFlow)
        ┌───────────────▼───────────────┴──────────────┐
        │  ViewModel                                    │
        │   - holds StateFlow<UiState>                  │
        │   - reduces events → calls UseCases           │
        │   - survives config changes (SavedStateHandle)│
        └───────────────┬───────────────▲──────────────┘
                         │               │ Flow<DomainModel> / Result
        ┌────────────────▼──────────────┴──────────────┐
        │  UseCase  (:core:usecase)                     │
        │   pure orchestration, suspend fun / Flow      │
        └────────────────┬──────────────▲──────────────┘
                         │               │
        ┌────────────────▼──────────────┴──────────────┐
        │  Repository interface  (:core:capability)     │
        │  └─ impl in :data:*  (Android APIs, IO)       │
        │     runs on Dispatchers.IO                    │
        └───────────────────────────────────────────────┘
```

**State contract per screen:**
```kotlin
data class ExplorerUiState(
    val isLoading: Boolean = false,
    val location: StorageRef? = null,
    val entries: List<FileNode> = emptyList(),
    val zone: Zone = Zone.System,          // System (read-only) vs Workspace
    val error: UiText? = null,
)

sealed interface ExplorerEvent {
    data class OpenFolder(val ref: StorageRef) : ExplorerEvent
    data class OpenFile(val ref: StorageRef) : ExplorerEvent
    data object PickTreeRoot : ExplorerEvent
    data class CopyToWorkspace(val ref: StorageRef) : ExplorerEvent
}
```

The Composable is a **pure function of `UiState`** — no business logic, no IO.
This makes screens trivially previewable (`@Preview`) and the ViewModel
unit-testable without Compose.

> **Internals you'll meet here:** how `ViewModel` outlives Activity recreation
> via `ViewModelStore` retained across the config-change teardown; how
> `SavedStateHandle` is backed by the `onSaveInstanceState` Bundle and survives
> process death; why `StateFlow` + `collectAsStateWithLifecycle` is the
> lifecycle-correct collection pattern (no leaks across `STOPPED`).

---

## 4. Component Tree (Compose UI)

```
DevExplorerApp                                  (@Composable root in :app)
└─ DevExplorerTheme                             (M3 ColorScheme + dynamic color)
   └─ Scaffold
      ├─ topBar      = DevExplorerTopBar        (zone indicator lives here)
      ├─ bottomBar   = ZoneNavigationBar        (System | Packages | Workspace | Settings)
      └─ content     = AppNavHost               (NavController-driven)
         │
         ├─ ExplorerRoute ─────────────────────────────────────────────┐
         │   └─ ExplorerScreen(state, onEvent)                          │
         │      ├─ ZoneBanner(zone)             ← System/Workspace badge │
         │      ├─ BreadcrumbBar(path)                                  │
         │      ├─ FileList(entries)                                    │
         │      │   └─ FileRow(node) × N        (icon by MIME/type)     │
         │      └─ FabCopyToWorkspace           (only when zone=System) │
         │                                                              │
         ├─ ApkViewerRoute                                              │
         │   └─ ApkViewerScreen(state, onEvent)                         │
         │      ├─ ApkSummaryCard               (pkg, versions, SDKs)   │
         │      ├─ TabRow[ Manifest | Perms | Certs | Resources | Dex ] │
         │      ├─ ManifestTree(xml)            (decoded binary XML)    │
         │      ├─ PermissionList(perms)                                │
         │      ├─ CertificatePanel(chain)      → SignatureRoute        │
         │      └─ ArchiveEntryList(zipEntries) (read-only)             │
         │                                                              │
         ├─ CodeViewerRoute                                             │
         │   └─ CodeViewerScreen(state)                                 │
         │      └─ SyntaxHighlightedText        (AnnotatedString)       │
         │                                                              │
         ├─ PackagesRoute                                               │
         │   └─ PackagesScreen(state, onEvent)                          │
         │      └─ PackageRow(pkg) × N          → ApkViewer for split   │
         │                                                              │
         ├─ WorkspaceRoute                                              │
         │   └─ WorkspaceScreen(state, onEvent) ← ONLY writable zone    │
         │      ├─ WorkspaceBanner              (distinct accent color)  │
         │      └─ WorkspaceItemRow × N         (rename/delete/extract)  │
         │                                                              │
         └─ SettingsRoute                                               │
             └─ SettingsScreen(state, onEvent)                         │
                ├─ ThemeSection                 (dynamic color toggle)  │
                ├─ LanguageSection              (per-app locales)       │
                └─ NetworkFeatureToggle         ← gates :data:sync      ┘
```

**Shared UI lives in `:core:designsystem`** (not shown above as a data/feature
module but compiled as a library): `ZoneBanner`, `FileRow`, color tokens,
typography, motion specs. This prevents features from re-inventing the visual
language and keeps the System↔Workspace distinction *consistent everywhere*.

> **Internals you'll meet here:** Compose's three phases (composition →
> layout → drawing) and why hoisting state up + passing lambdas down minimizes
> recomposition scope; `remember` vs `rememberSaveable` and the
> `Saver`/`Bundle` boundary; how `LazyColumn` recycles via `key`-based slot
> reuse rather than view recycling.

---

## 5. Navigation Graph

Single `NavController`, type-safe routes (Navigation-Compose with
`@Serializable` route objects). Top-level destinations map to the bottom nav;
detail destinations are pushed onto the back stack.

```
                         ┌──────────────────────────────────┐
   (bottom nav tabs)     │            AppNavHost             │
                         └──────────────────────────────────┘
   ┌───────────────┬──────────────┬───────────────┬─────────────────┐
   │               │              │               │                 │
   ▼               ▼              ▼               ▼                 ▼
[Explorer]     [Packages]     [Workspace]     [Settings]      (start = Explorer)
   │               │              │
   │ OpenFolder    │ tap pkg      │ tap item
   │ (self-push)   │              │
   ▼               ▼              ▼
[Explorer/        [ApkViewer]   [WorkspaceItem detail]
 {treeUri}/{path}]    │
   │ OpenFile         │ tap cert         │ tap resource
   ▼                  ▼                  ▼
[CodeViewer]      [Signature]        [CodeViewer]
 {fileRef}         {pkgOrApkRef}      {fileRef}
```

**Route definitions (type-safe):**
```kotlin
@Serializable data object Explorer
@Serializable data class  ExplorerAt(val treeUri: String, val path: String)
@Serializable data class  ApkViewer(val sourceRef: String)   // SAF uri or pkg name
@Serializable data class  CodeViewer(val fileRef: String, val lang: String? = null)
@Serializable data class  Signature(val sourceRef: String)
@Serializable data object Packages
@Serializable data object Workspace
@Serializable data class  WorkspaceItem(val id: Long)
@Serializable data object Settings
```

**Cross-feature contract:** features never call each other's code. Explorer
navigates to ApkViewer by passing a `sourceRef` *string*; ApkViewer re-resolves
it through its own repository. This keeps the feature dependency graph acyclic.

**Deep links (later milestone):** `devexplorer://apk?ref=…` lets the OS share
sheet hand an APK directly into ApkViewer — a clean way to study intent
filters and `ACTION_VIEW`.

> **Internals you'll meet here:** how the back stack is a `NavBackStackEntry`
> list each with its own `ViewModelStore` + `SavedStateRegistry`; how
> Navigation persists/restores the stack across process death via a saved
> `Bundle`; the difference between `popUpTo`/`launchSingleTop` and the
> multiple-back-stack behavior of bottom-nav tabs.

---

## 6. Data Flow — Two Worked Examples

### 6.1 "User opens an APK and reads its manifest"

```
User taps an .apk in Explorer
   │
   ▼
ExplorerScreen emits OpenFile(ref)
   │
   ▼
ExplorerViewModel: classify(ref) → it's an APK → navigate(ApkViewer(ref))
   │
   ▼
ApkViewerViewModel.init: launch { AnalyzeApkUseCase(ref) }
   │
   ▼
AnalyzeApkUseCase → ApkRepository.analyze(ref)        [interface in :core]
   │
   ▼  (impl in :data:apk, on Dispatchers.IO)
ApkRepositoryImpl:
   1. open InputStream via ContentResolver (SAF — permission-checked by OS)
   2. ZipInputStream → enumerate entries (AndroidManifest.xml, classes*.dex, resources.arsc, META-INF/*)
   3. decode binary AndroidManifest.xml  → Manifest model
   4. read PackageManager.getPackageArchiveInfo for pkg/version/SDK
   5. cache ApkSummary in :data:db (Room) keyed by content hash
   │
   ▼  returns immutable ApkSummary  (no write capability anywhere in this path)
ApkViewerViewModel reduces → ApkViewerUiState(manifest=…, loading=false)
   │
   ▼
ApkViewerScreen recomposes → ManifestTree renders decoded XML
```

Note every step is **read-only**: the only persistence is a *local cache* of the
analysis result, never a modification of the source artifact.

### 6.2 "User copies a system file into the Workspace"

```
ExplorerScreen (zone=System) → FabCopyToWorkspace → CopyToWorkspace(ref)
   │
   ▼
ExplorerViewModel → CopyIntoWorkspaceUseCase(ref)
   │
   ▼
CopyIntoWorkspaceUseCase requires WriteCapability   ← compile-time gate
   │  (only :data:workspace provides it)
   ▼
WorkspaceRepositoryImpl (Dispatchers.IO):
   read source via ContentResolver (read-only stream)
   write into app-private  filesDir/workspace/…   (the ONLY writable target)
   insert WorkspaceItem row in Room
   │
   ▼
Workspace tab now shows the copy, badged with the Workspace accent color.
The original is untouched — we never had write access to it.
```

> **Internals you'll meet here:** `ContentResolver.openInputStream` and the
> binder round-trip to the document provider; why SAF grants are URI-scoped and
> persisted via `takePersistableUriPermission`; scoped storage (API 29+) and why
> app-private `filesDir` needs no permission at all.

---

## 7. Persistence Model (Room)

Three concerns, all *local* and *derived* — nothing here is the source of truth
for user data outside the sandbox.

```
┌─────────────────┐   ┌──────────────────┐   ┌────────────────────┐
│ recent_locations│   │  analysis_cache  │   │  workspace_items   │
├─────────────────┤   ├──────────────────┤   ├────────────────────┤
│ id              │   │ contentHash (PK) │   │ id (PK)            │
│ treeUri         │   │ apkSummaryJson   │   │ displayName        │
│ label           │   │ analyzedAt       │   │ relPath (in sandbox)│
│ lastOpenedAt    │   │ schemaVersion    │   │ sourceDescription  │
└─────────────────┘   └──────────────────┘   │ sizeBytes / addedAt│
                                              └────────────────────┘
```

`analysis_cache` is keyed by **content hash**, so re-opening the same APK is
instant and the cache self-invalidates when bytes change — a nice place to study
hashing and cache-invalidation strategy.

> **Internals you'll meet here:** Room's compile-time SQL verification and
> generated DAO impls; how `Flow`-returning queries hook into SQLite's
> invalidation tracker to auto-emit on writes; migrations vs
> `fallbackToDestructiveMigration` and why schema versioning matters.

---

## 8. Background Work (WorkManager)

Used for the genuinely deferrable, longer jobs — kept off the UI path:
- **Workspace extraction** of a large archive (unzip into sandbox).
- **Batch analysis** of all installed packages for an offline catalog.
- **Cache pruning** on a periodic schedule.

```
Feature → enqueue OneTimeWorkRequest(tag, constraints)
   │
   ▼
WorkManager persists request in its own SQLite DB
   │
   ▼  (respects constraints: storage-not-low, battery, etc.)
CoroutineWorker.doWork() on background executor
   │  reports Progress / Result, observable as Flow<WorkInfo>
   ▼
UI observes WorkInfo → shows progress, survives app death & reboot
```

> **Internals you'll meet here:** how WorkManager picks `JobScheduler` (API 23+)
> vs `AlarmManager` fallbacks; guaranteed execution across process death/reboot;
> why work must be idempotent; constraint-driven scheduling and Doze
> interaction.

---

## 9. Theming, Motion & Localization

- **Material 3 theme** in `:core:designsystem`: dynamic color (Material You,
  API 31+) with a curated static fallback scheme; full light/dark; a dedicated
  **Workspace accent** that is *always* visually distinct from System.
- **Motion**: shared-element-style transitions between FileRow → ApkViewer; a
  consistent `MotionSpec` token set (durations/easing) so animation feels of a
  piece.
- **Localization**: string resources from day one; per-app language via
  `AppCompatDelegate`/`LocaleManager` (API 33 per-app locales). RTL-correct
  layouts. This is where the "string resource management" and "localization
  framework" goals get exercised concretely.

> **Internals you'll meet here:** how `Resources`/`AssetManager` resolve the
> right `values-<qualifier>` bucket from the device `Configuration`; per-app
> locales storage; dynamic color extraction from the wallpaper via the system
> palette.

---

## 10. The Safety Architecture (how constraints become guarantees)

This is worth its own section because it's the spine of the project.

1. **Capability types gate writes at compile time.** `CopyIntoWorkspaceUseCase`
   takes a `WriteCapability` parameter; only `:data:workspace` constructs one.
   No analysis module can be made to write, even by accident, because it can't
   obtain the token.
2. **The writable surface is exactly one directory.** `filesDir/workspace`. The
   Workspace repository refuses any path that escapes it (canonical-path check
   against directory traversal — a `../` becomes a studied defense, not a bug).
3. **Source artifacts are opened read-only**, always via `ContentResolver`/SAF,
   so the OS itself is the enforcement point for "files the user permitted."
4. **Network is one opt-in module.** `:data:sync` is the only module allowed to
   depend on a network client, it's gated behind a Settings flag, and a Gradle
   dependency-rule check (or a simple test) can assert no other module imports
   it. Default build paths never touch the network.
5. **Zones are visible.** The `ZoneBanner` + bottom-nav coloring make
   System (read-only) vs Workspace (read-write) unmistakable at every screen.

> **Internals you'll meet here:** the Android permission model (install-time vs
> runtime vs special access); URI permission grants and `FLAG_GRANT_READ_URI_
> PERMISSION`; how scoped storage removed broad filesystem access and what
> replaced it.

---

## 11. Educational Goal → Module Map

| Your learning goal | Where it lives | Key Android internal |
|---|---|---|
| File system hierarchy & permissions | `:data:storage`, `:feature:explorer` | SAF, scoped storage, `DocumentFile` |
| APK/ZIP parsing & structure | `:data:apk`, `:feature:apkviewer` | ZIP central directory, binary XML, ARSC |
| Package management internals | `:data:packages`, `:feature:packages` | `PackageManager`, `PackageInfo`, splits |
| Syntax highlighting / IDE rendering | `:feature:codeviewer` | `AnnotatedString`, text layout, spans |
| Resource (de)compilation concepts | `:data:resources` | AAPT2, `resources.arsc`, `AssetManager` |
| Digital signatures & cert chains | `:data:signature`, `:feature` | APK Signature Scheme v2/v3, X.509 |
| Material 3 theming & animation | `:core:designsystem` | dynamic color, Compose motion |
| Localization & string resources | app-wide, `:feature:settings` | `Configuration`, per-app locales |
| Background processing | `:data:work` | WorkManager, JobScheduler, Doze |
| Content providers & document storage | `:data:storage` | `ContentResolver`, SAF providers |

---

## 12. Build Order (the implementation roadmap)

We'll build in dependency order so every milestone is runnable:

1. **Skeleton** — `:app`, `:core:model`, `:core:designsystem`, theme, nav scaffold, empty screens.
2. **Explorer + Storage** — SAF tree picking, file listing, the System/Workspace zone model. *(file system + content providers)*
3. **Workspace** — the sandbox, capability types, copy-in flow. *(permissions + scoped storage)*
4. **APK Viewer** — ZIP enumeration, binary manifest decode, summary card. *(archive parsing)*
5. **Packages** — `PackageManager` listing → reuse ApkViewer. *(package management)*
6. **Signatures** — cert chain extraction & display. *(digital signatures)*
7. **Code Viewer** — syntax highlighting. *(text rendering)*
8. **Resources** — ARSC/asset inspection. *(resource compilation)*
9. **Room cache + history**, then **WorkManager** jobs. *(persistence + background)*
10. **Localization, motion polish, dynamic color**, optional `:data:sync`. *(theming + i18n)*

Each milestone ships with an internals write-up in `docs/internals/NN-*.md`.

---

## Appendix A — Tech Stack Decisions

| Choice | Why (for this project) |
|---|---|
| **Single-Activity + Compose** | Matches modern Android; lets us study one lifecycle deeply instead of many. |
| **Hilt** | Compile-time DI; teaches the generated-component model and scoping. |
| **Coroutines + Flow** | Structured concurrency; `Dispatchers.IO` discipline; `StateFlow` UDF. |
| **Room** | Compile-time-checked SQL; the canonical local-persistence path. |
| **Navigation-Compose (type-safe)** | Serializable routes; real back-stack/SavedState internals to learn. |
| **Multi-module** | Build-graph clarity + the capability-isolation safety model. |
| **Material 3** | Dynamic color + modern motion are explicit learning goals. |

## Appendix B — Package Naming

```
com.devexplorer
 ├─ app
 ├─ core.model | core.usecase | core.capability | core.designsystem
 ├─ data.storage | data.apk | data.packages | data.signature
 │   data.resources | data.db | data.work | data.sync
 └─ feature.explorer | feature.apkviewer | feature.codeviewer
     feature.packages | feature.workspace | feature.settings
```
