package com.jjwalter.pooltemp.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface matching the Flask /api/* surface in pool_temp.
 *
 * Auth (CF Access service token + optional bearer) is injected globally by
 * the OkHttp interceptor in [ApiClient], so endpoint signatures stay clean.
 * X-User is per-call (only meaningful on writes), so it's a @Header.
 */
interface ApiService {

    @GET("api/readings")
    suspend fun readings(): List<Reading>

    @GET("api/weather")
    suspend fun weather(): Weather

    @GET("api/switch")
    suspend fun switch(): SwitchState

    @GET("api/switch/history")
    suspend fun switchHistory(@Query("hours") hours: Int = 24): SwitchHistory

    @GET("api/history/{device}")
    suspend fun history(
        @retrofit2.http.Path("device") deviceId: String,
        @Query("hours") hours: Int = 24,
        @Query("bucket") bucketMinutes: Int? = null,
    ): List<HistoryPoint>

    @POST("api/switch/control")
    suspend fun setSwitch(
        @Body req: SwitchControlRequest,
        @Header("X-User") user: String,
    ): SwitchControlResponse

    @GET("api/version")
    suspend fun version(): VersionStatus
}
