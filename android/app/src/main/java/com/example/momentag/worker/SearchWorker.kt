package com.example.momentag.worker

import com.example.momentag.model.TagItem
import com.example.momentag.ui.components.SearchContentElement
import java.util.UUID

object SearchWorker {
    /**
     * "{tag}" 형식의 쿼리 문자열을 파싱하여 List<SearchContentElement>로 변환합니다.
     * @param query 검색할 쿼리 문자열 (예: "text {tag1} other text")
     * @param allTags TagItem을 찾기 위한 전체 태그 리스트
     * @return 파싱된 SearchContentElement 리스트
     */
    fun parseQueryToElements(
        query: String,
        allTags: List<TagItem>,
    ): List<SearchContentElement> {
        val elements = mutableListOf<SearchContentElement>()
        // "{tagname}" 형식을 찾는 정규식
        val tagRegex = "\\{([^}]+)\\}".toRegex()
        var lastIndex = 0

        tagRegex.findAll(query).forEach { matchResult ->
            // 태그 이전의 텍스트를 추가
            val textBefore = query.substring(lastIndex, matchResult.range.first)
            if (textBefore.isNotEmpty()) {
                elements.add(SearchContentElement.Text(id = UUID.randomUUID().toString(), text = textBefore))
            }

            // 태그(칩) 추가
            val tagName = matchResult.groupValues[1]

            // allTags 리스트에서 실제 TagItem을 검색 (대소문자 무시)
            val tagItem = allTags.find { it.tagName.equals(tagName, ignoreCase = true) }
            if (tagItem == null) {
                return@forEach
            }

            elements.add(SearchContentElement.Chip(id = UUID.randomUUID().toString(), tag = tagItem))

            lastIndex = matchResult.range.last + 1
        }

        // 마지막 태그 이후의 나머지 텍스트 추가
        val remainingText = query.substring(lastIndex)
        if (remainingText.isNotEmpty()) {
            elements.add(SearchContentElement.Text(id = UUID.randomUUID().toString(), text = remainingText))
        }

        // 파싱 결과가 비어있거나, 칩으로 끝나는 경우, 커서를 위한 빈 텍스트 필드 추가
        if (elements.isEmpty() || elements.last() is SearchContentElement.Chip) {
            elements.add(SearchContentElement.Text(id = UUID.randomUUID().toString(), text = ""))
        }

        // 쿼리가 태그 없이 텍스트만 있었던 경우
        if (elements.isEmpty() && query.isNotEmpty()) {
            elements.add(SearchContentElement.Text(id = UUID.randomUUID().toString(), text = query))
        }

        return elements
    }
}
