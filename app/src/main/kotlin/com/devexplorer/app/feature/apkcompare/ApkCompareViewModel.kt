package com.devexplorer.app.feature.apkcompare

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devexplorer.core.model.InstalledPackage
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.usecase.AnalyzeApkUseCase
import com.devexplorer.core.usecase.CompareApkUseCase
import com.devexplorer.core.usecase.ListInstalledPackagesUseCase
import com.devexplorer.data.apk.ApkFileRepository
import com.devexplorer.data.packages.PackageManagerRepository
import com.devexplorer.data.storage.SafStorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One picked side of the comparison (a package name + its display label). */
data class CompareSide(
    val ref: StorageRef,
    val label: String,
)

data class ApkCompareUiState(
    val isLoading: Boolean = true,
    val packages: List<InstalledPackage> = emptyList(),
    val old: CompareSide? = null,
    val new: CompareSide? = null,
    val isComparing: Boolean = false,
    val comparison: CompareApkUseCase.Comparison? = null,
    val errorMessage: String? = null,
) {
    /** True once two apps are selected, so the picker can collapse into the result. */
    val bothSelected: Boolean get() = old != null && new != null
}

sealed interface ApkCompareEvent {
    /** Tap a package: fills the first empty slot, or clears it if already picked. */
    data class Pick(val pkg: InstalledPackage) : ApkCompareEvent
    data object ClearSelection : ApkCompareEvent
    data object Refresh : ApkCompareEvent
}

/**
 * Picks two installed packages and produces the structural [CompareApkUseCase.Comparison]
 * between them. Every analysis is read-only (see [AnalyzeApkUseCase]); comparing
 * never modifies either app.
 */
class ApkCompareViewModel(
    private val listPackages: ListInstalledPackagesUseCase,
    private val compareApk: CompareApkUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApkCompareUiState())
    val uiState: StateFlow<ApkCompareUiState> = _uiState.asStateFlow()

    init {
        loadPackages()
    }

    fun onEvent(event: ApkCompareEvent) {
        when (event) {
            is ApkCompareEvent.Pick -> pick(event.pkg)
            ApkCompareEvent.ClearSelection -> _uiState.update {
                it.copy(old = null, new = null, comparison = null, errorMessage = null, isComparing = false)
            }
            ApkCompareEvent.Refresh -> loadPackages()
        }
    }

    private fun pick(pkg: InstalledPackage) {
        val ref = StorageRef.installedPackage(pkg.packageName)
        val state = _uiState.value
        when {
            // Tapping an already-selected app deselects it and drops any stale result.
            state.old?.ref == ref -> _uiState.update { it.copy(old = null, comparison = null) }
            state.new?.ref == ref -> _uiState.update { it.copy(new = null, comparison = null) }
            state.old == null -> _uiState.update { it.copy(old = CompareSide(ref, pkg.label)) }
            state.new == null -> {
                _uiState.update { it.copy(new = CompareSide(ref, pkg.label)) }
                compare()
            }
            else -> Unit // both slots full; ignore until one is cleared
        }
    }

    private fun compare() {
        val state = _uiState.value
        val old = state.old ?: return
        val new = state.new ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isComparing = true, errorMessage = null, comparison = null) }
            compareApk(old.ref, new.ref)
                .onSuccess { result -> _uiState.update { it.copy(isComparing = false, comparison = result) } }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isComparing = false,
                            errorMessage = "Couldn't compare these apps. One may be unreadable.",
                        )
                    }
                }
        }
    }

    private fun loadPackages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            listPackages(includeSystem = false)
                .onSuccess { packages -> _uiState.update { it.copy(isLoading = false, packages = packages) } }
                .onFailure {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Couldn't list installed apps.") }
                }
        }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val storage = SafStorageRepository(appContext)
                val packages = PackageManagerRepository(appContext)
                val apk = ApkFileRepository(appContext)
                ApkCompareViewModel(
                    listPackages = ListInstalledPackagesUseCase(packages),
                    compareApk = CompareApkUseCase(AnalyzeApkUseCase(storage, packages, apk)),
                )
            }
        }
    }
}
