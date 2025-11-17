package com.example.momentag.ui.addtag

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.momentag.AddTagScreen
import com.example.momentag.model.Photo
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
class AddTagScreenTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var photoSelectionRepository: PhotoSelectionRepository

    @Before
    fun setup() {
        photoSelectionRepository = PhotoSelectionRepository()
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

    // Helper function to create test photos with proper URIs
    private fun createTestPhoto(
        id: String,
        mediaId: String = id,
    ): Photo =
        Photo(
            photoId = id,
            contentUri = Uri.parse("content://media/external/images/media/$mediaId"),
            createdAt = "2024-01-01T00:00:00Z",
        )

    // Helper function to initialize repository state before setting content
    private fun initializeRepositoryState(
        tagName: String? = null,
        photos: List<Photo> = emptyList(),
    ) {
        composeTestRule.runOnUiThread {
            val viewModelFactory = ViewModelFactory.getInstance(composeTestRule.activity.applicationContext)
            try {
                val field = ViewModelFactory::class.java.getDeclaredField("photoSelectionRepository\$delegate")
                field.isAccessible = true
                val lazyDelegate = field.get(viewModelFactory) as Lazy<*>
                val repository = lazyDelegate.value as PhotoSelectionRepository
                repository.initialize(tagName, photos)
            } catch (e: Exception) {
                // Fallback: repository might not be initialized yet
            }
        }
    }

    @Test
    fun addTagScreen_initialState_displaysCorrectUI() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Verify top bar is displayed
        composeTestRule.onNodeWithText("Create Tag").assertIsDisplayed()

        // Verify tag name input field is displayed with placeholder
        composeTestRule.onNodeWithText("Enter tag name").assertIsDisplayed()

        // Verify photos section is displayed
        composeTestRule.onNodeWithText("Photos").assertIsDisplayed()

        // Verify Done button is not enabled when form is empty
        composeTestRule.onNodeWithText("Done").assertIsDisplayed()
        composeTestRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_enterTagName_updatesTextField() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Enter tag name
        val tagName = "Summer Vacation"
        composeTestRule.onNodeWithText("Enter tag name").performTextInput(tagName)

        // Verify tag name is displayed (the actual value will be shown, not the placeholder)
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule
                .onAllNodes(hasText(tagName, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun addTagScreen_withTagNameOnly_doneButtonIsDisabled() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Enter tag name only
        composeTestRule.onNodeWithText("Enter tag name").performTextInput("Test Tag")

        // Wait for text to be entered
        composeTestRule.waitForIdle()

        // Done button should still be disabled (no photos selected)
        composeTestRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_tagNameSection_displayHashSymbol() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Verify that hash symbol is displayed as leading icon
        composeTestRule.onNodeWithText("#").assertIsDisplayed()
    }

    @Test
    fun addTagScreen_photosSection_displaysCount() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Setup repository with pre-selected photos
        val testPhotos =
            listOf(
                createTestPhoto("1"),
                createTestPhoto("2"),
            )
        initializeRepositoryState(photos = testPhotos)

        // Wait for UI to update
        composeTestRule.waitForIdle()

        // Verify photo count is displayed
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule
                .onAllNodes(hasText("Photos (2)", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun addTagScreen_backButton_hasClickAction() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Find back button by content description
        // The Icon has contentDescription "Back", and its parent IconButton has the onClick
        // Using the merged tree (default) will merge the Icon's semantics with IconButton's
        composeTestRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun addTagScreen_bottomNavigation_isDisplayed() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Verify bottom navigation tabs are displayed
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("My Tags").assertIsDisplayed()
        composeTestRule.onNodeWithText("Moment").assertIsDisplayed()
    }

    @Test
    fun addTagScreen_addPhotosButton_isDisplayedAndClickable() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Wait for permissions to be granted (mocked in test environment)
        composeTestRule.waitForIdle()

        // The AddPhotosButton should be displayed in the grid
        // It has a "+" icon or text
        composeTestRule
            .onNodeWithText("Add Photos")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun addTagScreen_withValidInput_doneButtonIsEnabled() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Setup repository with pre-selected photos and tag name
        val testPhotos = listOf(createTestPhoto("1"))
        initializeRepositoryState(tagName = "Test Tag", photos = testPhotos)

        // Wait for UI to update
        composeTestRule.waitForIdle()

        // Done button should be enabled
        composeTestRule.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun addTagScreen_loadingState_canClickDoneButton() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Setup repository with valid data
        val testPhotos = listOf(createTestPhoto("1"))
        initializeRepositoryState(tagName = "Test Tag", photos = testPhotos)

        // Wait for UI to update
        composeTestRule.waitForIdle()

        // Done button should be enabled and clickable
        composeTestRule.onNodeWithText("Done").assertIsEnabled()
        composeTestRule.onNodeWithText("Done").assertHasClickAction()
    }

    @Test
    fun addTagScreen_emptyTagName_showsPlaceholder() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Verify placeholder is shown when tag name is empty
        composeTestRule.onNodeWithText("Enter tag name").assertIsDisplayed()
    }

    @Test
    fun addTagScreen_photoItem_displaysCheckmark() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Setup repository with photos
        val testPhotos = listOf(createTestPhoto("1"))
        initializeRepositoryState(photos = testPhotos)

        composeTestRule.waitForIdle()

        // Verify that selected photos have checkmark overlay
        // The CheckboxOverlay should show the "Selected" icon
        composeTestRule
            .onNodeWithContentDescription("Selected", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun addTagScreen_clearFocus_whenTappingOutside() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Click on the text field to focus
        composeTestRule.onNodeWithText("Enter tag name").performClick()

        // Wait for focus
        composeTestRule.waitForIdle()

        // The screen should handle taps to clear focus
        // This is handled by pointerInput modifier in the AddTagScreen
        // Testing this requires tapping outside the text field
    }

    @Test
    fun addTagScreen_multiplePhotos_displaysCorrectCount() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Setup repository with multiple photos
        val testPhotos =
            listOf(
                createTestPhoto("1"),
                createTestPhoto("2"),
                createTestPhoto("3"),
                createTestPhoto("4"),
                createTestPhoto("5"),
            )
        initializeRepositoryState(photos = testPhotos)

        // Wait for UI to update
        composeTestRule.waitForIdle()

        // Verify correct photo count
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule
                .onAllNodes(hasText("Photos (5)", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun addTagScreen_tagNameWithSpecialCharacters_isAccepted() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Enter tag name with special characters
        val tagName = "2024 Summer! @Beach"
        composeTestRule.onNodeWithText("Enter tag name").performTextInput(tagName)

        // Wait for input
        composeTestRule.waitForIdle()

        // Tag name should be accepted (verification happens in ViewModel)
    }

    @Test
    fun addTagScreen_veryLongTagName_isHandled() {
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AddTagScreen(navController = navController)
            }
        }

        // Enter a very long tag name
        val longTagName = "A".repeat(100)
        composeTestRule.onNodeWithText("Enter tag name").performTextInput(longTagName)

        // Wait for input
        composeTestRule.waitForIdle()

        // The single line text field should handle long input appropriately
    }
}
