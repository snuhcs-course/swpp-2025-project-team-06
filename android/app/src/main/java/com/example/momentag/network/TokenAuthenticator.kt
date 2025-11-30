package com.example.momentag.network

import android.content.Context
import com.example.momentag.R
import com.example.momentag.data.SessionExpirationManager
import com.example.momentag.data.SessionStore
import com.example.momentag.model.RefreshRequest
import com.example.momentag.model.RefreshResponse
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * TokenAuthenticator
 *
 * 역할: 401 응답 시 자동으로 토큰 리프레시 후 요청 재시도
 * - SessionStore를 사용하여 토큰 관리
 * - 리프레시 실패 시 토큰 삭제 (로그아웃)
 * - 재귀 호출 방지를 위해 별도 OkHttpClient 사용
 */
class TokenAuthenticator(
    private val context: Context,
    private val sessionStore: SessionStore,
    private val sessionExpirationManager: SessionExpirationManager,
) : Authenticator {
    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        val url = response.request.url.encodedPath

        // Skip token refresh for auth endpoints (login/signup should return proper errors)
        val skipRefreshPaths = listOf("/api/auth/signin/", "/api/auth/signup/", "/api/auth/refresh/")
        val shouldSkipRefresh = skipRefreshPaths.any { url.contains(it) }

        if (shouldSkipRefresh) {
            return null
        }

        return runBlocking {
            val refreshToken = sessionStore.getRefreshToken()

            if (refreshToken == null) { // no refresh token
                sessionStore.clearTokens()
                onSessionExpired()
                return@runBlocking null
            }

            val tokenResponse =
                try {
                    refreshTokenApi(refreshToken)
                } catch (e: Exception) {
                    sessionStore.clearTokens()
                    onSessionExpired()
                    return@runBlocking null
                }

            if (!tokenResponse.isSuccessful || tokenResponse.body() == null) { // expired or wrong Refresh Token
                sessionStore.clearTokens()
                onSessionExpired()
                return@runBlocking null
            }

            // restore new token
            val newAccessToken = tokenResponse.body()!!.access_token
            sessionStore.saveTokens(newAccessToken, refreshToken)

            // redo request
            response.request
                .newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        }
    }

    private suspend fun onSessionExpired() {
        sessionExpirationManager.onSessionExpired()
    }

    private suspend fun refreshTokenApi(refreshToken: String): retrofit2.Response<RefreshResponse> {
        val baseUrl = context.getString(R.string.API_BASE_URL)

        // Create OkHttpClient with SSL configuration for self-signed cert
        val okHttpClient =
            SslHelper
                .configureToTrustCertificate(
                    OkHttpClient.Builder(),
                    context,
                ).build()

        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(okHttpClient) // no authinterceptor → no recursion, but with SSL config
                .build()

        val service = retrofit.create(ApiService::class.java)
        return service.refreshToken(RefreshRequest(refreshToken))
    }
}
