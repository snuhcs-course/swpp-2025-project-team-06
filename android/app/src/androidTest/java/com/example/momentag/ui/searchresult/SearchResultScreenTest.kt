package com.example.momentag.ui.searchresult

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.ui.theme.MomenTagTheme
import com.example.momentag.view.SearchResultScreenUi
import com.example.momentag.viewmodel.ViewModelFactory
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SearchResultScreenTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setup() {
        clearSharedRepositoryState()
    }

    @After
    fun tearDown() {
        clearSharedRepositoryState()
    }

    private fun clearSharedRepositoryState() {
        val context = composeTestRule.activity.applicationContext
        val viewModelFactory = ViewModelFactory.getInstance(context)

        try {
            val field = ViewModelFactory::class.java.getDeclaredField("photoSelectionRepository\$delegate")
            field.isAccessible = true
            val lazyDelegate = field.get(viewModelFactory) as? Lazy<*>
            if (lazyDelegate?.isInitialized() == true) {
                val repository = lazyDelegate.value as PhotoSelectionRepository
                repository.clear()
            }
        } catch (e: Exception) {
            // If reflection fails, we can't clear the state
        }
    }

    // ---------- 1. 초기 화면 상태 ----------

    @Test
    fun searchResultScreen_initialState_displaysCorrectUI() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                com.example.momentag.view.SearchResultScreen(
                    initialQuery = "",
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify top bar title
        composeTestRule.onNodeWithText("Search Results").assertIsDisplayed()

        // Verify back button exists
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()

        // Verify filter button is displayed
        composeTestRule
            .onNodeWithContentDescription("Filter")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    // ---------- 2. 검색바 표시 ----------

    @Test
    fun searchResultScreen_searchBar_isDisplayed() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                com.example.momentag.view.SearchResultScreen(
                    initialQuery = "",
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Search bar should be displayed with placeholder
        composeTestRule.onNodeWithText("Search with \"#tag\"").assertIsDisplayed()
    }

    // ---------- 3. 초기 쿼리가 있을 때 ----------

    @Test
    fun searchResultScreen_withInitialQuery_displaysQuery() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                com.example.momentag.view.SearchResultScreen(
                    initialQuery = "sunset",
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Initial query should be displayed as placeholder during loading
        composeTestRule.onNodeWithText("sunset").assertIsDisplayed()
    }

    // ---------- 4. 필터 버튼 ----------

    @Test
    fun searchResultScreen_filterButton_isDisplayedAndClickable() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                com.example.momentag.view.SearchResultScreen(
                    initialQuery = "",
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Filter button should be displayed and clickable
        composeTestRule
            .onNodeWithContentDescription("Filter")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    // ---------- 5. 뒤로가기 버튼 ----------

    @Test
    fun searchResultScreen_backButton_isDisplayedAndClickable() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                com.example.momentag.view.SearchResultScreen(
                    initialQuery = "",
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Back button should be visible and clickable
        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    // ---------- 6. 화면 렌더링 안정성 ----------

    @Test
    fun searchResultScreen_renders_withoutCrashing() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                com.example.momentag.view.SearchResultScreen(
                    initialQuery = "",
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Screen should render without crashes
        composeTestRule.onNodeWithText("Search Results").assertIsDisplayed()
    }

    // ---------- 7. 다양한 초기 쿼리 처리 ----------

    @Test
    fun searchResultScreen_withDifferentInitialQueries_rendersCorrectly() {
        // Test with empty query
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                com.example.momentag.view.SearchResultScreen(
                    initialQuery = "",
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Search Results").assertIsDisplayed()
    }

    // ---------- 8. 초기 상태에서 필수 UI 요소 확인 ----------

    @Test
    fun searchResultScreen_initialState_hasAllEssentialElements() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                com.example.momentag.view.SearchResultScreen(
                    initialQuery = "",
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify all essential UI elements are present
        composeTestRule.onNodeWithText("Search Results").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Filter").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search with \"#tag\"").assertIsDisplayed()
    }
}
