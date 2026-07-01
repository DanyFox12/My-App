# Milestone 8 — Resources: how an APK organizes assets

The Resources tab breaks down an APK's `res/`, `lib/`, and `assets/` so you can
see how Android packages and looks up resources. It's built from the ZIP entry
list we already have — no extra parsing — and paired here with the concepts
behind `resources.arsc`.

---

## 1. Three kinds of "resources" in an APK

- **`res/`** — *compiled* resources: drawables, layouts, `values`, animations,
  etc. AAPT2 compiles your source `res/` into these and assigns each a numeric
  **resource ID**. Files are grouped by **type** and **configuration qualifier**:
  `res/drawable-hdpi/…`, `res/values-ar/…`, `res/layout-land/…`. We group entries
  by their base type (stripping the qualifier) and count them.
- **`lib/<abi>/`** — native `.so` libraries, one directory per ABI
  (`arm64-v8a`, `armeabi-v7a`, `x86_64`…). We count them per ABI, which is
  exactly what shows whether an app ships 64-bit code.
- **`assets/`** — *raw* files, delivered byte-for-byte with no compilation and no
  resource ID (read via `AssetManager`).

## 2. `resources.arsc` — the compiled resource table

`resources.arsc` is the heart of Android resource lookup. It's a binary table
that maps a **resource ID** (e.g. `0x7f0a0031`) to the actual value or file path,
**per configuration**. When your code does `getString(R.string.app_name)` or the
framework needs `@drawable/ic_launcher` for the current density/locale, it
consults this table:

- A resource ID is `PPTTEEEE`: package (`0x7f` for app resources), type, entry.
- For each ID the table holds a list of **configuration-specific** values, so the
  runtime can pick the `values-ar` string or the `drawable-xxhdpi` bitmap that
  matches the device `Configuration`.
- It's typically stored **`STORED`** (uncompressed) in the ZIP so it can be
  `mmap`-ed and read without inflating — you can confirm that on the Contents tab.

Fully decoding `.arsc` (string pool → package → type spec → config entries) is a
substantial parser and a great deeper exercise; this milestone surfaces the
structure and concepts, and reports the table's presence and the `res/` layout it
indexes.

## 3. Why grouping in the UI (not a new parser) is the right call here

Everything on this tab is derived from the ZIP central directory we already read
in milestone 4 — a single pass of `groupingBy { … }.eachCount()`. That keeps the
feature fast and dependency-free while still teaching the important structural
facts: what resource types an app uses, which ABIs it targets, whether it uses
raw assets, and whether it carries a compiled resource table.

---

## Try it / observe it

- Open a real app's APK → **Resources**: see `drawable`/`layout`/`values`/… counts,
  the ABIs under `lib/`, and the `assets/` count.
- Cross-check on **Contents**: `resources.arsc` is usually `STORED` (so it can be
  memory-mapped), while `res/…/*.xml` are `DEFLATED`.
- Compare a 32-bit-only vs 64-bit app by their `lib/<abi>` breakdown.

> Build note: this milestone is UI logic over already-parsed data; `:app` needs
> the Android SDK to build and is validated by review plus your local build.
