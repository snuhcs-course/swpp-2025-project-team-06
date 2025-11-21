package com.example.momentag.ui.searchresult

import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.HiltTestActivity
import com.example.momentag.R
import com.example.momentag.model.Photo
import com.example.momentag.ui.theme.MomenTagTheme
import com.example.momentag.view.SearchResultScreen
import com.example.momentag.viewmodel.SearchViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@ExperimentalTestApi
class SearchResultScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var vm: SearchViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        vm = ViewModelProvider(composeTestRule.activity)[SearchViewModel::class.java]
    }

    /**
     * reflection으로 ViewModel 내부 private MutableStateFlow 필드 값을 바꿔서
     * UI에 데이터/상태를 주입
     */
    private fun <T, VM : ViewModel> setFlow(
        viewModel: VM,
        name: String,
        value: T,
    ) {
        try {
            val field = viewModel::class.java.getDeclaredField(name)
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val flow = field.get(viewModel) as MutableStateFlow<T>
            flow.value = value
        } catch (e: NoSuchFieldException) {
            // Field might not exist in the ViewModel for all test cases.
        }
    }

    /**
     * 테스트용 가짜 사진 데이터 생성
     */
    private fun createFakePhotos(count: Int): List<Photo> =
        (1..count).map {
            Photo(
                photoId = "photo$it",
                contentUri = Uri.parse("content://fake/photo$it"),
                createdAt = "2024-01-01",
            )
        }

    private fun setContent(initialQuery: String = "") {
        composeTestRule.setContent {
            MomenTagTheme {
                SearchResultScreen(
                    initialQuery = initialQuery,
                    navController = rememberNavController(),
                    onNavigateBack = {},
                )
            }
        }
    }

    /**
     * 초기 화면 상태 테스트: TopBar와 검색창이 올바르게 표시되는지 확인
     */
    @Test
    fun searchResultScreen_initialState_displaysCorrectUI() {
        setContent()
        composeTestRule.waitForIdle()

        // Verify top bar title
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.search_result_title)).assertIsDisplayed()

        // Verify back button exists
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_navigate_back)).assertIsDisplayed()

        // Verify search bar is displayed
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag)).assertIsDisplayed()
    }

    /**
     * 뒤로가기 버튼 테스트: 뒤로가기 버튼이 표시되고 클릭 가능한지 확인
     */
    @Test
    fun searchResultScreen_backButton_isDisplayedAndClickable() {
        setContent()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_navigate_back))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    /**
     * 초기 검색어 테스트: 초기 검색어가 주어졌을 때 검색창에 올바르게 표시되는지 확인
     */
    @Test
    fun searchResultScreen_withInitialQuery_displaysQuery() {
        setContent(initialQuery = "sunset")
        composeTestRule.waitForIdle()

        // 초기 검색어가 로딩 중 플레이스홀더로 표시됨
        composeTestRule.onNodeWithText("sunset").assertIsDisplayed()
    }

    /**
     * 로딩 상태 테스트: 데이터 로딩 중에 로딩 인디케이터가 표시되는지 확인
     */
    @Test
    fun searchResultScreen_loadingState_displaysProgressIndicator() {
        // Given: 검색 상태가 로딩 중일 때
        setFlow(vm, "_searchState", SearchViewModel.SemanticSearchState.Loading)
        setContent() // initialQuery를 비워서 LaunchedEffect의 search 호출 방지

        // Then: 로딩 텍스트가 표시됨
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.loading_with_ellipsis)).assertIsDisplayed()
    }

    /**
     * 데이터 표시 테스트: 가짜 검색 결과를 주입했을 때 사진들이 올바르게 표시되는지 확인
     */
    @Test
    fun searchResultScreen_withFakeResults_displaysPhotos() {
        // Given: 검색 결과로 3개의 사진이 있는 상태
        val fakePhotos = createFakePhotos(3)
        setFlow(vm, "_searchState", SearchViewModel.SemanticSearchState.Success(fakePhotos, "test"))
        setContent() // initialQuery를 비워서 LaunchedEffect의 search 호출 방지

        // Then: 3개의 사진이 모두 화면에 표시됨
        fakePhotos.forEach { photo ->
            // 에러 메시지에 기반하여 contentDescription을 직접 구성
            val expectedContentDescription =
                composeTestRule.activity.getString(R.string.cd_photo_item, photo.photoId)
            composeTestRule.onNodeWithContentDescription(expectedContentDescription).assertIsDisplayed()
        }
    }

    /**
     * 결과 없음 테스트: 검색 결과가 없을 때 '결과 없음' 메시지가 표시되는지 확인
     */
    @Test
    fun searchResultScreen_emptyResults_displaysEmptyMessage() {
        // Given: "empty_query"에 대한 검색 결과가 없는 상태
        val query = "empty_query"
        setFlow(vm, "_searchState", SearchViewModel.SemanticSearchState.Empty(query))
        setContent() // initialQuery를 비워서 LaunchedEffect의 search 호출 방지

        // Then: '결과 없음'을 알리는 커스텀 UI가 표시됨
        val expectedText = composeTestRule.activity.getString(R.string.search_empty_state_no_results, query)
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
    }
}
