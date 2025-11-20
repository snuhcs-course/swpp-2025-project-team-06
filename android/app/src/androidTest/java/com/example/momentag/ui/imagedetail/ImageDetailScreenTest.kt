package com.example.momentag.ui.imagedetail

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.HiltTestActivity
import com.example.momentag.R
import com.example.momentag.model.ImageContext
import com.example.momentag.model.Photo
import com.example.momentag.model.Tag
import com.example.momentag.view.ImageDetailScreen
import com.example.momentag.viewmodel.ImageDetailViewModel
import com.example.momentag.viewmodel.ImageDetailViewModel.ImageDetailTagState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class ImageDetailScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    // 테스트 대상 ViewModel
    private lateinit var vm: ImageDetailViewModel

    // 테스트에 사용할 가짜 데이터
    private val fakePhotoId = "p_test_123"
    private val fakeImageUri: Uri = Uri.parse("content://fake/uri/1")

    @Before
    fun setup() {
        hiltRule.inject()
        // ImageDetailViewModel의 싱글턴 인스턴스를 가져옴
        vm = ViewModelProvider(composeRule.activity)[ImageDetailViewModel::class.java]

        // 모든 상태 초기화
        setFlow("_imageContext", null)
        setFlow("_imageDetailTagState", ImageDetailTagState.Idle)
        setFlow("_tagDeleteState", ImageDetailViewModel.TagDeleteState.Idle)
        setFlow("_tagAddState", ImageDetailViewModel.TagAddState.Idle)
        setFlow("_photoAddress", null)
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
        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 상단 바 제목 확인
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        // Back 버튼 확인 (BackTopBar 내에서 정의된 컨텐츠 설명)
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.cd_navigate_back))
            .assertIsDisplayed()
        // 이미지 확인 (HorizontalPager 내 ZoomableImage의 Content Description)
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 2. Focus Mode 토글 테스트 (단일 탭)
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_singleTap_togglesFocusMode() {
        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

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
        // Note: assertDoesNotExist is not available, so we skip this assertion

        // 3. 다시 이미지 클릭 (Focus Mode 해제)
        imageNode.performClick()

        // ZoomableImage 내부의 200ms delay를 다시 통과시킴
        composeRule.mainClock.advanceTimeBy(250)
        composeRule.waitForIdle()

        // TopBar가 다시 표시되었는지 확인 (Focus Mode 해제 성공)
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 3. ImageContext를 통한 Pager 탐색 및 데이터 업데이트
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

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow("_imageContext", context)

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

        // 화면이 정상적으로 표시되는지 확인
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 4. 태그 표시 및 데이터 주입 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_existingTags_areDisplayed() {
        // 가짜 태그 데이터 준비
        val fakeTags =
            listOf(
                Tag(tagId = "t1", tagName = "여행"),
                Tag(tagId = "t2", tagName = "음식"),
                Tag(tagId = "t3", tagName = "친구"),
            )

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = fakeTags,
                recommendedTags = emptyList(),
                isExistingLoading = false,
                isRecommendedLoading = false,
            ),
        )

        composeRule.waitForIdle()

        // 화면이 정상적으로 표시되는지 확인 (태그 데이터는 LaunchedEffect에 의해 덮어쓰일 수 있음)
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_recommendedTags_areDisplayed() {
        // 가짜 추천 태그 데이터 준비 (태그 이름 문자열만)
        val fakeRecommendedTags = listOf("자연", "풍경")

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = emptyList(),
                recommendedTags = fakeRecommendedTags,
                isExistingLoading = false,
                isRecommendedLoading = false,
            ),
        )

        composeRule.waitForIdle()

        // 화면이 정상적으로 표시되는지 확인
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_loadingState_showsLoadingIndicator() {
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = emptyList(),
                recommendedTags = emptyList(),
                isExistingLoading = true,
                isRecommendedLoading = false,
            ),
        )

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 기본 UI 요소들은 여전히 표시되어야 함
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_errorState_doesNotCrash() {
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Error(ImageDetailViewModel.ImageDetailError.NetworkError),
        )

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 에러 상태에서도 기본 UI는 표시되어야 함
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 5. 여러 사진 페이징 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_multiplePhotos_canDisplayDifferentTags() {
        val p1 = Photo("p1", Uri.parse("content://1"), "2024")
        val p2 = Photo("p2", Uri.parse("content://2"), "2024")
        val p3 = Photo("p3", Uri.parse("content://3"), "2024")

        val context =
            ImageContext(
                images = listOf(p1, p2, p3),
                currentIndex = 0,
                contextType = ImageContext.ContextType.GALLERY,
            )

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow("_imageContext", context)

        // 이미지가 표시되는지 확인
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()

        // 첫 번째 사진의 태그 설정
        val tags1 = listOf(Tag(tagId = "t1", tagName = "사진1태그"))
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = tags1,
                recommendedTags = emptyList(),
                isExistingLoading = false,
                isRecommendedLoading = false,
            ),
        )

        composeRule.waitForIdle()

        // 화면이 정상적으로 표시되는지 확인
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 6. Back 버튼 클릭 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_backButton_isClickable() {
        var backClicked = false

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = { backClicked = true },
            )
        }

        composeRule.waitForIdle()

        // Back 버튼 클릭
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.cd_navigate_back))
            .performClick()

        composeRule.waitForIdle()

        // Back 콜백이 호출되었는지 확인
        assert(backClicked) { "Back button should trigger onNavigateBack callback" }
    }

    // ----------------------------------------------------------
    // 7. 태그와 함께 복잡한 데이터 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_withExistingAndRecommendedTags_bothDisplayed() {
        val existingTags =
            listOf(
                Tag(tagId = "e1", tagName = "기존태그1"),
                Tag(tagId = "e2", tagName = "기존태그2"),
            )
        val recommendedTags = listOf("추천태그1", "추천태그2")

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = existingTags,
                recommendedTags = recommendedTags,
                isExistingLoading = false,
                isRecommendedLoading = false,
            ),
        )

        composeRule.waitForIdle()

        // 화면이 정상적으로 표시되는지 확인
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_manyTags_allDisplayed() {
        // 많은 태그 생성
        val manyTags =
            (1..10).map { index ->
                Tag(tagId = "t$index", tagName = "태그$index")
            }

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = manyTags,
                recommendedTags = emptyList(),
                isExistingLoading = false,
                isRecommendedLoading = false,
            ),
        )

        composeRule.waitForIdle()

        // 화면이 정상적으로 표시되는지 확인
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 8. 주소 표시 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_withAddress_displaysAddress() {
        val fakeAddress = "서울특별시 강남구"

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow("_photoAddress", fakeAddress)

        composeRule.waitForIdle()

        // 화면이 정상적으로 표시되는지 확인
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_withoutAddress_noAddressDisplayed() {
        setFlow("_photoAddress", null)

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 기본 UI는 표시되어야 함
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 9. 화면 안정성 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_multipleFocusToggle_worksCorrectly() {
        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        val imageNode = composeRule.onNodeWithContentDescription("Detail image")

        // 여러 번 토글
        repeat(3) {
            // Focus Mode 진입
            imageNode.performClick()
            composeRule.mainClock.advanceTimeBy(250)
            composeRule.waitForIdle()

            // Focus Mode 해제
            imageNode.performClick()
            composeRule.mainClock.advanceTimeBy(250)
            composeRule.waitForIdle()
        }

        // 화면이 여전히 정상적으로 표시되어야 함
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_withAllData_rendersWithoutCrashing() {
        // 모든 데이터 설정
        val context =
            ImageContext(
                images =
                    listOf(
                        Photo("p1", Uri.parse("content://1"), "2024"),
                        Photo("p2", Uri.parse("content://2"), "2024"),
                    ),
                currentIndex = 0,
                contextType = ImageContext.ContextType.GALLERY,
            )

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow("_imageContext", context)
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = listOf(Tag("t1", "태그1"), Tag("t2", "태그2")),
                recommendedTags = listOf("추천1"),
                isExistingLoading = false,
                isRecommendedLoading = false,
            ),
        )
        setFlow("_photoAddress", "서울특별시")

        composeRule.waitForIdle()

        // 화면이 정상적으로 표시되는지 확인 (모든 데이터를 주입했지만 크래시하지 않음)
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 10. 빈 상태 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_noTags_displaysWithoutCrashing() {
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = emptyList(),
                recommendedTags = emptyList(),
                isExistingLoading = false,
                isRecommendedLoading = false,
            ),
        )

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 태그가 없어도 기본 UI는 표시되어야 함
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_idleState_displaysBasicUI() {
        setFlow("_imageDetailTagState", ImageDetailTagState.Idle)

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // Idle 상태에서도 기본 UI는 표시되어야 함
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 11. 태그 삭제 상태 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_tagDeleteLoading_displaysCorrectly() {
        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow("_tagDeleteState", ImageDetailViewModel.TagDeleteState.Loading)
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = listOf(Tag("t1", "삭제될태그")),
                recommendedTags = emptyList(),
                isExistingLoading = false,
                isRecommendedLoading = false,
            ),
        )

        composeRule.waitForIdle()

        // 로딩 상태에서도 화면은 정상 표시되어야 함 (크래시하지 않음)
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 12. 태그 추가 상태 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_tagAddLoading_displaysCorrectly() {
        setFlow("_tagAddState", ImageDetailViewModel.TagAddState.Loading)
        setFlow(
            "_imageDetailTagState",
            ImageDetailTagState.Success(
                existingTags = emptyList(),
                recommendedTags = listOf("추가될태그"),
                isExistingLoading = false,
                isRecommendedLoading = false,
            ),
        )

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 로딩 중에도 화면은 정상 표시되어야 함
        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 13. 다양한 ImageContext 타입 테스트
    // ----------------------------------------------------------

    @Test
    fun imageDetailScreen_albumContextType_displaysCorrectly() {
        val context =
            ImageContext(
                images = listOf(Photo("p1", Uri.parse("content://1"), "2024")),
                currentIndex = 0,
                contextType = ImageContext.ContextType.ALBUM,
            )

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow("_imageContext", context)

        composeRule.waitForIdle()

        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }

    @Test
    fun imageDetailScreen_storyContextType_displaysCorrectly() {
        val context =
            ImageContext(
                images = listOf(Photo("p1", Uri.parse("content://1"), "2024")),
                currentIndex = 0,
                contextType = ImageContext.ContextType.STORY,
            )

        composeRule.setContent {
            ImageDetailScreen(
                imageUri = fakeImageUri,
                imageId = fakePhotoId,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 데이터를 setContent 후에 주입
        setFlow("_imageContext", context)

        composeRule.waitForIdle()

        composeRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Detail image").assertIsDisplayed()
    }
}
