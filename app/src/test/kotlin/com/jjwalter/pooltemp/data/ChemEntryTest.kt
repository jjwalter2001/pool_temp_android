package com.jjwalter.pooltemp.data

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * These cover the code that decides which database column a selection lands
 * in. A mistake here writes plausible-looking but wrong chemistry rather than
 * failing loudly, which is exactly the kind of bug a test should catch.
 *
 * The field descriptors mirror what the server sends from `_entry_fields`.
 */
class ChemEntryTest {

    private val ph = ChemField(
        key = "ph", label = "pH", valueField = "ph",
        underFlag = "ph_below7", overFlag = "ph_over",
        underLabel = "Below 6.8", overLabel = "Above 8.0",
        min = 6.8, max = 8.0, step = 0.1, decimals = 1,
    )
    private val fc = ChemField(
        key = "fc", label = "Free chlorine", valueField = "fc",
        underFlag = null, overFlag = "fc_over",
        underLabel = null, overLabel = "Over 5",
        min = 0.0, max = 5.0, step = 1.0, decimals = 0,
    )
    private val cya = ChemField(
        key = "cya", label = "Cyanuric acid", valueField = "cya",
        underFlag = "cya_below30", overFlag = "cya_over",
        underLabel = "Below 30", overLabel = "Over 120",
        min = 30.0, max = 120.0, step = 10.0, decimals = 0,
    )
    private val swg = ChemField(
        key = "swg", label = "SWG output %", valueField = "swg_pct",
        min = 0.0, max = 100.0, step = 5.0, decimals = 0, suffix = "%",
    )
    private val fields = listOf(ph, fc, cya, swg)

    // ── Options ──────────────────────────────────────────────────────────

    @Test
    fun `ph options span the scale without floating point drift`() {
        // Sentinels are not numbers and are checked separately.
        val numbers = ChemEntry.optionsFor(ph)
            .map { it.value }
            .filter { it != ChemEntry.UNDER && it != ChemEntry.OVER }
        assertTrue("6.8" in numbers)
        assertTrue("7.5" in numbers)
        // Accumulating 0.1 twelve times gives 7.999999999999999; counting by
        // index gives the value the server actually stores.
        assertTrue("8.0" in numbers, "expected a clean 8.0, got $numbers")
        assertEquals(13, numbers.size, "6.8 to 8.0 by 0.1 is 13 steps")
        assertFalse(numbers.any { it.length != 3 }, "a value drifted: $numbers")
    }

    @Test
    fun `off-scale entries bracket the numbers`() {
        val opts = ChemEntry.optionsFor(ph)
        assertEquals(ChemEntry.UNDER, opts.first().value)
        assertEquals("Below 6.8", opts.first().label)
        assertEquals(ChemEntry.OVER, opts.last().value)
        assertEquals("Above 8.0", opts.last().label)
    }

    @Test
    fun `a field with no lower limit gets no below entry`() {
        // Zero chlorine is a real reading, not an underflow.
        val opts = ChemEntry.optionsFor(fc)
        assertFalse(opts.any { it.value == ChemEntry.UNDER })
        assertEquals("0", opts.first().value)
    }

    @Test
    fun `swg options carry their suffix but store a bare number`() {
        val opts = ChemEntry.optionsFor(swg)
        val fifty = opts.first { it.value == "50" }
        assertEquals("50%", fifty.label)
    }

    // ── Mapping selections onto columns ──────────────────────────────────

    @Test
    fun `a plain value lands in its own column`() {
        val body = ChemEntry.buildBody(mapOf("ph" to "7.5", "fc" to "4"), fields)
        assertEquals(JsonPrimitive(7.5), body["ph"])
        assertEquals(JsonPrimitive(4), body["fc"])
    }

    @Test
    fun `whole-number fields serialise as integers`() {
        // The server rejects a non-integer chlorine, so 4.0 would 400.
        val body = ChemEntry.buildBody(mapOf("fc" to "4"), fields)
        assertEquals("4", body["fc"].toString())
    }

    @Test
    fun `below maps to the under flag and contributes no value`() {
        val body = ChemEntry.buildBody(mapOf("ph" to ChemEntry.UNDER), fields)
        assertEquals(JsonPrimitive(1), body["ph_below7"])
        assertNull(body["ph"], "a flag and a value together are rejected")
        assertNull(body["ph_over"])
    }

