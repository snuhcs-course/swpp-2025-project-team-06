package com.example.momentag.network

import android.content.Context
import com.example.momentag.data.SessionManager
import com.example.momentag.model.LoginRegisterRequest
import com.example.momentag.model.LoginResponse
import com.example.momentag.model.Photo
import com.example.momentag.model.Tag
import okhttp3.OkHttpClient
import com.example.momentag.model.*
import retrofit2.Response
import retrofit2.http.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
interface ApiService {
    @GET("home/tags")
    suspend fun getHomeTags(): List<Tag>

    @GET("tags/{tagName}")
    suspend fun getPhotosByTag(@Path("tagName") tagName: String): List<Photo>

    @POST("api/auth/signin")
    suspend fun login(@Body loginRequest: LoginRegisterRequest): Response<LoginResponse>

    @POST("api/auth/signup")
    suspend fun register(@Body registerRequest: LoginRegisterRequest): Response<RegisterResponse>

    @POST("api/auth/refresh")
    suspend fun refreshToken(@Body refreshRequest: RefreshRequest): Response<RefreshResponse>

    @POST("api/auth/signout")
    suspend fun logout(@Body logoutRequest: RefreshRequest): Response<Unit>
}

object RetrofitInstance {
    private const val BASE_URL = "http://127.0.0.1:8000/"

    private var apiService: ApiService? = null

    fun getApiService(context: Context): ApiService {
        if (apiService == null) {
            val sessionManager = SessionManager(context.applicationContext)
            val authInterceptor = AuthInterceptor(sessionManager)
            val tokenAuthenticator = TokenAuthenticator(context, sessionManager)

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .authenticator(tokenAuthenticator)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            apiService = retrofit.create(ApiService::class.java)
        }
        return apiService!!
    }
}
