package com.example.momentag.model

/**
 * ImageContext - 이미지 상세보기에서 사용할 컨텍스트 정보
 *
 * @param images 전체 이미지 목록 (Photo 객체 포함 - photoId와 contentUri)
 * @param currentIndex 현재 보고 있는 이미지의 인덱스
 * @param contextType 컨텍스트 타입 (앨범, 태그앨범, 검색결과 등)
 */
data class ImageContext(
    val images: List<Photo>,
    val currentIndex: Int,
    val contextType: ContextType,
) {
    enum class ContextType {
        ALBUM, // 로컬 앨범
        TAG_ALBUM, // 태그 앨범
        SEARCH_RESULT, // 검색 결과
        GALLERY, // 전체 갤러리
    }
}
