package com.example.momentag.network

import com.example.momentag.model.LoginRequest
import com.example.momentag.model.LoginResponse
import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.PhotoToPhotoRequest
import com.example.momentag.model.RefreshRequest
import com.example.momentag.model.RefreshResponse
import com.example.momentag.model.RegisterRequest
import com.example.momentag.model.RegisterResponse
import com.example.momentag.model.StoryWrapperResponse
import com.example.momentag.model.Tag
import com.example.momentag.model.TagId
import com.example.momentag.model.TagName
import com.example.momentag.model.TagResponse
import com.example.momentag.model.TaskInfo
import com.example.momentag.model.TaskStatus
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
interface ApiService {
    @GET("api/tags/")
    suspend fun getAllTags(): Response<List<TagResponse>>

    @GET("api/photos/")
    suspend fun getAllPhotos(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): Response<List<PhotoResponse>>

    @POST("api/tags/")
    suspend fun postTags(
        @Body request: TagName,
    ): Response<TagId>

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
        @Body tagIdList: List<TagId>,
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
    ): Response<List<TaskInfo>>

    @GET("api/photos/{photo_id}/")
    suspend fun getPhotoDetail(
        @Path("photo_id") photoId: String,
    ): Response<PhotoDetailResponse>

    @GET("api/tasks/")
    suspend fun getTaskStatus(
        @Query("task_ids") taskIds: String,
    ): Response<List<TaskStatus>>

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

    @GET("api/new-stories/")
    suspend fun getStories(
        @Query("size") size: Int? = null,
    ): Response<StoryWrapperResponse>

    @POST("api/photos/recommendation/")
    suspend fun recommendPhotosFromPhotos(
        @Body photoIds: PhotoToPhotoRequest,
    ): Response<List<PhotoResponse>>

    @PUT("api/tags/{tag_id}/")
    suspend fun renameTag(
        @Path("tag_id") tagId: String,
        @Body tagName: TagName,
    ): Response<TagId>
}

/**
 * RetrofitInstance
 *
 * Retrofit 싱글톤 인스턴스 제공
 * - AuthInterceptor: 모든 요청에 AccessToken 자동 추가
 * - TokenAuthenticator: 401 시 자동 리프레시 → 재시도
 */
