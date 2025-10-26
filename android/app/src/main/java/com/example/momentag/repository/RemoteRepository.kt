package com.example.momentag.repository

import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoDetailResponse
import com.example.momentag.model.PhotoUploadData
import com.example.momentag.model.Tag
import com.example.momentag.network.ApiService
import java.io.IOException

/**
 * RemoteRepository (Feature Repository)
 *
 * 역할: 실제 API 호출만 담당
 * - 인증은 AuthInterceptor/Authenticator가 자동 처리
 * - 토큰 관리 로직 없음
 * - 순수하게 비즈니스 도메인 API만 호출
 */
class RemoteRepository(
    private val apiService: ApiService,
) {
    sealed class PhotoDetailResult {
        data class Success(
            val detail: PhotoDetailResponse,
        ) : PhotoDetailResult()

        data class BadRequest(
            val message: String,
        ) : PhotoDetailResult()

        data class Unauthorized(
            val message: String,
        ) : PhotoDetailResult()

        data class NetworkError(
            val message: String,
        ) : PhotoDetailResult()

        data class Error(
            val message: String,
        ) : PhotoDetailResult()
    }

    /**
     * 모든 태그 조회
     * (인증 헤더는 AuthInterceptor가 자동 추가)
     */
    suspend fun getAllTags(): List<Tag> = emptyList() // TODO: receive tag_id, tag, thumbnail_path_id

    /**
     * 특정 태그의 사진 조회
     * @param tagName 태그명
     * @return 사진 리스트
     * (인증 헤더는 AuthInterceptor가 자동 추가)
     */
    suspend fun getPhotosByTag(tagName: String): List<Photo> = emptyList()

    suspend fun getPhotoDetail(photoId: String): PhotoDetailResult =
        try {
            val response = apiService.getPhotoDetail(photoId)

            if (response.isSuccessful) {
                val detail = response.body()!!
                PhotoDetailResult.Success(detail)
            } else {
                when (response.code()) {
                    401 -> PhotoDetailResult.Unauthorized("Authentication failed")
                    400 -> PhotoDetailResult.BadRequest("Bad request")
                    else -> PhotoDetailResult.Error("An unknown error occurred: ${response.message()}")
                }
            }
        } catch (e: IOException) {
            PhotoDetailResult.NetworkError("Network error: ${e.message}")
        } catch (e: Exception) {
            PhotoDetailResult.Error("An unexpected error occurred: ${e.message}")
        }

    suspend fun uploadPhotos(photoUploadData: PhotoUploadData) =
        apiService.uploadPhotos(
            photo = photoUploadData.photo,
            metadata = photoUploadData.metadata,
        )
}
