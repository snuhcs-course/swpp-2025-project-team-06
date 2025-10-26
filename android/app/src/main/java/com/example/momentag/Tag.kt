package com.example.momentag

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.momentag.ui.theme.TagColor
import com.example.momentag.ui.theme.Word

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
private fun tagContainer(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .background(color = TagColor, shape = RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * 단일 진입점: 텍스트와 Variant 로 모든 동작 제어
 */
@Composable
fun tagChip(
    text: String,
    variant: TagVariant = TagVariant.Plain,
    modifier: Modifier = Modifier,
) {
    val alpha = if (variant is TagVariant.Recommended) 0.5f else 1f

    tagContainer(modifier = modifier.alpha(alpha)) {
        Text(text = text, fontSize = 14.sp, color = Word)
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
) = tagChip(text = text, variant = TagVariant.Plain, modifier = modifier)

/** X 버튼 항상 보이는 태그 */
@Composable
fun tagX(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) = tagChip(text = text, variant = TagVariant.CloseAlways(onDismiss), modifier = modifier)

/** 삭제 모드일 때만 X 버튼 보이는 태그 */
@Composable
fun tagXMode(
    text: String,
    isDeleteMode: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) = tagChip(
    text = text,
    variant = TagVariant.CloseWhen(isDeleteMode, onDismiss),
    modifier = modifier,
)

/** 추천 태그 (투명도가 적용된 태그) */
@Composable
fun tagRecommended(
    text: String,
    modifier: Modifier = Modifier,
) = tagChip(text = text, variant = TagVariant.Recommended, modifier = modifier)
