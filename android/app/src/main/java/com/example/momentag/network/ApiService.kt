package com.example.momentag.network

import android.content.Context
import com.example.momentag.R
import com.example.momentag.data.SessionManager
import com.example.momentag.model.LoginRequest
import com.example.momentag.model.LoginResponse
import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.PhotoToPhotoRequest
import com.example.momentag.model.RefreshRequest
import com.example.momentag.model.RefreshResponse
import com.example.momentag.model.RegisterRequest
import com.example.momentag.model.RegisterResponse
import com.example.momentag.model.StoryResponse
import com.example.momentag.model.Tag
import com.example.momentag.model.TagCreateRequest
import com.example.momentag.model.TagCreateResponse
import com.example.momentag.model.TagIdRequest
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
interface ApiService {
    @GET("api/tags/")
    suspend fun getAllTags(): Response<List<Tag>>

    @GET("api/photos/")
    suspend fun getAllPhotos(): Response<List<PhotoResponse>>

    @POST("api/tags/")
    suspend fun postTags(
        @Body request: TagCreateRequest,
    ): Response<TagCreateResponse>

    @GET("api/photos/albums/{tagId}/")
    suspend fun getPhotosByTag(
        @Path("tagId") tagId: String,
    ): Response<List<PhotoResponse>>

    @DELETE("api/photos/{photo_id}/tags/{tag_id}/")
    suspend fun removeTagFromPhoto(
        @Path("photo_id") photoId: String,
        @Path("tag_id") tagId: String,
    ): Response<Unit>

    @DELETE("api/tags/{tag_id}/")
    suspend fun removeTag(
        @Path("tag_id") tagId: String,
    ): Response<Unit>

    @POST("api/photos/{photo_id}/tags/")
    suspend fun postTagsToPhoto(
        @Path("photo_id") photoId: String,
        @Body tagIdList: List<TagIdRequest>,
    ): Response<Unit>

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

    @Multipart
    @POST("api/photos/")
    suspend fun uploadPhotos(
        @Part photo: List<MultipartBody.Part>,
        @Part("metadata") metadata: RequestBody,
    ): Response<Unit>

    @GET("/api/photos/{photo_id}/")
    suspend fun getPhotoDetail(
        @Path("photo_id") photoId: String,
    ): Response<PhotoDetailResponse>

    /**
     * Semantic Search API
     * GET 방식으로 텍스트 쿼리로 유사한 이미지 검색
     *
     * @param query 검색 쿼리 텍스트
     * @param offset 페이지네이션을 위한 오프셋 (기본값: 0)
     * @return Response<List<PhotoResponse>
     */
    @GET("api/search/semantic/")
    suspend fun semanticSearch(
        @Query("query") query: String,
        @Query("offset") offset: Int = 0,
    ): Response<List<PhotoResponse>>

    @GET("api/photos/{photo_id}/recommendation/")
    suspend fun recommendTagFromPhoto(
        @Path("photo_id") photoId: String,
    ): Response<List<Tag>>

    @GET("api/tags/{tag_id}/recommendation/")
    suspend fun recommendPhotosFromTag(
        @Path("tag_id") tagId: String,
    ): Response<List<PhotoResponse>>

    @GET("api/stories/")
    suspend fun getStories(
        @Query("size") size: Int,
    ): Response<StoryResponse>

    @POST("api/photos/recommendation/")
    suspend fun recommendPhotosFromPhotos(
        @Body photoIds: PhotoToPhotoRequest,
    ): Response<List<PhotoResponse>>
}

/**
 * RetrofitInstance
 *
 * Retrofit 싱글톤 인스턴스 제공
 * - AuthInterceptor: 모든 요청에 AccessToken 자동 추가
 * - TokenAuthenticator: 401 시 자동 리프레시 → 재시도
 */
object RetrofitInstance {
    private var apiService: ApiService? = null

    private fun createCustomTrustManager(context: Context): X509TrustManager {
        val resourceId = context.resources.getIdentifier(
            "myclass",
            "raw",
            context.packageName
        )
        val certInputStream: InputStream = context.resources.openRawResource(resourceId)

        val certificateFactory = CertificateFactory.getInstance("X.509")
        val certificate = certificateFactory.generateCertificate(certInputStream)
        certInputStream.close()

        val keyStoreType = KeyStore.getDefaultType()
        val keyStore = KeyStore.getInstance(keyStoreType)
        keyStore.load(null, null)
        keyStore.setCertificateEntry("ca", certificate)

        val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
        val tmf = TrustManagerFactory.getInstance(tmfAlgorithm)
        tmf.init(keyStore)

        return tmf.trustManagers[0] as X509TrustManager
    }

    private fun createCustomSslSocketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), null)
        return sslContext.socketFactory
    }

    fun getApiService(context: Context): ApiService {
        if (apiService == null) {
            val sessionStore = SessionManager.getInstance(context.applicationContext)
            val authInterceptor = AuthInterceptor(sessionStore)
            val tokenAuthenticator = TokenAuthenticator(context.applicationContext, sessionStore)

            val customTrustManager = createCustomTrustManager(context.applicationContext)
            val customSslSocketFactory = createCustomSslSocketFactory(customTrustManager)

            val okHttpClient =
                OkHttpClient
                    .Builder()
                    .addInterceptor(authInterceptor)
                    .authenticator(tokenAuthenticator)
                    .sslSocketFactory(customSslSocketFactory, customTrustManager)
                    .hostnameVerifier { _, _ -> true }
//                    .connectTimeout(30, TimeUnit.SECONDS)
//                    .readTimeout(30, TimeUnit.SECONDS)
//                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build()

            val retrofit =
                Retrofit
                    .Builder()
                    .baseUrl(context.getString(R.string.API_BASE_URL))
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

            apiService = retrofit.create(ApiService::class.java)
        }
        return apiService!!
    }
}
