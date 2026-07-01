package com.devexplorer.app

import android.app.Application

/**
 * Application entry point. Intentionally minimal for now — it exists so we have
 * a stable process-level hook for later milestones (DI graph root, WorkManager
 * configuration, strict-mode in debug, etc.). Declared in the manifest via
 * android:name=".DevExplorerApplication".
 */
class DevExplorerApplication : Application()
