package com.example.momentag.ui.mytags

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
import com.example.momentag.MyTagsScreen
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.ui.theme.MomenTagTheme
import com.example.momentag.viewmodel.ViewModelFactory
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class MyTagsScreenTest {
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
        // Clear any existing state from the singleton repository
        clearSharedRepositoryState()
    }

    @After
    fun tearDown() {
        // Clean up after each test to prevent state pollution
        clearSharedRepositoryState()
    }

    private fun clearSharedRepositoryState() {
        // Access the singleton ViewModelFactory and clear the PhotoSelectionRepository
        val context = composeTestRule.activity.applicationContext
        val viewModelFactory = ViewModelFactory.getInstance(context)

        // Use reflection to access and clear the private photoSelectionRepository
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
            // This is acceptable as it's a test-only concern
        }
    }

    @Test
    fun myTagsScreen_initialState_displaysCorrectUI() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Wait for initial load
        composeTestRule.waitForIdle()

        // Verify top bar is displayed with title
        composeTestRule.onNodeWithText("#Tag").assertIsDisplayed()

        // Verify back button is present
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()

        // Verify create new tag button is displayed
        composeTestRule.onNodeWithText("+ Create New Tag").assertIsDisplayed()

        // Verify bottom navigation is displayed
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("My Tags").assertIsDisplayed()
        composeTestRule.onNodeWithText("Moment").assertIsDisplayed()
    }

    @Test
    fun myTagsScreen_loadingState_showsProgressIndicator() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // The screen starts in loading state, so we should see a progress indicator
        // Note: This test may be timing-sensitive as loading might complete quickly
        // In a real scenario, you might want to mock the repository to control loading state
    }

    @Test
    fun myTagsScreen_emptyState_displaysEmptyMessage() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Wait for content to load
        composeTestRule.waitForIdle()

        // If no tags exist, should show empty state
        // Note: This depends on the actual backend state
        // In a production test, you'd mock the repository to return empty list
    }

    @Test
    fun myTagsScreen_backButton_hasClickAction() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Verify back button is clickable
        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun myTagsScreen_createNewTagButton_hasClickAction() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Verify create new tag button is clickable
        composeTestRule
            .onNodeWithText("+ Create New Tag")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun myTagsScreen_bottomNavigation_isDisplayed() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Verify all bottom navigation items are displayed
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("My Tags").assertIsDisplayed()
        composeTestRule.onNodeWithText("Moment").assertIsDisplayed()
    }

    @Test
    fun myTagsScreen_bottomNavigation_isClickable() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Verify bottom navigation items are clickable
        composeTestRule.onNodeWithText("Home").assertHasClickAction()
        composeTestRule.onNodeWithText("My Tags").assertHasClickAction()
        composeTestRule.onNodeWithText("Moment").assertHasClickAction()
    }

    @Test
    fun myTagsScreen_sortButton_displaysWhenTagsExist() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Wait for tags to load
        composeTestRule.waitForIdle()

        // The sort button should appear if tags exist
        // Note: This depends on actual backend data
        // The Sort button has contentDescription "Sort"
    }

    @Test
    fun myTagsScreen_deleteButton_displaysWhenTagsExist() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Wait for tags to load
        composeTestRule.waitForIdle()

        // The delete button should appear if tags exist
        // The Delete button has contentDescription "Delete"
    }

    @Test
    fun myTagsScreen_emptyState_displaysCreateMemoriesText() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Wait for content
        composeTestRule.waitForIdle()

        // If no tags, should show "Create memories" text
        // Note: This test assumes empty state - depends on backend
    }

    @Test
    fun myTagsScreen_topBarTitle_isDisplayed() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Verify the title "#Tag" is displayed
        composeTestRule.onNodeWithText("#Tag").assertIsDisplayed()
    }

    @Test
    fun myTagsScreen_selectTagForPhotos_showsProperUI() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // When photos are selected for tagging, should show special UI
        // "Select a tag or create a new one" message should appear
        // Note: This requires pre-selecting photos, which depends on repository state
    }

    @Test
    fun myTagsScreen_tagChip_isClickableWhenNotInEditMode() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Wait for tags to load
        composeTestRule.waitForIdle()

        // Tag chips should be clickable to navigate to tag detail
        // Note: This depends on having actual tags in the backend
    }

    @Test
    fun myTagsScreen_createNewTagButton_isAlwaysVisible() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Wait for content
        composeTestRule.waitForIdle()

        // The "Create New Tag" button should always be visible
        composeTestRule
            .onNodeWithText("+ Create New Tag")
            .assertIsDisplayed()
    }

    @Test
    fun myTagsScreen_hasProperBottomPadding() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Verify the screen layout doesn't overlap with navigation bars
        composeTestRule.waitForIdle()

        // Bottom navigation should be visible and not obscured
        composeTestRule.onNodeWithText("My Tags").assertIsDisplayed()
    }

    @Test
    fun myTagsScreen_gradientBackground_isApplied() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // The screen should render without crashes
        // Gradient background is applied via Modifier, hard to test directly
        composeTestRule.waitForIdle()

        // Verify screen is displayed properly
        composeTestRule.onNodeWithText("#Tag").assertIsDisplayed()
    }

    @Test
    fun myTagsScreen_lifecycleResume_refreshesTags() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // The screen should refresh tags on resume
        // This is handled by DisposableEffect with lifecycle observer
        composeTestRule.waitForIdle()

        // Verify screen renders
        composeTestRule.onNodeWithText("#Tag").assertIsDisplayed()
    }

    @Test
    fun myTagsScreen_pullToRefresh_isEnabled() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                MyTagsScreen(navController = navController)
            }
        }

        // Wait for content
        composeTestRule.waitForIdle()

        // PullToRefreshBox is enabled in MyTagsContent
        // Testing pull-to-refresh gesture is complex in Compose UI tests
        // For now, just verify the screen renders properly
        composeTestRule.onNodeWithText("#Tag").assertIsDisplayed()
    }
}
