package com.example.momentag.network

import android.content.Context
import com.example.momentag.data.SessionManager
import com.example.momentag.model.LoginRequest
import com.example.momentag.model.LoginResponse
import com.example.momentag.model.Photo
import com.example.momentag.model.RefreshRequest
import com.example.momentag.model.RefreshResponse
import com.example.momentag.model.RegisterRequest
import com.example.momentag.model.RegisterResponse
import com.example.momentag.model.SemanticSearchResponse
import com.example.momentag.model.Tag
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
interface ApiService {
    @GET("home/tags")
    suspend fun getHomeTags(): List<Tag>

    @GET("tags/{tagName}")
    suspend fun getPhotosByTag(
        @Path("tagName") tagName: String,
    ): List<Photo>

    @POST("api/auth/signin/")
    suspend fun login(
        @Body loginRequest: LoginRequest,
    ): Response<LoginResponse>

    @POST("api/auth/signup/")
    suspend fun register(
        @Body registerRequest: RegisterRequest,
    ): Response<RegisterResponse>

    @POST("api/auth/refresh/")
    suspend fun refreshToken(
        @Body refreshRequest: RefreshRequest,
    ): Response<RefreshResponse>

    @POST("api/auth/signout/")
    suspend fun logout(
        @Body logoutRequest: RefreshRequest,
    ): Response<Unit>

    /**
     * Semantic Search API
     * GET 방식으로 텍스트 쿼리로 유사한 이미지 검색
     *
     * @param query 검색 쿼리 텍스트
     * @param offset 페이지네이션을 위한 오프셋 (기본값: 0)
     * @return Response<SemanticSearchResponse> - photos: List<Int>
     */
    @GET("api/search/semantic/")
    suspend fun semanticSearch(
        @Query("query") query: String,
        @Query("offset") offset: Int = 0,
    ): Response<SemanticSearchResponse>
}

/**
 * RetrofitInstance
 *
 * Retrofit 싱글톤 인스턴스 제공
 * - AuthInterceptor: 모든 요청에 AccessToken 자동 추가
 * - TokenAuthenticator: 401 시 자동 리프레시 → 재시도
 */
object RetrofitInstance {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    private var apiService: ApiService? = null

    fun getApiService(context: Context): ApiService {
        if (apiService == null) {
            val sessionStore = SessionManager.getInstance(context.applicationContext)
            val authInterceptor = AuthInterceptor(sessionStore)
            val tokenAuthenticator = TokenAuthenticator(sessionStore)

            val okHttpClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor(authInterceptor)
                    .authenticator(tokenAuthenticator)
//                    .connectTimeout(30, TimeUnit.SECONDS)
//                    .readTimeout(30, TimeUnit.SECONDS)
//                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

            val retrofit =
                Retrofit
                    .Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

            apiService = retrofit.create(ApiService::class.java)
        }
        return apiService!!
    }
}
