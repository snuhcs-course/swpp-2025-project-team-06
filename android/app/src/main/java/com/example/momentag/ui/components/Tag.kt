package com.example.momentag.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.momentag.ui.theme.Animation
import com.example.momentag.ui.theme.Dimen
import com.example.momentag.ui.theme.IconIntent
import com.example.momentag.ui.theme.IconSizeRole
import com.example.momentag.ui.theme.StandardIcon

/**
 * 태그 변형(Variant)
 */
sealed interface TagVariant {
    /** X 버튼 없음 */
    data object Plain : TagVariant

    /** X 버튼 항상 보임 */
    data class CloseAlways(
        val onDismiss: () -> Unit,
    ) : TagVariant

    /** isDeleteMode 가 true일 때만 X 버튼 보임 */
    data class CloseWhen(
        val isDeleteMode: Boolean,
        val onDismiss: () -> Unit,
    ) : TagVariant

    /** 추천 태그 (투명도 적용) */
    data object Recommended : TagVariant
}

/**
 * 공통 컨테이너 - 배경/모서리/패딩/정렬
 * 표준 높이: 32dp (상하 패딩 4dp + 텍스트 영역 24dp + 상하 패딩 4dp)
 */
@Composable
private fun TagContainer(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .height(Dimen.TagHeight)
                .background(color = color, shape = RoundedCornerShape(Dimen.Radius50))
                .padding(horizontal = Dimen.ItemSpacingMedium, vertical = Dimen.GridItemSpacing),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * 단일 진입점: 텍스트와 Variant 로 모든 동작 제어
 */
@Composable
fun TagChip(
    text: String,
    variant: TagVariant = TagVariant.Plain,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    // 5. Derived 상태 및 계산된 값
    val alpha = if (variant is TagVariant.Recommended) 0.5f else 1f

    // 13. UI (TagContainer)
    TagContainer(modifier = modifier.alpha(alpha), color = color) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(modifier = Modifier.width(Dimen.GridItemSpacing))

        when (variant) {
            is TagVariant.Plain -> Unit

            is TagVariant.CloseAlways -> {
                IconButton(
                    onClick = variant.onDismiss,
                    modifier = Modifier.size(Dimen.ItemSpacingLarge),
                ) {
                    StandardIcon.Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        sizeRole = IconSizeRole.InlineAction,
                        intent = IconIntent.Inverse,
                    )
                }
            }
            is TagVariant.CloseWhen -> {
                if (variant.isDeleteMode) {
                    IconButton(
                        onClick = variant.onDismiss,
                        modifier = Modifier.size(Dimen.IconButtonsSizeXSmall),
                    ) {
                        StandardIcon.Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            sizeRole = IconSizeRole.InlineAction,
                            intent = IconIntent.Inverse,
                        )
                    }
                }
            }

            is TagVariant.Recommended -> Unit
        }
    }
}

// ---- 기존 API와의 호환을 위한 얇은 래퍼들 ----

/** 기본 태그 (X 버튼 없음) */
@Composable
fun tag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
) = TagChip(text = text, variant = TagVariant.Plain, modifier = modifier, color = color)

/** X 버튼 항상 보이는 태그 */
@Composable
fun tagX(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
) = TagChip(text = text, variant = TagVariant.CloseAlways(onDismiss), modifier = modifier, color = color)

/** 삭제 모드일 때만 X 버튼 보이는 태그 */
@Composable
fun tagXMode(
    text: String,
    isDeleteMode: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
) = TagChip(
    text = text,
    variant = TagVariant.CloseWhen(isDeleteMode, onDismiss),
    modifier = modifier,
    color = color,
)

/** 추천 태그 (투명도가 적용된 태그) */
@Composable
fun tagRecommended(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
) = TagChip(text = text, variant = TagVariant.Recommended, modifier = modifier, color = color)

/**
 * 태그와 개수를 함께 보여주는 컴포넌트 (MyTags 화면용)
 * 롱프레스 시 개별 수정 모드로 확장되며, 가로 길이 변화를 최소화하고 카운트를 숨김
 */

