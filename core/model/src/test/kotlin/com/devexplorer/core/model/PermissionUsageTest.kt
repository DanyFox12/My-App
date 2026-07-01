package com.devexplorer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionUsageTest {

    private fun app(name: String) = InstalledPackage(
        packageName = "pkg.$name",
        label = name,
        versionName = "1.0",
        versionCode = 1,
        isSystem = false,
        minSdk = 24,
        targetSdk = 34,
        firstInstallTime = 0,
        lastUpdateTime = 0,
        apkPath = null,
    )

    @Test
    fun inverts_and_sorts_by_usage_then_name() {
        val camera = "android.permission.CAMERA"
        val internet = "android.permission.INTERNET"
        val mic = "android.permission.RECORD_AUDIO"

        val index = buildPermissionIndex(
            listOf(
                app("Zed") to listOf(camera, internet),
                app("Alpha") to listOf(internet),
                app("Mid") to listOf(internet, mic),
            ),
        )

        // INTERNET used by 3 (most), then CAMERA and RECORD_AUDIO by 1 each,
        // ties broken by permission name.
        assertEquals(listOf(internet, camera, mic), index.map { it.permission })
        assertEquals(3, index[0].count)
        // Apps within a permission are sorted by label case-insensitively.
        assertEquals(listOf("Alpha", "Mid", "Zed"), index[0].apps.map { it.label })
    }

    @Test
    fun short_name_is_the_tail() {
        val usage = PermissionUsage("android.permission.CAMERA", emptyList())
        assertEquals("CAMERA", usage.shortName)
    }
}
