package com.jjwalter.pooltemp.ui.chemistry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jjwalter.pooltemp.data.ApiClient
import com.jjwalter.pooltemp.data.ChemReading
import com.jjwalter.pooltemp.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Past chemistry tests, newest first.
 *
 * Read only on purpose. Correcting a bad row means seeing neighbouring values
 * and retyping carefully, which is a desk job; the web page owns editing and
 * deleting. Keeping it read only here also means no destructive gesture can
 * fire by accident in a wet hand at the pool.
 */
class ChemistryHistoryViewModel(private val settings: Settings) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val readings: List<ChemReading> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val cfg = settings.current()
            if (!cfg.isComplete) {
                _state.update { it.copy(loading = false, error = "Config incomplete") }
                return@launch
            }
            runCatching { ApiClient.forConfig(cfg).chemHistory() }.fold(
                onSuccess = { rows ->
                    _state.update {
                        // The API returns oldest first; reading history is
                        // scanned newest first.
                        it.copy(loading = false, readings = rows.reversed())
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: e::class.simpleName ?: "Network error",
                        )
                    }
                },
            )
        }
    }
}

class ChemistryHistoryViewModelFactory(
    private val settings: Settings,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChemistryHistoryViewModel(settings) as T
}
