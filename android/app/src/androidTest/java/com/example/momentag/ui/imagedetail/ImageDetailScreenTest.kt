package com.example.momentag.ui.imagedetail

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.model.ImageContext
import com.example.momentag.model.ImageDetailTagState
import com.example.momentag.model.Photo
import com.example.momentag.model.Tag
import com.example.momentag.view.ImageDetailScreen
import com.example.momentag.viewmodel.ImageDetailViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageDetailScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // 테스트 대상 ViewModel
    private lateinit var vm: ImageDetailViewModel

    // 테스트에 사용할 가짜 데이터
    private val fakePhotoId = "p_test_123"
    private val fakeImageUri: Uri = Uri.parse("content://fake/uri/1")

    @Before
    fun setup() {
        val factory = ViewModelFactory.getInstance(composeRule.activity)
        // ImageDetailViewModel의 싱글턴 인스턴스를 가져옴
        vm = ViewModelProvider(composeRule.activity, factory)[ImageDetailViewModel::class.java]

        // 모든 상태 초기화
        setFlow("_imageContext", null)
        setFlow("_imageDetailTagState", ImageDetailTagState.Idle)
        setFlow("_tagDeleteState", ImageDetailViewModel.TagDeleteState.Idle)
        setFlow("_tagAddState", ImageDetailViewModel.TagAddState.Idle)
        setFlow("_photoAddress", null)
    }

    /**
     * **핵심 수정 사항**: setContent 호출을 @Test 함수 내부로 이동하여,
     * 각 테스트가 독립적인 Compose Hierarchy를 가지도록 보장합니다.
     */
    private fun setContent() {
        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }
    }

    // ViewModel의 private MutableStateFlow를 리플렉션을 통해 설정하는 유틸리티 함수
    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(
        name: String,
        value: T,
    ) {
        val field = ImageDetailViewModel::class.java.getDeclaredField(name)
        field.isAccessible = true
        val flow = field.get(vm) as MutableStateFlow<T>
        flow.value = value
    }

    // ----------------------------------------------------------
    // 1. 기본 UI 요소 표시 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_initialState_showsTopBarAndImage() {
        setContent()

        // 상단 바 제목 확인
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        // Back 버튼 확인 (BackTopBar 내에서 정의된 컨텐츠 설명)
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        // 이미지 확인 (HorizontalPager 내 ZoomableImage의 Content Description)
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 2. 태그 섹션 - 로딩 상태 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_tagSection_showsLoadingIndicators() {
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = emptyList(),
                recommendedTags = emptyList(),
                isExistingLoading = true,
                isRecommendedLoading = true,
            ),
        )

        setContent()

        // CircularProgressIndicator가 두 번 표시되는지 확인 (기존 태그 로딩 + 추천 태그 로딩)
        // SemanticsProperties.ProgressBarRangeInfo를 사용하여 ProgressIndicator를 찾음
        val hasProgress: SemanticsMatcher =
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

        // ProgressIndicator가 2개 있는지 확인
        composeRule
            .onAllNodes(hasProgress)
            .assertCountEquals(2)
    }

    // ----------------------------------------------------------
    // 3. Focus Mode 토글 테스트 (단일 탭)
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_singleTap_togglesFocusMode() {
        setContent()

        // Test Rule의 시계를 제어 모드로 변경하여 딜레이를 수동으로 처리할 수 있게 함
        composeRule.mainClock.autoAdvance = false

        // 1. 초기 상태: TopBar 표시
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()

        // 2. 이미지 클릭 (Focus Mode 진입)
        val imageNode = composeRule.onNodeWithContentDescription("Detail image")

        // 터치 입력을 실행
        imageNode.performClick()

        // ZoomableImage 내부의 200ms delay를 통과시키기 위해 시계를 수동으로 전진
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle() // UI가 변경 사항을 반영할 시간을 줍니다.

        // TopBar가 사라졌는지 확인 (Focus Mode 진입 성공)
        composeRule.onAllNodes(hasText("MomenTag")).assertCountEquals(0)

        // 3. 다시 이미지 클릭 (Focus Mode 해제)
        imageNode.performClick()

        // ZoomableImage 내부의 200ms delay를 다시 통과시킴
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()

        // TopBar가 다시 표시되었는지 확인 (Focus Mode 해제 성공)
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 6. ImageContext를 통한 Pager 탐색 및 데이터 업데이트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_imageContext_loadsMultiplePhotos() {
        val p1 = Photo("p1", Uri.parse("content://1"), "2024")
        val p2 = Photo("p2", Uri.parse("content://2"), "2024")

        val context =
            ImageContext(
                images = listOf(p1, p2),
                currentIndex = 0,
                contextType = ImageContext.ContextType.GALLERY,
            )

        setFlow("_imageContext", context)
        // [수정] setContent()를 테스트 함수 시작 부분으로 이동
        setContent()

        // 1. 첫 번째 이미지 확인 (Index 0)
        composeRule
            .onNodeWithContentDescription("Detail image")
            .assertIsDisplayed()

        // 초기 로딩 후 첫 번째 사진의 태그가 로드되는지 확인
        val existingTags = listOf(Tag(tagId = "t1", tagName = "첫번째사진태그"))
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = existingTags,
                recommendedTags = emptyList(),
                isExistingLoading = false,
                isRecommendedLoading = false,
            ),
        )
        // LaunchedEffect가 실행될 시간을 기다림
        composeRule.waitForIdle()

        composeRule.onNodeWithText("첫번째사진태그").assertIsDisplayed()
    }
}
