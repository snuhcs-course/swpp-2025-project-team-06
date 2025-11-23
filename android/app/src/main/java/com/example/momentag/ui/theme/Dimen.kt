package com.example.momentag.ui.theme

import androidx.compose.ui.unit.dp

/**
 * MomenTag 디자인 시스템에 사용되는 모든 치수(Dimension)를 중앙에서 관리하는 객체입니다.
 *
 * 이 객체는 패딩, 간격, 크기, 둥글기 등 UI 요소의 모든 `dp` 값을 포함합니다.
 * 일관된 디자인을 유지하고, 향후 디자인 변경에 유연하게 대응하기 위해 사용됩니다.
 *
 * @see <a href="https://github.com/swpp-2025-project-team-06/project-docs/blob/main/CONVENTION.md#dimensions">Dimension Convention</a>
 */
object Dimen {
    // ============================================================================================
    // 1. 레이아웃 및 간격 (Layout & Spacing)
    // ============================================================================================

    /** 화면 전체의 좌우 여백 */
    val ScreenHorizontalPadding = 16.dp

    /** 입력 폼 중심 화면의 좌우 여백 */
    val FormScreenHorizontalPadding = 24.dp

    /** UI 섹션(Section) 사이의 수직 간격 */
    val SectionSpacing = 24.dp

    /** 아이템 사이의 넓은 간격 */
    val ItemSpacingLarge = 16.dp

    /** 아이템 사이의 중간 간격 */
    val ItemSpacingMedium = 12.dp

    /** 아이템 사이의 좁은 간격 */
    val ItemSpacingSmall = 8.dp

    /** 가장 좁은 간격 */
    val SpacingXXSmall = 2.dp

    /** 그리드 아이템(사진, 앨범 등) 사이의 간격 */
    val GridItemSpacing = 4.dp

    /** 앨범 그리드 아이템 사이의 간격 */
    val AlbumGridItemSpacing = 12.dp

    val ButtonStartPadding = 32.dp

    // ============================================================================================
    // 2. 컴포넌트 크기 (Component Size)
    // ============================================================================================

    // --- Buttons ---
    val ButtonHeightLarge = 52.dp
    val ButtonHeightMedium = 44.dp
    val IconButtonSizeLarge = 40.dp
    val IconButtonSizeMediumLarge = 36.dp
    val IconButtonSizeMedium = 32.dp
    val IconButtonSizeSmall = 24.dp
    val IconButtonsSizeXSmall = 16.dp

    // --- Bars ---
    val TopBarHeight = 56.dp
    val BottomNavBarHeight = 56.dp
    val SearchBarMinHeight = 48.dp
    val TitleRowHeight = 40.dp

    // 검색바 내부 텍스트필드의 최소 너비
    val MinSearchBarMinHeight = 10.dp

    // --- Scrollbar ---
    val ScrollbarWidth = 6.dp
    val ScrollbarWidthActive = 8.dp
    val ScrollbarMinThumbHeight = 48.dp

    // --- Tags ---
    val TagHeight = 32.dp
    val TagMaxTextWidth = 180.dp
    val CustomTagChipTextFieldWidth = 80.dp
    val StoryTagChipBadgeSize = 18.dp

    // --- Others ---
    val EmptyStateImageSize = 120.dp
    val InputHelperTextHeight = 20.dp

    // --- Progress Indicators ---
    val CircularProgressSizeBig = 48.dp
    val CircularProgressSizeMedium = 32.dp
    val CircularProgressSizeSmall = 20.dp
    val CircularProgressSizeXSmall = 18.dp
    val CircularProgressStrokeWidthBig = 5.dp
    val CircularProgressStrokeWidth = 4.dp
    val CircularProgressStrokeWidthMedium = 3.dp
    val CircularProgressStrokeWidthSmall = 2.dp

    // ============================================================================================
    // 3. 컴포넌트 내부 여백 (Component Padding)
    // ============================================================================================

    /** 카드 등 일반 컴포넌트 내부 여백 */
    val ComponentPadding = 16.dp

    /** 버튼의 수평 내부 여백 */
    val ButtonPaddingHorizontal = 16.dp
    val ButtonPaddingLargeHorizontal = 20.dp
    val ButtonPaddingVertical = 10.dp
    val ButtonPaddingSmallVertical = 6.dp

    /** 하단 네비게이션 바 내부 여백 */
    val BottomNavHorizontalPadding = 30.dp
    val BottomNavItemVerticalPadding = 6.dp

    /** 다이얼로그 내부 여백 */
    val DialogPadding = 32.dp

    /** 검색 관련 여백 */
    val SearchSidePadding = 6.dp
    val SearchSideEmptyPadding = 0.dp

    /** 태그 관련 여백 */
    val TagItemSpacer = 4.dp
    val TagHorizontalPadding = 4.dp
    val TagCustomChipHorizontalPadding = 12.dp
    val CountTagEditHorizontalPadding = 12.dp
    val TagChipWithCountSpacer = 6.dp
    val TagChipVerticalPadding = 4.dp
    val TagChipHorizontalPadding = 8.dp

    /** 입력 필드(TextField)의 에러 메시지 여백 */
    val ErrorMessagePadding = 4.dp

    // ============================================================================================
    // 4. 모서리 둥글기 (Corner Radius)
    // ============================================================================================

    val ImageCornerRadius = 4.dp
    val ButtonCornerRadius = 8.dp
    val ComponentCornerRadius = 12.dp
    val TagCornerRadius = 16.dp
    val DialogCornerRadius = 16.dp
    val SearchBarCornerRadius = 24.dp
    val Radius2 = 2.dp
    val Radius6 = 6.dp
    val Radius20 = 20.dp
    val Radius50 = 50.dp

    // ============================================================================================
    // 5. 그림자 (Elevation)
    // ============================================================================================

    val BottomNavTonalElevation = 4.dp
    val BottomNavShadowElevation = 8.dp
    val ErrorDialogElevation = 12.dp
    val ButtonShadowElevation = 6.dp
    val ButtonDisabledShadowElevation = 2.dp
    val SearchBarShadowElevation = 2.dp

    // ============================================================================================
    // 6. 화면별 특수 치수 (Screen-specific Dimensions)
    // ============================================================================================

    /** Floating UI(FAB 등)와 하단 네비게이션 바 사이의 여백 */
    val FloatingButtonAreaPadding = 80.dp
    val FloatingButtonAreaPaddingLarge = 200.dp

    // --- AlbumScreen ---
    // 앨범 추천 패널의 최소 높이
    val ExpandedPanelMinHeight = 200.dp

    // 앨범 추천 패널의 기본 높이
    val ExpandedPanelHeight = 300.dp

    // --- StoryScreen --- 스토리 화면의 이미지 높이
    val StoryImageHeight = 480.dp
}
