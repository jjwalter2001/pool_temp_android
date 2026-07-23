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
