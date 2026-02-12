package com.dhmeter.app.ui.screens.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.MultiRunComparisonResult
import com.dhmeter.domain.usecase.CompareMultipleRunsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CompareUiState(
    val comparison: MultiRunComparisonResult? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class CompareViewModel @Inject constructor(
    private val compareMultipleRunsUseCase: CompareMultipleRunsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CompareUiState())
    val uiState: StateFlow<CompareUiState> = _uiState.asStateFlow()

    fun loadComparison(trackId: String, runIds: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, comparison = null) }
            
            compareMultipleRunsUseCase(trackId, runIds)
                .onSuccess { comparison ->
                    _uiState.update { it.copy(comparison = comparison, isLoading = false, error = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false, comparison = null) }
                }
        }
    }
}
