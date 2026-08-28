package com.jjwalter.pooltemp.ui.chemistry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jjwalter.pooltemp.data.ApiClient
import com.jjwalter.pooltemp.data.ApiService
import com.jjwalter.pooltemp.data.ChemAction
import com.jjwalter.pooltemp.data.ChemDismissRequest
import com.jjwalter.pooltemp.data.ChemDoseRequest
import com.jjwalter.pooltemp.data.ChemEntry
import com.jjwalter.pooltemp.data.ChemField
import com.jjwalter.pooltemp.data.ChemLatest
import com.jjwalter.pooltemp.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Chemistry state + actions.
 *
 * The entry form is held here rather than in the composable so a rotation or a
 * brief backgrounding at poolside does not discard a half-entered test.
 *
 * The form's shape comes from the server's field descriptors, and the mapping
 * from a selection to a database column lives in [ChemEntry], which is pure
 * and unit tested. Nothing here decides which column a reading lands in.
 */
class ChemistryViewModel(private val settings: Settings) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val saving: Boolean = false,
        val error: String? = null,
        val latest: ChemLatest? = null,
        val fields: List<ChemField> = emptyList(),
        /** Keyed by field key. Blank or absent means "not tested". */
        val selections: Map<String, String> = emptyMap(),
        val salt: String = "",
        val note: String = "",
        val config: Map<String, String> = emptyMap(),
        val productLabels: Map<String, String> = emptyMap(),
    ) {
        val seasonOpen: Boolean get() = latest?.seasonOpen ?: true
        val isEmpty: Boolean get() = ChemEntry.isEmpty(selections, salt)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var api: ApiService? = null

    init {
        viewModelScope.launch {
            val cfg = settings.current()
            if (cfg.isComplete) {
                api = ApiClient.forConfig(cfg)
                refresh()
            } else {
                _state.update { it.copy(loading = false, error = "Config incomplete") }
            }
        }
    }

    fun refresh() {
        val a = api ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { a.chemLatest() }.fold(
                onSuccess = { latest ->
                    val cfg = runCatching { a.chemConfig() }.getOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            latest = latest,
                            config = cfg?.config ?: it.config,
                            fields = cfg?.fields ?: it.fields,
                            productLabels = cfg?.products
                                ?.associate { p -> p.key to p.label }
                                ?: it.productLabels,
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(loading = false, error = describe(e)) }
                },
            )
        }
    }

    // ── Form editing ─────────────────────────────────────────────────────
    // Nothing is preselected. A blank field means "not tested", and defaulting
    // one would quietly invent a reading the user never took.

    fun setValue(key: String, value: String) = _state.update {
        it.copy(selections = it.selections + (key to value))
    }

    fun setSalt(text: String) = _state.update {
        it.copy(salt = text.filter { c -> c.isDigit() }.take(5))
    }

    fun setNote(text: String) = _state.update { it.copy(note = text.take(500)) }

    // ── Saving ───────────────────────────────────────────────────────────

    fun save() {
        val a = api ?: return
        val s = _state.value
        if (s.isEmpty) {
            _state.update { it.copy(error = "Enter at least one reading first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val body = ChemEntry.buildBody(
                selections = s.selections,
                fields = s.fields,
                salt = s.salt,
                note = s.note,
                source = "app",
            )
            runCatching { a.postChemReading(body) }.fold(
                onSuccess = { rec ->
                    _state.update {
                        it.copy(
                            saving = false, latest = rec,
                            selections = emptyMap(), salt = "", note = "",
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, error = describe(e)) }
                },
            )
        }
    }

    /**
     * Record that a recommended dose was actually added.
     *
     * `onDate` is the day it went in the water, which is not necessarily the
     * day the button was tapped. Calibration pairs are matched in hours, so a
     * morning dose recorded at bedtime would skew the learned multiplier. Null
     * means now, and the server resolves a date to midday in the pool's
     * timezone rather than the phone's.
     */
    fun logDose(action: ChemAction, readingId: Int?, onDate: String? = null) {
        val a = api ?: return
        val product = action.product ?: return
        val amount = action.amount ?: return
        viewModelScope.launch {
            runCatching {
                a.postChemDose(
                    ChemDoseRequest(
                        product = product, amount = amount,
                        unit = action.unit ?: "", onDate = onDate,
                        readingId = readingId, recommendedAmount = amount,
                    ),
                )
            }.fold(
                onSuccess = {
                    // The server now marks the action done, so a refresh is
                    // the single source of truth. Local "recorded" state was
                    // what made the recommendation reappear after a reload.
                    refresh()
                },
                onFailure = { e -> _state.update { it.copy(error = describe(e)) } },
            )
        }
    }

    /**
     * Skip a recommendation, or put it back.
     *
     * Keyed by the action's stable key rather than its position, because the
     * engine regenerates actions on every fetch and the order shifts as they
     * come and go.
     */
    fun toggleSkip(action: ChemAction, readingId: Int?) {
        val a = api ?: return
        val id = readingId ?: return
        viewModelScope.launch {
            runCatching {
                a.dismissChemAction(id, ChemDismissRequest(action.key, !action.dismissed))
            }.fold(
                onSuccess = { rec -> _state.update { it.copy(latest = rec) } },
                onFailure = { e -> _state.update { it.copy(error = describe(e)) } },
            )
        }
    }

    /** Closing the pool stops the overdue nudges and the DUE badge. */
    fun setSeasonOpen(open: Boolean) {
        val a = api ?: return
        viewModelScope.launch {
            runCatching {
                a.postChemConfig(mapOf("season_open" to if (open) "1" else "0"))
            }.fold(
                onSuccess = { refresh() },
                onFailure = { e -> _state.update { it.copy(error = describe(e)) } },
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** A 400 carries a usable message, so surface it rather than a status code. */
    private fun describe(e: Throwable): String = when {
        e is HttpException && e.code() == 400 ->
            runCatching { e.response()?.errorBody()?.string() }
                .getOrNull()
                ?.let { body ->
                    Regex("\"([^\"]*(?:must|cannot|implausibly)[^\"]*)\"")
                        .find(body)?.groupValues?.get(1)
                }
                ?: "That was rejected. Check the values."
        else -> e.message ?: e::class.simpleName ?: "Network error"
    }
}

class ChemistryViewModelFactory(private val settings: Settings) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChemistryViewModel(settings) as T
}
