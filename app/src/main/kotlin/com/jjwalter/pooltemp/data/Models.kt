package com.jjwalter.pooltemp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data classes mirroring the shapes the Flask /api/... endpoints return.
 *
 * Field names match the server's JSON, except where annotated with
 * [SerialName]. New backend fields can land here as optional with defaults
 * without breaking older app builds.
 */

@Serializable
data class Reading(
    val deviceId: String,
    val name: String? = null,
    @SerialName("temperature_f") val temperatureF: Double? = null,
    @SerialName("temperature_c") val temperatureC: Double? = null,
    val humidity: Double? = null,
    val battery: Int? = null,
    val lastUpdate: Long,
    val lastUpdateAgo: Long? = null,
)

@Serializable
data class Weather(
    val ts: Long? = null,
    @SerialName("air_temp_f") val airTempF: Double? = null,
    @SerialName("air_temp_c") val airTempC: Double? = null,
    @SerialName("dew_point_f") val dewPointF: Double? = null,
    @SerialName("dew_point_c") val dewPointC: Double? = null,
    @SerialName("solar_radiation") val solarRadiation: Double? = null,
    @SerialName("wind_avg_mph") val windAvgMph: Double? = null,
    @SerialName("wind_gust_mph") val windGustMph: Double? = null,
    val humidity: Double? = null,
)

/** Current heater (Tuya switch) state. `live` may differ from `state` until
 *  the next poll writes the event. `user` is who flipped it (null if external
 *  / poll-detected). */
@Serializable
data class SwitchState(
    val state: Int? = null,
    val ts: Long? = null,
    val user: String? = null,
    val live: Boolean? = null,
)

@Serializable
data class HistoryPoint(
    val ts: Long,
    @SerialName("temp_f") val tempF: Double? = null,
    @SerialName("temp_c") val tempC: Double? = null,
    val humidity: Double? = null,
)

@Serializable
data class SwitchEvent(
    val state: Int,
    val ts: Long,
    val user: String? = null,
)

@Serializable
data class SwitchHistory(
    @SerialName("prev_state") val prevState: Int? = null,
    val events: List<SwitchEvent> = emptyList(),
)

@Serializable
data class SwitchControlRequest(val on: Boolean)

@Serializable
data class SwitchControlResponse(
    val ok: Boolean? = null,
    val state: Int? = null,
    val error: String? = null,
)

/** Armed state of the pool lightning-alert system (an HA input_boolean the
 *  backend proxies). `armed` is null only if the field is absent. */
@Serializable
data class LightningState(val armed: Boolean? = null)

@Serializable
data class LightningControlRequest(val armed: Boolean)

@Serializable
data class LightningControlResponse(
    val ok: Boolean? = null,
    val armed: Boolean? = null,
    val error: String? = null,
)

@Serializable
data class VersionStatus(
    val current: String? = null,
    val latest: String? = null,
    @SerialName("update_available") val updateAvailable: Boolean? = null,
)

// ── Water chemistry ──────────────────────────────────────────────────────────
// Mirrors /api/chem/*. Censored readings are a flag plus a null value, never a
// sentinel number: the Taylor K-1005 cannot read chlorine above 5 ppm, pH below
// 7, or cyanuric acid below 30, so those readings have no value to store.

@Serializable
data class ChemReading(
    val id: Int? = null,
    val ts: Long? = null,
    val ph: Double? = null,
    @SerialName("ph_below7") val phBelow7: Int = 0,
    @SerialName("ph_over") val phOver: Int = 0,
    val fc: Int? = null,
    @SerialName("fc_over") val fcOver: Int = 0,
    val tc: Int? = null,
    @SerialName("tc_over") val tcOver: Int = 0,
    val ta: Int? = null,
    @SerialName("ta_over") val taOver: Int = 0,
    val cya: Int? = null,
    @SerialName("cya_below30") val cyaBelow30: Int = 0,
    @SerialName("cya_over") val cyaOver: Int = 0,
    val salt: Int? = null,
    val ch: Int? = null,
    @SerialName("ch_under") val chUnder: Int = 0,
    @SerialName("ch_over") val chOver: Int = 0,
    @SerialName("swg_pct") val swgPct: Int? = null,
    @SerialName("water_temp_f") val waterTempF: Double? = null,
    val note: String? = null,
)

/** One recommended step. Either a product dose (product + amount + unit) or a
 *  salt cell change (swgPct), never both. */
@Serializable
data class ChemAction(
    val order: Int = 0,
    /** Stable identity, used to remember a skip across regenerations. */
    val key: String = "",
    val dismissed: Boolean = false,
    val product: String? = null,
    val amount: Double? = null,
    val unit: String? = null,
    val reason: String = "",
    val note: String? = null,
    @SerialName("wait_minutes") val waitMinutes: Int = 0,
    val sequence: String? = null,
    @SerialName("swg_pct") val swgPct: Int? = null,
)

