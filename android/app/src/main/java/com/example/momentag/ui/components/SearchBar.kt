package com.example.momentag.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.momentag.model.TagItem
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Rect

sealed class SearchContentElement {
    abstract val id: String // each element has unique ID

    data class Text(
        override val id: String,
        val text: String = ""
    ) : SearchContentElement()

    data class Chip(
        override val id: String,
        val tag: TagItem
    ) : SearchContentElement()
}


/**
 * 칩/텍스트 기반 검색바
 *
 * @param contentItems 검색창의 칩/텍스트 구조 리스트
 * @param textStates 모든 텍스트 필드의 UI 상태 맵
 * @param focusRequesters 모든 텍스트 필드의 포커스 리퀘스터 맵
 * @param onContainerClick 검색바의 빈 공간 클릭 시
 * @param onChipClick 칩 클릭 시
 * @param onTextChange 텍스트 필드 값 변경 시
 * @param onFocus 텍스트 필드 포커스 시
 * @param onSearch 검색 버튼(돋보기) 클릭 시
 * @param placeholder 플레이스홀더 텍스트
 * @param isFocused [신규] 검색바가 현재 포커스 상태인지 여부 (UI 변경용)
 */
@Composable
fun ChipSearchBar(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    isFocused: Boolean, // [신규] 포커스 상태를 외부에서 받음
    hideCursor: Boolean,
    contentItems: List<SearchContentElement>,
    textStates: Map<String, TextFieldValue>,
    focusRequesters: Map<String, FocusRequester>,
    bringIntoViewRequesters: Map<String, BringIntoViewRequester>,
    onContainerClick: () -> Unit,
    onChipClick: (Int) -> Unit,
    onTextChange: (id: String, newValue: TextFieldValue) -> Unit,
    onFocus: (id: String?) -> Unit,
    onSearch: () -> Unit,
    placeholder: String = "검색 또는 #태그 입력"
) {
    // [신규] 기존 SearchBar의 색상 정의를 그대로 가져옴
    val colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
    )

    // [신규] 포커스 상태에 따라 컨테이너 색상 결정
    val containerColor = if (isFocused) {
        colors.focusedContainerColor
    } else {
        colors.unfocusedContainerColor
    }

    // [수정] Row가 기존 SearchBar(TextField)의 모양을 흉내 냄
    Row(
        modifier = modifier // HomeScreen에서 (Modifier.weight(1f))가 적용될 것임
            .heightIn(min = 48.dp) // TextField의 최소 높이와 맞춤
            .background(
                color = containerColor,
                shape = RoundedCornerShape(24.dp) // [수정] 기존 SearchBar의 둥근 모서리
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onContainerClick() } // 컨테이너(배경) 클릭을 상위로 전달
            .padding(horizontal = 16.dp), // [수정] TextField의 아이콘 패딩과 맞춤
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [신규] 기존 SearchBar의 leadingIcon을 추가
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // [수정] InternalChipSearchInput가 남은 공간을 채움
        InternalChipSearchInput(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp), // 아이콘과 텍스트 사이 간격
            listState = listState,
            hideCursor = hideCursor,
            contentItems = contentItems,
            textStates = textStates,
            focusRequesters = focusRequesters,
            bringIntoViewRequesters = bringIntoViewRequesters,
            onContainerClick = onContainerClick,
            onChipClick = onChipClick,
            onTextChange = onTextChange,
            onFocus = onFocus,
            onSearch = onSearch,
            placeholder = placeholder
        )
    }
}

