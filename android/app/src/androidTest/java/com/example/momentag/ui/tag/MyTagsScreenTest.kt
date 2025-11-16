package com.example.momentag.ui.tag

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.MyTagsScreen
import com.example.momentag.model.MyTagsUiState
import com.example.momentag.model.TagCntData
import com.example.momentag.viewmodel.MyTagsViewModel
import com.example.momentag.viewmodel.TagSortOrder
import com.example.momentag.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyTagsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var vm: MyTagsViewModel

    @Before
    fun setup() {
        val factory = ViewModelFactory.getInstance(composeRule.activity)
        vm = ViewModelProvider(composeRule.activity, factory)[MyTagsViewModel::class.java]

        vm.clearDraft()
        vm.clearTagSelection()
        vm.clearActionState()

        setFlow("_uiState", MyTagsUiState.Loading)
        setFlow("_isEditMode", false)
        setFlow("_selectedTagsForBulkEdit", emptySet<String>())
        setFlow("_sortOrder", TagSortOrder.CREATED_DESC)
        setFlow("_tagActionState", com.example.momentag.viewmodel.TagActionState.Idle)

        val saveField = MyTagsViewModel::class.java.getDeclaredField("_saveState")
        saveField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (saveField.get(vm) as MutableStateFlow<MyTagsViewModel.SaveState>).value =
            MyTagsViewModel.SaveState.Idle
    }

    private fun setContent() {
        composeRule.setContent {
            MyTagsScreen(navController = rememberNavController())
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(f: String, v: T) {
        val field = MyTagsViewModel::class.java.getDeclaredField(f)
        field.isAccessible = true
        val flow = field.get(vm) as MutableStateFlow<T>
        flow.value = v
    }

    private fun hasProgressBar() =
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun waitForIdleUI() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("My Tags"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ------------------------------------------------------------
    // 1. 초기 로딩 ProgressIndicator 테스트
    // ------------------------------------------------------------
    @Test
    fun initialLoadingStateShowsProgressIndicator() {
        // 1) 초기에 refreshTags()가 실행되므로 일단 Content 표시
        setContent()

        // 2) ProgressBar가 나타날 때까지 기다림
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasProgressBar())
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNode(hasProgressBar()).assertIsDisplayed()
    }

    // ------------------------------------------------------------
    // 2. Success 상태 — 태그 목록 표시
    // ------------------------------------------------------------
    @Test
    fun successStateShowsTags() {
        // 1) 초기 refreshTags() 실행 → UI 안정화
        setContent()
        waitForIdleUI()

        // 2) 테스트용 상태 덮어쓰기
        val tags = listOf(
            TagCntData("t1", "Vacation", 10),
            TagCntData("t2", "Family", 5),
        )
        setFlow("_uiState", MyTagsUiState.Success(tags))
        setContent()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Vacation"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("My Tags").assertIsDisplayed()
        composeRule.onNodeWithText("Vacation").assertIsDisplayed()
        composeRule.onNodeWithText("Family").assertIsDisplayed()
    }

    // ------------------------------------------------------------
    // 3. Empty 상태
    // ------------------------------------------------------------
    @Test
    fun emptyStateShowsMessage() {
        setContent()
        waitForIdleUI()

        setFlow("_uiState", MyTagsUiState.Success(emptyList()))
        setContent()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Create memories"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Empty Tag").assertIsDisplayed()
        composeRule.onNodeWithText("Create memories").assertIsDisplayed()
    }

    // ------------------------------------------------------------
    // 4. Error 배너 테스트
    // ------------------------------------------------------------
    @Test
    fun errorStateShowsErrorBanner() {
        setContent()
        waitForIdleUI()

        val msg = "Network problem"
        setFlow("_uiState", MyTagsUiState.Error(msg))
        setContent()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Failed to Load Tags"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Failed to Load Tags").assertIsDisplayed()
        composeRule.onNodeWithText(msg).assertIsDisplayed()
    }

    // ------------------------------------------------------------
    // 5. Sort 버튼 동작
    // ------------------------------------------------------------
    @Test
    fun sortButtonOpensSheet() {
        setContent()
        waitForIdleUI()

        val tags = listOf(
            TagCntData("t1", "Apple", 1),
            TagCntData("t2", "Banana", 2),
        )
        setFlow("_uiState", MyTagsUiState.Success(tags))
        setContent()

        composeRule.onNodeWithContentDescription("Sort").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Sort by").assertIsDisplayed()
        composeRule.onNodeWithText("Name (A-Z)").assertIsDisplayed()
        composeRule.onNodeWithText("Name (Z-A)").assertIsDisplayed()
    }

    // ------------------------------------------------------------
    // 6. Bulk Delete 클릭 → 확인 다이얼로그
    // ------------------------------------------------------------
    @Test
    fun bulkDeleteDialogAppears() {
        setContent()
        waitForIdleUI()

        val tags = listOf(
            TagCntData("t1", "Apple", 1),
            TagCntData("t2", "Banana", 1),
        )
        setFlow("_uiState", MyTagsUiState.Success(tags))
        setFlow("_isEditMode", true)
        setFlow("_selectedTagsForBulkEdit", setOf("t1","t2"))
        setContent()

        composeRule.onNodeWithContentDescription("Delete").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Delete Tags").assertIsDisplayed()
        composeRule.onNodeWithText("Are you sure you want to delete 2 tag(s)?")
            .assertIsDisplayed()
    }

    // ------------------------------------------------------------
    // 7. SaveState.Error → Save Failed 배너 표시
    // ------------------------------------------------------------
    @Test
    fun saveStateErrorShowsBanner() {
        setContent()
        waitForIdleUI()

        setFlow("_uiState", MyTagsUiState.Success(emptyList()))

        val msg = "Save failed!!"
        val field = MyTagsViewModel::class.java.getDeclaredField("_saveState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as MutableStateFlow<MyTagsViewModel.SaveState>
        flow.value = MyTagsViewModel.SaveState.Error(msg)

        setContent()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Save failed"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Save failed").assertIsDisplayed()
        composeRule.onNodeWithText(msg).assertIsDisplayed()
    }

    // ------------------------------------------------------------
    // 8. SaveState.Loading → 전체 화면 로딩 표시
    // ------------------------------------------------------------
    @Test
    fun saveStateLoadingShowsOverlay() {
        setContent()
        waitForIdleUI()

        setFlow("_uiState", MyTagsUiState.Success(emptyList()))

        val field = MyTagsViewModel::class.java.getDeclaredField("_saveState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as MutableStateFlow<MyTagsViewModel.SaveState>
        flow.value = MyTagsViewModel.SaveState.Loading

        setContent()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasProgressBar())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ------------------------------------------------------------
    // 9. Create new tag 버튼
    // ------------------------------------------------------------
    @Test
    fun createNewTagButtonVisible() {
        setContent()
        waitForIdleUI()

        setFlow("_uiState", MyTagsUiState.Success(emptyList()))
        setContent()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("+ Create New Tag"))
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("+ Create New Tag").assertIsDisplayed()
        composeRule.onNodeWithText("+ Create New Tag").assertHasClickAction()
    }
}
