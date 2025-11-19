package com.example.momentag.ui.theme

import androidx.compose.ui.unit.dp

object Dimen {
    // 1. 화면 레벨 (Screen Level)
    val ScreenHorizontalPadding = 16.dp // 콘텐츠 중심 화면의 좌우 여백
    val FormScreenHorizontalPadding = 24.dp // 입력 폼 중심 화면의 좌우 여백

    // 2. 간격 (Spacers)
    val SectionSpacing = 24.dp // Section 수직 간격
    val ItemSpacingLarge = 16.dp // 아이템 간의 넓은 간격
    val ItemSpacingMedium = 12.dp // 아이템 간의 중간 간격
    val ItemSpacingSmall = 8.dp // 아이템 간의 좁은 간격
    val SpacingXXSmall = 2.dp // 좁은 간격

    // 3. 컴포넌트 내부 (Inner Component)
    val ComponentPadding = 16.dp // 카드 등 일반 컴포넌트 내부 여백
    val ButtonPaddingVertical = 10.dp // 버튼의 수직 내부 여백
    val ButtonPaddingSmallVertical = 6.dp
    val ButtonPaddingHorizontal = 16.dp // 버튼의 수평 내부 여백
    val ButtonPaddingLargeHorizontal = 20.dp // 넓은 버튼의 수평 내부 여백

    // 4. 그리드 (Grid)
    val GridItemSpacing = 4.dp // 사진 그리드 아이템 사이의 간격
    val AlbumGridItemSpacing = 12.dp // 앨범 그리드 아이템 사이의 간격

    // 5. 특정한 상황 별 패딩&Size ( 에러, Floating, 바텀 Nav, 로딩 )
    val ErrorMessagePadding = 4.dp // 입력 Field 에러 메시지 여백
    val FloatingButtonAreaPadding = 80.dp // Floating UI 하단 여백
    val FloatingButtonAreaPaddingLarge = 200.dp // Floating UI 넓은 하단 여백

    val BottomNavBarHeight = 56.dp
    val BottomNavHorizontalPadding = 30.dp
    val BottomNavItemVerticalPadding = 6.dp

    val CircularProgressSizeSmall = 20.dp
    val CircularProgressSizeBig = 48.dp
    val CircularProgressStrokeWidthSmall = 2.dp
    val CircularProgressStrokeWidth = 4.dp

    val IconButtonsSizeXSmall = 16.dp
    val IconButtonSizeSmall = 24.dp
    val IconButtonSizeMedium = 32.dp
    val IconButtonSizeLarge = 40.dp

    val DialogPadding = 32.dp
    val SearchBarMinHeight = 48.dp
    val TagHeight = 32.dp

    val SearchSidePadding = 6.dp
    val SearchSideEmptyPadding = 0.dp
    val MinSearchBarMinHeight = 10.dp

    // 6. 모서리 둥글기 (Corner Radius)
    val ImageCornerRadius = 4.dp
    val ButtonCornerRadius = 8.dp
    val ComponentCornerRadius = 12.dp
    val TagCornerRadius = 16.dp
    val DialogCornerRadius = 16.dp
    val SearchBarCornerRadius = 24.dp

    // 7. 그림자 (Elevation)
    val BottomNavTonalElevation = 4.dp
    val BottomNavShadowElevation = 8.dp
    val ErrorDialogElevation = 12.dp

    // 8. 태그 (Tag)
    val TagItemSpacer = 4.dp
    val TagHorizontalPadding = 4.dp
    val TagCustomChipHorizontalPadding = 12.dp
    val CountTagEditHorizontalPadding = 12.dp
    val TagChipWithCountSpacer = 6.dp
    val TagMaxTextWidth = 180.dp
    val TagChipVerticalPadding = 4.dp
    val TagChipHorizontalPadding = 8.dp
    val StoryTagChipBadgeSize = 18.dp
    val CustomTagChipTextFieldWidth = 80.dp
}
