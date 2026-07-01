package com.devexplorer.core.model

/**
 * The two safety zones the whole app is organized around.
 *
 * [System] is everything the user is *browsing* — always read-only. We never
 * hold write access to it; the OS (via the Storage Access Framework) is the
 * enforcement point.
 *
 * [Workspace] is the single app-private sandbox directory — the only writable
 * surface in the entire app. Every screen renders a clear visual badge for the
 * zone it's in so the read-only/read-write distinction is never ambiguous.
 *
 * See docs/ARCHITECTURE.md §10 (Safety Architecture).
 */
enum class Zone {
    System,
    Workspace,
}