/**
 * [신규] ChipSearchBar의 내부 구현입니다.
 * HomeScreen.kt의 ChipBasedSearchInput 로직을 그대로 가져왔습니다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InternalChipSearchInput(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    hideCursor: Boolean,
    contentItems: List<SearchContentElement>,
    textStates: Map<String, TextFieldValue>,
    focusRequesters: Map<String, FocusRequester>,
    bringIntoViewRequesters: Map<String, BringIntoViewRequester>,
    onContainerClick: () -> Unit,
    onChipClick: (Int) -> Unit,
    onTextChange: (id: String, newValue: TextFieldValue) -> Unit,
    onFocus: (id: String?) -> Unit,
    onSearch: () -> Unit,
    placeholder: String
) {
    // [수정] Box 래퍼를 제거하고 LazyRow만 남깁니다.
    // 배경과 클릭 이벤트는 상위 Row(ChipSearchBar)가 처리합니다.
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onContainerClick() }
    ) {
        itemsIndexed(
            items = contentItems,
            key = { _, item -> item.id }
        ) { index, item ->
            when (item) {
                is SearchContentElement.Chip -> {
                    SearchChipView(
                        tag = item.tag,
                        onClick = { onChipClick(index) },
                    )
                }
                is SearchContentElement.Text -> {
                    val focusRequester = focusRequesters[item.id] ?: remember { FocusRequester() }
                    val bringIntoViewRequester = bringIntoViewRequesters[item.id] ?: remember { BringIntoViewRequester() }

                    // [신규] 커서 위치 계산을 위해 TextLayoutResult를 저장
                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    val scope = rememberCoroutineScope() // bringIntoView 호출용

                    // [신규] 오른쪽에 추가할 패딩을 계산하기 위해 LocalDensity 가져오기
                    val density = LocalDensity.current

                    // --- 텍스트 너비 측정 로직 ---
                    val textValue = textStates[item.id] ?: TextFieldValue()
                    val text = textValue.text.removePrefix("\u200B")
                    val textMeasurer = rememberTextMeasurer()
                    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)

                    val measuredWidthInPixels = remember(text, textStyle) {
                        textMeasurer.measure(AnnotatedString(text), style = textStyle).size.width
                    }

                    val textWidthDp = with(LocalDensity.current) { measuredWidthInPixels.toDp() }

                    // [신규] 1. 플레이스홀더인지, (플레이스홀더가 아닌) 그냥 빈 필드인지 확인
                    val isPlaceholder = (textValue.text == "\u200B" || textValue.text.isEmpty()) && contentItems.size == 1
                    val isEmptyText = text.isEmpty() && !isPlaceholder

                    // [수정] 2. 텍스트 좌우 패딩 결정
                    val sidePadding = if (isEmptyText) 0.dp else 6.dp // 비어있으면 0dp
                    val totalHorizontalPadding = sidePadding * 2

                    // [수정] 3. 최소 너비 결정
                    val minFieldWidth = 10.dp // 비어있으면 0dp

                    val finalWidth = (textWidthDp + totalHorizontalPadding).coerceAtLeast(minFieldWidth)
                    // --- 로직 끝 ---

                    // [신규] hideCursor 상태에 따라 커서 브러시 결정
                    val cursorBrush = if (hideCursor) {
                        SolidColor(Color.Transparent)
                    } else {
                        SolidColor(MaterialTheme.colorScheme.primary)
                    }

                    // [신규] LaunchedEffect: 텍스트 레이아웃이나 커서 위치가 변경된 *후*에 실행
                    LaunchedEffect(textLayoutResult, textValue.selection) {
                        // 1. 레이아웃 결과가 있는지 확인
                        textLayoutResult?.let { layoutResult ->
                            // 2. [안전 장치] 커서 위치가 현재 레이아웃의 유효 범위 내에 있는지 확인
                            val textLength = layoutResult.layoutInput.text.length
                            val selectionEnd = textValue.selection.end

                            if (selectionEnd in 0..textLength) {
                                // 1. 커서의 사각형을 계산
                                val cursorRect = layoutResult.getCursorRect(selectionEnd)

                                // 2. [신규] 오른쪽에 추가할 패딩 정의 (예: 16.dp)
                                val endPaddingPx = with(density) { sidePadding.toPx() } // + 8.dp

                                // 3. [신규] 커서 사각형의 오른쪽에 패딩을 더한 '새 가상 사각형' 생성
                                val rectWithPadding = Rect(
                                    left = cursorRect.left,
                                    top = cursorRect.top,
                                    right = cursorRect.right + endPaddingPx, // <-- 핵심: 오른쪽으로 16dp 더 넓게
                                    bottom = cursorRect.bottom
                                )

                                // 4. 커서를 뷰로 스크롤
                                scope.launch {
                                    bringIntoViewRequester.bringIntoView(rectWithPadding)
                                }
                            }
                        }
                    }

                    BasicTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            // 1. HomeScreen으로 변경 사항을 알림 (기존 로직)
                            onTextChange(item.id, newValue)
                        },
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier
                            .then(
                                if (isPlaceholder) {
                                    Modifier.fillMaxWidth() // 플레이스홀더는 남은 공간 채움
                                } else {
                                    Modifier.width(finalWidth) // 텍스트는 계산된 너비
                                }
                            )
                            .focusRequester(focusRequester)
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onFocus(item.id)
                                } else {
                                    onFocus(null) // [수정] 포커스를 잃으면 null 전달
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        maxLines = 1, // 스크롤 방지
//                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        cursorBrush = cursorBrush,
                        textStyle = textStyle,

                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),

                        decorationBox = { innerTextField ->
                            if (isPlaceholder) {
                                Text(
                                    placeholder, // [수정] 파라미터 사용
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                                )
                            }
                            innerTextField()
                        },
                    )
                }
            }
        }
    }
}


/**
 * [신규] 검색창 내부에 표시될 칩 (HomeScreen.kt에서 가져옴)
 */
@Composable
private fun SearchChipView(
    tag: TagItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onClick() }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "#${tag.tagName}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}


// --- [기존 SearchBar.kt 코드] ---
// (이 컴포넌트들은 다른 곳에서 아직 사용할 수 있으므로 남겨둡니다)

/**
 * 검색바 컴포넌트 (내부 상태 관리 버전)
 * HomeScreen, SearchResultScreen 등에서 재사용
 *
 * @param onSearch 검색 실행 콜백 (검색어를 파라미터로 받음)
 * @param modifier Modifier
 * @param placeholder 플레이스홀더 텍스트 (기본: "Search Anything...")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search Photos",
) {
    var searchText by remember { mutableStateOf("") }

    TextField(
        value = searchText,
        onValueChange = { searchText = it },
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.25f),
                focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(searchText) }),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * 검색바 컴포넌트 (외부 상태 관리 버전)
 * 검색어를 외부에서 제어해야 할 때 사용
 *
 * @param value 현재 검색어
 * @param onValueChange 검색어 변경 콜백
 * @param onSearch 검색 실행 콜백
 * @param modifier Modifier
 * @param placeholder 플레이스홀더 텍스트 (기본: "Search Anything...")
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarControlledCustom(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search Anything...",
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors =
            TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
            ),
        textStyle = MaterialTheme.typography.bodyLarge,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            IconButton(onClick = onSearch) {
                Icon(imageVector = Icons.Default.Search, contentDescription = "검색 실행")
            }
        },
    )
}