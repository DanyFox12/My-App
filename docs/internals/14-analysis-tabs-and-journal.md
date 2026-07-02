# Enhancement — deep-analysis tabs & the analysis journal

The APK Viewer grows four analysis tabs — **Security**, **DEX** packages,
**Native** libraries, and resource **Strings** — plus an **analysis journal**
that remembers what each package looked like and shows a "changed since vX"
card after an update. Everything follows the house pattern from
[Milestone 4](04-apk-parsing.md): from-scratch, fail-soft binary readers in
the pure-Kotlin `:core:model` layer, with the Android side supplying bytes.

```
ApkViewerScreen tabs
  Security → securityAudit(summary)             // pure audit over the parsed summary
  DEX      → ApkViewerViewModel.loadDexPackages()   // lazy: first tab open only
               → ReadDexPackagesUseCase → ApkFileRepository.readDexPackages
                   → DexPackages.parse(sequence)    // one DEX in memory at a time
  Native   → summary.nativeLibs                 // Elf.parse over first 4 KB per .so
  Strings  → summary.arscStrings                // Arsc.readGlobalStrings, display-capped

analyze() success
  → TrackAnalysisUseCase(summary, now)          // Room journal, :data:db
      snapshotOf → record only if versionCode changed → prune to last 5
      latestOtherVersion → snapshotDelta → "Changed since vX" card (Overview)
```

---

## 1. The security scorecard

`securityAudit(summary)` is a pure function over the already-parsed
`ApkSummary`: every finding derives from the decoded binary manifest and the
requested-permission list — no code from the APK ever runs. Flags on the
`<application>` element (`android:debuggable`, `android:testOnly`,
`android:allowBackup`, `android:usesCleartextTraffic`) each map to a
`SecurityFinding` with a machine-readable `SecurityCheck` identity, a severity
(`Warning`/`Note`/`Info`) and concrete evidence items; the `SecurityTab` UI
owns the wording, the model owns the facts. Exported
activities/services/receivers — and, more loudly, content providers — are
reported when no `android:permission` guards them. Requested permissions are
matched against a curated `DANGEROUS_PERMISSIONS` constant (not
`PackageManager` lookups) so the audit stays pure and works for archives that
aren't installed. An outdated `targetSdkVersion` (below 30; `Warning` below
23) is a finding of its own, because old targets opt out of runtime
permissions, scoped storage and component-export rules.

**Implicitly exported components** are part of the same check: a component
that declares an `<intent-filter>` but no `android:exported` attribute is
exported *by default* when the app targets SDK < 31 — API 31 made the
attribute mandatory for exactly this footgun. The audit therefore treats "has
an intent-filter, omits `android:exported`, targets < 31" as exported, and
folds those components into the `ExportedComponents` / `ExportedProviders`
evidence lists; an explicit `android:exported="false"` always wins.

## 2. Walking the whole DEX

