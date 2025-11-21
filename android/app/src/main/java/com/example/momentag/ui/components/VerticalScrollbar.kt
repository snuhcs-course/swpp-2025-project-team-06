package com.example.momentag.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import com.example.momentag.ui.theme.Dimen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A vertical scrollbar component for LazyVerticalGrid that provides visual feedback
 * and fast scrolling capability.
 *
 * Features:
 * - Auto-hide after period of inactivity
 * - Drag thumb to scroll
 * - Tap track to jump to position
 * - Visual feedback on interaction
 * - Smooth fade in/out animations
 *
 * @param state The LazyGridState of the grid to control
 * @param modifier Modifier to be applied to the scrollbar
 * @param enabled Whether the scrollbar accepts user interactions (dragging/tapping)
 * @param scrollbarWidth Width of the scrollbar track and thumb
 * @param scrollbarWidthActive Width of the scrollbar when being dragged
 * @param scrollbarPadding Padding from the edge
 * @param thumbColor Color of the scrollbar thumb when idle
 * @param thumbColorActive Color of the scrollbar thumb when being dragged
 * @param trackColor Color of the scrollbar track
 * @param autoHideDelay Delay in milliseconds before auto-hiding the scrollbar
 * @param minThumbHeight Minimum height of the scrollbar thumb in dp
 */
