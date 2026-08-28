package com.jjwalter.pooltemp.ui.chemistry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jjwalter.pooltemp.data.ApiClient
import com.jjwalter.pooltemp.data.ApiService
import com.jjwalter.pooltemp.data.ChemDose
import com.jjwalter.pooltemp.data.ChemReading
import com.jjwalter.pooltemp.data.Settings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Two histories behind one screen: the tests taken, and what was added.
 *
 * Read only on purpose. Correcting a row means seeing neighbouring values and
 * retyping carefully, which is a desk job; the web page owns editing and
 * deleting. It also means no destructive gesture can fire from a wet hand at
 * the pool.
 */
class ChemistryHistoryViewModel(private val settings: Settings) : ViewModel() {

    enum class Tab { TESTS, ADDITIONS }

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val tab: Tab = Tab.TESTS,
        val readings: List<ChemReading> = emptyList(),
        val doses: List<ChemDose> = emptyList(),
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setTab(tab: Tab) = _state.update { it.copy(tab = tab) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val cfg = settings.current()
            if (!cfg.isComplete) {
                _state.update { it.copy(loading = false, error = "Config incomplete") }
                return@launch
            }
            val api: ApiService = ApiClient.forConfig(cfg)
            // Both in parallel: switching tabs should not trigger a fetch, and
            // the two lists are small.
            runCatching {
                coroutineScope {
                    val r = async { api.chemHistory() }
                    val d = async { api.chemDoses() }
                    r.await() to d.await()
                }
            }.fold(
                onSuccess = { (readings, doses) ->
                    _state.update {
                        // Both APIs return oldest first; history is scanned
                        // newest first.
                        it.copy(
                            loading = false,
                            readings = readings.reversed(),
                            doses = doses.reversed(),
                        )
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
