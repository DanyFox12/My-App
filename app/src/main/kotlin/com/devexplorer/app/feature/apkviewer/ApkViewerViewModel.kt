package com.devexplorer.app.feature.apkviewer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.DexPackageNode
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.usecase.AnalyzeApkUseCase
import com.devexplorer.core.usecase.ExtractEntryUseCase
import com.devexplorer.core.usecase.ReadDexPackagesUseCase
import com.devexplorer.data.apk.ApkFileRepository
import com.devexplorer.data.packages.PackageManagerRepository
import com.devexplorer.data.storage.SafStorageRepository
import com.devexplorer.data.workspace.WorkspaceModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApkViewerUiState(
    val isLoading: Boolean = true,
    val summary: ApkSummary? = null,
    val errorMessage: String? = null,
    /** Transient one-shot message (e.g. the result of an extraction). */
    val message: String? = null,
    /** Per-package DEX tree — loaded on demand the first time the tab opens. */
    val dexPackages: DexPackageNode? = null,
    val isDexPackagesLoading: Boolean = false,
    /** True once a DEX-tree load finished (even if it found no parseable DEX). */
    val dexPackagesLoaded: Boolean = false,
)

/**
 * Analyzes one APK (identified by [sourceRef]) and exposes the result. The
 * analysis is entirely read-only; see [AnalyzeApkUseCase]. The one write it can
 * trigger — [extract] — only ever copies an entry INTO the Workspace sandbox via
 * [ExtractEntryUseCase]; the source archive is never modified.
 */
class ApkViewerViewModel(
    private val analyzeApk: AnalyzeApkUseCase,
    private val extractEntry: ExtractEntryUseCase,
    private val readDexPackages: ReadDexPackagesUseCase,
    private val sourceRef: StorageRef,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApkViewerUiState())
    val uiState: StateFlow<ApkViewerUiState> = _uiState.asStateFlow()

    init {
        analyze()
    }

    fun retry() = analyze()

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** Copy a single archive entry into the Workspace sandbox. */
    fun extract(entryName: String) {
        viewModelScope.launch {
            extractEntry(sourceRef, entryName)
                .onSuccess { item -> _uiState.update { it.copy(message = "Extracted \"${item.name}\" to Workspace") } }
                .onFailure { _uiState.update { it.copy(message = "Couldn't extract that entry") } }
        }
    }

    /**
     * Walk the whole DEX for the per-package tree. Deliberately lazy — it reads
     * every classes*.dex byte, so it only runs when the DEX tab first opens.
     */
    fun loadDexPackages() {
        val state = _uiState.value
        if (state.isDexPackagesLoading || state.dexPackagesLoaded) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDexPackagesLoading = true) }
            val tree = readDexPackages(sourceRef).getOrNull()
            _uiState.update {
                it.copy(dexPackages = tree, isDexPackagesLoading = false, dexPackagesLoaded = true)
            }
        }
    }

    private fun analyze() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            analyzeApk(sourceRef)
                .onSuccess { summary ->
                    _uiState.update { it.copy(isLoading = false, summary = summary) }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Couldn't analyze this file. It may be unreadable or not a valid archive.",
                        )
                    }
                }
        }
    }

    companion object {
        fun factory(appContext: Context, sourceRef: StorageRef): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val storage = SafStorageRepository(appContext)
                    val packages = PackageManagerRepository(appContext)
                    val apk = ApkFileRepository(appContext)
                    val workspace = WorkspaceModule.repository(appContext)
                    val writeToken = WorkspaceModule.writeCapability()
                    ApkViewerViewModel(
                        analyzeApk = AnalyzeApkUseCase(storage, packages, apk),
                        extractEntry = ExtractEntryUseCase(storage, packages, workspace, writeToken),
                        readDexPackages = ReadDexPackagesUseCase(storage, packages, apk),
                        sourceRef = sourceRef,
                    )
                }
            }
    }
}
