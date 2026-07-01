package com.devexplorer.core.designsystem.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf
import com.devexplorer.core.model.DevicePerformanceTier
import com.devexplorer.core.model.PerformanceBudget

/**
 * Detects a [DevicePerformanceTier] from real device signals, once, at startup.
 *
 * Why this exists: the app must feel rich on a flagship and still butter-smooth
 * on an old/budget phone. Rather than sprinkle `if (oldPhone)` everywhere, we
 * classify the device once and let the resulting [PerformanceBudget] drive every
 * expensive decision (animation, blur, cache sizes, list prefetch).
 *
 * Signals used (all cheap, all safe on every API level ≥ 24):
 *  - [ActivityManager.isLowRamDevice]: the OS's own "go easy" flag.
 *  - RAM class / total memory: separates budget from flagship.
 *  - Available processors: a rough parallelism proxy.
 *  - API level: newer OS versions ship better-tuned runtimes & GPU paths.
 */
object DeviceCapabilities {

    fun detect(context: Context): DevicePerformanceTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return DevicePerformanceTier.Medium

        // The OS explicitly asks apps to be frugal — honor it immediately.
        if (am.isLowRamDevice) return DevicePerformanceTier.Low

        val memInfo = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalMb = memInfo.totalMem / (1024 * 1024)
        val cores = Runtime.getRuntime().availableProcessors()

        return when {
            // Very little RAM or very few cores → tread lightly.
            totalMb < 3_000 || cores <= 4 -> DevicePerformanceTier.Low

            // Comfortable headroom and a modern OS → allow the rich experience.
            totalMb >= 6_000 && cores >= 8 &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> DevicePerformanceTier.High

            else -> DevicePerformanceTier.Medium
        }
    }
}

/**
 * Ambient access to the resolved [PerformanceBudget] anywhere in the Compose
 * tree. Provided once near the root (see DevExplorerTheme). Defaults to the
 * Medium budget so previews and tests render sensibly without a real device.
 */
val LocalPerformanceBudget = staticCompositionLocalOf<PerformanceBudget> {
    DevicePerformanceTier.Medium.budget
}
