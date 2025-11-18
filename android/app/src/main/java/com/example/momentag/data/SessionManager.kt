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
import kotlinx.coroutines.runBlocking

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * SessionManager
 *
 * SessionStore 인터페이스 구현체
 * DataStore를 사용한 토큰 저장소
 * - 순수하게 저장/조회/삭제 기능만 제공
 * - 비즈니스 로직 없음
 * - Hilt로 싱글톤 관리
 *
 * Note: 클래스명이 SessionManager이지만 실제로는 DataStoreSessionStore입니다.
 * 기존 코드와의 호환성을 위해 SessionManager 이름을 유지합니다.
 */
@javax.inject.Singleton
class SessionManager
    @javax.inject.Inject
    constructor(
        @dagger.hilt.android.qualifiers.ApplicationContext context: Context,
    ) : SessionStore {
        private val dataStore = context.sessionDataStore

        companion object {
            private val ACCESS_TOKEN_KEY = stringPreferencesKey("access_token")
            private val REFRESH_TOKEN_KEY = stringPreferencesKey("refresh_token")

            // Legacy getInstance for backward compatibility during migration
            // This will be removed in Phase 6
            @Volatile
            private var instance: SessionManager? = null

            @Deprecated("Use Hilt injection instead", ReplaceWith("Inject SessionStore in constructor"))
            fun getInstance(context: Context): SessionManager =
                instance
                    ?: synchronized(this) {
                        instance
                            ?: SessionManager(context.applicationContext).also { instance = it }
                    }
        }

        // memory cache on StateFlow
        private val _accessToken = MutableStateFlow<String?>(null)
        override val accessTokenFlow: StateFlow<String?> = _accessToken.asStateFlow()

        private val _refreshToken = MutableStateFlow<String?>(null)
        override val refreshTokenFlow: StateFlow<String?> = _refreshToken.asStateFlow()

        private val _isLoaded = MutableStateFlow(false)
        override val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

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

        override suspend fun saveTokens(
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

        // Return latest token; if cache empty, synchronously load from DataStore to avoid stale reads across instances
        override fun getAccessToken(): String? {
            val cached = _accessToken.value
            if (!cached.isNullOrBlank()) return cached

            runBlocking {
                dataStore.data.first().let { prefs ->
                    _accessToken.value = prefs[ACCESS_TOKEN_KEY]
                    _refreshToken.value = prefs[REFRESH_TOKEN_KEY]
                }
                _isLoaded.value = true
            }
            return _accessToken.value
        }

        override fun getRefreshToken(): String? {
            val cached = _refreshToken.value
            if (!cached.isNullOrBlank()) return cached

            runBlocking {
                dataStore.data.first().let { prefs ->
                    _accessToken.value = prefs[ACCESS_TOKEN_KEY]
                    _refreshToken.value = prefs[REFRESH_TOKEN_KEY]
                }
                _isLoaded.value = true
            }
            return _refreshToken.value
        }

        override suspend fun clearTokens() {
            dataStore.edit { prefs ->
                prefs.remove(ACCESS_TOKEN_KEY)
                prefs.remove(REFRESH_TOKEN_KEY)
            }
            _accessToken.value = null
            _refreshToken.value = null
        }
    }
