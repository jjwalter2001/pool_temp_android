package com.jjwalter.pooltemp.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Turning entry-form selections into a reading the server will accept.
 *
 * Pure on purpose. This is the code that decides which database column a
 * selection lands in, and a mistake here writes plausible-looking but wrong
 * chemistry rather than failing loudly, so it is worth being able to test
 * without a device, a network, or a database.
 *
 * The field descriptors come from the server, so the mapping itself is not
 * duplicated here; this only applies it.
 */
object ChemEntry {

    /**
     * Off-scale readings are entries in the dropdown rather than separate
     * checkboxes, so one control per parameter and a value can never coexist
     * with an off-scale marker. These sentinels exist only in the UI.
     */
    const val UNDER = "__under__"
    const val OVER = "__over__"

    /** One selectable entry: the value to store (or a sentinel) and its label. */
    data class Option(val value: String, val label: String)

    /**
     * The dropdown entries for one field.
     *
     * Steps are counted by index rather than accumulated: adding 0.1 twelve
     * times lands on 7.999999999999999, which would no longer match the value
     * the server stores.
     */
    fun optionsFor(field: ChemField): List<Option> {
        val out = mutableListOf<Option>()
        field.underLabel?.let { out += Option(UNDER, it) }
        if (field.step > 0) {
            val count = ((field.max - field.min) / field.step).roundToInt()
            for (i in 0..count) {
                val v = field.min + i * field.step
                val text = if (field.decimals == 0) v.roundToInt().toString()
                else String.format(Locale.US, "%.${field.decimals}f", v)
                out += Option(text, text + field.suffix)
            }
        }
        field.overLabel?.let { out += Option(OVER, it) }
        return out
    }

    /**
     * Build the POST body for a new reading.
     *
     * `selections` is keyed by field key; a blank or missing entry means "not
     * tested" and is omitted entirely, because the server reads an absent
     * field as untested and a null as nothing at all.
     *
     * A sentinel sets its flag and contributes no value. The server rejects a
     * row carrying both, so the two must never be emitted together.
     */
    fun buildBody(
        selections: Map<String, String>,
        fields: List<ChemField>,
        salt: String = "",
        note: String = "",
        source: String = "app",
    ): JsonObject {
        val out = mutableMapOf<String, JsonPrimitive>()
        out["source"] = JsonPrimitive(source)

        fields.forEach { f ->
            when (val v = selections[f.key]?.trim().orEmpty()) {
                "" -> Unit
                UNDER -> f.underFlag?.let { out[it] = JsonPrimitive(1) }
                OVER -> f.overFlag?.let { out[it] = JsonPrimitive(1) }
                else -> {
                    // Whole numbers go as integers so the server's "chlorine
                    // must be a whole number" check sees an Int, not 4.0.
                    val d = v.toDoubleOrNull()
                    if (d != null) {
                        out[f.valueField] =
                            if (f.decimals == 0) JsonPrimitive(d.roundToInt())
                            else JsonPrimitive(d)
                    }
                }
            }
        }

        salt.trim().toIntOrNull()?.let { out["salt"] = JsonPrimitive(it) }
        note.trim().takeIf { it.isNotEmpty() }?.let { out["note"] = JsonPrimitive(it) }
        return JsonObject(out)
    }

    /** True when nothing has been entered, so there is nothing to save. */
    fun isEmpty(selections: Map<String, String>, salt: String): Boolean =
        selections.values.all { it.isBlank() } && salt.isBlank()

    /**
     * The recommended amount as editable text, at full precision.
     *
     * Not the one-decimal display rounding: prefilling 0.25 lb as "0.3" and
     * then recording it unchanged would log a dose nobody poured.
     */
    fun amountText(amount: Double?): String {
        if (amount == null) return ""
        return java.math.BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()
    }

    /**
     * What the user typed as the amount actually added, or null if it is not a
     * usable dose. A comma decimal separator is accepted because some keyboards
     * offer only that. Zero or negative is rejected: it would sit in the
     * calibration set as a pour that moved the water for free.
     */
    fun parseAmount(text: String): Double? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return if (v.isFinite() && v > 0) v else null
    }
}
