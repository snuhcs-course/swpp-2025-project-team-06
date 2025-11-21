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
            val existingSession = loadSession(ImageContext.ContextType.SEARCH_RESULT, query, photos.size)
            currentSession = existingSession?.copy(photos = photos) ?: BrowserSession(photos, ImageContext.ContextType.SEARCH_RESULT, query)
        }

        /**
         * 태그 앨범을 세션으로 저장
         */
        fun setTagAlbum(
            photos: List<Photo>,
            tagName: String,
        ) {
            val existingSession = loadSession(ImageContext.ContextType.TAG_ALBUM, tagName, photos.size)
            currentSession = existingSession?.copy(photos = photos) ?: BrowserSession(photos, ImageContext.ContextType.TAG_ALBUM, tagName)
        }

        /**
         * 로컬 앨범을 세션으로 저장
         */
        fun setLocalAlbum(
            photos: List<Photo>,
            albumName: String,
        ) {
            val existingSession = loadSession(ImageContext.ContextType.ALBUM, albumName, photos.size)
            currentSession = existingSession?.copy(photos = photos) ?: BrowserSession(photos, ImageContext.ContextType.ALBUM, albumName)
        }

        /**
         * 전체 갤러리를 세션으로 저장
         */
        fun setGallery(photos: List<Photo>) {
            val existingSession = loadSession(ImageContext.ContextType.GALLERY, "Gallery", photos.size)
            currentSession = existingSession?.copy(photos = photos) ?: BrowserSession(photos, ImageContext.ContextType.GALLERY, "Gallery")
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
         * 현재 보고 있는 사진의 인덱스를 업데이트 (ImageDetailScreen에서 페이지 변경 시 호출)
         */
        fun updateCurrentIndex(newIndex: Int) {
            currentSession?.let { session ->
                if (newIndex in session.photos.indices) {
                    currentSession = session.copy(currentIndex = newIndex)
                }
            }
        }

        /**
         * 마지막으로 본 사진의 인덱스를 반환
         */
        fun getCurrentIndex(): Int? = currentSession?.currentIndex

        private fun loadSession(
            contextType: ImageContext.ContextType,
            metadata: String,
            numPhotos: Int,
        ): BrowserSession? =
            currentSession?.takeIf {
                it.contextType == contextType && it.metadata == metadata && it.currentIndex < numPhotos
            }

        /**
         * 브라우징 세션 데이터 클래스
         */
        private data class BrowserSession(
            val photos: List<Photo>,
            val contextType: ImageContext.ContextType,
            val metadata: String,
            val currentIndex: Int = 0,
        )
    }
