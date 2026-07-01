package com.devexplorer.core.capability

/**
 * The capability model — the type-level backbone of the app's safety story.
 *
 * The idea: a piece of code can only perform an operation if it holds the
 * matching capability *token*. Read use-cases require a [ReadCapability]; write
 * use-cases (arriving with the Workspace in milestone 3) will require a
 * [WriteCapability]. Because the concrete tokens are produced ONLY by the
 * corresponding data module, no analysis/browsing code can obtain a
 * [WriteCapability] — it literally cannot call a write path, even by mistake.
 *
 * These are marker interfaces with no members: their *type*, not their content,
 * is the permission. The read side is used now; the write side is defined here
 * so the contract is complete and documented, and gets its sole implementation
 * in :data:workspace in milestone 3.
 *
 * See docs/ARCHITECTURE.md §10 (Safety Architecture).
 */

/** Grants permission to READ from a storage source (never to write). */
interface ReadCapability

/**
 * Grants permission to WRITE — only ever into the Workspace sandbox. The single
 * implementation lives in :data:workspace (milestone 3); nothing else can mint
 * one, which is what makes "APK analysis is read-only" a compile-time fact.
 */
interface WriteCapability
