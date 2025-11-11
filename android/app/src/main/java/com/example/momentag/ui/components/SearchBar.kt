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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.momentag.model.TagItem

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
 */
@Composable
fun ChipSearchBar(
    modifier: Modifier = Modifier,
    // HomeScreen의 상태 값들
    contentItems: List<SearchContentElement>,
    textStates: Map<String, TextFieldValue>,
    focusRequesters: Map<String, FocusRequester>,

    // HomeScreen의 이벤트 핸들러들
    onContainerClick: () -> Unit,
    onChipClick: (Int) -> Unit,
    onTextChange: (id: String, newValue: TextFieldValue) -> Unit,
    onFocus: (id: String) -> Unit,
    onSearch: () -> Unit, // 검색 버튼 클릭

    // SearchBarControlledCustom의 UI 값
    placeholder: String = "검색 또는 #태그 입력"
) {
    // [수정] 여기가 SearchBarControlledCustom의 UI를 모방한 컨테이너입니다.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp) // TextField의 기본 높이와 유사하게
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow, // SearchBarControlledCustom의 색상
                shape = RoundedCornerShape(16.dp) // SearchBarControlledCustom의 모양
            )
            .padding(start = 16.dp, end = 4.dp), // TextField의 내부 아이콘 패딩과 유사하게
        verticalAlignment = Alignment.CenterVertically
    ) {
        // [수정] 이것이 SearchBarControlledCustom의 "leadingIcon" 입니다.
        // (HomeScreen에서는 버튼이 오른쪽에 있었지만, 기존 SearchBar.kt는 왼쪽에 있었습니다)
        // (만약 오른쪽(trailing)을 원하시면 이 Icon을 IconButton 안으로 옮기시면 됩니다)
//        Icon(
//            imageVector = Icons.Default.Search,
//            contentDescription = "Search",
//            tint = MaterialTheme.colorScheme.onSurfaceVariant,
//            modifier = Modifier.padding(end = 8.dp)
//        )

        // [수정] Box가 아닌, InternalChipSearchInput를 직접 호출합니다.
        // (InternalChipSearchInput이 LazyRow를 포함)
        InternalChipSearchInput(
            modifier = Modifier.weight(1f),
            contentItems = contentItems,
            textStates = textStates,
            focusRequesters = focusRequesters,
            onContainerClick = onContainerClick,
            onChipClick = onChipClick,
            onTextChange = onTextChange,
            onFocus = onFocus,
            placeholder = placeholder // 플레이스홀더 전달
        )

        // [수정] 이것이 SearchBarControlledCustom의 "trailingIcon" 입니다.
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "검색 실행",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    contentItems: List<SearchContentElement>,
    textStates: Map<String, TextFieldValue>,
    focusRequesters: Map<String, FocusRequester>,
    onContainerClick: () -> Unit,
    onChipClick: (Int) -> Unit,
    onTextChange: (id: String, newValue: TextFieldValue) -> Unit,
    onFocus: (id: String) -> Unit,
    placeholder: String
) {
    // [수정] Box 래퍼를 제거하고 LazyRow만 남깁니다.
    // 배경과 클릭 이벤트는 상위 Row(ChipSearchBar)가 처리합니다.
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                    // [기존] HomeScreen.kt의 BasicTextField 로직 전체
                    val focusRequester = focusRequesters[item.id] ?: remember { FocusRequester() }

                    // --- 텍스트 너비 측정 로직 ---
                    val textValue = textStates[item.id] ?: TextFieldValue()
                    val text = textValue.text.removePrefix("\u200B")
                    val textMeasurer = rememberTextMeasurer()
                    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)

                    val measuredWidthInPixels = remember(text, textStyle) {
                        textMeasurer.measure(AnnotatedString(text), style = textStyle).size.width
                    }

                    val textWidthDp = with(LocalDensity.current) { measuredWidthInPixels.toDp() }
                    val horizontalPadding = 8.dp
                    val minFieldWidth = 10.dp
                    val finalWidth = (textWidthDp + horizontalPadding).coerceAtLeast(minFieldWidth)
                    // --- 로직 끝 ---

                    val isPlaceholder = (textValue.text == "\u200B" || textValue.text.isEmpty()) && contentItems.size == 1

                    BasicTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            onTextChange(item.id, newValue)
                        },
                        modifier = Modifier
                            .then(
                                if (isPlaceholder) {
                                    Modifier.fillMaxWidth() // 플레이스홀더는 남은 공간 채움
                                } else {
                                    Modifier.width(finalWidth) // 텍스트는 계산된 너비
                                }
                            )
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onFocus(item.id)
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        maxLines = 1, // 스크롤 방지
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = textStyle,
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
                .clickable { onClick() }
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