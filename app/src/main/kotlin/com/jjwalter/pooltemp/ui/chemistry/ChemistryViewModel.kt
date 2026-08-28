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
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Chemistry state + actions.
 *
 * The entry form is held here rather than in the composable so a rotation or a
 * brief backgrounding at poolside does not discard a half-entered test.
 */
class ChemistryViewModel(private val settings: Settings) : ViewModel() {

    companion object {
        /**
         * Off-scale readings are entries in the dropdown, not separate
         * checkboxes: one control per parameter, and picking "Below 6.8"
         * cannot contradict a value the way a checkbox beside a filled field
         * could. These sentinels exist only in the UI; they map onto the flag
         * columns at save time.
         */
        const val UNDER = "__under__"
        const val OVER = "__over__"
    }

    /** One selectable entry: the stored value (or a sentinel) and its label. */
    data class Option(val value: String, val label: String)

    data class Scale(val options: List<Option> = emptyList())

    data class Scales(
        val ph: Scale = Scale(),
        val cl: Scale = Scale(),
        val ta: Scale = Scale(),
        val cya: Scale = Scale(),
        val ch: Scale = Scale(),
        val swg: Scale = Scale(),
    )

    /** Each field is one string: blank, a number, or an off-scale sentinel. */
    data class Form(
        val ph: String = "",
        val fc: String = "",
        val tc: String = "",
        val ta: String = "",
        val cya: String = "",
        val ch: String = "",
        val salt: String = "",
        val swg: String = "",
        val note: String = "",
    ) {
        val isEmpty: Boolean
            get() = listOf(ph, fc, tc, ta, cya, ch, salt, swg).all { it.isBlank() }
    }

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
     * Build the option lists from the server's scale bounds so the app, the web
     * page, and the engine's off-scale handling all read the same numbers.
     *
     * Steps are counted by index rather than accumulated: adding 0.1 twelve
     * times lands on 7.999999999999999, and the option would no longer match
     * the value the server stores.
     */
    private fun buildScales(cfg: Map<String, String>): Scales {
        fun n(key: String, fallback: Double) = cfg[key]?.toDoubleOrNull() ?: fallback

        fun scale(
            lo: Double,
            hi: Double,
            step: Double,
            decimals: Int = 0,
            underLabel: String? = null,
            overLabel: String? = null,
            suffix: String = "",
        ): Scale {
            val out = mutableListOf<Option>()
            underLabel?.let { out += Option(UNDER, it) }
            val count = ((hi - lo) / step).roundToInt()
            for (i in 0..count) {
                val v = lo + i * step
                val text = if (decimals == 0) v.roundToInt().toString()
                else String.format(Locale.US, "%.${decimals}f", v)
                out += Option(text, text + suffix)
            }
            overLabel?.let { out += Option(OVER, it) }
            return Scale(out)
        }

        fun whole(v: Double) = v.roundToInt().toString()
        val phLo = n("ph_scale_min", 6.8)
        val phHi = n("ph_scale_max", 8.0)
        val clHi = n("cl_scale_max", 5.0)
        val cyaLo = n("cya_scale_min", 30.0)
        val cyaHi = n("cya_scale_max", 120.0)
        val chLo = n("ch_scale_min", 100.0)
        val chHi = n("ch_scale_max", 400.0)
        val taHi = n("ta_scale_max", 150.0)

        return Scales(
            ph = scale(
                phLo, phHi, n("ph_scale_step", 0.1), decimals = 1,
                underLabel = "Below " + String.format(Locale.US, "%.1f", phLo),
                overLabel = "Above " + String.format(Locale.US, "%.1f", phHi),
            ),
            cl = scale(0.0, clHi, 1.0, overLabel = "Over " + whole(clHi)),
            ta = scale(
                n("ta_scale_min", 0.0), taHi, n("ta_scale_step", 10.0),
                overLabel = "Over " + whole(taHi),
            ),
            cya = scale(
                cyaLo, cyaHi, n("cya_scale_step", 10.0),
                underLabel = "Below " + whole(cyaLo),
                overLabel = "Over " + whole(cyaHi),
            ),
            ch = scale(
                chLo, chHi, n("ch_scale_step", 10.0),
                underLabel = "Below " + whole(chLo),
                overLabel = "Over " + whole(chHi),
            ),
            swg = scale(0.0, 100.0, n("swg_scale_step", 5.0), suffix = "%"),
        )
    }

    // ── Form editing ─────────────────────────────────────────────────────
    // Nothing is preselected. A blank field means "not tested", and defaulting
    // one would quietly invent a reading the user never took.

    enum class Which { PH, FC, TC, TA, CYA, CH, SWG }

    fun setValue(which: Which, value: String) = _state.update { s ->
        val f = s.form
        s.copy(
            form = when (which) {
                Which.PH -> f.copy(ph = value)
                Which.FC -> f.copy(fc = value)
                Which.TC -> f.copy(tc = value)
                Which.TA -> f.copy(ta = value)
                Which.CYA -> f.copy(cya = value)
                Which.CH -> f.copy(ch = value)
                Which.SWG -> f.copy(swg = value)
            },
        )
    }

    fun setSalt(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(salt = text.filter { it.isDigit() }.take(5)))
    }

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
            // A sentinel sets its flag; anything else is a value. Never both,
            // because the server rejects a row carrying a flag and a value.
            fun num(v: String) = if (v == UNDER || v == OVER) null else v.toIntOrNull()
            fun flag(v: String, sentinel: String) = if (v == sentinel) 1 else null

            val req = ChemReadingRequest(
                ph = if (f.ph == UNDER || f.ph == OVER) null else f.ph.toDoubleOrNull(),
                phBelow7 = flag(f.ph, UNDER),
                phOver = flag(f.ph, OVER),
                fc = num(f.fc),
                fcOver = flag(f.fc, OVER),
                tc = num(f.tc),
                tcOver = flag(f.tc, OVER),
                ta = num(f.ta),
                taOver = flag(f.ta, OVER),
                cya = num(f.cya),
                cyaBelow30 = flag(f.cya, UNDER),
                cyaOver = flag(f.cya, OVER),
                ch = num(f.ch),
                chUnder = flag(f.ch, UNDER),
                chOver = flag(f.ch, OVER),
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

    /**
     * Record that a recommended dose was actually added.
     *
     * `ts` is when it went in the water, which is not necessarily when the
     * button was tapped. Calibration pairs are matched in hours, so a morning
     * dose recorded at bedtime would skew the learned multiplier. Null means
     * now.
     */
    fun logDose(action: ChemAction, readingId: Int?, ts: Long? = null) {
        val a = api ?: return
        val product = action.product ?: return
        val amount = action.amount ?: return
        viewModelScope.launch {
            runCatching {
                a.postChemDose(
                    ChemDoseRequest(
                        product = product, amount = amount,
                        unit = action.unit ?: "", ts = ts,
                        readingId = readingId, recommendedAmount = amount,
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
