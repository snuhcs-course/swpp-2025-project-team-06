package com.example.momentag

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp

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
                .padding(horizontal = 12.dp), // vertical = 8.dp),
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
    val alpha = if (variant is TagVariant.Recommended) 0.5f else 1f

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
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss Tag")
                }
            }

            is TagVariant.CloseWhen -> {
                AnimatedVisibility(visible = variant.isDeleteMode) {
                    IconButton(
                        onClick = variant.onDismiss,
                        modifier = Modifier.size(16.dp),
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss Tag")
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
) {
    Row(
        modifier =
            modifier
                .height(48.dp)
                .background(color = color, shape = RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (!isEditMode) {
                                onClick()
                            }
                        },
                        onLongPress = {
                            onLongClick?.invoke()
                        },
                    )
                }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Edit 모드일 때 연필 아이콘을 가장 앞에 표시
        if (isEditMode && onEdit != null) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Tag",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = tagName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        )

        // Edit 모드일 때 X 아이콘을 뒤에 표시
        if (isEditMode && onDelete != null) {
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Tag",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
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
    Box(
        modifier =
            modifier
                .clickable(enabled = enabled) { onClick() }
                .alpha(if (enabled) 1f else 0.6f),
        // 전체 칩 클릭 가능하게
        contentAlignment = Alignment.CenterStart, // 기본 칩은 왼쪽부터 배치되니까 큰 의미는 없음
    ) {
        // 1) 원래 tagChip 그대로 그린다 (스타일 건드리지 않음)
        TagChip(
            text = text,
            variant = TagVariant.Plain,
            modifier = Modifier, // 여기선 아무 커스텀 x
        )

        // 2) 선택된 경우에만 우상단 체크 뱃지 오버레이
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd) // tagChip의 영역 기준 우상단
                        .offset(x = 4.dp, y = (-4).dp) // 살짝 밖으로 튀어나오게 보이게
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50)),
                // 초록 동그라미
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
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
    var isExpanded by remember { mutableStateOf(false) }
    var tagText by remember { mutableStateOf("") }

    AnimatedContent(
        targetState = isExpanded,
        label = "expand_collapse",
        modifier = modifier,
    ) { expanded ->
        if (!expanded) {
            // Collapsed state: Show only "+"
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            isExpanded = true
                            onExpanded?.invoke()
                        }.padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Companion.Medium),
                )
            }
        } else {
            // Expanded state: Show X, TextField, and Checkmark
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Cancel button (X)
                IconButton(
                    onClick = {
                        isExpanded = false
                        tagText = ""
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
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

                // Confirm button (Checkmark)
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
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm",
                        tint =
                            if (tagText.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            },
                        modifier = Modifier.size(16.dp),
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
    var isExpanded by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = isExpanded,
        label = "confirmable_recommended_tag",
    ) { expanded ->
        if (expanded) {
            // Expanded state with confirm/dismiss buttons
            Row(
                modifier =
                    Modifier
                        .height(32.dp)
                        .background(
                            color = color,
                            shape = RoundedCornerShape(50),
                        ).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { isExpanded = false },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Recommended Tag",
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = tagName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                IconButton(
                    onClick = {
                        onConfirm(tagName)
                        isExpanded = false
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Confirm Recommended Tag",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else {
            // Collapsed state, looks like a normal recommended tag but is clickable
            Box(modifier = Modifier.clickable { isExpanded = true }) {
                tagRecommended(text = tagName, color = color)
            }
        }
    }
}
