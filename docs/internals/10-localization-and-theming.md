# Milestone 10 — Localization, theming & performance-aware motion

The capstone: per-app language, Material You dynamic color, an RTL-correct Arabic
translation, and motion that scales to the device. All wired through a Settings
screen.

---

## 1. Per-app localization via `Configuration`

Android picks resources by matching the Context's **`Configuration`** to a
resource bucket: a device set to Arabic reads `values-ar/strings.xml`; otherwise
`values/` (the default). We override the language per-app by wrapping the base
context before any UI is built:

```kotlin
override fun attachBaseContext(newBase: Context) {
    val tag = SettingsStore.readLanguage(newBase)         // synchronous read
    super.attachBaseContext(LocaleContext.wrap(newBase, tag))
}

// wrap:
val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
base.createConfigurationContext(config)
```

Now every `stringResource(...)` in the app resolves against the chosen locale.
Changing the language calls `Activity.recreate()`, which re-runs
`attachBaseContext` with the new tag. Because the read happens in
`attachBaseContext` — before any coroutine runs — settings are stored in
**SharedPreferences** (synchronous), not DataStore.

`android:supportsRtl="true"` (set in milestone 1) means the Arabic layout mirrors
automatically: the nav bar, list chevrons, and text alignment all flip. Compose's
`start`/`end` paddings (which we used throughout) do the right thing in both
directions.

## 2. Material You dynamic color, toggleable

`DevExplorerTheme(dynamicColor = …)` already chose dynamic vs curated colors by
API level (milestone 1). Now the user controls it: the Settings switch flips a
flag in `SettingsStore`, its `StateFlow` is collected in `MainActivity`, and the
theme **recomposes instantly** — no restart. On pre-Android-12 devices the switch
is disabled with an "available on Android 12+" note, and the curated brand palette
is used. One preference, correct on every phone.

## 3. Motion that respects the device

The nav transitions now pull their duration from the device
`PerformanceBudget` established in milestone 1:

```kotlin
val fade = tween<Float>(durationMillis = LocalPerformanceBudget.current.crossfadeMillis)
NavHost(enterTransition = { fadeIn(fade) }, exitTransition = { fadeOut(fade) })
```

On a Low-tier device `crossfadeMillis` is `0`, so screens **snap** instead of
fading — no animation cost where frames are precious. On High-tier it's a smooth
250 ms. The same budget already gates blur and cache sizes elsewhere; motion just
joins the policy.

## 4. Network stays off unless you ask

The Settings screen exposes a **Network features** switch, off by default. It
sets a `networkEnabled` flag that any future online feature (the architecture's
opt-in `:data:sync` module) must check before doing anything. Nothing in the app
touches the network today; the flag is the single, visible gate that keeps the
"network-off by default" guarantee honest and user-controlled.

---

## Try it / observe it

- Settings → Language → **العربية**: the whole app switches to Arabic and mirrors
  to RTL; back to English or System restores it.
- Toggle **Dynamic color** on Android 12+ and watch the palette re-tint live from
  your wallpaper; on older phones the switch is disabled and the brand colors show.
- On a low-RAM emulator, screen changes snap (0 ms) instead of fading — the
  performance budget in action.

> Build note: the localization/theming/settings live in `:app` (+ the milestone-1
> theme in `:core:designsystem`), which need the Android SDK to build and are
> validated by review plus your local build.
