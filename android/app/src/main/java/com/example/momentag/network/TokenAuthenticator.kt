package com.example.momentag.network

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
    private val sessionStore: SessionStore,
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
                return@runBlocking null
            }

            val tokenResponse =
                try {
                    refreshTokenApi(refreshToken)
                } catch (e: Exception) {
                    sessionStore.clearTokens()
                    return@runBlocking null
                }

            if (!tokenResponse.isSuccessful || tokenResponse.body() == null) { // expired or wrong Refresh Token
                sessionStore.clearTokens()
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

    private suspend fun refreshTokenApi(refreshToken: String): retrofit2.Response<RefreshResponse> {
        val retrofit =
            Retrofit
                .Builder()
                .baseUrl(RetrofitInstance.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .client(
                    OkHttpClient
                        .Builder()
                        .build(),
                ) // no authinterceptor → no recursion
                .build()

        val service = retrofit.create(ApiService::class.java)
        return service.refreshToken(RefreshRequest(refreshToken))
    }
}
