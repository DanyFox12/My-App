# Enhancement — DEX insight & APK comparison

Two additions that build on the existing read-only APK pipeline (Milestone 4):
a **DEX header reader** that surfaces method/class counts, and an **APK diff**
that compares two analyses side-by-side. Both live in the pure-Kotlin
`:core:model` layer, so they are exhaustively unit-tested with no Android
dependency.

```
ApkFileRepository.analyze(stream)
  → readDexStats(temp, entries)        // reads only each DEX's 0x70-byte header
      → Dex.parseHeader(name, bytes)   // :core:model, from-scratch, fail-soft
          → DexFileStats               // string/type/proto/field/method/class counts
  → ApkSummary(..., dexStats = DexStats(files))

CompareApkUseCase(old, new)
  → analyzeApk(old) ; analyzeApk(new)  // two read-only analyses
      → diffApks(oldSummary, newSummary) → ApkDiff
```

---

## 1. Reading the DEX header

A `classes*.dex` file starts with a fixed **0x70-byte header** whose size fields
tell you, without walking the whole file, how many strings, types, protos,
fields, methods, and classes it defines. We validate the `dex\n0nn\0` magic and
the little-endian `ENDIAN_CONSTANT` (`0x12345678`), then read the six
`*_ids_size` / `class_defs_size` words at their documented offsets:

| Offset | Field             |
|-------:|-------------------|
| `0x38` | `string_ids_size` |
| `0x40` | `type_ids_size`   |
| `0x48` | `proto_ids_size`  |
| `0x50` | `field_ids_size`  |
| `0x58` | `method_ids_size` |
| `0x60` | `class_defs_size` |

`method_ids_size` is the number of **method references** — the value that runs
into Android's hard **65,536-per-DEX** ceiling (the reason multidex exists).
`DexStats.nearsMethodLimit` flags any DEX close to it, and the APK Viewer's
Overview tab renders a warning so "why is this app multidex?" becomes a fact.

Like the binary-XML decoder, `Dex.parseHeader` is **fail-soft**: anything that
isn't a well-formed little-endian DEX returns `null` instead of throwing, so a
weird archive degrades gracefully. `ApkFileRepository` reads only the header
bytes per DEX (not the whole file), so this stays cheap even for large apps.

## 2. Diffing two APKs

`diffApks(old, new)` is a pure function over two `ApkSummary` snapshots. It
computes, with `new - old` deltas throughout:

- **Identity** — package, label, version name/code, min/target/compile SDK, each
  as a before/after `FieldChange`.
- **Size** — uncompressed/compressed bytes and entry count.
- **Composition** — per-`ApkPart` byte deltas (reusing `apkComposition`), sorted
  by magnitude.
- **DEX** — method/class/DEX-file deltas from each side's `DexStats`.
- **Permissions** — set difference: added, removed, and unchanged counts.
- **Signing** — compares the two certificate SHA-256 sets: `Same`, `Different`
  (re-signed / different author), or `Unknown` when either side is unsigned.

`CompareApkUseCase` orchestrates two read-only analyses and hands the pair to
`diffApks`; the Compare screen (reached from the Packages toolbar) lets you pick
two installed apps and renders the result, shareable via `buildApkDiffReport`.

## 3. Why this lives in `:core:model`

Everything above is deterministic and Android-free, so it is covered by plain
JVM unit tests: `DexInfoTest` builds synthetic DEX headers and asserts the parsed
counts and limit flag; `ApkDiffTest` / `ApkDiffReportTest` assert the deltas and
the rendered report; `CompareApkUseCaseTest` drives the use case with fakes. The
Android layer only supplies bytes.