@Composable
fun TagChipWithCount(
    tagName: String,
    count: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showCheckbox: Boolean = false,
    isChecked: Boolean = false,
) {
    var textOverflow by remember { mutableStateOf(false) }

    // reasonable maximum width for the tag text before ellipsizing
    val maxTextWidth = Dimen.TagMaxTextWidth
    Row(
        modifier =
            modifier
                .height(Dimen.TagHeight)
                .background(color = color, shape = RoundedCornerShape(Dimen.TagCornerRadius))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // Allow click when in checkbox mode (showCheckbox) or when not in individual edit mode
                            if (showCheckbox || !isEditMode) {
                                onClick()
                            }
                        },
                        onLongPress = {
                            onLongClick?.invoke()
                        },
                    )
                }.padding(
                    horizontal = Dimen.CountTagEditHorizontalPadding,
                    vertical = Dimen.TagChipVerticalPadding,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 왼쪽: Edit 모드일 때 연필 아이콘 표시 (수정/확인)
        if (isEditMode && onEdit != null) {
            Row {
                IconButton(
                    onClick = onEdit!!,
                    modifier = Modifier.size(Dimen.IconButtonsSizeXSmall),
                ) {
                    StandardIcon.Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Tag",
                        sizeRole = IconSizeRole.InlineAction,
                        intent = IconIntent.Inverse,
                    )
                }
                Spacer(modifier = Modifier.width(Dimen.TagItemSpacer))
            }
        }

        Text(
            text = tagName,
            modifier = Modifier.widthIn(max = maxTextWidth),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult -> textOverflow = textLayoutResult.hasVisualOverflow },
        )

        if (!isEditMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.width(Dimen.TagChipWithCountSpacer))
                // 카운트는 항상 표시
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
                // showCheckbox가 true면 카운트 옆에 체크박스도 표시
                if (showCheckbox) {
                    Spacer(modifier = Modifier.width(Dimen.TagItemSpacer))
                    Box(
                        modifier =
                            Modifier
                                .size(Dimen.IconButtonsSizeXSmall)
                                .clip(CircleShape)
                                .background(
                                    if (isChecked) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                                    },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isChecked) {
                            StandardIcon.Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Checked",
                                sizeRole = IconSizeRole.ChipAction,
                                tintOverride = color,
                            )
                        }
                    }
                }
            }
        }

        // 오른쪽: Edit 모드일 때 휴지통 아이콘 표시 (삭제/취소)
        if (isEditMode && onDelete != null) {
            Row {
                Spacer(modifier = Modifier.width(Dimen.TagItemSpacer))
                IconButton(
                    onClick = onDelete!!,
                    modifier = Modifier.size(Dimen.IconButtonsSizeXSmall),
                ) {
                    StandardIcon.Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        sizeRole = IconSizeRole.InlineAction,
                        intent = IconIntent.Inverse,
                    )
                }
            }
        }
    }
}

@Composable
fun StoryTagChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var textOverflow by remember { mutableStateOf(false) }
    var showFullName by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val backgroundColor =
        when {
            isSelected -> MaterialTheme.colorScheme.primary
            !enabled -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.primaryContainer
        }

    val textColor =
        when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.onSurface
        }

    Box(
        modifier =
            modifier
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            showFullName = false
                            onClick()
                        },
                        onLongPress = {
                            if (textOverflow) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showFullName = true
                            }
                        },
                        onPress = {
                            tryAwaitRelease()
                            showFullName = false
                        },
                    )
                },
        contentAlignment = Alignment.CenterStart,
    ) {
        TagContainer(
            modifier = Modifier,
            color = backgroundColor,
        ) {
            Text(
                text = text,
                modifier =
                    if (showFullName) {
                        Modifier
                    } else {
                        Modifier.widthIn(max = Dimen.TagMaxTextWidth)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = if (showFullName) TextOverflow.Visible else TextOverflow.Ellipsis,
                onTextLayout = { layoutResult -> textOverflow = layoutResult.hasVisualOverflow },
            )
            Spacer(modifier = Modifier.width(Dimen.TagItemSpacer))
        }

        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = Dimen.TagItemSpacer, y = -Dimen.TagItemSpacer)
                        .size(Dimen.StoryTagChipBadgeSize)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                contentAlignment = Alignment.Center,
            ) {
                StandardIcon.Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    sizeRole = IconSizeRole.ChipAction,
                    tintOverride = Color.White,
                )
            }
        }
    }
}

