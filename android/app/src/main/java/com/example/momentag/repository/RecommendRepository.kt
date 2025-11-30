package com.example.momentag.repository

import com.example.momentag.model.PhotoResponse
import com.example.momentag.model.PhotoToPhotoRequest
import com.example.momentag.model.StoryStateResponse
import com.example.momentag.model.Tag
import com.example.momentag.network.ApiService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendRepository
    @Inject
    constructor(
        private val apiService: ApiService,
    ) {
        sealed class RecommendResult<T> {
            data class Success<T>(
                val data: T,
            ) : RecommendResult<T>()

            data class BadRequest<T>(
                val message: String,
            ) : RecommendResult<T>()

            data class Unauthorized<T>(
                val message: String,
            ) : RecommendResult<T>()

            data class NetworkError<T>(
                val message: String,
            ) : RecommendResult<T>()

            data class Error<T>(
                val message: String,
            ) : RecommendResult<T>()
        }

        sealed class StoryResult<T> {
            data class Success<T>(
                val data: T,
            ) : StoryResult<T>()

            data class NotReady<T>(
                val message: String,
            ) : StoryResult<T>()

            data class BadRequest<T>(
                val message: String,
            ) : StoryResult<T>()

            data class Unauthorized<T>(
                val message: String,
            ) : StoryResult<T>()

            data class NetworkError<T>(
                val message: String,
            ) : StoryResult<T>()

            data class Error<T>(
                val message: String,
            ) : StoryResult<T>()
        }

        suspend fun recommendTagFromPhoto(photoId: String): RecommendResult<List<Tag>> =
            try {
                val response = apiService.recommendTagFromPhoto(photoId)

                if (response.isSuccessful) {
                    val tags = response.body()!!
                    RecommendResult.Success(tags)
                } else {
                    when (response.code()) {
                        401 -> RecommendResult.Unauthorized("Authentication failed")
                        400 -> RecommendResult.BadRequest("Bad request")
                        else -> RecommendResult.Error("An unknown error occurred: ${response.message()}")
                    }
                }
            } catch (e: IOException) {
                RecommendResult.NetworkError("Network error: ${e.message}")
            } catch (e: Exception) {
                RecommendResult.Error("An unexpected error occurred: ${e.message}")
            }

        suspend fun recommendPhotosFromTag(tagId: String): RecommendResult<List<PhotoResponse>> =
            try {
                val response = apiService.recommendPhotosFromTag(tagId)

                if (response.isSuccessful) {
                    val photos = response.body()!!
                    RecommendResult.Success(photos)
                } else {
                    when (response.code()) {
                        401 -> RecommendResult.Unauthorized("Authentication failed")
                        400 -> RecommendResult.BadRequest("Bad request")
                        else -> RecommendResult.Error("An unknown error occurred: ${response.message()}")
                    }
                }
            } catch (e: IOException) {
                RecommendResult.NetworkError("Network error: ${e.message}")
            } catch (e: Exception) {
                RecommendResult.Error("An unexpected error occurred: ${e.message}")
            }

        suspend fun recommendPhotosFromPhotos(photoIds: List<String>): RecommendResult<List<PhotoResponse>> =
            try {
                val response = apiService.recommendPhotosFromPhotos(PhotoToPhotoRequest(photoIds))
                if (response.isSuccessful) {
                    val photos = response.body()!!
                    RecommendResult.Success(photos)
                } else {
                    when (response.code()) {
                        401 -> RecommendResult.Unauthorized("Authentication failed")
                        400 -> RecommendResult.BadRequest("Bad request")
                        else -> RecommendResult.Error("An unknown error occurred: ${response.message()}")
                    }
                }
            } catch (e: IOException) {
                RecommendResult.NetworkError("Network error: ${e.message}")
            } catch (e: Exception) {
                RecommendResult.Error("An unexpected error occurred: ${e.message}")
            }

        suspend fun getStories(size: Int? = null): StoryResult<StoryStateResponse> =
            try {
                val response = apiService.getStories(size)

                if (response.isSuccessful) {
                    val storyWrapper = response.body()!!
                    StoryResult.Success(storyWrapper)
                } else {
                    when (response.code()) {
                        401 -> StoryResult.Unauthorized("Authentication failed")
                        400 -> StoryResult.BadRequest("Bad request")
                        else -> StoryResult.Error("An unknown error occurred: ${response.message()}")
                    }
                }
            } catch (e: IOException) {
                StoryResult.NetworkError("Network error: ${e.message}")
            } catch (e: Exception) {
                StoryResult.Error("An unexpected error occurred: ${e.message}")
            }
    }