@Composable
fun VerticalScrollbar(
    state: LazyGridState,
    modifier: Modifier = Modifier.Companion,
    enabled: Boolean = true,
    scrollbarWidth: Dp = Dimen.ScrollbarWidth,
    scrollbarWidthActive: Dp = Dimen.ScrollbarWidthActive,
    scrollbarPadding: Dp = Dimen.SpacingXXSmall,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    thumbColorActive: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.Companion.Transparent,
    autoHideDelay: Long = 1500L,
    minThumbHeight: Dp = Dimen.ScrollbarMinThumbHeight,
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // State for visibility and interaction
    var isVisible by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }
    var viewportHeight by remember { mutableFloatStateOf(0f) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Calculate if scrollbar should be shown
    val isScrollable by remember {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            layoutInfo.totalItemsCount > 0 &&
                layoutInfo.totalItemsCount > layoutInfo.visibleItemsInfo.size
        }
    }

    // Calculate thumb size and position
    val scrollbarMetrics by remember {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            if (!isScrollable || layoutInfo.totalItemsCount == 0) {
                return@derivedStateOf ScrollbarMetrics(0f, 0f, 0f)
            }

            val totalItems = layoutInfo.totalItemsCount.toFloat()
            val visibleItems = layoutInfo.visibleItemsInfo.size.toFloat()

            // Calculate thumb size as proportion of viewport
            val thumbSizeRatio = (visibleItems / totalItems).coerceIn(0.1f, 1.0f)
            val minThumbPx = with(density) { minThumbHeight.toPx() }
            val thumbHeightPx = (viewportHeight * thumbSizeRatio).coerceAtLeast(minThumbPx)

            // Calculate scroll progress (0.0 to 1.0)
            val firstVisibleIndex = state.firstVisibleItemIndex.toFloat()
            val scrollableItems = (totalItems - visibleItems).coerceAtLeast(1f)
            val scrollProgress = (firstVisibleIndex / scrollableItems).coerceIn(0f, 1f)

            // Calculate thumb position
            val maxScrollDistance = viewportHeight - thumbHeightPx
            val thumbOffsetPx = scrollProgress * maxScrollDistance

            ScrollbarMetrics(
                thumbHeight = thumbHeightPx,
                thumbOffset = thumbOffsetPx,
                scrollProgress = scrollProgress,
            )
        }
    }

    // Show scrollbar when scrolling
    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            isVisible = true
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    // Cancel dragging when disabled
    LaunchedEffect(enabled) {
        if (!enabled && isDragging) {
            isDragging = false
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    // Auto-hide after delay
    LaunchedEffect(lastInteractionTime, isDragging) {
        if (lastInteractionTime > 0 && !isDragging) {
            delay(autoHideDelay)
            if (System.currentTimeMillis() - lastInteractionTime >= autoHideDelay) {
                isVisible = false
            }
        }
    }

    // Animated alpha for smooth show/hide
    val alpha by animateFloatAsState(
        targetValue = if (isVisible && isScrollable) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "scrollbarAlpha",
    )

    // Animated width for active state
    val currentWidth by animateFloatAsState(
        targetValue =
            with(density) {
                if (isDragging) scrollbarWidthActive.toPx() else scrollbarWidth.toPx()
            },
        animationSpec = tween(durationMillis = 150),
        label = "scrollbarWidth",
    )

    // Current thumb color based on interaction state
    val currentThumbColor = if (isDragging) thumbColorActive else thumbColor

    if (isScrollable) {
        Box(
            modifier =
                modifier
                    .fillMaxHeight()
                    .width(scrollbarWidth + scrollbarPadding * 2)
                    .alpha(alpha)
                    .onSizeChanged { size ->
                        viewportHeight = size.height.toFloat()
                    }.pointerInput(enabled) {
                        // Handle tap on track to jump to position
                        detectTapGestures { offset ->
                            if (enabled && !isDragging) {
                                val targetProgress = (offset.y / viewportHeight).coerceIn(0f, 1f)
                                val layoutInfo = state.layoutInfo
                                val totalItems = layoutInfo.totalItemsCount
                                val visibleItems = layoutInfo.visibleItemsInfo.size
                                val targetIndex =
                                    (targetProgress * (totalItems - visibleItems))
                                        .roundToInt()
                                        .coerceIn(0, totalItems - 1)

                                coroutineScope.launch {
                                    state.scrollToItem(targetIndex)
                                }

                                isVisible = true
                                lastInteractionTime = System.currentTimeMillis()
                            }
                        }
                    },
        ) {
            // Track background
            Box(
                modifier =
                    Modifier.Companion
                        .fillMaxHeight()
                        .width(scrollbarWidth)
                        .background(trackColor),
            )

            // Scrollbar thumb
            Box(
                modifier =
                    Modifier.Companion
                        .offset {
                            IntOffset(
                                x = 0,
                                y = scrollbarMetrics.thumbOffset.roundToInt(),
                            )
                        }.width(
                            with(density) { currentWidth.toDp() },
                        ).height(
                            with(density) { scrollbarMetrics.thumbHeight.toDp() },
                        ).clip(RoundedCornerShape(Dimen.Radius6))
                        .background(currentThumbColor)
                        .pointerInput(enabled) {
                            detectVerticalDragGestures(
                                onDragStart = {
                                    if (enabled) {
                                        isDragging = true
                                        dragOffset = scrollbarMetrics.thumbOffset
                                        isVisible = true
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onDragCancel = {
                                    isDragging = false
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    if (enabled) {
                                        dragOffset += dragAmount
                                        val maxScrollDistance =
                                            viewportHeight - scrollbarMetrics.thumbHeight
                                        val normalizedOffset =
                                            (dragOffset / maxScrollDistance).coerceIn(0f, 1f)

                                        val layoutInfo = state.layoutInfo
                                        val totalItems = layoutInfo.totalItemsCount
                                        val visibleItems = layoutInfo.visibleItemsInfo.size
                                        val targetIndex =
                                            (normalizedOffset * (totalItems - visibleItems))
                                                .roundToInt()
                                                .coerceIn(0, totalItems - 1)

                                        coroutineScope.launch {
                                            state.scrollToItem(targetIndex)
                                        }
                                    }
                                },
                            )
                        },
            )
        }
    }
}

/**
 * Metrics for scrollbar rendering
 *
 * @param thumbHeight Height of the scrollbar thumb in pixels
 * @param thumbOffset Y offset of the scrollbar thumb in pixels
 * @param scrollProgress Current scroll progress from 0.0 to 1.0
 */
private data class ScrollbarMetrics(
    val thumbHeight: Float,
    val thumbOffset: Float,
    val scrollProgress: Float,
)
