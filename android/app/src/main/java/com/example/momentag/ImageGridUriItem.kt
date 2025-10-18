package com.example.momentag

import android.net.Uri
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
import com.example.momentag.model.ImageContext
import com.example.momentag.viewmodel.ImageDetailViewModel

/**
 * 이미지 그리드 아이템 - 일반 모드와 선택 모드를 모두 지원
 *
 * @param imageUri 이미지 URI
 * @param navController 네비게이션 컨트롤러
 * @param modifier Modifier
 * @param imageDetailViewModel 이미지 상세 뷰모델 (선택사항)
 * @param allImages 전체 이미지 리스트 (스와이프용)
 * @param contextType 이미지 컨텍스트 타입
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
    imageUri: Uri,
    navController: NavController,
    modifier: Modifier = Modifier,
    imageDetailViewModel: ImageDetailViewModel? = null,
    allImages: List<Uri>? = null,
    contextType: ImageContext.ContextType = ImageContext.ContextType.GALLERY,
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
            model = imageUri,
            contentDescription = null,
            modifier =
                if (isSelectionMode || onLongPress != null) {
                    // 선택 모드이거나 롱프레스 지원 시 combinedClickable 사용
                    imageModifier.combinedClickable(
                        onClick = {
                            if (isSelectionMode && onToggleSelection != null) {
                                onToggleSelection()
                            } else {
                                navigateToImageDetail(
                                    imageUri = imageUri,
                                    navController = navController,
                                    imageDetailViewModel = imageDetailViewModel,
                                    allImages = allImages,
                                    contextType = contextType,
                                )
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
                        navigateToImageDetail(
                            imageUri = imageUri,
                            navController = navController,
                            imageDetailViewModel = imageDetailViewModel,
                            allImages = allImages,
                            contextType = contextType,
                        )
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

/**
 * 이미지 상세 화면으로 네비게이션 (내부 헬퍼 함수)
 */
private fun navigateToImageDetail(
    imageUri: Uri,
    navController: NavController,
    imageDetailViewModel: ImageDetailViewModel?,
    allImages: List<Uri>?,
    contextType: ImageContext.ContextType,
) {
    // 이미지 컨텍스트 설정
    if (imageDetailViewModel != null && allImages != null) {
        val index = allImages.indexOf(imageUri)
        imageDetailViewModel.setImageContext(
            ImageContext(
                images = allImages,
                currentIndex = index.coerceAtLeast(0),
                contextType = contextType,
            ),
        )
    }
    navController.navigate(Screen.Image.createRoute(imageUri))
}
