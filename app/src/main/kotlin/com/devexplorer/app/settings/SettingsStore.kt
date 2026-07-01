package com.devexplorer.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-tunable app settings. [language] is a BCP-47 tag, or "" for system default. */
data class AppSettings(
    val dynamicColor: Boolean = true,
    val language: String = "",
    val networkEnabled: Boolean = false,
)

/**
 * Settings persistence, backed by SharedPreferences.
 *
 * SharedPreferences (not DataStore) on purpose: the language is read
 * **synchronously** in [android.app.Activity.attachBaseContext], before any
 * coroutine can run, so we need a blocking read. A process-wide singleton keeps
 * the reactive [settings] flow and the static read in sync.
 */
class SettingsStore private constructor(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setDynamicColor(enabled: Boolean) = update { putBoolean(KEY_DYNAMIC, enabled) }
    fun setLanguage(tag: String) = update { putString(KEY_LANGUAGE, tag) }
    fun setNetworkEnabled(enabled: Boolean) = update { putBoolean(KEY_NETWORK, enabled) }

    private inline fun update(edits: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.edits()
        editor.apply()
        _settings.value = read()
    }

    private fun read() = AppSettings(
        dynamicColor = prefs.getBoolean(KEY_DYNAMIC, true),
        language = prefs.getString(KEY_LANGUAGE, "").orEmpty(),
        networkEnabled = prefs.getBoolean(KEY_NETWORK, false),
    )

    companion object {
        private const val PREFS = "devexplorer_settings"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_NETWORK = "network_enabled"

        @Volatile
        private var instance: SettingsStore? = null

        fun get(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context).also { instance = it }
            }

        /** Blocking read of just the language tag, for attachBaseContext. */
        fun readLanguage(context: Context): String =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, "").orEmpty()
    }
}
