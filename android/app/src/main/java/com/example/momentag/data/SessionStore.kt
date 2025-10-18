package com.example.momentag.data

import kotlinx.coroutines.flow.StateFlow

/**
 * SessionStore Interface
 *
 * 역할: 토큰/세션의 저장소만 담당 (DataStore/EncryptedSharedPreferences 등)
 * - 토큰 저장/조회/삭제 기능만 제공
 * - 비즈니스 로직을 포함하지 않음
 */
interface SessionStore {
    /**
     * Access token의 실시간 상태를 관찰할 수 있는 Flow
     */
    val accessTokenFlow: StateFlow<String?>

    /**
     * Refresh token의 실시간 상태를 관찰할 수 있는 Flow
     */
    val refreshTokenFlow: StateFlow<String?>

    /**
     * 데이터 로딩 완료 여부
     */
    val isLoaded: StateFlow<Boolean>

    /**
     * Access token 조회 (메모리 캐시 우선)
     * @return 저장된 access token 또는 null
     */
    fun getAccessToken(): String?

    /**
     * Refresh token 조회 (메모리 캐시 우선)
     * @return 저장된 refresh token 또는 null
     */
    fun getRefreshToken(): String?

    /**
     * 토큰 저장
     * @param accessToken 저장할 access token
     * @param refreshToken 저장할 refresh token
     */
    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
    )

    /**
     * 토큰 삭제
     */
    suspend fun clearTokens()
}