    @Test
    fun `above maps to the over flag`() {
        val body = ChemEntry.buildBody(mapOf("ph" to ChemEntry.OVER), fields)
        assertEquals(JsonPrimitive(1), body["ph_over"])
        assertNull(body["ph"])
        assertNull(body["ph_below7"])
    }

    @Test
    fun `each parameter uses its own flags`() {
        // The bug this guards against is cyanuric acid's "below" writing pH's
        // flag, which would store a plausible reading in the wrong column.
        val body = ChemEntry.buildBody(
            mapOf("cya" to ChemEntry.UNDER, "ph" to "7.5"), fields,
        )
        assertEquals(JsonPrimitive(1), body["cya_below30"])
        assertNull(body["ph_below7"])
        assertEquals(JsonPrimitive(7.5), body["ph"])
    }

    @Test
    fun `a sentinel on a field with no such flag is dropped, not misfiled`() {
        val body = ChemEntry.buildBody(mapOf("fc" to ChemEntry.UNDER), fields)
        assertNull(body["fc"])
        assertEquals(1, body.size, "only source should remain, got $body")
    }

    @Test
    fun `untested fields are omitted entirely`() {
        // Absent means untested to the server; a null would be different.
        val body = ChemEntry.buildBody(mapOf("ph" to "", "fc" to "4"), fields)
        assertNull(body["ph"])
        assertNull(body["ph_below7"])
        assertEquals(JsonPrimitive(4), body["fc"])
    }

    @Test
    fun `swg uses its value_field name, not its key`() {
        val body = ChemEntry.buildBody(mapOf("swg" to "40"), fields)
        assertEquals(JsonPrimitive(40), body["swg_pct"])
        assertNull(body["swg"])
    }

    @Test
    fun `salt and note ride along, blank ones do not`() {
        val withBoth = ChemEntry.buildBody(
            emptyMap(), fields, salt = "3250", note = "after rain",
        )
        assertEquals(JsonPrimitive(3250), withBoth["salt"])
        assertEquals(JsonPrimitive("after rain"), withBoth["note"])

        val without = ChemEntry.buildBody(emptyMap(), fields, salt = "  ", note = "  ")
        assertNull(without["salt"])
        assertNull(without["note"])
    }

    @Test
    fun `source is always present`() {
        assertEquals(JsonPrimitive("app"), ChemEntry.buildBody(emptyMap(), fields)["source"])
    }

    @Test
    fun `an unknown field key is ignored rather than guessed at`() {
        val body = ChemEntry.buildBody(mapOf("mystery" to "9"), fields)
        assertEquals(1, body.size)
    }

    // ── Empty form ───────────────────────────────────────────────────────

    @Test
    fun `empty means nothing selected and no salt`() {
        assertTrue(ChemEntry.isEmpty(mapOf("ph" to "", "fc" to ""), ""))
        assertFalse(ChemEntry.isEmpty(mapOf("ph" to "7.5"), ""))
        assertFalse(ChemEntry.isEmpty(emptyMap(), "3250"))
        // An off-scale marker is a reading, not an empty field.
        assertFalse(ChemEntry.isEmpty(mapOf("ph" to ChemEntry.UNDER), ""))
    }

    // ── Amount actually added ────────────────────────────────────────────

    @Test
    fun `prefilled amount keeps full precision`() {
        assertEquals("0.25", ChemEntry.amountText(0.25))
        assertEquals("128", ChemEntry.amountText(128.0))
        assertEquals("8.7", ChemEntry.amountText(8.7))
        assertEquals("", ChemEntry.amountText(null))
    }

    @Test
    fun `an unchanged prefill parses back to the recommendation`() {
        for (a in listOf(0.25, 128.0, 8.7, 1.0 / 3)) {
            assertEquals(a, ChemEntry.parseAmount(ChemEntry.amountText(a)))
        }
    }

    @Test
    fun `typed amounts parse, including a comma decimal`() {
        assertEquals(64.0, ChemEntry.parseAmount("64"))
        assertEquals(2.5, ChemEntry.parseAmount(" 2.5 "))
        assertEquals(2.5, ChemEntry.parseAmount("2,5"))
    }

    @Test
    fun `unusable amounts are rejected`() {
        for (bad in listOf("", "0", "-3", "abc", "NaN", "Infinity", "1.2.3")) {
            assertNull(ChemEntry.parseAmount(bad), bad)
        }
    }
}
