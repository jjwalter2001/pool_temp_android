package com.jjwalter.pooltemp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jjwalter.pooltemp.data.ApiClient
import com.jjwalter.pooltemp.data.ApiService
import com.jjwalter.pooltemp.data.ChemLatest
import com.jjwalter.pooltemp.data.HistoryPoint
import com.jjwalter.pooltemp.data.LightningControlRequest
import com.jjwalter.pooltemp.data.LightningState
import com.jjwalter.pooltemp.data.Reading
import com.jjwalter.pooltemp.data.Settings
import com.jjwalter.pooltemp.data.SwitchControlRequest
import com.jjwalter.pooltemp.data.SwitchState
import com.jjwalter.pooltemp.data.Weather
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
        val lightning: LightningState? = null,
        /** Newest chemistry reading plus its recommendation. Null while
         *  loading or if the backend predates the chemistry endpoints. */
        val chem: ChemLatest? = null,
        val lastFetchedMs: Long? = null,
        val heaterPending: Boolean = false,
        val lightningPending: Boolean = false,
        /** 24h temperature history per device, populated by the second
         *  fetch pass after the readings list is known. Empty list means
         *  "loaded but no data"; missing key means "not yet loaded". */
        val histories: Map<String, List<HistoryPoint>> = emptyMap(),
    )

    /** Result of the parallel phase-A fetch. A named type (vs Triple) keeps
     *  the destructuring readable as fields are added. */
    private data class PhaseA(
        val readings: List<Reading>,
        val weather: Weather?,
        val switch: SwitchState?,
        val lightning: LightningState?,
        val chem: ChemLatest?,
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

    /** Two-pass refresh: A) readings + weather + switch in parallel so the
     *  cards render quickly; B) 24h history per device in the background so
     *  sparklines fill in once available. A failure in A surfaces; B
     *  failures are silent (histories are non-essential). */
    fun refresh() {
        val a = api ?: return
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true, error = null) }
            val phaseA = runCatching {
                coroutineScope {
                    val r = async { a.readings() }
                    val w = async { runCatching { a.weather() }.getOrNull() }
                    val s = async { runCatching { a.switch() }.getOrNull() }
                    // Lightning-alert state is optional (503 if HA isn't wired
                    // up on the backend); a null just hides the card.
                    val l = async { runCatching { a.lightning() }.getOrNull() }
                    // Chemistry is optional the same way: an older backend
                    // 404s here and the card simply does not render.
                    val c = async { runCatching { a.chemLatest() }.getOrNull() }
                    PhaseA(r.await(), w.await(), s.await(), l.await(), c.await())
                }
            }
            phaseA.fold(
                onSuccess = { (readings, weather, switch, lightning, chem) ->
                    _state.update {
                        it.copy(
                            initialLoading = false,
                            refreshing = false,
                            readings = readings,
                            weather = weather,
                            switch = switch,
                            lightning = lightning,
                            chem = chem,
                            lastFetchedMs = System.currentTimeMillis(),
                            error = null,
                        )
                    }
                    fetchHistories(a, readings.map { it.deviceId })
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            initialLoading = false,
                            refreshing = false,
                            error = e.message ?: e::class.simpleName ?: "Network error",
                        )
                    }
                },
            )
        }
    }

    private suspend fun fetchHistories(a: ApiService, devices: List<String>) {
        if (devices.isEmpty()) return
        runCatching {
            coroutineScope {
                val results = devices.map { id ->
                    async {
                        id to runCatching { a.history(id, hours = 24) }
                            .getOrElse { emptyList() }
                    }
                }.awaitAll().toMap()
                _state.update { it.copy(histories = results) }
            }
        }
        // Swallow Phase B errors: a missing sparkline is not a screen-level
        // failure, and the snackbar would be noisy.
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

    fun setLightning(armed: Boolean) {
        val a = api ?: return
        viewModelScope.launch {
            _state.update { it.copy(lightningPending = true, error = null) }
            try {
                val res = a.setLightning(LightningControlRequest(armed = armed), user = userName)
                if (res.ok == true) {
                    // Optimistic update; the follow-up refresh reconciles with
                    // HA's authoritative state.
                    _state.update {
                        it.copy(
                            lightningPending = false,
                            lightning = LightningState(armed = res.armed ?: armed),
                        )
                    }
                    refresh()
                } else {
                    _state.update {
                        it.copy(
                            lightningPending = false,
                            error = res.error ?: "Lightning alert control failed",
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        lightningPending = false,
                        error = e.message ?: "Lightning alert request failed",
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
