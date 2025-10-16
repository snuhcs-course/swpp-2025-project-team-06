package com.example.momentag.network

import com.example.momentag.data.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionManager: SessionManager,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val url = originalRequest.url.encodedPath

        // Skip auth header for login/signup/refresh endpoints
        val skipAuthPaths = listOf("/api/auth/signin/", "/api/auth/signup/", "/api/auth/refresh/")
        val shouldSkipAuth = skipAuthPaths.any { url.contains(it) }

        val request =
            if (shouldSkipAuth) {
                originalRequest
            } else {
                val token = sessionManager.getAccessToken()
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
