package com.example.momentag.ui.components

import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.momentag.model.TagItem
import kotlinx.coroutines.launch

sealed class SearchContentElement {
    abstract val id: String // each element has unique ID

    data class Text(
        override val id: String,
        val text: String = "",
    ) : SearchContentElement()

    data class Chip(
        override val id: String,
        val tag: TagItem,
    ) : SearchContentElement()
}

/**
 * 칩/텍스트 기반 검색바
 */
@Composable
fun ChipSearchBar(
    modifier: Modifier = Modifier,
    listState: LazyListState,
    isFocused: Boolean,
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
    placeholder: String = "검색 또는 #태그 입력",
) {
    val colors =
        TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        )

    val containerColor =
        if (isFocused) {
            colors.focusedContainerColor
        } else {
            colors.unfocusedContainerColor
        }

    Row(
        modifier =
            modifier
                .heightIn(min = 48.dp)
                .background(
                    color = containerColor,
                    shape = RoundedCornerShape(24.dp),
                ).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onContainerClick() }
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        InternalChipSearchInput(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
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
            placeholder = placeholder,
        )
    }
}

/**
 * ChipSearchBar의 내부 구현
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
    placeholder: String,
) {
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onContainerClick() },
    ) {
        itemsIndexed(
            items = contentItems,
            key = { _, item -> item.id },
        ) { index, item ->
            when (item) {
                is SearchContentElement.Chip -> {
                    SearchChipView(
                        tag = item.tag,
                        onClick = { onChipClick(index) },
                    )
                }
                is SearchContentElement.Text -> {
                    val focusRequester = focusRequesters[item.id] // <-- null일 수 있음
                    val bringIntoViewRequester = bringIntoViewRequesters[item.id] // <-- null일 수 있음

                    // 커서 위치 계산을 위해 TextLayoutResult를 저장
                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    val scope = rememberCoroutineScope() // bringIntoView 호출용

                    // 오른쪽에 추가할 패딩을 계산하기 위해 LocalDensity 가져오기
                    val density = LocalDensity.current

                    // 텍스트 너비 측정 로직
                    val textValue = textStates[item.id] ?: TextFieldValue()
                    val text = textValue.text.removePrefix("\u200B")
                    val textMeasurer = rememberTextMeasurer()
                    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)

                    val measuredWidthInPixels =
                        remember(text, textStyle) {
                            textMeasurer.measure(AnnotatedString(text), style = textStyle).size.width
                        }

                    val textWidthDp = with(LocalDensity.current) { measuredWidthInPixels.toDp() }

                    val isPlaceholder = (textValue.text == "\u200B" || textValue.text.isEmpty()) && contentItems.size == 1
                    val isEmptyText = text.isEmpty() && !isPlaceholder

                    val sidePadding = if (isEmptyText) 0.dp else 6.dp // 비어있으면 0dp
                    val totalHorizontalPadding = sidePadding * 2

                    val minFieldWidth = 10.dp

                    val finalWidth = (textWidthDp + totalHorizontalPadding).coerceAtLeast(minFieldWidth)

                    val cursorBrush =
                        if (hideCursor) {
                            SolidColor(Color.Transparent)
                        } else {
                            SolidColor(MaterialTheme.colorScheme.primary)
                        }

                    // 텍스트 레이아웃이나 커서 위치가 변경
                    LaunchedEffect(textLayoutResult, textValue.selection) {
                        textLayoutResult?.let { layoutResult ->
                            val textLength = layoutResult.layoutInput.text.length
                            val selectionEnd = textValue.selection.end

                            if (selectionEnd in 0..textLength) {
                                // 커서의 사각형을 계산
                                val cursorRect = layoutResult.getCursorRect(selectionEnd)

                                // 오른쪽에 추가할 패딩
                                val endPaddingPx = with(density) { sidePadding.toPx() } // + 8.dp

                                val rectWithPadding =
                                    Rect(
                                        left = cursorRect.left,
                                        top = cursorRect.top,
                                        right = cursorRect.right + endPaddingPx, // <-- 핵심: 오른쪽으로 16dp 더 넓게
                                        bottom = cursorRect.bottom,
                                    )

                                // 커서를 뷰로 스크롤
                                scope.launch {
                                    bringIntoViewRequester?.bringIntoView(rectWithPadding)
                                }
                            }
                        }
                    }

                    val baseModifier = Modifier
                        .then(
                            if (isPlaceholder) {
                                Modifier.fillMaxWidth()
                            } else {
                                Modifier.width(finalWidth)
                            },
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                onFocus(item.id)
                            } else {
                                onFocus(null)
                            }
                        }.padding(horizontal = 4.dp, vertical = 8.dp)

                    BasicTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            onTextChange(item.id, newValue)
                        },
                        onTextLayout = { textLayoutResult = it },
                        modifier =
                            Modifier
                                .then(baseModifier)
                                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                                .then(if (bringIntoViewRequester != null) Modifier.bringIntoViewRequester(bringIntoViewRequester) else Modifier),
                        maxLines = 1, // 스크롤 방지
                        cursorBrush = cursorBrush,
                        textStyle = textStyle,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        decorationBox = { innerTextField ->
                            if (isPlaceholder) {
                                Text(
                                    placeholder,
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
 * 검색창 내부에 표시될 칩 (HomeScreen.kt에서 가져옴)
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
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onClick() },
                ).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "#${tag.tagName}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
fun SuggestionChip(
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

/**
 * 검색 기록 드롭다운에 표시될 개별 항목 UI
 */
@Composable
fun SearchHistoryItem(
    query: String,
    allTags: List<TagItem>,
    parser: (String, List<TagItem>) -> List<SearchContentElement>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable { onHistoryClick(query) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = "History",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))

        FlowRow(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val elements =
                remember(query, allTags) {
                    parser(query, allTags)
                }

            elements.forEach { element ->
                when (element) {
                    is SearchContentElement.Text -> {
                        if (element.text.isNotEmpty()) {
                            Text(
                                text = element.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    is SearchContentElement.Chip -> {
                        Box(
                            modifier =
                                Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                    ).padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "#${element.tag.tagName}",
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = { onHistoryDelete(query) },
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete history item",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}