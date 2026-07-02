# Wow features — X-ray treemap, a binary-XML decoder & reverse permission search

Three features that each turn an Android internal into something you can *see*:
a **treemap** of what an APK's bytes actually are, a **from-scratch decoder**
for the binary AndroidManifest.xml (AXML) format, and a **reverse permission
index** over every installed app. As before ([Milestone 11](11-dex-insight-and-apk-diff.md)),
the interesting logic is pure Kotlin in `:core:model` and unit-tested; the
Android layers only supply bytes.

```
APK Viewer › X-ray      summary.entries → apkComposition(entries)   // size buckets
                          → Treemap(slices)                          // single-Canvas layout

APK Viewer › Manifest   ApkFileRepository.readManifest(temp)         // ZipFile → raw bytes
                          → BinaryXml.decode(bytes) → XmlNode tree   // our AXML parser
                            → ApkSummary.manifest → ManifestTab

Packages › shield       GetPermissionUsageUseCase(includeSystem)
                          → PackagesRepository.permissionUsage
                              getInstalledPackages(GET_PERMISSIONS)  // apps → permissions
                            → buildPermissionIndex(...)              // permission → apps
```

---

## 1. The X-ray treemap

`apkComposition(entries)` (`:core:model`) folds the ZIP entry list from
[Milestone 4](04-apk-parsing.md) into `ApkPart` buckets by **uncompressed** size,
classifying each entry by name: `*.dex` → DEX code, `resources.arsc` → resource
table, `res/` → resources, `lib/` → native, `assets/` → assets, `META-INF/` →
signatures, everything else → other (the same directories [Milestone 8](08-resources.md)
explains). Directories are skipped, zero-byte slices dropped, and the result is
sorted largest-first — which matters for the layout below.

The `Treemap` composable (`:core:designsystem`) lays slices out with a recursive
**split-along-the-longer-edge** scheme, a simple cousin of the squarified
algorithm: it finds the index where the first group accumulates roughly half the
total value, splits the rectangle along its longer edge in proportion to that
group's weight, and recurses into both halves. Because slices arrive
largest-first, big parts land as near-square tiles instead of slivers. Every
tile is drawn on a single `Canvas` (one node, no per-tile composables) with a
hairline stroke as the gap, and a label + byte count is drawn only when the tile
exceeds 92×44 px, so it stays readable at any size. `XrayTab` (private in
`ApkViewerScreen.kt`) maps parts to colors and pairs the treemap with a legend
of per-part bytes and percentages.

## 2. AndroidManifest.xml is not XML — decoding AXML

The manifest inside an APK is **binary**: AAPT2 compiles the text you wrote into
AXML so the system can parse it without an XML parser at install time. `BinaryXml`
(`:core:model`) decodes it from scratch. The file is a stream of little-endian
**chunks**, each starting with the same header: `u16 type`, `u16 headerSize`,
`u32 size`. The decoder checks the file chunk's type (`0x0003`, `RES_XML_TYPE`),
skips its header, then walks chunk to chunk by adding `size` — unknown chunk
types (like the resource-ID map) are simply stepped over.

| Type | Chunk | What we read |
|-----:|-------|--------------|
| `0x0001` | string pool | every name and string value, by index |
| `0x0100` | start namespace | prefix + URI string indices |
| `0x0102` | start element | element name + attribute array |
| `0x0103` | end element | pop the tree stack |

**The string pool** is the heart of the format: nothing in AXML stores text
inline, everything is an index into this pool. Its header carries the string
count (`+8`), flags (`+16`) and the data start (`+20`); an offset table begins
at `+28`. Flag bit 8 selects the encoding. UTF-16 strings are a `u16` char count
(with a high-bit escape into a second `u16` for long strings) followed by
UTF-16LE data; UTF-8 strings carry *two* varints — character count, then byte
count, each 1–2 bytes — before the bytes. The same chunk format underlies
`resources.arsc` (see [doc 14](14-analysis-tabs-and-journal.md)).

**Elements and namespaces.** A start-element chunk stores the element name at
`+20` and an attribute table whose offset/stride/count live at `+24`/`+26`/`+28`
(relative to the 16-byte extended header — hence `pos + 16 + attrStart`). Each
attribute is `(namespace URI, name, raw string, typed value)`, all indices.
There is no literal `"android:name"` anywhere: the attribute stores the URI
`http://schemas.android.com/apk/res/android`, and the decoder remembers each
start-namespace chunk's URI→prefix mapping so the attribute renders back as
`android:name`.

**Typed values.** An attribute's value is a `Res_value`: a `dataType` byte and
four data bytes, which `formatValue` renders — `0x03` string (pool index),
`0x01`/`0x02` resource/attribute references (`@0x7f010001` / `?0x…`), `0x12`
boolean (`data != 0`), `0x10`/`0x11` decimal/hex ints, `0x04` float (raw IEEE
bits), `0x1c–0x1f` colors (`#AARRGGBB`), and `0x05`/`0x06` dimensions and
fractions, which pack a mantissa, a radix selector and a unit nibble into one
int (decoded via a radix-multiplier table into `24dip`, `12sp`, `50%`, …).

Like `Dex.parseHeader`, `BinaryXml.decode` is **fail-soft** — malformed input
returns `null`, never throws. `ApkFileRepository.readManifest` pulls the entry's
bytes with `ZipFile` into `ApkSummary.manifest`, and the Manifest tab flattens
the `XmlNode` tree into indented monospace lines — a manifest viewer that owes
nothing to `PackageManager`. `BinaryXmlTest` builds a synthetic UTF-8 pool and
asserts pool parsing, UTF-16 reads, and each typed-value rendering.

## 3. Reverse permission search

"Which apps can use the camera?" is a query `PackageManager` doesn't answer
directly — it hands out *app → permissions*, one package at a time.
`PackageManagerRepository.permissionUsage` calls
`getInstalledPackages(GET_PERMISSIONS)` and reads each `requestedPermissions`
array — what the manifest *requests*, not what's granted — then the pure
`buildPermissionIndex` inverts the pairs into *permission → apps*, sorting apps
alphabetically and permissions by usage count, then name. The **include-system**
switch is applied *before* inversion (via `ApplicationInfo.FLAG_SYSTEM`, as in
[Milestone 5](05-package-manager.md)), so flipping it re-queries and every count
changes honestly. The `PermissionSearch` screen — a shield action in the
Packages top bar — filters the loaded index in UI state as you type and expands
each row into the apps requesting it; `GetPermissionUsageUseCase` wraps the
lookup in `Result` so a `PackageManager` hiccup surfaces as an error state, and
`PermissionUsageTest` pins the inversion and sort order.
