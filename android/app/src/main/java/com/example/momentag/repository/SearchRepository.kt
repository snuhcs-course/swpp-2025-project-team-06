package com.example.momentag.repository

import com.example.momentag.model.PhotoResponse
import com.example.momentag.network.ApiService
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SearchRepository
 *
 * 역할: 검색 관련 API 호출 담당
 * - Semantic Search API 호출 (GET 방식)
 * - 결과를 Sealed Class로 반환
 * - 인증은 AuthInterceptor가 자동 처리
 */
@Singleton
class SearchRepository
    @Inject
    constructor(
        private val apiService: ApiService,
    ) {
        /**
         * Semantic Search 결과를 나타내는 Sealed Class
         */
        sealed class SearchResult {
            data class Success(
                val photos: List<PhotoResponse>,
            ) : SearchResult()

            data class Empty(
                val query: String,
            ) : SearchResult()

            data class BadRequest(
                val message: String,
            ) : SearchResult()

            data class Unauthorized(
                val message: String,
            ) : SearchResult()

            data class NetworkError(
                val message: String,
            ) : SearchResult()

            data class Error(
                val message: String,
            ) : SearchResult()
        }

        /**
         * Semantic Search 수행 (GET 방식)
         * @param query 검색 쿼리 (텍스트)
         * @param offset 페이지네이션 오프셋 (기본값: 0)
         * @return SearchResult
         */
        suspend fun semanticSearch(
            query: String,
            offset: Int = 0,
        ): SearchResult {
            if (query.isBlank()) {
                return SearchResult.BadRequest("Query cannot be empty")
            }

            if (offset < 0) {
                return SearchResult.BadRequest("Offset must be non-negative")
            }

            return try {
                val response = apiService.semanticSearch(query, offset)

                when {
                    response.isSuccessful && response.body() != null -> {
                        val photos = response.body()!!
                        if (photos.isEmpty()) {
                            SearchResult.Empty(query)
                        } else {
                            SearchResult.Success(photos)
                        }
                    }

                    response.code() == 400 -> {
                        SearchResult.BadRequest("Invalid request: ${response.message()}")
                    }

                    response.code() == 401 -> {
                        SearchResult.Unauthorized("Authentication required")
                    }

                    response.code() == 404 -> {
                        SearchResult.Empty(query)
                    }

                    else -> {
                        SearchResult.Error("Unexpected error: ${response.code()}")
                    }
                }
            } catch (e: IOException) {
                SearchResult.NetworkError("Network error: ${e.message}")
            } catch (e: Exception) {
                SearchResult.Error("Unknown error: ${e.message}")
            }
        }
    }
