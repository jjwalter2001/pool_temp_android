package com.jjwalter.pooltemp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "pool_temp_settings")

/**
 * App-wide configuration. All values are stored as preferences and exposed
 * as a single [Config] flow. First-launch onboarding writes the bundle in
 * one go via [save].
 */
class Settings(private val context: Context) {

    data class Config(
        /** Origin URL of the pool_temp Flask app, no trailing slash. */
        val baseUrl: String,
        /** Cloudflare Access service token client id. */
        val cfClientId: String,
        /** Cloudflare Access service token client secret. */
        val cfClientSecret: String,
        /** Optional API_TOKEN; sent as Authorization: Bearer when non-blank. */
        val apiToken: String,
        /** Family-member name attached to heater toggles via X-User. */
        val userName: String,
    ) {
        val isComplete: Boolean get() =
            baseUrl.isNotBlank() &&
            cfClientId.isNotBlank() &&
            cfClientSecret.isNotBlank() &&
            userName.isNotBlank()
    }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val CF_CLIENT_ID = stringPreferencesKey("cf_client_id")
        val CF_CLIENT_SECRET = stringPreferencesKey("cf_client_secret")
        val API_TOKEN = stringPreferencesKey("api_token")
        val USER_NAME = stringPreferencesKey("user_name")
    }

    val config: Flow<Config> = context.dataStore.data.map { prefs ->
        Config(
            baseUrl = prefs[Keys.BASE_URL].orEmpty(),
            cfClientId = prefs[Keys.CF_CLIENT_ID].orEmpty(),
            cfClientSecret = prefs[Keys.CF_CLIENT_SECRET].orEmpty(),
            apiToken = prefs[Keys.API_TOKEN].orEmpty(),
            userName = prefs[Keys.USER_NAME].orEmpty(),
        )
    }

    val hasConfig: Flow<Boolean> = config.map { it.isComplete }

    suspend fun current(): Config = config.first()

    suspend fun save(c: Config) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = c.baseUrl.trimEnd('/')
            prefs[Keys.CF_CLIENT_ID] = c.cfClientId.trim()
            prefs[Keys.CF_CLIENT_SECRET] = c.cfClientSecret.trim()
            prefs[Keys.API_TOKEN] = c.apiToken.trim()
            prefs[Keys.USER_NAME] = c.userName.trim()
        }
    }
}