The header reader from [DEX insight](11-dex-insight-and-apk-diff.md) answers
"how many"; `DexPackages` answers "where". For each `classes*.dex` it
cross-references four tables located via the 0x70-byte header: `class_defs`
(32-byte entries whose first `u32` is the class's `type_ids` index),
`method_ids` (8-byte entries whose first `u16` is the declaring type),
`type_ids` (a `u32` index into `string_ids`), and finally the
`string_data_item` — a ULEB128 `utf16_size` we skip, then MUTF-8 bytes to the
NUL (decoded as plain UTF-8, exact for class names). A descriptor like
`Lcom/example/Foo;` becomes package `com.example`; arrays and primitives
resolve to null and drop out. Type→package resolution is **memoized per file**,
because thousands of `method_ids` share a handful of declaring types.

Cost and robustness shape the rest. Parsing is **fail-soft per file**: each DEX
counts into private maps that merge into the shared totals only after the whole
file parses, so one malformed DEX — even one that throws mid-walk — is skipped
inside `runCatching` without polluting the merged counts, and the tree is null
only when *nothing* parses. And the walk is deliberately **lazy** —
`loadDexPackages()` runs only when the DEX tab first opens (guarded by
`dexPackagesLoaded`, with failures kept distinct from "no DEX" so the tab can
retry), and `readDexPackages` feeds `DexPackages.parse` a `Sequence` from a
`ZipInputStream`, so only one DEX's bytes are alive at a time even for huge
multidex apps. The resulting `DexPackageNode` tree sorts children by descending
`totalMethodRefs` — the 64K-budget currency — so the heaviest library floats to
the top; packages the app merely calls into (`android.*`) appear with zero
defined classes.

## 3. Native libraries & 16 KB pages

`Elf` reads the first `HEADER_READ_BYTES` (4096) of every `lib/<abi>/*.so`:
the `e_ident` block gives the magic, bitness (class 1/2) and endianness
(little-endian only — big-endian isn't an Android ABI), and `e_machine` at
offset 18 maps to a human name (ARM, ARM64, x86, x86-64, RISC-V). The
interesting part is the **program-header walk**: `minLoadAlignment` is the
smallest `p_align` across `PT_LOAD` segments, and `supports16KbPages` is true
only when it is ≥ 16384 — devices with 16 KB memory pages (Android 15's
hardware wave) refuse to map 4 KB-aligned libraries, so this is a real
compatibility signal. `NativeLibs.build` rolls files up per ABI; entries that
don't parse still appear with a null `ElfInfo` (so per-ABI sizes stay
truthful) and make that ABI's readiness tri-state `null` instead of a guess.

## 4. The `resources.arsc` string pool

`resources.arsc` is a chunked binary like the manifest, and its **global
string pool is literally the same chunk format** — so `Arsc` validates the
`RES_TABLE` (0x0002) header, hops past it via its own header size, walks
sibling chunks by their size fields until `RES_STRING_POOL` (0x0001), and
hands the offset to `BinaryXml.parseStringPool`. A flag bit selects the
encoding per pool: UTF-8 entries carry two 1–2-byte lengths (char count, then
byte count); UTF-16 entries a 2-or-4-byte little-endian length. The pool holds
every string *value* across all locales and can run to hundreds of thousands,
so decoding stops at `Arsc.DISPLAY_CAP` (5,000) while `ArscStrings` keeps the
true `totalCount` — the searchable Strings tab can say "first N of M".

## 5. The analysis journal, deltas & pins

`snapshotOf(summary, analyzedAt)` distills an analysis into a compact
`AnalysisSnapshot` — version, size, entry count, sorted permissions, DEX
method/class counts — or null when the archive has no package name/version
code; the full `ApkSummary` is deliberately never persisted, and `analyzedAt`
comes from the caller so the domain stays clock-free. `TrackAnalysisUseCase`
makes the journal a *change log*, not an access log: it **records only when
the version code changed**, and diffs against `latestOtherVersion` — the
newest *different* build — so the "Changed since vX" card survives repeated
viewings of the same version. `snapshotDelta` is pure: null for same-version
or different-package pairs, otherwise size/entry/method-ref deltas plus
added/removed permission sets.

Persistence extends [Milestone 9](09-persistence-and-background.md)'s Room
database to **schema v2** with a hand-written `MIGRATION_1_2` in `DbModule` —
DDL matching the entities exactly, because Room validates the schema on open
and there is no destructive fallback. `RoomAnalysisHistoryRepository.record`
inserts, then prunes to the newest **5 rows per package**; permissions live in
one newline-joined column (display-and-diff data, not relational). The same
migration adds `favorite_packages`, backing the pin stars in the Packages
list: `PackagesUiState` sorts pinned apps first, and a Favorites chip filters
down to them. All of the above is Android-free and JVM-tested —
`SecurityAuditTest`, `DexPackagesTest` (synthetic DEX), `ElfTest`,
`NativeLibsTest`, `ArscTest`, `AnalysisSnapshotTest`, `TrackAnalysisUseCaseTest`.
