package com.devexplorer.app.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Applies a chosen language to a Context by wrapping it with a locale-overridden
 * [Configuration]. Called from `attachBaseContext`, this makes the whole Activity
 * (and everything it inflates) resolve `values-<lang>` resources — the core of
 * how per-app localization works: the runtime picks the resource bucket that
 * matches the Context's `Configuration`.
 *
 * Works on every supported API level (createConfigurationContext is API 17+).
 * On API 33+ the platform also offers system-backed per-app locales; this
 * approach is the portable equivalent.
 */
object LocaleContext {
    fun wrap(base: Context, languageTag: String): Context {
        if (languageTag.isBlank()) return base
        val locale = Locale.forLanguageTag(languageTag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
