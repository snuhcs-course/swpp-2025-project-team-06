package com.example.momentag

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.momentag.model.Photo

/**
 * 이미지 그리드 아이템 - 일반 모드와 선택 모드를 모두 지원
 *
 * ImageContext는 ImageBrowserRepository를 통해 자동으로 관리됩니다.
 * UI는 단순히 navigate만 하면 됩니다.
 *
 * @param photo Photo 객체 (photoId + contentUri)
 * @param navController 네비게이션 컨트롤러
 * @param modifier Modifier
 * @param isSelectionMode 선택 모드 활성화 여부 (기본: false)
 * @param isSelected 현재 선택 여부 (기본: false)
 * @param onToggleSelection 선택/해제 콜백 (선택사항)
 * @param onLongPress 롱프레스 콜백 (선택사항)
 * @param cornerRadius 모서리 둥글기 (기본: 16dp)
 * @param topPadding 상단 패딩 (기본: 12dp)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageGridUriItem(
    photo: Photo,
    navController: NavController,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelection: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    cornerRadius: Dp = 16.dp,
    topPadding: Dp = 12.dp,
) {
    Box(modifier = modifier) {
        // 이미지
        val imageModifier =
            Modifier
                .padding(top = topPadding)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(cornerRadius))
                .align(Alignment.BottomCenter)
                .alpha(if (isSelectionMode && isSelected) 0.5f else 1f)

        AsyncImage(
            model = photo.contentUri,
            contentDescription = null,
            modifier =
                if (isSelectionMode || onLongPress != null) {
                    // 선택 모드이거나 롱프레스 지원 시 combinedClickable 사용
                    imageModifier.combinedClickable(
                        onClick = {
                            if (isSelectionMode && onToggleSelection != null) {
                                onToggleSelection()
                            } else {
                                // Just navigate - ImageContext loaded from Repository
                                navController.navigate(Screen.Image.createRoute(photo.contentUri, photo.photoId))
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode && onLongPress != null) {
                                onLongPress()
                                onToggleSelection?.invoke()
                            }
                        },
                    )
                } else {
                    // 일반 클릭만 지원
                    imageModifier.clickable {
                        // Just navigate - ImageContext loaded from Repository
                        navController.navigate(Screen.Image.createRoute(photo.contentUri, photo.photoId))
                    }
                },
            contentScale = ContentScale.Crop,
        )

        // 선택 표시 (선택 모드일 때만)
        if (isSelectionMode) {
            Box(
                modifier =
                    Modifier
                        .padding(top = topPadding)
                        .aspectRatio(1f)
                        .align(Alignment.BottomCenter)
                        .background(
                            if (isSelected) {
                                Color.Black.copy(alpha = 0.4f)
                            } else {
                                Color.Transparent
                            },
                        ),
            )

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(48.dp),
                )
            }
        }
    }
}
