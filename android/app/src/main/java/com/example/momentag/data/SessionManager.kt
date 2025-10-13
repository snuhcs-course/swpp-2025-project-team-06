package com.example.momentag.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

class SessionManager(
    context: Context,
) {
    private val dataStore = context.sessionDataStore

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")
    }

    // memory cache on StateFlow
    private val _accessToken = MutableStateFlow<String?>(null)
    val accessTokenFlow: StateFlow<String?> = _accessToken.asStateFlow()

    private val _refreshToken = MutableStateFlow<String?>(null)
    val refreshTokenFlow: StateFlow<String?> = _refreshToken.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    init {
        // store token from DataStore to StateFlow
        CoroutineScope(Dispatchers.IO).launch {
            dataStore.data.first().let { prefs ->
                _accessToken.value = prefs[ACCESS_TOKEN_KEY]
                _refreshToken.value = prefs[REFRESH_TOKEN_KEY]
            }
            _isLoaded.value = true
        }
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
    ) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = accessToken
            prefs[REFRESH_TOKEN_KEY] = refreshToken
        }
        _accessToken.value = accessToken
        _refreshToken.value = refreshToken
    }

    // return token from memory
    fun getAccessToken(): String? = _accessToken.value

    fun getRefreshToken(): String? = _refreshToken.value

    suspend fun clearTokens() {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
        }
        _accessToken.value = null
        _refreshToken.value = null
    }
}
