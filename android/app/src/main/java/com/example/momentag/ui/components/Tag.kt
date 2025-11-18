package com.example.momentag.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.LabelOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                .height(32.dp)
                .background(color = color, shape = RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 4.dp),
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
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(4.dp))

        when (variant) {
            is TagVariant.Plain -> Unit

            is TagVariant.CloseAlways -> {
                IconButton(
                    onClick = variant.onDismiss,
                    modifier = Modifier.size(16.dp),
                ) {
                    StandardIcon.Icon(
                        imageVector = Icons.AutoMirrored.Filled.LabelOff,
                        contentDescription = "UnTag",
                        sizeRole = IconSizeRole.InlineAction,
                    )
                }
            }

            is TagVariant.CloseWhen -> {
                AnimatedVisibility(visible = variant.isDeleteMode) {
                    IconButton(
                        onClick = variant.onDismiss,
                        modifier = Modifier.size(16.dp),
                    ) {
                        StandardIcon.Icon(
                            imageVector = Icons.AutoMirrored.Filled.LabelOff,
                            contentDescription = "UnTag",
                            sizeRole = IconSizeRole.InlineAction,
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
    val maxTextWidth = 180.dp
    Row(
        modifier =
            modifier
                .height(32.dp)
                .background(color = color, shape = RoundedCornerShape(16.dp))
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
                    horizontal = if (isEditMode) 6.dp else 12.dp,
                    vertical = 4.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 왼쪽: Edit 모드일 때 연필 아이콘 표시 (수정/확인)
        if (isEditMode && onEdit != null) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(20.dp),
            ) {
                StandardIcon.Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Tag",
                    sizeRole = IconSizeRole.InlineAction,
                    intent = IconIntent.Inverse,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
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

        // Edit 모드가 아닐 때만 카운트 표시
        if (!isEditMode) {
            Spacer(modifier = Modifier.width(6.dp))

            // showCheckbox가 true면 체크박스, 아니면 카운트
            if (showCheckbox) {
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
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
            } else {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                )
            }
        }

        // 오른쪽: Edit 모드일 때 휴지통 아이콘 표시 (삭제/취소)
        if (isEditMode && onDelete != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(20.dp),
            ) {
                StandardIcon.Icon(
                    imageVector = Icons.AutoMirrored.Filled.LabelOff,
                    contentDescription = "UnTag",
                    sizeRole = IconSizeRole.InlineAction,
                    intent = IconIntent.Inverse,
                )
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
    // 5. Derived 상태 및 계산된 값
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

    // 13. UI (Box)
    Box(
        modifier =
            modifier
                .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.CenterStart,
    ) {
        TagContainer(
            modifier = Modifier,
            color = backgroundColor,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }

        // 2) 선택된 경우에만 우상단 체크 뱃지 오버레이
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
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
) {
    // 4. 로컬 상태 변수
    var isExpanded by remember { mutableStateOf(false) }
    var tagText by remember { mutableStateOf("") }

    // 13. UI (AnimatedContent)
    AnimatedContent(
        targetState = isExpanded,
        label = "expand_collapse",
        modifier = modifier,
    ) { expanded ->
        if (!expanded) {
            // Collapsed state: Show only "+" - 32dp height
            Box(
                modifier =
                    Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            isExpanded = true
                            onExpanded?.invoke()
                        }.padding(horizontal = 12.dp, vertical = 4.dp),
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
                        .height(32.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 왼쪽: Confirm button (Checkmark)
                IconButton(
                    onClick = {
                        if (tagText.isNotBlank()) {
                            onTagAdded(tagText.trim())
                            isExpanded = false
                            tagText = ""
                        }
                    },
                    modifier = Modifier.size(24.dp),
                    enabled = tagText.isNotBlank(),
                ) {
                    val confirmTint =
                        if (tagText.isNotBlank()) {
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
                Spacer(modifier = Modifier.width(4.dp))

                BasicTextField(
                    value = tagText,
                    onValueChange = { tagText = it },
                    modifier =
                        Modifier
                            .width(80.dp)
                            .padding(horizontal = 4.dp),
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

                Spacer(modifier = Modifier.width(4.dp))

                // 오른쪽: Cancel button (Close icon)
                IconButton(
                    onClick = {
                        isExpanded = false
                        tagText = ""
                    },
                    modifier = Modifier.size(24.dp),
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
