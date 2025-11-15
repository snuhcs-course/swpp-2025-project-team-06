package com.example.momentag.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.Photo
import com.example.momentag.model.SemanticSearchState
import com.example.momentag.model.TagItem
import com.example.momentag.model.TagLoadingState
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.repository.SearchRepository
import com.example.momentag.repository.TokenRepository
import com.example.momentag.ui.components.SearchContentElement
import com.example.momentag.viewmodel.HomeViewModel.HomeLoadingState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SearchViewModel
 *
 * 역할: Semantic Search UI 상태 관리
 * - SearchRepository에 비즈니스 로직 위임
 * - ImageBrowserRepository로 검색 결과 세션 관리
 * - UI 상태만 관리
 */
class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val photoSelectionRepository: PhotoSelectionRepository,
    private val localRepository: LocalRepository,
    private val imageBrowserRepository: ImageBrowserRepository,
    private val tokenRepository: TokenRepository,
    private val remoteRepository: RemoteRepository,
) : ViewModel() {
    private val _tagLoadingState = MutableStateFlow<TagLoadingState>(TagLoadingState.Idle)
    val tagLoadingState = _tagLoadingState.asStateFlow()

    private val _searchState = MutableStateFlow<SemanticSearchState>(SemanticSearchState.Idle)
    val searchState = _searchState.asStateFlow()

    val selectedPhotos: StateFlow<List<Photo>> = photoSelectionRepository.selectedPhotos

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    // TokenRepository의 로그인 상태 Flow 구독
    private val isLoggedInFlow = tokenRepository.isLoggedIn

    init {
        loadSearchHistory()

        viewModelScope.launch {
            isLoggedInFlow.collect { accessToken ->
                if (accessToken == null) {
                    clearHistoryAndReload()
                }
            }
        }
    }

    fun loadServerTags() {
        viewModelScope.launch {
            _tagLoadingState.value = TagLoadingState.Loading

            when (val result = remoteRepository.getAllTags()) {
                is RemoteRepository.Result.Success -> {
                    val tags = result.data

                    val tagItems =
                        tags.map { tag ->
                            TagItem(
                                tagName = tag.tagName,
                                coverImageId = tag.thumbnailPhotoPathId,
                                tagId = tag.tagId,
                                createdAt = tag.createdAt,
                                updatedAt = tag.updatedAt,
                                photoCount = tag.photoCount,
                            )
                        }
                    _tagLoadingState.value = TagLoadingState.Success(tags = tagItems)
                }

                is RemoteRepository.Result.Error -> {
                    _tagLoadingState.value = TagLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.Unauthorized -> {
                    _tagLoadingState.value = TagLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.BadRequest -> {
                    _tagLoadingState.value = TagLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.NetworkError -> {
                    _tagLoadingState.value = TagLoadingState.Error(result.message)
                }
                is RemoteRepository.Result.Exception -> {
                    _tagLoadingState.value = TagLoadingState.Error(result.e.message ?: "Unknown error")
                }
            }
        }
    }

    fun resetTagLoadingState() {
        _tagLoadingState.value = TagLoadingState.Idle
    }

    fun setSelectionMode(isOn: Boolean) {
        _isSelectionMode.value = isOn
    }

    fun loadSearchHistory() {
        viewModelScope.launch {
            _searchHistory.value = localRepository.getSearchHistory()
        }
    }

    private fun clearHistoryAndReload() {
        viewModelScope.launch {
            localRepository.clearSearchHistory()
            _searchHistory.value = emptyList()
        }
    }

    fun onSearchTextChanged(query: String) {
        _searchText.value = query
    }

    /**
     * Semantic Search 수행 (GET 방식)
     * @param query 검색 쿼리
     * @param offset 오프셋
     */
    fun search(
        query: String,
        offset: Int = 0,
    ) {
        if (query.isBlank()) {
            _searchState.value = SemanticSearchState.Error("Query cannot be empty")
            return
        }

        viewModelScope.launch {
            localRepository.addSearchHistory(query)
            loadSearchHistory()

            _searchState.value = SemanticSearchState.Loading
            _searchText.value = query

            when (val result = searchRepository.semanticSearch(query, offset)) {
                is SearchRepository.SearchResult.Success -> {
                    val photos = localRepository.toPhotos(result.photos)
                    imageBrowserRepository.setSearchResults(photos, query)
                    _searchState.value =
                        SemanticSearchState.Success(
                            photos = photos,
                            query = query,
                        )
                }

                is SearchRepository.SearchResult.Empty -> {
                    _searchState.value = SemanticSearchState.Empty(query)
                    imageBrowserRepository.clear()
                }

                is SearchRepository.SearchResult.BadRequest -> {
                    _searchState.value = SemanticSearchState.Error(result.message)
                }

                is SearchRepository.SearchResult.Unauthorized -> {
                    _searchState.value = SemanticSearchState.Error("Please login again")
                }

                is SearchRepository.SearchResult.NetworkError -> {
                    _searchState.value = SemanticSearchState.NetworkError(result.message)
                }

                is SearchRepository.SearchResult.Error -> {
                    _searchState.value = SemanticSearchState.Error(result.message)
                }
            }
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            localRepository.removeSearchHistory(query)
            loadSearchHistory()
        }
    }

    fun togglePhoto(photo: Photo) {
        photoSelectionRepository.togglePhoto(photo)
    }

    fun resetSelection() {
        photoSelectionRepository.clear()
    }

    /**
     * Get photos ready for sharing
     * Returns list of content URIs to share via Android ShareSheet
     */
    fun getPhotosToShare() = selectedPhotos.value

    /**
     * 검색 상태 초기화
     */
    fun resetSearchState() {
        _searchState.value = SemanticSearchState.Idle
        imageBrowserRepository.clear()
    }

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

    val textStates = mutableStateMapOf<String, TextFieldValue>()
    val contentItems = mutableStateListOf<SearchContentElement>()
    var focusedElementId by mutableStateOf<String?>(null)
    var ignoreFocusLoss by mutableStateOf(false)

    // "이 ID로 포커스를 요청하라"는 신호
    private val _requestFocus = MutableSharedFlow<String>()
    val requestFocus = _requestFocus.asSharedFlow()

    // "이 ID를 화면에 보이게 하라"는 신호
    private val _bringIntoView = MutableSharedFlow<String>()
    val bringIntoView = _bringIntoView.asSharedFlow()

    init {
        if (contentItems.isEmpty()) {
            val initialId = UUID.randomUUID().toString()
            contentItems.add(SearchContentElement.Text(id = initialId, text = ""))
            textStates[initialId] = TextFieldValue("\u200B", TextRange(1))
        }
    }

    fun onFocus(id: String?) {
        if (id == null && ignoreFocusLoss) {
            return
        }
        focusedElementId = id
    }

    fun onContainerClick() {
        val lastTextElement =
            contentItems.lastOrNull { it is SearchContentElement.Text }
        if (lastTextElement != null) {
            val currentTfv = textStates[lastTextElement.id]
            if (currentTfv != null) {
                val end = currentTfv.text.length
                textStates[lastTextElement.id] =
                    currentTfv.copy(selection = TextRange(end))
            }
            focusedElementId = lastTextElement.id

            viewModelScope.launch {
                _requestFocus.emit(lastTextElement.id)
            }
        }
    }

    private fun findNextTextElementId(startIndex: Int): String? {
        if (startIndex >= contentItems.size - 1) return null
        for (i in (startIndex + 1) until contentItems.size) {
            val item = contentItems.getOrNull(i)
            if (item is SearchContentElement.Text) {
                return item.id
            }
        }
        return null
    }

    fun onChipClick(index: Int) {
        val nextTextId = findNextTextElementId(index)
        if (nextTextId != null) {
            val currentTfv = textStates[nextTextId]
            if (currentTfv != null) {
                textStates[nextTextId] =
                    currentTfv.copy(selection = TextRange(1))
            }
            focusedElementId = nextTextId

            viewModelScope.launch {
                _requestFocus.emit(nextTextId)
            }
        }
    }

    fun onTextChange(id: String, newValue: TextFieldValue) {
        // 1. scope.launch { bringIntoView... } -> '신호'로 변경
        //    (Composable이 이 신호를 받고 bringIntoView를 실행할 거예요)
        viewModelScope.launch {
            _bringIntoView.emit(id)
        }

        val oldValue = textStates[id] ?: TextFieldValue()
        val oldText = oldValue.text
        val newText = newValue.text

        // 한글 등 IME 조합 중인지 확인
        val isComposing = newValue.composition != null

        if (isComposing) {
            textStates[id] = newValue
            return
        }

        // ZWSP가 삭제되었는지(커서가 1이었는지) 감지
        val didBackspaceAtStart =
            oldText.startsWith("\u200B") &&
                    !newText.startsWith("\u200B") &&
                    oldValue.selection.start == 1

        if (didBackspaceAtStart) {
            val currentIndex = contentItems.indexOfFirst { it.id == id }
            val currentItem =
                contentItems[currentIndex] as SearchContentElement.Text

            if (currentIndex <= 0) {
                textStates[id] = TextFieldValue("\u200B", TextRange(1))
                return
            }

            val prevItem = contentItems[currentIndex - 1]

            // 1a. 바로 앞이 칩인 경우 (e.g., [TextA] [ChipB] [TextC(현재)])
            if (prevItem is SearchContentElement.Chip) {
                val prevPrevIndex = currentIndex - 2

                // 1a-1. [TextA] [ChipB] [TextC] -> [TextA + TextC]
                if (prevPrevIndex >= 0 && contentItems[prevPrevIndex] is SearchContentElement.Text) {
                    val textA =
                        contentItems[prevPrevIndex] as SearchContentElement.Text
                    val textC = currentItem
                    val mergedText = textA.text + textC.text

                    contentItems.removeAt(currentIndex)
                    contentItems.removeAt(currentIndex - 1)
                    textStates.remove(id)

                    contentItems[prevPrevIndex] = textA.copy(text = mergedText)
                    val newTfv =
                        TextFieldValue(
                            "\u200B" + mergedText,
                            TextRange(textA.text.length + 1),
                        )
                    textStates[textA.id] = newTfv

                    // 2. requestFocusById(textA.id) -> '신호'로 변경
                    viewModelScope.launch {
                        _requestFocus.emit(textA.id)
                    }
                } else { // 1a-2. [ChipA] [ChipB] [TextC] 또는 [Start] [ChipB] [TextC] -> [ChipA] [TextC]
                    contentItems.removeAt(currentIndex - 1)
                    // 3. requestFocusById(id) -> '신호'로 변경
                    viewModelScope.launch {
                        _requestFocus.emit(id)
                    }
                }
            } else if (prevItem is SearchContentElement.Text) { // 1b. 바로 앞이 텍스트인 경우 (e.g., [TextA] [TextC(현재)])
                val textA = prevItem
                val textC = currentItem
                val mergedText = textA.text + textC.text

                contentItems.removeAt(currentIndex)
                textStates.remove(id)

                contentItems[currentIndex - 1] = textA.copy(text = mergedText)
                val newTfv =
                    TextFieldValue(
                        "\u200B" + mergedText,
                        TextRange(textA.text.length + 1),
                    )
                textStates[textA.id] = newTfv

                // 4. requestFocusById(textA.id) -> '신호'로 변경
                viewModelScope.launch {
                    _requestFocus.emit(textA.id)
                }
            }
            return
        }

        // ZWSP 및 커서 위치 강제 로직
        val (text, selection) =
            if (newText.startsWith("\u200B")) {
                Pair(newText, newValue.selection)
            } else {
                Pair(
                    "\u200B$newText",
                    TextRange(
                        newValue.selection.start + 1,
                        newValue.selection.end + 1,
                    ),
                )
            }
        val finalSelection =
            if (selection.start == 0 && selection.end == 0) {
                TextRange(1)
            } else {
                selection
            }
        val finalValue = TextFieldValue(text, finalSelection)

        textStates[id] = finalValue
        val currentItemIndex = contentItems.indexOfFirst { it.id == id }
        if (currentItemIndex != -1) {
            contentItems[currentItemIndex] =
                (contentItems[currentItemIndex] as SearchContentElement.Text).copy(
                    text = text.removePrefix("\u200B"),
                )
        }
    }

    fun addTagFromSuggestion(tag: TagItem) {
        // 1. ignoreFocusLoss를 ViewModel에서 관리
        ignoreFocusLoss = true

        if (focusedElementId == null) return

        val currentId = focusedElementId!!
        val currentIndex = contentItems.indexOfFirst { it.id == currentId }
        val currentInput = textStates[currentId] ?: return

        val text = currentInput.text
        val cursor = currentInput.selection.start
        val textUpToCursor = text.substring(0, cursor)
        val lastHashIndex = textUpToCursor.lastIndexOf('#')

        if (lastHashIndex != -1) {
            // 텍스트 분리
            val precedingText = text.substring(0, lastHashIndex).removePrefix("\u200B")
            val succeedingText = text.substring(cursor)

            // 새 칩과 새 텍스트 필드 생성
            val newChipId = UUID.randomUUID().toString()
            val newChip = SearchContentElement.Chip(newChipId, tag)

            val newTextId = UUID.randomUUID().toString()
            val newText = SearchContentElement.Text(newTextId, succeedingText)

            // 현재 텍스트 필드 업데이트
            contentItems[currentIndex] =
                (contentItems[currentIndex] as SearchContentElement.Text).copy(text = precedingText)
            textStates[currentId] =
                TextFieldValue("\u200B" + precedingText, TextRange(precedingText.length + 1))

            // 새 칩과 새 텍스트 필드 삽입
            contentItems.add(currentIndex + 1, newChip)
            contentItems.add(currentIndex + 2, newText)

            // 새 텍스트 필드 상태 설정 (커서는 1)
            textStates[newTextId] = TextFieldValue("\u200B" + succeedingText, TextRange(1))

            // 2. UI 객체 생성 코드는 모두 삭제 (LaunchedEffect(contentItems.size)가 처리)
            // focusRequesters[newTextId] = FocusRequester() <-- 삭제
            // bringIntoViewRequesters[newTextId] = BringIntoViewRequester() <-- 삭제

            // 3. 포커스 상태 변경
            focusedElementId = newTextId

            // 포커스를 새로 생긴 텍스트필드로 옮기라는 '신호' 전송
            viewModelScope.launch {
                _requestFocus.emit(newTextId)
            }
        }
    }
}
