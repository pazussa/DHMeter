package com.dropindh.app.ui.screens.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhmeter.domain.model.RunEvent
import com.dhmeter.domain.usecase.GetRunEventsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EventsUiState(
    val events: List<RunEvent> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val getRunEventsUseCase: GetRunEventsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(EventsUiState())
    val uiState: StateFlow<EventsUiState> = _uiState.asStateFlow()

    fun loadEvents(runId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            getRunEventsUseCase(runId)
                .onSuccess { events ->
                    _uiState.update { it.copy(events = events, isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }
}

