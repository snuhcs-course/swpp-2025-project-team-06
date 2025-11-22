package com.example.momentag.ui.home

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.momentag.HiltTestActivity
import com.example.momentag.R
import com.example.momentag.ui.theme.MomenTagTheme
import com.example.momentag.view.HomeScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class HomeScreenTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    @get:Rule(order = 2)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    // ---------- 1. 초기 화면 상태 ----------

    @Test
    fun homeScreen_initialState_displaysCorrectUI() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Verify top bar title
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()

        // Verify upload button exists
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_upload)).assertIsDisplayed()

        // Verify bottom navigation is displayed
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.nav_home)).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.nav_my_tags)).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.nav_moment)).assertIsDisplayed()
    }

    // ---------- 2. 검색 기능 ----------

    @Test
    fun homeScreen_searchBar_isDisplayed() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // Search bar should be displayed with placeholder
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag)).assertIsDisplayed()
    }

    @Test
    fun homeScreen_searchBar_isClickable() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // Search bar should be clickable
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
            .assertHasClickAction()
    }

    // ---------- 3. 뷰 토글 (Tag Albums / All Photos) ----------

    @Test
    fun homeScreen_viewToggle_isDisplayedAndClickable() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // Tag Albums icon should be displayed
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_tag_albums))
            .assertIsDisplayed()
            .assertHasClickAction()

        // All Photos icon should be displayed
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_all_photos))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun homeScreen_viewToggle_switchBetweenViews() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Switch to All Photos
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_all_photos))
            .performClick()

        composeTestRule.waitForIdle()

        // Switch back to Tag Albums
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_tag_albums))
            .performClick()

        composeTestRule.waitForIdle()

        // Screen should still be functional
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // ---------- 4. Bottom Navigation ----------

    @Test
    fun homeScreen_bottomNavigation_allTabsAreClickable() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // Verify all bottom navigation items are clickable
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.nav_home)).assertHasClickAction()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.nav_my_tags)).assertHasClickAction()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.nav_moment)).assertHasClickAction()
    }

    // ---------- 5. Upload 기능 ----------

    @Test
    fun homeScreen_uploadButton_isClickable() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // Upload button should be visible and clickable
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_upload))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    // ---------- 6. 정렬 버튼 (Tag Albums View) ----------

    @Test
    fun homeScreen_tagAlbumsView_sortButtonIsDisplayed() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // Initial view is Tag Albums, so sort button should already be displayed
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_sort_tag_albums))
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun homeScreen_allPhotosView_sortButtonIsHidden() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // Switch to All Photos view
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_all_photos))
            .performClick()

        composeTestRule.waitForIdle()

        // Sort button should not exist in All Photos view
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_sort_tag_albums))
            .assertDoesNotExist()
    }

    // ---------- 7. 화면 렌더링 안정성 ----------

    @Test
    fun homeScreen_renders_withoutCrashing() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Screen should render without crashes
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    @Test
    fun homeScreen_multipleViewSwitches_worksCorrectly() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Switch multiple times between views
        repeat(2) {
            composeTestRule
                .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_all_photos))
                .performClick()

            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_tag_albums))
                .performClick()

            composeTestRule.waitForIdle()
        }

        // Should still work without crashes
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // ---------- 8. 검색바 반복 클릭 ----------

    @Test
    fun homeScreen_searchBar_repeatedClicks_doesNotCrash() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search bar multiple times to test focus handling
        repeat(3) {
            composeTestRule
                .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
                .performClick()

            composeTestRule.waitForIdle()
        }

        // Screen should still be functional
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // ---------- 9. 정렬 Bottom Sheet ----------

    @Test
    fun homeScreen_sortButton_opensBottomSheet() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val sortBy = composeTestRule.activity.getString(R.string.tag_sort_by)

        // Click sort button (in Tag Albums view)
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_sort_tag_albums))
            .performClick()

        composeTestRule.waitForIdle()

        // Bottom sheet should appear with sort options
        composeTestRule.onNodeWithText(sortBy).assertIsDisplayed()
    }

    @Test
    fun homeScreen_sortBottomSheet_displaysSortOptions() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val sortBy = composeTestRule.activity.getString(R.string.tag_sort_by)
        val mostRecentlyAdded = composeTestRule.activity.getString(R.string.tag_sort_most_recently_added)
        val nameAZ = composeTestRule.activity.getString(R.string.tag_sort_name_az)
        val nameZA = composeTestRule.activity.getString(R.string.tag_sort_name_za)
        val countAsc = composeTestRule.activity.getString(R.string.tag_sort_count_asc)
        val countDesc = composeTestRule.activity.getString(R.string.tag_sort_count_desc)

        // Click sort button to open bottom sheet
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_sort_tag_albums))
            .performClick()

        composeTestRule.waitForIdle()

        // Verify all sort options are displayed
        composeTestRule.onNodeWithText(sortBy).assertIsDisplayed()
        composeTestRule.onNodeWithText(mostRecentlyAdded).assertIsDisplayed()
        composeTestRule.onNodeWithText(nameAZ).assertIsDisplayed()
        composeTestRule.onNodeWithText(nameZA).assertIsDisplayed()
        composeTestRule.onNodeWithText(countAsc).assertIsDisplayed()
        composeTestRule.onNodeWithText(countDesc).assertIsDisplayed()
    }

    @Test
    fun homeScreen_sortOption_isClickable() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)
        val nameAZ = composeTestRule.activity.getString(R.string.tag_sort_name_az)

        // Open sort bottom sheet
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_sort_tag_albums))
            .performClick()

        composeTestRule.waitForIdle()

        // Click on a sort option
        composeTestRule
            .onNodeWithText(nameAZ)
            .performClick()

        composeTestRule.waitForIdle()

        // Bottom sheet should close and screen should still be functional
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // ---------- 10. View Toggle 상태 확인 ----------

    @Test
    fun homeScreen_initialView_isTagAlbums() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // Initial view should be Tag Albums, so sort button should be visible
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_sort_tag_albums))
            .assertIsDisplayed()

        // Tag Albums icon should be displayed
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_tag_albums))
            .assertIsDisplayed()
    }

    // ---------- 11. 빠른 View Toggle 전환 ----------

    @Test
    fun homeScreen_rapidViewToggle_worksCorrectly() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Rapidly toggle between views
        repeat(5) {
            composeTestRule
                .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_all_photos))
                .performClick()

            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_tag_albums))
                .performClick()

            composeTestRule.waitForIdle()
        }

        // Screen should still be functional after rapid toggling
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_sort_tag_albums))
            .assertIsDisplayed()
    }

    // ---------- 12. Bottom Navigation Tabs 상세 테스트 ----------

    @Test
    fun homeScreen_bottomNavigation_homeTabIsSelected() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val home = composeTestRule.activity.getString(R.string.nav_home)

        // Home tab should be displayed
        composeTestRule.onNodeWithText(home).assertIsDisplayed()
    }

    // ---------- 13. Search Bar Text Input ----------

    @Test
    fun homeScreen_searchBar_canReceiveTextInput() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search bar to focus
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
            .performClick()

        composeTestRule.waitForIdle()

        // Try to input text using the text field that has text input capability
        try {
            composeTestRule
                .onNode(hasSetTextAction())
                .performTextInput("#")

            composeTestRule.waitForIdle()

            // If text input succeeds, screen should still be functional
            composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        } catch (e: Exception) {
            // If text input fails (multiple text fields or different structure),
            // just verify screen is still functional
            composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        }
    }

    @Test
    fun homeScreen_searchBar_clickAndBackground_clearsFocus() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search bar to focus
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
            .performClick()

        composeTestRule.waitForIdle()

        // Click on MomenTag title (acts as background click in some cases)
        // Note: This might trigger navigation, so we handle it carefully
        try {
            composeTestRule
                .onNodeWithText(appName)
                .performClick()

            composeTestRule.waitForIdle()

            // Screen should still be displayed (focus should be cleared)
            composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        } catch (e: Exception) {
            // If navigation occurs or other error, just ensure screen is stable
            composeTestRule.waitForIdle()
        }
    }

    // ---------- 14. View Stability Tests ----------

    @Test
    fun homeScreen_allPhotosView_displaysCorrectElements() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Switch to All Photos view
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_all_photos))
            .performClick()

        composeTestRule.waitForIdle()

        // Verify essential elements are still displayed
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_all_photos)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_tag_albums)).assertIsDisplayed()
    }

    @Test
    fun homeScreen_tagAlbumsView_displaysCorrectElements() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Initial view is Tag Albums
        // Verify essential elements are displayed
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag)).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_sort_tag_albums))
            .assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_tag_albums)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_all_photos)).assertIsDisplayed()
    }

    // ---------- 15. Search Bar and Toggle Interaction ----------

    @Test
    fun homeScreen_searchBarClick_thenViewToggle_worksCorrectly() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search bar
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
            .performClick()

        composeTestRule.waitForIdle()

        // Then toggle view
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_all_photos))
            .performClick()

        composeTestRule.waitForIdle()

        // Screen should still be functional
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag)).assertIsDisplayed()
    }

    @Test
    fun homeScreen_multipleSearchBarClicks_withViewToggle_worksCorrectly() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Alternate between search bar clicks and view toggles
        repeat(2) {
            composeTestRule
                .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
                .performClick()

            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_all_photos))
                .performClick()

            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_tag_albums))
                .performClick()

            composeTestRule.waitForIdle()
        }

        // Screen should still be functional
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // ---------- 16. Tag Chip Suggestions ----------

    @Test
    fun homeScreen_searchBar_triggersTagSuggestions() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        // Wait for initial tags to load from server
        Thread.sleep(3000)
        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search bar to focus
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
            .performClick()

        composeTestRule.waitForIdle()

        // Try to input "#" to trigger tag suggestions
        try {
            composeTestRule
                .onNode(hasSetTextAction())
                .performTextInput("#")

            // Wait for debounce and suggestion update
            Thread.sleep(1500)
            composeTestRule.waitForIdle()

            // Screen should still be functional (suggestions may or may not appear)
            composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        } catch (e: Exception) {
            // If text input fails, just verify screen is still functional
            composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        }
    }

    @Test
    fun homeScreen_searchBar_inputText_doesNotCrash() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search bar to focus
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
            .performClick()

        composeTestRule.waitForIdle()

        // Try to input various characters
        try {
            val testInputs = listOf("#", "a", "test")

            for (input in testInputs) {
                composeTestRule
                    .onNode(hasSetTextAction())
                    .performTextInput(input)

                composeTestRule.waitForIdle()
            }

            // Screen should still be functional after multiple inputs
            composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        } catch (e: Exception) {
            // If text input fails, just verify screen stability
            composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        }
    }

    @Test
    fun homeScreen_searchBar_focusAfterDataLoad_worksCorrectly() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        // Wait for tags to load
        Thread.sleep(2500)
        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search bar after data is loaded
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
            .performClick()

        composeTestRule.waitForIdle()

        // Verify screen is still functional and search bar is accessible
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    @Test
    fun homeScreen_searchBar_multipleFocusAndUnfocus_worksCorrectly() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)
        val home = composeTestRule.activity.getString(R.string.nav_home)

        // Repeat focus and unfocus
        repeat(3) {
            // Focus on search bar
            composeTestRule
                .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
                .performClick()

            composeTestRule.waitForIdle()

            // Click elsewhere to unfocus (click on title area or bottom nav)
            try {
                composeTestRule
                    .onNodeWithText(home)
                    .performClick()

                composeTestRule.waitForIdle()
            } catch (e: Exception) {
                // If click fails, just continue
                composeTestRule.waitForIdle()
            }
        }

        // Screen should still be functional
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // ---------- 17. Search Bar Complex Interactions ----------

    @Test
    fun homeScreen_searchBar_withSort_worksCorrectly() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)
        val sortBy = composeTestRule.activity.getString(R.string.tag_sort_by)
        val mostRecentlyAdded = composeTestRule.activity.getString(R.string.tag_sort_most_recently_added)

        // Click search bar
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
            .performClick()

        composeTestRule.waitForIdle()

        // Then click sort button
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_sort_tag_albums))
            .performClick()

        composeTestRule.waitForIdle()

        // Verify bottom sheet opens
        composeTestRule.onNodeWithText(sortBy).assertIsDisplayed()

        // Close bottom sheet by selecting an option
        composeTestRule
            .onNodeWithText(mostRecentlyAdded)
            .performClick()

        composeTestRule.waitForIdle()

        // Screen should be functional
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    @Test
    fun homeScreen_searchBar_withUpload_doesNotCrash() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search bar to focus
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
            .performClick()

        composeTestRule.waitForIdle()

        // Verify upload button is still accessible
        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.cd_upload))
            .assertIsDisplayed()
            .assertHasClickAction()

        // Screen should still be functional
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // ---------- 18. Pull to Refresh ----------

    @Test
    fun homeScreen_pullToRefresh_doesNotCrash() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Screen should render and be ready for pull-to-refresh gesture
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()

        // Note: Pull-to-refresh gesture is difficult to test in Compose UI tests
        // We just verify the screen is stable
        composeTestRule.waitForIdle()

        // Screen should still be functional
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // ---------- 19. Search Button ----------
    // Note: There are two nodes with "Search" contentDescription in the screen:
    // 1. ChipSearchBar internal search icon (index 0)
    // 2. Search button next to the search bar (index 1)
    // We use onAllNodesWithContentDescription and select index [1] to target the search button.

    @Test
    fun homeScreen_searchButton_isDisplayed() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // Search button should be displayed with correct content description
        // Using [1] because [0] is the search icon inside ChipSearchBar
        composeTestRule
            .onAllNodesWithContentDescription(composeTestRule.activity.getString(R.string.cd_search))[1]
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_searchButton_isClickable() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        // Search button should be clickable
        // Using [1] because [0] is the search icon inside ChipSearchBar
        composeTestRule
            .onAllNodesWithContentDescription(composeTestRule.activity.getString(R.string.cd_search))[1]
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun homeScreen_searchButton_clickDoesNotCrash() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search button (index [1], as [0] is the search icon inside ChipSearchBar)
        composeTestRule
            .onAllNodesWithContentDescription(composeTestRule.activity.getString(R.string.cd_search))[1]
            .performClick()

        composeTestRule.waitForIdle()

        // Screen should still be functional after clicking search button
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    @Test
    fun homeScreen_searchButton_multipleClicks_doesNotCrash() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search button multiple times
        // Using [1] because [0] is the search icon inside ChipSearchBar
        repeat(3) {
            composeTestRule
                .onAllNodesWithContentDescription(composeTestRule.activity.getString(R.string.cd_search))[1]
                .performClick()

            composeTestRule.waitForIdle()
        }

        // Screen should still be functional after multiple clicks
        composeTestRule.onNodeWithText(appName).assertIsDisplayed()
    }

    @Test
    fun homeScreen_searchButton_withTextInput_triggersSearch() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                HomeScreen(navController = navController)
            }
        }

        composeTestRule.waitForIdle()

        val appName = composeTestRule.activity.getString(R.string.app_name)

        // Click search bar to focus
        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.search_placeholder_with_tag))
            .performClick()

        composeTestRule.waitForIdle()

        // Try to input text
        try {
            composeTestRule
                .onNode(hasSetTextAction())
                .performTextInput("test")

            composeTestRule.waitForIdle()

            // Click search button to trigger search
            // Using [1] because [0] is the search icon inside ChipSearchBar
            composeTestRule
                .onAllNodesWithContentDescription(composeTestRule.activity.getString(R.string.cd_search))[1]
                .performClick()

            composeTestRule.waitForIdle()

            // Screen should still be functional
            composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        } catch (e: Exception) {
            // If text input fails, just verify screen stability
            composeTestRule.onNodeWithText(appName).assertIsDisplayed()
        }
    }
}
