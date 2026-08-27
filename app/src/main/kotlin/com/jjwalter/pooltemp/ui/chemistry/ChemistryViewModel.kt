package com.jjwalter.pooltemp.ui.chemistry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.jjwalter.pooltemp.data.ApiClient
import com.jjwalter.pooltemp.data.ApiService
import com.jjwalter.pooltemp.data.ChemAction
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

/**
 * Chemistry state + actions.
 *
 * The entry form is held here rather than in the composable so a rotation or a
 * brief backgrounding at poolside does not discard a half-entered test.
 */
class ChemistryViewModel(private val settings: Settings) : ViewModel() {

    /**
     * One field of the entry form. A censored reading is a flag with no value,
     * matching the schema: checking the box clears and disables the number.
     */
    data class Field(
        val text: String = "",
        val censored: Boolean = false,
    ) {
        val enabled: Boolean get() = !censored
    }

    data class Form(
        val ph: Field = Field(),
        val fc: Field = Field(),
        val tc: Field = Field(),
        val cya: Field = Field(),
        val ta: String = "",
        val ch: String = "",
        val salt: String = "",
        val swg: String = "",
        val note: String = "",
    ) {
        /** Nothing to save if every field is blank and no box is ticked. */
        val isEmpty: Boolean
            get() = listOf(ph, fc, tc, cya).all { it.text.isBlank() && !it.censored } &&
                listOf(ta, ch, salt, swg).all { it.isBlank() }
    }

    data class UiState(
        val loading: Boolean = true,
        val saving: Boolean = false,
        val error: String? = null,
        val latest: ChemLatest? = null,
        val form: Form = Form(),
        /** Set after a successful save so the screen can show the fresh
         *  recommendation instead of the entry form. */
        val justSaved: ChemLatest? = null,
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
                    // Product labels are cosmetic, so a failure here must not
                    // block the screen; the raw key is a usable fallback.
                    val cfg = runCatching { a.chemConfig() }.getOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            latest = latest,
                            config = cfg?.config ?: it.config,
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

    /** pH is stored to one decimal place, so a second digit is refused at the
     *  keystroke rather than bounced by the server. */
    fun setPh(text: String) = _state.update { s ->
        val cleaned = text.filter { it.isDigit() || it == '.' }
        val dot = cleaned.indexOf('.')
        val capped = if (dot >= 0 && cleaned.length > dot + 2) cleaned.take(dot + 2) else cleaned
        s.copy(form = s.form.copy(ph = s.form.ph.copy(text = capped)))
    }

    /** Chlorine is a whole number 0 through 5, which is all a DPD comparator
     *  can resolve. */
    private fun clampChlorine(text: String): String {
        val digits = text.filter { it.isDigit() }.take(1)
        return if (digits.toIntOrNull()?.let { it in 0..5 } == true) digits else ""
    }

    fun setFc(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(fc = s.form.fc.copy(text = clampChlorine(text))))
    }

    fun setTc(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(tc = s.form.tc.copy(text = clampChlorine(text))))
    }

    fun setCya(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(cya = s.form.cya.copy(text = text.filter { it.isDigit() })))
    }

    fun setTa(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(ta = text.filter { it.isDigit() }))
    }

    fun setCh(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(ch = text.filter { it.isDigit() }))
    }

    fun setSalt(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(salt = text.filter { it.isDigit() }))
    }

    fun setSwg(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(swg = text.filter { it.isDigit() }.take(3)))
    }

    fun setNote(text: String) = _state.update { s ->
        s.copy(form = s.form.copy(note = text.take(500)))
    }

    /** Ticking a censored box clears its value; the two are mutually
     *  exclusive and the server rejects a row carrying both. */
    fun setPhBelow7(on: Boolean) = _state.update { s ->
        s.copy(form = s.form.copy(ph = Field(text = if (on) "" else s.form.ph.text, censored = on)))
    }

    fun setFcOver(on: Boolean) = _state.update { s ->
        s.copy(form = s.form.copy(fc = Field(text = if (on) "" else s.form.fc.text, censored = on)))
    }

    fun setTcOver(on: Boolean) = _state.update { s ->
        s.copy(form = s.form.copy(tc = Field(text = if (on) "" else s.form.tc.text, censored = on)))
    }

    fun setCyaBelow30(on: Boolean) = _state.update { s ->
        s.copy(form = s.form.copy(cya = Field(text = if (on) "" else s.form.cya.text, censored = on)))
    }

    // ── Saving ───────────────────────────────────────────────────────────

    fun save() {
        val a = api ?: return
        val form = _state.value.form
        if (form.isEmpty) {
            _state.update { it.copy(error = "Enter at least one reading first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            val req = ChemReadingRequest(
                ph = form.ph.text.toDoubleOrNull(),
                phBelow7 = if (form.ph.censored) 1 else null,
                fc = form.fc.text.toIntOrNull(),
                fcOver = if (form.fc.censored) 1 else null,
                tc = form.tc.text.toIntOrNull(),
                tcOver = if (form.tc.censored) 1 else null,
                cya = form.cya.text.toIntOrNull(),
                cyaBelow30 = if (form.cya.censored) 1 else null,
                ta = form.ta.toIntOrNull(),
                ch = form.ch.toIntOrNull(),
                salt = form.salt.toIntOrNull(),
                swgPct = form.swg.toIntOrNull(),
                note = form.note.ifBlank { null },
            )
            runCatching { a.postChemReading(req) }.fold(
                onSuccess = { rec ->
                    _state.update {
                        it.copy(
                            saving = false,
                            latest = rec,
                            justSaved = rec,
                            form = Form(),
                            doseLogged = emptySet(),
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(saving = false, error = describe(e)) }
                },
            )
        }
    }

    /** Record that a recommended dose was actually added. This is what feeds
     *  the calibration layer, so it matters that it is easy to tap. */
    fun logDose(action: ChemAction, readingId: Int?) {
        val a = api ?: return
        val product = action.product ?: return
        val amount = action.amount ?: return
        viewModelScope.launch {
            val req = ChemDoseRequest(
                product = product,
                amount = amount,
                unit = action.unit ?: "",
                readingId = readingId,
                recommendedAmount = amount,
            )
            runCatching { a.postChemDose(req) }.fold(
                onSuccess = {
                    _state.update { it.copy(doseLogged = it.doseLogged + action.order) }
                    refresh()
                },
                onFailure = { e ->
                    _state.update { it.copy(error = describe(e)) }
                },
            )
        }
    }

    fun dismissSaved() = _state.update { it.copy(justSaved = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    /** A 400 from the reading endpoint carries a usable message ("ph must have
     *  at most one decimal place"), so surface it rather than a status code. */
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
