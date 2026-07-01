# Milestone 7 — Text rendering & syntax highlighting

Tapping a text file opens a Code Viewer with IDE-style highlighting. This is a
study of Compose text (`AnnotatedString`, spans, `LazyColumn` for big text) and
of doing bounded IO safely.

---

## 1. Bounded reads (don't OOM the device)

`ReadTextUseCase` reads **at most 1 MiB** (+1 byte to detect truncation):

```kotlin
val bytes = input.readUpTo(maxBytes + 1)
val truncated = bytes.size > maxBytes
```

Every app has a per-process heap cap set by the device's RAM class; slurping an
arbitrarily large file into a `String` is a classic `OutOfMemoryError`. Capping
the read is a deliberate performance/robustness choice, and the UI shows a "large
file — first part only" banner when it kicks in. We also avoid
`InputStream.readNBytes` (API 33+) and hand-roll the read loop so it works back to
API 24.

## 2. `AnnotatedString`: styled text in Compose

Compose renders styled runs of text as an `AnnotatedString` — a string plus a
list of `SpanStyle` ranges. Our highlighter builds one:

```kotlin
buildAnnotatedString {
    withStyle(SpanStyle(color = colors.keyword)) { append("fun") }
    append(" ")
    withStyle(SpanStyle(color = colors.plain)) { append("main") }
}
```

The colors come straight from the **M3 theme** (`primary` for keywords,
`tertiary` for strings, `onSurfaceVariant` for comments…), so highlighting
automatically adapts to light/dark and Material You dynamic color.

## 3. A single-pass tokenizer (not regex soup)

`highlightCode` scans left-to-right once, classifying: block comments, line
comments, string/char literals (respecting `\` escapes), numbers, and
identifiers (keyword vs plain, from per-language keyword sets). Language is
guessed from the file extension.

Properties that matter:
- **Can't loop**: every branch advances the cursor.
- **Fails soft**: unknown input just renders as plain text; a runaway string or
  comment simply ends at end-of-input. There is no input that crashes it.

It's approximate — a *viewer*, not a parser — which is exactly the right amount of
work for the job.

## 4. Why per-line highlighting in a `LazyColumn`

Rendering a whole large file as one `Text` forces Compose to lay out *all* of it
up front — slow and memory-hungry. Instead we split into lines and render them in
a `LazyColumn`, so only the lines on screen are composed and highlighted. Each
line gets a right-aligned number gutter for the IDE feel.

The trade-off: highlighting is per-line, so a block comment or string spanning
multiple lines is only highlighted on the line where it starts. For a viewer this
is a fine exchange for smooth scrolling of big files — and a natural upgrade later
is to carry a "currently inside a block comment" state between lines.

## 5. Read-only, and safely typed

The viewer reads through the same `StorageRepository.openInputStream` (SAF,
read-only) used everywhere else. A NUL-byte heuristic flags binary files and
shows a notice instead of garbage. Nothing here can write — there's no
`WriteCapability` anywhere in this path.

---

## Try it / observe it

- In the Explorer, tap a `.kt`, `.java`, `.xml`, or `.json` file → highlighted,
  with line numbers; scroll a big file and watch it stay smooth.
- Toggle system dark mode → the highlight colors follow the theme.
- Open a large log file → the truncation banner appears; the app stays responsive.

> Build note: `:core:model`, `:core:capability`, `:core:usecase` compile-verified
> here; `:core:designsystem` (highlighter) and `:app` need the Android SDK and are
> validated by review plus your local build.