/** A dose the engine deliberately withheld, with the reason. The cyanuric acid
 *  re-dose block is the common one. */
@Serializable
data class ChemBlocked(
    val product: String? = null,
    val reason: String = "",
)

@Serializable
data class ChemLsi(
    val value: Double? = null,
    val band: String? = null,
    val note: String? = null,
)

@Serializable
data class ChemLatest(
    val reading: ChemReading? = null,
    @SerialName("reading_id") val readingId: Int? = null,
    @SerialName("days_since") val daysSince: Int? = null,
    val status: Map<String, String> = emptyMap(),
    val actions: List<ChemAction> = emptyList(),
    val blocked: List<ChemBlocked> = emptyList(),
    val warnings: List<String> = emptyList(),
    val lsi: ChemLsi? = null,
    /** Actions still outstanding, i.e. not skipped. */
    @SerialName("pending_count") val pendingCount: Int = 0,
    /** False once the pool is closed: no DUE badge, no overdue nudges. */
    @SerialName("season_open") val seasonOpen: Boolean = true,
    /** Present only on a validation failure. */
    val errors: List<String>? = null,
)

/** Fields left null are omitted by the Json config (explicitNulls = false), and
 *  the server reads an absent field as "not tested". */
@Serializable
data class ChemReadingRequest(
    val ph: Double? = null,
    @SerialName("ph_below7") val phBelow7: Int? = null,
    @SerialName("ph_over") val phOver: Int? = null,
    val fc: Int? = null,
    @SerialName("fc_over") val fcOver: Int? = null,
    val tc: Int? = null,
    @SerialName("tc_over") val tcOver: Int? = null,
    val ta: Int? = null,
    @SerialName("ta_over") val taOver: Int? = null,
    val cya: Int? = null,
    @SerialName("cya_below30") val cyaBelow30: Int? = null,
    @SerialName("cya_over") val cyaOver: Int? = null,
    val salt: Int? = null,
    val ch: Int? = null,
    @SerialName("ch_under") val chUnder: Int? = null,
    @SerialName("ch_over") val chOver: Int? = null,
    @SerialName("swg_pct") val swgPct: Int? = null,
    val note: String? = null,
    val source: String = "app",
)

@Serializable
data class ChemDoseRequest(
    val product: String,
    val amount: Double,
    val unit: String,
    /** When it actually went in. Omitted means now. Calibration matches pairs
     *  in hours, so recording a morning dose at bedtime skews the multiplier. */
    val ts: Long? = null,
    @SerialName("reading_id") val readingId: Int? = null,
    @SerialName("recommended_amount") val recommendedAmount: Double? = null,
    val source: String = "app",
)

@Serializable
data class ChemDoseResponse(
    @SerialName("dose_id") val doseId: Int? = null,
    val errors: List<String>? = null,
)

@Serializable
data class ChemProduct(
    val key: String,
    val label: String,
    val kind: String? = null,
    val unit: String? = null,
    @SerialName("cal_multiplier") val calMultiplier: Double = 1.0,
    @SerialName("cal_n") val calN: Int = 0,
)

@Serializable
data class ChemConfig(
    val config: Map<String, String> = emptyMap(),
    val products: List<ChemProduct> = emptyList(),
    val fields: List<ChemField> = emptyList(),
)

@Serializable
data class ChemDismissRequest(
    @SerialName("action_key") val actionKey: String,
    val dismissed: Boolean = true,
)

/** A chemical actually added, as read back from the log. */
@Serializable
data class ChemDose(
    val id: Int? = null,
    val ts: Long? = null,
    val product: String = "",
    @SerialName("product_label") val productLabel: String = "",
    val amount: Double = 0.0,
    val unit: String = "",
    @SerialName("reading_id") val readingId: Int? = null,
    @SerialName("recommended_amount") val recommendedAmount: Double? = null,
    /** The amount matched what was recommended, which is what makes a dose
     *  usable as calibration evidence rather than just a log entry. */
    @SerialName("as_recommended") val asRecommended: Boolean = false,
    val source: String = "",
    val note: String? = null,
)

/**
 * One entry field, as described by the server.
 *
 * Both clients build their dropdowns and their flag mapping from these, so
 * "which flag does Below mean for cyanuric acid" is answered once on the
 * server rather than copied into each client where the copies can drift.
 */
@Serializable
data class ChemField(
    val key: String,
    val label: String,
    @SerialName("value_field") val valueField: String,
    @SerialName("under_flag") val underFlag: String? = null,
    @SerialName("over_flag") val overFlag: String? = null,
    @SerialName("under_label") val underLabel: String? = null,
    @SerialName("over_label") val overLabel: String? = null,
    val min: Double = 0.0,
    val max: Double = 0.0,
    val step: Double = 1.0,
    val decimals: Int = 0,
    val suffix: String = "",
)
