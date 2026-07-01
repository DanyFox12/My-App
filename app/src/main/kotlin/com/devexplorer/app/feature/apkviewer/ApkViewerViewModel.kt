package com.devexplorer.app.feature.apkviewer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devexplorer.core.model.ApkSummary
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.usecase.AnalyzeApkUseCase
import com.devexplorer.data.apk.ApkFileRepository
import com.devexplorer.data.packages.PackageManagerRepository
import com.devexplorer.data.storage.SafStorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApkViewerUiState(
    val isLoading: Boolean = true,
    val summary: ApkSummary? = null,
    val errorMessage: String? = null,
)

/**
 * Analyzes one APK (identified by [sourceRef]) and exposes the result. The
 * analysis is entirely read-only; see [AnalyzeApkUseCase].
 */
class ApkViewerViewModel(
    private val analyzeApk: AnalyzeApkUseCase,
    private val sourceRef: StorageRef,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApkViewerUiState())
    val uiState: StateFlow<ApkViewerUiState> = _uiState.asStateFlow()

    init {
        analyze()
    }

    fun retry() = analyze()

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
                    ApkViewerViewModel(AnalyzeApkUseCase(storage, packages, apk), sourceRef)
                }
            }
    }
}
