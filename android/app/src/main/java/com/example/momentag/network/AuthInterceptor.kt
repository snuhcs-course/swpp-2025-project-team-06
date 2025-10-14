package com.example.momentag.network

import com.example.momentag.data.SessionStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * AuthInterceptor
 *
 * 역할: 매 요청에 AccessToken을 자동으로 추가
 * - 메모리 캐시에서 즉시 토큰 조회 (SessionStore 사용)
 * - 인증이 필요없는 엔드포인트는 스킵
 * - 토큰 리프레시는 TokenAuthenticator가 담당
 */
class AuthInterceptor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.encodedPath

        // Skip auth header for login/signup/refresh endpoints
        val skipAuthPaths = listOf("/api/auth/signin/", "/api/auth/signup/", "/api/auth/refresh/")
        val shouldSkipAuth = skipAuthPaths.any { url.contains(it) }

        val request =
            if (shouldSkipAuth) {
                println("AuthInterceptor: Skipping auth for $url")
                originalRequest
            } else {
                // SessionStore에서 메모리 캐시된 토큰을 즉시 가져옴
                val token = sessionStore.getAccessToken()
                println("AuthInterceptor: Adding token to $url, token=${token?.take(20)}...")
                originalRequest
                    .newBuilder()
                    .apply {
                        if (!token.isNullOrBlank()) {
                            header("Authorization", "Bearer $token")
                        }
                    }.build()
            }

        return chain.proceed(request)
    }
}
