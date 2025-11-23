package com.example.momentag.ui.components

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import com.example.momentag.R
import com.example.momentag.model.TagItem
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon
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
    isCursorHidden: Boolean,
    contentItems: List<SearchContentElement>,
    textStates: Map<String, TextFieldValue>,
    focusRequesters: Map<String, FocusRequester>,
    bringIntoViewRequesters: Map<String, BringIntoViewRequester>,
    onContainerClick: () -> Unit,
    onChipClick: (Int) -> Unit,
    onTextChange: (id: String, newValue: TextFieldValue) -> Unit,
    onFocus: (id: String?) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit = {},
    hasContent: Boolean = false,
    placeholder: String = "Search with \"#tag\"",
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

    Box(
        modifier =
            modifier
                .heightIn(min = Dimen.SearchBarMinHeight)
                .shadow(
                    elevation = Dimen.SearchBarShadowElevation,
                    shape = RoundedCornerShape(Dimen.SearchBarCornerRadius),
                ).background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(Dimen.SearchBarCornerRadius),
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        color = containerColor,
                        shape = RoundedCornerShape(Dimen.SearchBarCornerRadius),
                    ).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onContainerClick() }
                    .padding(horizontal = Dimen.ScreenHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StandardIcon.Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.cd_search),
                intent = IconIntent.Muted,
            )

            InternalChipSearchInput(
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(start = Dimen.ItemSpacingSmall),
                listState = listState,
                isCursorHidden = isCursorHidden,
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

            if (isFocused && hasContent) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(Dimen.IconButtonSizeSmall),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        StandardIcon.Icon(
                            imageVector = Icons.Default.Close,
                            sizeRole = IconSizeRole.InlineAction,
                            tintOverride = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            contentDescription = stringResource(R.string.cd_clear_text),
                        )
                    }
                }
            }
        }
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
    isCursorHidden: Boolean,
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
        horizontalArrangement = Arrangement.spacedBy(Dimen.SearchSideEmptyPadding),
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

                    val sidePadding = if (isEmptyText) Dimen.SearchSideEmptyPadding else Dimen.SearchSidePadding // 비어있으면 0dp
                    val totalHorizontalPadding = sidePadding * 2

                    val minFieldWidth = Dimen.MinSearchBarMinHeight

                    val finalWidth = (textWidthDp + totalHorizontalPadding).coerceAtLeast(minFieldWidth)

                    val cursorBrush =
                        if (isCursorHidden) {
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
                                val endPaddingPx = with(density) { sidePadding.toPx() }

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

                    val baseModifier =
                        Modifier
                            .then(
                                if (isPlaceholder) {
                                    Modifier.fillMaxWidth()
                                } else {
                                    Modifier.width(finalWidth)
                                },
                            ).onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onFocus(item.id)
                                } else {
                                    onFocus(null)
                                }
                            }.padding(horizontal = Dimen.GridItemSpacing, vertical = Dimen.ItemSpacingSmall)

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
                                .then(
                                    if (bringIntoViewRequester !=
                                        null
                                    ) {
                                        Modifier.bringIntoViewRequester(bringIntoViewRequester)
                                    } else {
                                        Modifier
                                    },
                                ),
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
                ).padding(horizontal = Dimen.ButtonPaddingVertical, vertical = Dimen.ButtonPaddingSmallVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
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
                .padding(horizontal = Dimen.ButtonPaddingVertical, vertical = Dimen.ButtonPaddingSmallVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
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
                .padding(horizontal = Dimen.ScreenHorizontalPadding, vertical = Dimen.ItemSpacingMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StandardIcon.Icon(
            imageVector = Icons.Default.History,
            contentDescription = "History",
            sizeRole = IconSizeRole.Navigation,
            intent = IconIntent.Muted,
        )
        Spacer(modifier = Modifier.width(Dimen.ItemSpacingMedium))

        FlowRow(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalArrangement = Arrangement.spacedBy(Dimen.GridItemSpacing),
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
                                modifier = Modifier.padding(vertical = Dimen.GridItemSpacing),
                            )
                        }
                    }
                    is SearchContentElement.Chip -> {
                        Box(
                            modifier =
                                Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(Dimen.ButtonCornerRadius),
                                    ).padding(horizontal = Dimen.ButtonPaddingVertical, vertical = Dimen.GridItemSpacing),
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

        Spacer(modifier = Modifier.width(Dimen.ItemSpacingSmall))

        IconButton(
            onClick = { onHistoryDelete(query) },
            modifier = Modifier.size(Dimen.SectionSpacing),
        ) {
            StandardIcon.Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete history item",
                sizeRole = IconSizeRole.InlineAction,
                intent = IconIntent.Muted,
            )
        }
    }
}
