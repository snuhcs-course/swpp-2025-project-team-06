package com.example.momentag.ui.selectimage

import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.HiltTestActivity
import com.example.momentag.R
import com.example.momentag.model.Photo
import com.example.momentag.view.SelectImageScreen
import com.example.momentag.viewmodel.SelectImageViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hilt 환경에서 동작
 * - Hilt가 ViewModel을 생성하게 둠 (hiltRule.inject())
 * - 생성된 ViewModel 인스턴스를 가져와 reflection으로 내부 MutableStateFlow 값을 설정
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SelectImageScreenTest {
    // Hilt rule
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    // Compose rule using HiltTestActivity so Hilt VM factory is available
    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var vm: SelectImageViewModel

    // test-controlled initial state containers (실제 Flow 객체는 ViewModel 내부의 MutableStateFlow를 사용)
    @Before
    fun setup() {
        // must inject Hilt BEFORE requesting the ViewModel
        hiltRule.inject()

        // get the Hilt-created ViewModel from the activity's ViewModelProvider
        vm = ViewModelProvider(composeRule.activity)[SelectImageViewModel::class.java]

        // 모든 상태 초기화 (기본값 설정)
        setFlow("_allPhotos", emptyList<Photo>())
        setFlow("_isLoading", false)
        setFlow("_isLoadingMore", false)
        setFlow("_isSelectionMode", true)
        setFlow("_recommendState", SelectImageViewModel.RecommendState.Idle)
        setFlow("_recommendedPhotos", emptyList<Photo>())
        setFlow("_addPhotosState", SelectImageViewModel.AddPhotosState.Idle)

        // 뷰모델 초깃값 정리 메서드(있는 경우 호출)
        try {
            vm.clearDraft()
        } catch (_: Exception) {
            // 일부 구현에서는 clearDraft가 없을 수 있음.
        }
    }

    private fun setContent() {
        composeRule.setContent {
            SelectImageScreen(
                navController = rememberNavController(),
            )
        }
    }

    /**
     * reflection으로 ViewModel 내부 private MutableStateFlow 필드 값을 바꿔서
     * UI에 데이터/상태를 주입
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(
        name: String,
        value: T,
    ) {
        val field = SelectImageViewModel::class.java.getDeclaredField(name)
        field.isAccessible = true
        val flow = field.get(vm) as MutableStateFlow<T>
        flow.value = value
    }

    private fun hasProgress(): SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun waitForProgress() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasProgress()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ----------------------------------------------------------
    // 1. 기본 UI 요소 표시 테스트
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_initialState_showsTitleAndButton() {
        // 문자열 리소스 가져오기
        val selectPhotos = composeRule.activity.getString(R.string.select_image_title)
        val addToTag = composeRule.activity.getString(R.string.tag_add_to_tag)

        setContent()

        composeRule.onNodeWithText(selectPhotos).assertIsDisplayed()
        composeRule.onNodeWithText(addToTag).assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 2. 초기 로딩 상태 (메인 ProgressIndicator 표시)
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_initialLoading_showsProgressIndicator() {
        setFlow("_isLoading", true)
        setContent()

        // 로딩이 실제로 나타날 때까지 기다림
        waitForProgress()

        composeRule.onAllNodes(hasProgress()).assertCountEquals(2) // 2 indicator
    }

    // ----------------------------------------------------------
    // 3. 사진 그리드 표시
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_photos_displayedInGrid() {
        val p =
            listOf(
                Photo("p1", Uri.parse("content://1"), "2024-01-01"),
                Photo("p2", Uri.parse("content://2"), "2024-01-02"),
            )
        setFlow("_allPhotos", p)

        // 문자열 리소스 가져오기
        val selectPhotos = composeRule.activity.getString(R.string.select_image_title)
        val photoP1 = composeRule.activity.getString(R.string.cd_photo, "p1")
        val photoP2 = composeRule.activity.getString(R.string.cd_photo, "p2")

        setContent()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText(selectPhotos)).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(photoP1).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(photoP2).assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 4. 사진 선택 → 카운터 표시
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_photoSelection_showsCounter() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)

        // 문자열 리소스 가져오기
        val photoP1 = composeRule.activity.getString(R.string.cd_photo, "p1")
        val oneSelected = composeRule.activity.getString(R.string.select_image_selected_count, 1)

        setContent()

        composeRule.onNodeWithContentDescription(photoP1).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(oneSelected).assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 5. 선택 해제
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_photoSelection_toggle() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)

        // 문자열 리소스 가져오기
        val photoP1 = composeRule.activity.getString(R.string.cd_photo, "p1")
        val oneSelected = composeRule.activity.getString(R.string.select_image_selected_count, 1)

        setContent()

        val photo = composeRule.onNodeWithContentDescription(photoP1)

        photo.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(oneSelected).assertIsDisplayed()

        photo.performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasText(oneSelected)).assertCountEquals(0)
    }

    // ----------------------------------------------------------
    // 6. Add to Tag 버튼 활성화 조건
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_doneButton_enabledOnlyWhenSelected() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)

        // 문자열 리소스 가져오기
        val addToTag = composeRule.activity.getString(R.string.tag_add_to_tag)
        val photoP1 = composeRule.activity.getString(R.string.cd_photo, "p1")

        setContent()

        val btn = composeRule.onNodeWithText(addToTag)
        btn.assertIsNotEnabled()

        composeRule.onNodeWithContentDescription(photoP1).performClick()
        composeRule.waitForIdle()

        btn.assertIsEnabled()
    }

    // ----------------------------------------------------------
    // 7. 추천 Chip - Idle
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_recommendChip_idle() {
        setFlow("_recommendState", SelectImageViewModel.RecommendState.Idle)

        // 문자열 리소스 가져오기
        val gettingReady = composeRule.activity.getString(R.string.photos_getting_ready)

        setContent()

        composeRule.onNodeWithText(gettingReady, substring = true).assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 8. 추천 Chip - Loading
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_recommendChip_loading() {
        setFlow("_recommendState", SelectImageViewModel.RecommendState.Loading)

        // 문자열 리소스 가져오기
        val findingSuggestions = composeRule.activity.getString(R.string.photos_finding_suggestions)

        setContent()

        composeRule.onNodeWithText(findingSuggestions, substring = true).assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 9. 추천 확장 패널 + 사진 선택
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_recommendPhotos_expandable() {
        val rec =
            listOf(
                Photo("r1", Uri.parse("content://10"), "2024"),
                Photo("r2", Uri.parse("content://11"), "2024"),
            )

        setFlow("_recommendState", SelectImageViewModel.RecommendState.Success(rec))
        setFlow("_recommendedPhotos", rec)

        // 문자열 리소스 가져오기
        val suggestedForYou = composeRule.activity.getString(R.string.photos_suggested_for_you)
        val photoR1 = composeRule.activity.getString(R.string.cd_photo, "r1")
        val photoR2 = composeRule.activity.getString(R.string.cd_photo, "r2")

        setContent()

        val chip = composeRule.onNode(hasText(suggestedForYou) and hasClickAction())
        chip.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(photoR1).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(photoR2).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(photoR1).performClick()
        composeRule.waitForIdle()

        assertEquals(1, vm.selectedPhotos.value.size)
        assertEquals(
            "r1",
            vm.selectedPhotos.value
                .first()
                .photoId,
        )
    }
}
