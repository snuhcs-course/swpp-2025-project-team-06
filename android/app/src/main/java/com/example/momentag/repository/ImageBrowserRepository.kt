package com.example.momentag.repository

import com.example.momentag.model.ImageContext
import com.example.momentag.model.Photo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ImageBrowserRepository
 *
 * 이미지 브라우징 세션을 관리하는 Repository
 * - 검색 결과, 태그 앨범, 로컬 갤러리 등의 이미지 목록을 세션으로 저장
 * - ImageDetailScreen에서 이전/다음 이미지 탐색을 위한 컨텍스트 제공
 * - ViewModel 간 데이터 중복을 방지하고 단일 진실 공급원(Single Source of Truth) 역할
 *
 * Managed by Hilt as singleton
 */
@Singleton
class ImageBrowserRepository
    @Inject
    constructor() {
        private var currentSession: BrowserSession? = null

        /**
         * 검색 결과를 세션으로 저장
         */
        fun setSearchResults(
            photos: List<Photo>,
            query: String,
        ) {
            currentSession =
                BrowserSession(
                    photos = photos,
                    contextType = ImageContext.ContextType.SEARCH_RESULT,
                    metadata = query,
                )
        }

        /**
         * 태그 앨범을 세션으로 저장
         */
        fun setTagAlbum(
            photos: List<Photo>,
            tagName: String,
        ) {
            currentSession =
                BrowserSession(
                    photos = photos,
                    contextType = ImageContext.ContextType.TAG_ALBUM,
                    metadata = tagName,
                )
        }

        /**
         * 로컬 앨범을 세션으로 저장
         */
        fun setLocalAlbum(
            photos: List<Photo>,
            albumName: String,
        ) {
            currentSession =
                BrowserSession(
                    photos = photos,
                    contextType = ImageContext.ContextType.ALBUM,
                    metadata = albumName,
                )
        }

        /**
         * 전체 갤러리를 세션으로 저장
         */
        fun setGallery(photos: List<Photo>) {
            currentSession =
                BrowserSession(
                    photos = photos,
                    contextType = ImageContext.ContextType.GALLERY,
                    metadata = "Gallery",
                )
        }

        /**
         * 스토리를 세션으로 저장
         */
        fun setStory(photo: Photo) {
            currentSession =
                BrowserSession(
                    photos = listOf(photo),
                    contextType = ImageContext.ContextType.STORY,
                    metadata = "Story",
                )
        }

        /**
         * 특정 사진의 ImageContext를 반환 (photoId 기반)
         * @param photoId 현재 보고 있는 사진의 ID
         * @return ImageContext 또는 null (세션이 없거나 사진을 찾을 수 없는 경우)
         */
        fun getPhotoContext(photoId: String): ImageContext? {
            val session = currentSession ?: return null
            val index = session.photos.indexOfFirst { it.photoId == photoId }
            if (index == -1) return null

            return ImageContext(
                images = session.photos,
                currentIndex = index,
                contextType = session.contextType,
            )
        }

        /**
         * 특정 사진의 ImageContext를 반환 (URI 기반)
         * @param contentUri 현재 보고 있는 사진의 URI
         * @return ImageContext 또는 null (세션이 없거나 사진을 찾을 수 없는 경우)
         */
        fun getPhotoContextByUri(contentUri: android.net.Uri): ImageContext? {
            val session = currentSession
            if (session == null) {
                return null
            }

            val index = session.photos.indexOfFirst { it.contentUri == contentUri }

            if (index == -1) {
                return null
            }

            val context =
                ImageContext(
                    images = session.photos,
                    currentIndex = index,
                    contextType = session.contextType,
                )
            return context
        }

        /**
         * 현재 세션 초기화
         */
        fun clear() {
            currentSession = null
        }

        /**
         * 현재 저장된 세션이 있는지 확인
         */
        fun hasSession(): Boolean = currentSession != null

        /**
         * 브라우징 세션 데이터 클래스
         */
        private data class BrowserSession(
            val photos: List<Photo>,
            val contextType: ImageContext.ContextType,
            val metadata: String,
        )
    }
