package com.example.momentag.network

import android.content.Context
import com.example.momentag.data.SessionManager
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

class TokenAuthenticator(
    private val context: Context,
    private val sessionManager: SessionManager
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        return runBlocking {
            val refreshToken = sessionManager.getRefreshToken()

            if (refreshToken == null) { // no refresh token
                sessionManager.clearTokens()
                return@runBlocking null
            }

            val tokenResponse = try {
                refreshTokenApi(refreshToken)
            } catch (e: Exception) {
                sessionManager.clearTokens()
                return@runBlocking null
            }

            if (!tokenResponse.isSuccessful || tokenResponse.body() == null) { // expired or wrong Refresh Token
                sessionManager.clearTokens()
                return@runBlocking null
            }

            // restore new token
            val newAccessToken = tokenResponse.body()!!.access_token
            sessionManager.saveTokens(newAccessToken, refreshToken)

            // redo request
            response.request.newBuilder()
                .header("Authorization", "Bearer $newAccessToken")
                .build()
        }
    }

    private suspend fun refreshTokenApi(refreshToken: String): retrofit2.Response<RefreshResponse> {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://127.0.0.1:8000/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient.Builder().build()) // no authinterceptor → no recursion
            .build()

        val service = retrofit.create(ApiService::class.java)
        return service.refreshToken(RefreshRequest(refreshToken))
    }
}
