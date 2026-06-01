package com.jjwalter.pooltemp.data

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds a Retrofit [ApiService] bound to the user's saved [Settings.Config].
 *
 * The OkHttp client injects the Cloudflare Access service-token headers on
 * every request, plus the optional Flask-side bearer token when configured.
 * X-User is per-call (heater toggles only) and lives on the endpoint.
 *
 * Rebuild via [forConfig] whenever settings change -- HTTP clients are cheap.
 */
object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun forConfig(cfg: Settings.Config): ApiService {
        require(cfg.isComplete) { "ApiClient requires a complete config" }
        val baseUrl = cfg.baseUrl.trimEnd('/') + "/"

        val auth = Interceptor { chain ->
            val b = chain.request().newBuilder()
                .header("CF-Access-Client-Id", cfg.cfClientId)
                .header("CF-Access-Client-Secret", cfg.cfClientSecret)
            if (cfg.apiToken.isNotBlank()) {
                b.header("Authorization", "Bearer ${cfg.apiToken}")
            }
            chain.proceed(b.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            // BASIC keeps secrets out of logs while still tracing requests.
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val ok = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(ok)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
