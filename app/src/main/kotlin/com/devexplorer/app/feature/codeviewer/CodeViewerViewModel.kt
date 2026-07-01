package com.devexplorer.app.feature.codeviewer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.devexplorer.core.model.StorageRef
import com.devexplorer.core.model.TextDocument
import com.devexplorer.core.usecase.ReadTextUseCase
import com.devexplorer.data.storage.SafStorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CodeViewerUiState(
    val isLoading: Boolean = true,
    val document: TextDocument? = null,
    val errorMessage: String? = null,
)

class CodeViewerViewModel(
    private val readText: ReadTextUseCase,
    private val sourceRef: StorageRef,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeViewerUiState())
    val uiState: StateFlow<CodeViewerUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            readText(sourceRef)
                .onSuccess { doc -> _uiState.update { it.copy(isLoading = false, document = doc) } }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "Couldn't open this file as text.")
                    }
                }
        }
    }

    companion object {
        fun factory(appContext: Context, sourceRef: StorageRef): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val storage = SafStorageRepository(appContext)
                    CodeViewerViewModel(ReadTextUseCase(storage), sourceRef)
                }
            }
    }
}
