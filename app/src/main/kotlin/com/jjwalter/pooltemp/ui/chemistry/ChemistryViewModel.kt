package com.jjwalter.pooltemp.ui.chemistry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jjwalter.pooltemp.data.ApiClient
import com.jjwalter.pooltemp.data.ApiService
import com.jjwalter.pooltemp.data.ChemAction
import com.jjwalter.pooltemp.data.ChemDismissRequest
import com.jjwalter.pooltemp.data.ChemDoseRequest
import com.jjwalter.pooltemp.data.ChemLatest
import com.jjwalter.pooltemp.data.ChemReadingRequest
import com.jjwalter.pooltemp.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import kotlin.math.roundToInt

/**
 * Chemistry state + actions.
 *
 * The entry form is held here rather than in the composable so a rotation or a
 * brief backgrounding at poolside does not discard a half-entered test.
 */
class ChemistryViewModel(private val settings: Settings) : ViewModel() {

    /**
     * One field of the entry form.
     *
     * A reading is either a value from the scale or an off-scale marker, never
     * both: the server rejects a row carrying a flag and a value together.
     */
    data class Field(
        val value: String = "",
        val under: Boolean = false,
        val over: Boolean = false,
    ) {
        val censored: Boolean get() = under || over
        val enabled: Boolean get() = !censored
    }

    data class Form(
        val ph: Field = Field(),
        val fc: Field = Field(),
        val tc: Field = Field(),
        val ta: Field = Field(),
        val cya: Field = Field(),
        val ch: Field = Field(),
        val salt: String = "",
        val swg: String = "",
        val note: String = "",
    ) {
        val isEmpty: Boolean
            get() = listOf(ph, fc, tc, ta, cya, ch)
                .all { it.value.isBlank() && !it.censored } &&
                salt.isBlank() && swg.isBlank()
    }

    /** The option list and boundary labels for one field, from `pool_config`. */
    data class Scale(
        val options: List<String> = emptyList(),
        val low: String = "",
        val high: String = "",
    )

    data class Scales(
        val ph: Scale = Scale(),
        val cl: Scale = Scale(),
        val ta: Scale = Scale(),
        val cya: Scale = Scale(),
        val ch: Scale = Scale(),
        val swg: Scale = Scale(),
    )

    data class UiState(
        val loading: Boolean = true,
        val saving: Boolean = false,
        val error: String? = null,
        val latest: ChemLatest? = null,
        val form: Form = Form(),
        val scales: Scales = Scales(),
        val doseLogged: Set<Int> = emptySet(),
        val config: Map<String, String> = emptyMap(),
        val productLabels: Map<String, String> = emptyMap(),
    )

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
                        val config = cfg?.config ?: it.config
                        it.copy(
                            loading = false,
                            latest = latest,
                            config = config,
                            scales = buildScales(config),
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

    // ── Scales ───────────────────────────────────────────────────────────

    /**
     * Build the dropdown options from the server's scale bounds so the app,
     * the web page, and the engine's off-scale handling all agree.
     *
     * Steps are counted by index rather than accumulated: adding 0.1 twelve
     * times lands on 7.999999999999999, and the option would no longer match
     * the value the server stores.
     */
    private fun buildScales(cfg: Map<String, String>): Scales {
        fun n(key: String, fallback: Double) = cfg[key]?.toDoubleOrNull() ?: fallback
        fun steps(lo: Double, hi: Double, step: Double, decimals: Int): List<String> {
            val count = ((hi - lo) / step).roundToInt()
            return (0..count).map { i ->
                val v = lo + i * step
                if (decimals == 0) v.roundToInt().toString()
                else String.format(java.util.Locale.US, "%.${decimals}f", v)
            }
        }

        val phLo = n("ph_scale_min", 6.8)
        val phHi = n("ph_scale_max", 8.0)
        val clHi = n("cl_scale_max", 5.0)
        val taLo = n("ta_scale_min", 0.0)
        val taHi = n("ta_scale_max", 150.0)
        val cyaLo = n("cya_scale_min", 30.0)
        val cyaHi = n("cya_scale_max", 120.0)
        val chLo = n("ch_scale_min", 100.0)
        val chHi = n("ch_scale_max", 400.0)

        fun whole(v: Double) = v.roundToInt().toString()
        return Scales(
            ph = Scale(steps(phLo, phHi, n("ph_scale_step", 0.1), 1),
                String.format(java.util.Locale.US, "%.1f", phLo),
                String.format(java.util.Locale.US, "%.1f", phHi)),
            cl = Scale(steps(0.0, clHi, 1.0, 0), "", whole(clHi)),
            ta = Scale(steps(taLo, taHi, n("ta_scale_step", 10.0), 0), "", whole(taHi)),
            cya = Scale(steps(cyaLo, cyaHi, n("cya_scale_step", 10.0), 0),
                whole(cyaLo), whole(cyaHi)),
            ch = Scale(steps(chLo, chHi, n("ch_scale_step", 10.0), 0),
                whole(chLo), whole(chHi)),
            swg = Scale(steps(0.0, 100.0, n("swg_scale_step", 5.0), 0), "", "100"),
        )
    }

    // ── Form editing ─────────────────────────────────────────────────────
    // Nothing is preselected. A blank field means "not tested", and defaulting
    // one would quietly invent a reading the user never took.

    private fun edit(which: Which, block: (Field) -> Field) = _state.update { s ->
        val f = s.form
        s.copy(
            form = when (which) {
                Which.PH -> f.copy(ph = block(f.ph))
                Which.FC -> f.copy(fc = block(f.fc))
                Which.TC -> f.copy(tc = block(f.tc))
                Which.TA -> f.copy(ta = block(f.ta))
                Which.CYA -> f.copy(cya = block(f.cya))
                Which.CH -> f.copy(ch = block(f.ch))
            },
        )
    }

    enum class Which { PH, FC, TC, TA, CYA, CH }

    fun setValue(which: Which, value: String) = edit(which) { it.copy(value = value) }

    /** Under and over are mutually exclusive, and either clears the value. */
    fun setUnder(which: Which, on: Boolean) = edit(which) {
        Field(value = if (on) "" else it.value, under = on, over = if (on) false else it.over)
    }

    fun setOver(which: Which, on: Boolean) = edit(which) {
        Field(value = if (on) "" else it.value, under = if (on) false else it.under, over = on)
    }

    fun setSalt(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(salt = text.filter { it.isDigit() }.take(5)))
    }

