package com.example.momentag.ui.selectimage

import android.net.Uri
import androidx.activity.ComponentActivity
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
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.SelectImageScreen
import com.example.momentag.model.Photo
import com.example.momentag.model.RecommendState
import com.example.momentag.viewmodel.SelectImageViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class SelectImageScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var vm: SelectImageViewModel

    @Before
    fun setup() {
        val factory = ViewModelFactory.getInstance(composeRule.activity)
        vm = ViewModelProvider(composeRule.activity, factory)[SelectImageViewModel::class.java]

        // 모든 상태 초기화 (UI는 절대 수정 안 함)
        setFlow("_allPhotos", emptyList<Photo>())
        setFlow("_isLoading", false)
        setFlow("_isLoadingMore", false)
        setFlow("_isSelectionMode", true)
        setFlow("_recommendState", RecommendState.Idle)
        setFlow("_recommendedPhotos", emptyList<Photo>())
        setFlow("_addPhotosState", SelectImageViewModel.AddPhotosState.Idle)

        vm.clearDraft()
    }

    private fun setContent() {
        composeRule.setContent {
            SelectImageScreen(navController = rememberNavController())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(name: String, value: T) {
        val field = SelectImageViewModel::class.java.getDeclaredField(name)
        field.isAccessible = true
        val flow = field.get(vm) as MutableStateFlow<T>
        flow.value = value
    }

    private fun hasProgress(): SemanticsMatcher =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun waitForProgress() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasProgress()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ----------------------------------------------------------
    // 1. 기본 UI 요소 표시 테스트
    // ----------------------------------------------------------

    @Test
    fun showsTitleAndButton() {
        setContent()
        composeRule.onNodeWithText("Select Photos").assertIsDisplayed()
        composeRule.onNodeWithText("Add to Tag").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 2. 초기 로딩 상태 (메인 ProgressIndicator 표시)
    // ----------------------------------------------------------

    @Test
    fun initialLoadingStateShowsProgressIndicator() {
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
    fun photosDisplayedInGrid() {
        val p = listOf(
            Photo("p1", Uri.parse("content://1"), "2024-01-01"),
            Photo("p2", Uri.parse("content://2"), "2024-01-02"),
        )
        setFlow("_allPhotos", p)
        setContent()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Select Photos")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Photo p1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Photo p2").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 4. 사진 선택 → 카운터 표시
    // ----------------------------------------------------------

    @Test
    fun photoSelectionShowsCounter() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)
        setContent()

        composeRule.onNodeWithContentDescription("Photo p1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 5. 선택 해제
    // ----------------------------------------------------------

    @Test
    fun photoSelectionToggle() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)
        setContent()

        val photo = composeRule.onNodeWithContentDescription("Photo p1")

        photo.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1 selected").assertIsDisplayed()

        photo.performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasText("1 selected")).assertCountEquals(0)
    }

    // ----------------------------------------------------------
    // 6. Add to Tag 버튼 활성화 조건
    // ----------------------------------------------------------

    @Test
    fun doneButtonEnabledOnlyWhenSelected() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)
        setContent()

        val btn = composeRule.onNodeWithText("Add to Tag")
        btn.assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("Photo p1").performClick()
        composeRule.waitForIdle()

        btn.assertIsEnabled()
    }

    // ----------------------------------------------------------
    // 7. 추천 Chip - Idle
    // ----------------------------------------------------------

    @Test
    fun recommendChipIdle() {
        setFlow("_recommendState", RecommendState.Idle)
        setContent()

        composeRule.onNodeWithText("Getting ready...").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 8. 추천 Chip - Loading
    // ----------------------------------------------------------

    @Test
    fun recommendChipLoading() {
        setFlow("_recommendState", RecommendState.Loading)
        setContent()

        composeRule.onNodeWithText("Finding suggestions...").assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 9. 추천 확장 패널 + 사진 선택
    // ----------------------------------------------------------

    @Test
    fun recommendPhotosExpandable() {
        val rec = listOf(
            Photo("r1", Uri.parse("content://10"), "2024"),
            Photo("r2", Uri.parse("content://11"), "2024"),
        )

        setFlow("_recommendState", RecommendState.Success(rec))
        setFlow("_recommendedPhotos", rec)
        setContent()

        val chip = composeRule.onNode(hasText("Suggested for You") and hasClickAction())
        chip.performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Photo r1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Photo r2").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Photo r1").performClick()
        composeRule.waitForIdle()

        assertEquals(1, vm.selectedPhotos.value.size)
        assertEquals("r1", vm.selectedPhotos.value.first().photoId)
    }
}