@Composable
fun CustomTagChip(
    onTagAdded: (String) -> Unit,
    modifier: Modifier = Modifier,
    onExpanded: (() -> Unit)? = null,
    onValidationError: ((Boolean) -> Unit)? = null,
) {
    // 4. 로컬 상태 변수
    var isExpanded by remember { mutableStateOf(false) }
    var tagText by remember { mutableStateOf("") }

    LaunchedEffect(tagText) {
        onValidationError?.invoke(tagText.length > 25)
    }

    // 13. UI (AnimatedContent)
    AnimatedContent(
        targetState = isExpanded,
        transitionSpec = {
            (
                fadeIn(
                    animationSpec = Animation.mediumTween(),
                ) +
                    scaleIn(initialScale = 0.9f, animationSpec = Animation.mediumTween())
            ).togetherWith(
                fadeOut(
                    animationSpec = Animation.mediumTween(),
                ) + scaleOut(targetScale = 0.9f, animationSpec = Animation.mediumTween()),
            )
        },
        label = "expand_collapse",
        modifier = modifier,
    ) { expanded ->
        if (!expanded) {
            // Collapsed state: Show only "+" - 32dp height
            Box(
                modifier =
                    Modifier
                        .height(Dimen.TagHeight)
                        .clip(RoundedCornerShape(Dimen.Radius50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            isExpanded = true
                            onExpanded?.invoke()
                        }.padding(horizontal = Dimen.TagCustomChipHorizontalPadding, vertical = Dimen.TagChipVerticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Medium),
                )
            }
        } else {
            // Expanded state: 왼쪽 확인, 오른쪽 취소 - maintain 32dp height
            Row(
                modifier =
                    Modifier
                        .height(Dimen.TagHeight)
                        .clip(RoundedCornerShape(Dimen.Radius50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = Dimen.TagChipHorizontalPadding, vertical = Dimen.TagChipVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 왼쪽: Confirm button (Checkmark)
                IconButton(
                    onClick = {
                        if (tagText.isNotBlank() && tagText.length <= 25) {
                            onTagAdded(tagText.trim())
                            isExpanded = false
                            tagText = ""
                        }
                    },
                    modifier = Modifier.size(Dimen.IconButtonSizeSmall),
                    enabled = tagText.isNotBlank() && tagText.length <= 25,
                ) {
                    val confirmTint =
                        if (tagText.isNotBlank() && tagText.length <= 25) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        }
                    StandardIcon.Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        sizeRole = IconSizeRole.InlineAction,
                        tintOverride = confirmTint,
                    )
                }

                // Text field
                Spacer(modifier = Modifier.width(Dimen.TagItemSpacer))

                BasicTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    modifier =
                        Modifier
                            .width(Dimen.CustomTagChipTextFieldWidth)
                            .padding(horizontal = Dimen.TagHorizontalPadding),
                    textStyle =
                        MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Companion.Medium,
                        ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        if (tagText.isEmpty()) {
                            Text(
                                text = "Tag name",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        innerTextField()
                    },
                )

                Spacer(modifier = Modifier.width(Dimen.TagItemSpacer))

                // 오른쪽: Cancel button (Close icon)
                IconButton(
                    onClick = {
                        isExpanded = false
                        tagText = ""
                    },
                    modifier = Modifier.size(Dimen.IconButtonSizeSmall),
                ) {
                    StandardIcon.Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        sizeRole = IconSizeRole.InlineAction,
                        intent = IconIntent.Muted,
                    )
                }
            }
        }
    }
}

@Composable
fun ConfirmableRecommendedTag(
    tagName: String,
    onConfirm: (String) -> Unit,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
) {
    // 클릭 시 즉시 추가 (confirm 단계 제거)
    Box(modifier = Modifier.clickable { onConfirm(tagName) }) {
        tagRecommended(text = tagName, color = color)
    }
}