    fun setSwg(value: String) = _state.update { s -> s.copy(form = s.form.copy(swg = value)) }

    fun setNote(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(note = text.take(500)))
    }

    // ── Saving ───────────────────────────────────────────────────────────

    fun save() {
        val a = api ?: return
        val f = _state.value.form
        if (f.isEmpty) {
            _state.update { it.copy(error = "Enter at least one reading first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val req = ChemReadingRequest(
                ph = f.ph.value.toDoubleOrNull(),
                phBelow7 = if (f.ph.under) 1 else null,
                phOver = if (f.ph.over) 1 else null,
                fc = f.fc.value.toIntOrNull(),
                fcOver = if (f.fc.over) 1 else null,
                tc = f.tc.value.toIntOrNull(),
                tcOver = if (f.tc.over) 1 else null,
                ta = f.ta.value.toIntOrNull(),
                taOver = if (f.ta.over) 1 else null,
                cya = f.cya.value.toIntOrNull(),
                cyaBelow30 = if (f.cya.under) 1 else null,
                cyaOver = if (f.cya.over) 1 else null,
                ch = f.ch.value.toIntOrNull(),
                chUnder = if (f.ch.under) 1 else null,
                chOver = if (f.ch.over) 1 else null,
                salt = f.salt.toIntOrNull(),
                swgPct = f.swg.toIntOrNull(),
                note = f.note.ifBlank { null },
            )
            runCatching { a.postChemReading(req) }.fold(
                onSuccess = { rec ->
                    _state.update {
                        it.copy(
                            saving = false, latest = rec,
                            form = Form(), doseLogged = emptySet(),
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, error = describe(e)) }
                },
            )
        }
    }

    /** Record that a recommended dose was actually added. Feeds calibration. */
    fun logDose(action: ChemAction, readingId: Int?) {
        val a = api ?: return
        val product = action.product ?: return
        val amount = action.amount ?: return
        viewModelScope.launch {
            runCatching {
                a.postChemDose(
                    ChemDoseRequest(
                        product = product, amount = amount,
                        unit = action.unit ?: "", readingId = readingId,
                        recommendedAmount = amount,
                    ),
                )
            }.fold(
                onSuccess = {
                    _state.update { it.copy(doseLogged = it.doseLogged + action.order) }
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

    fun clearError() = _state.update { it.copy(error = null) }

    /** A 400 carries a usable message, so surface it rather than a status code. */
    private fun describe(e: Throwable): String = when {
        e is HttpException && e.code() == 400 ->
            runCatching { e.response()?.errorBody()?.string() }
                .getOrNull()
                ?.let { body -> Regex("\"([^\"]*must[^\"]*)\"").find(body)?.groupValues?.get(1) }
                ?: "That reading was rejected. Check the values."
        else -> e.message ?: e::class.simpleName ?: "Network error"
    }
}

class ChemistryViewModelFactory(private val settings: Settings) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChemistryViewModel(settings) as T
}
