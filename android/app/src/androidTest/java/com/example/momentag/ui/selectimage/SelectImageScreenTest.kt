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
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.model.Photo
import com.example.momentag.view.SelectImageScreen
import com.example.momentag.viewmodel.SelectImageViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import com.example.momentag.HiltTestActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hilt 환경에서 동작하도록 최소 변경한 버전.
 * - Hilt가 ViewModel을 생성하게 둠 (hiltRule.inject())
 * - 생성된 ViewModel 인스턴스를 가져와 reflection으로 내부 MutableStateFlow 값을 설정
 *
 * 복사 붙여넣기 후 바로 테스트 실행 가능해야 함.
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
            // 일부 구현에서는 clearDraft가 없을 수 있음. 무시.
        }
    }

    private fun setContent() {
        composeRule.setContent {
            SelectImageScreen(
                navController = rememberNavController(),
//                selectImageViewModel = vm, // 기존 스크린이 파라미터로 받을 수 있으면 그대로 주고, 아니라면 SelectImageScreen()만 써도 Hilt VM이 사용됨
            )
        }
    }

    /**
     * reflection으로 ViewModel 내부 private MutableStateFlow 필드 값을 바꿔서
     * UI에 데이터/상태를 주입한다.
     *
     * (필드명은 프로젝트의 ViewModel 내부 필드명과 정확히 맞아야 함.
     *  네가 제공한 코드 기준으로 `_allPhotos`, `_isLoading`, 등으로 가정.)
     */
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

    // ------------------ 테스트들 ------------------

    @Test
    fun selectImageScreen_initialState_showsTitleAndButton() {
        setContent()
        composeRule.onNodeWithText("Select Photos").assertIsDisplayed()
        composeRule.onNodeWithText("Add to Tag").assertIsDisplayed()
    }

    @Test
    fun selectImageScreen_initialLoading_showsProgressIndicator() {
        setFlow("_isLoading", true)
        setContent()

        waitForProgress()
        // 화면 설계에 따라 indicator 개수는 다를 수 있음. 원래 테스트는 2개였으므로 그대로 둠.
        composeRule.onAllNodes(hasProgress()).assertCountEquals(2)
    }

    @Test
    fun selectImageScreen_photos_displayedInGrid() {
        val p = listOf(
            Photo("p1", Uri.parse("content://1"), "2024-01-01"),
            Photo("p2", Uri.parse("content://2"), "2024-01-02"),
        )
        setFlow("_allPhotos", p)
        setContent()

        composeRule.waitUntil(timeoutMillis = 5000) {
            composeRule.onAllNodes(hasText("Select Photos")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Photo p1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Photo p2").assertIsDisplayed()
    }

    @Test
    fun selectImageScreen_photoSelection_showsCounter() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)
        setContent()

        composeRule.onNodeWithContentDescription("Photo p1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test
    fun selectImageScreen_photoSelection_toggle() {
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

    @Test
    fun selectImageScreen_doneButton_enabledOnlyWhenSelected() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)
        setContent()

        val btn = composeRule.onNodeWithText("Add to Tag")
        btn.assertIsNotEnabled()

        composeRule.onNodeWithContentDescription("Photo p1").performClick()
        composeRule.waitForIdle()

        btn.assertIsEnabled()
    }

    @Test
    fun selectImageScreen_recommendChip_idle() {
        setFlow("_recommendState", SelectImageViewModel.RecommendState.Idle)
        setContent()

        composeRule.onNodeWithText("Getting ready...").assertIsDisplayed()
    }

    @Test
    fun selectImageScreen_recommendChip_loading() {
        setFlow("_recommendState", SelectImageViewModel.RecommendState.Loading)
        setContent()

        composeRule.onNodeWithText("Finding suggestions...").assertIsDisplayed()
    }

    @Test
    fun selectImageScreen_recommendPhotos_expandable() {
        val rec = listOf(
            Photo("r1", Uri.parse("content://10"), "2024"),
            Photo("r2", Uri.parse("content://11"), "2024"),
        )

        setFlow("_recommendState", SelectImageViewModel.RecommendState.Success(rec))
        setFlow("_recommendedPhotos", rec)
        setContent()

        val chip = composeRule.onAllNodes(hasText("Suggested for You") and hasClickAction()).fetchSemanticsNodes().first()
        // performClick requires a node, so find it by the text + click action node:
        composeRule.onNode(hasText("Suggested for You") and hasClickAction()).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Photo r1").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Photo r2").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Photo r1").performClick()
        composeRule.waitForIdle()

        assertEquals(1, vm.selectedPhotos.value.size)
        assertEquals(
            "r1",
            vm.selectedPhotos.value.first().photoId,
        )
    }
}
