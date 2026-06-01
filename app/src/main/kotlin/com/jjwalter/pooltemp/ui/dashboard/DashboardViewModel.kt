package com.jjwalter.pooltemp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jjwalter.pooltemp.data.ApiClient
import com.jjwalter.pooltemp.data.ApiService
import com.jjwalter.pooltemp.data.Reading
import com.jjwalter.pooltemp.data.Settings
import com.jjwalter.pooltemp.data.SwitchControlRequest
import com.jjwalter.pooltemp.data.SwitchState
import com.jjwalter.pooltemp.data.Weather
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dashboard state + actions. One VM per screen instance, scoped to the
 * Dashboard nav destination. Auto-refresh ticking is driven by the screen
 * via [refresh] (see DashboardScreen.LaunchedEffect) so it pauses cleanly
 * when the screen leaves composition.
 */
class DashboardViewModel(private val settings: Settings) : ViewModel() {

    data class UiState(
        val initialLoading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val readings: List<Reading> = emptyList(),
        val weather: Weather? = null,
        val switch: SwitchState? = null,
        val lastFetchedMs: Long? = null,
        val heaterPending: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var api: ApiService? = null
    private var userName: String = ""

    init {
        viewModelScope.launch {
            val cfg = settings.current()
            if (cfg.isComplete) {
                api = ApiClient.forConfig(cfg)
                userName = cfg.userName
                refresh()
            } else {
                // Shouldn't happen -- MainActivity only routes here when
                // hasConfig is true -- but guard anyway.
                _state.update { it.copy(initialLoading = false, error = "Config incomplete") }
            }
        }
    }

    /** Fetches readings, weather, and switch in parallel. Safe to call
     *  repeatedly; concurrent calls just overwrite each other's results. */
    fun refresh() {
        val a = api ?: return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            try {
                coroutineScope {
                    val r = async { a.readings() }
                    val w = async { runCatching { a.weather() }.getOrNull() }
                    val s = async { runCatching { a.switch() }.getOrNull() }
                    val readings = r.await()
                    val weather = w.await()
                    val switch = s.await()
                    _state.update {
                        it.copy(
                            initialLoading = false,
                            refreshing = false,
                            readings = readings,
                            weather = weather,
                            switch = switch,
                            lastFetchedMs = System.currentTimeMillis(),
                            error = null,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        initialLoading = false,
                        refreshing = false,
                        error = e.message ?: e::class.simpleName ?: "Network error",
                    )
                }
            }
        }
    }

    fun setHeater(on: Boolean) {
        val a = api ?: return
        viewModelScope.launch {
            _state.update { it.copy(heaterPending = true, error = null) }
            try {
                val res = a.setSwitch(SwitchControlRequest(on = on), user = userName)
                if (res.ok == true) {
                    // Optimistic local update; the refresh that follows
                    // reconciles with the authoritative DB state.
                    _state.update {
                        val nowSec = System.currentTimeMillis() / 1000
                        val updated = (it.switch ?: SwitchState()).copy(
                            state = res.state ?: if (on) 1 else 0,
                            ts = nowSec,
                            user = userName,
                            live = on,
                        )
                        it.copy(heaterPending = false, switch = updated)
                    }
                    refresh()
                } else {
                    _state.update {
                        it.copy(
                            heaterPending = false,
                            error = res.error ?: "Heater control failed",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        heaterPending = false,
                        error = e.message ?: "Heater request failed",
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

class DashboardViewModelFactory(private val settings: Settings) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DashboardViewModel(settings) as T
}
