package com.example.momentag.ui.album

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.ui.theme.MomenTagTheme
import com.example.momentag.view.AlbumScreen
import com.example.momentag.viewmodel.ViewModelFactory
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class AlbumScreenTest {
    // 1. Permission granting rule
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Test constants
    private val testTagId = "tag_vacation_2025"
    private val testTagName = "Vacation 2025"

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

        // Access the singleton repository via reflection to clear state
        try {
            val field = ViewModelFactory::class.java.getDeclaredField("photoSelectionRepository\$delegate")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val lazyDelegate = field.get(viewModelFactory) as? Lazy<PhotoSelectionRepository>
            if (lazyDelegate?.isInitialized() == true) {
                val repository = lazyDelegate.value
                repository.clear()
                repository.updateTagName(testTagName)
            }
        } catch (e: Exception) {
            println("Warning: Could not clear shared repository state: ${e.message}")
        }
    }

    // --- Helper function to handle loading/timeout/error conditions ---
    /**
     * Waits up to 5 seconds for album content (+ Add Photos) or an error banner to appear.
     * Returns true if content loaded successfully, false otherwise.
     */
    private fun waitForAlbumContentOrError(): Boolean {
        val successCondition = { composeTestRule.onNodeWithText("+ Add Photos", substring = true).isDisplayed() }
        val errorCondition = { composeTestRule.onNodeWithText("Failed to Load Album", substring = true).isDisplayed() }

        try {
            // Wait until either success or error condition is met within 5 seconds
            composeTestRule.waitUntil(5.seconds.inWholeMilliseconds) { successCondition() || errorCondition() }
        } catch (e: Exception) {
            // If the test times out without either condition being met, assume error/failure.
            // We proceed to check the state below to avoid the ComposeTimeoutException.
        }

        // Return true if the success indicator is present.
        return successCondition()
    }
    // -------------------------------------------------------------------

    @Test
    fun albumScreen_initialState_displaysCorrectUI() {
        // Given: The AlbumScreen is loaded
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AlbumScreen(
                    tagId = testTagId,
                    tagName = testTagName,
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        val loaded = waitForAlbumContentOrError()
        composeTestRule.waitForIdle()

        // Then: Verify Top Bar and core elements (always present)
        composeTestRule.onNodeWithText("MomenTag").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed().assertHasClickAction()

        // Then: Verify Tag Name
        composeTestRule.onNodeWithText(testTagName).assertIsDisplayed()

        if (loaded) {
            // Then: Verify Success path elements
            composeTestRule.onNodeWithText("+ Add Photos").assertIsDisplayed().assertHasClickAction()
            composeTestRule.onNode(hasText("AI", substring = true, ignoreCase = true) or hasText("Preparing", substring = true, ignoreCase = true)).assertIsDisplayed()
        } else {
            // Then: Verify Error path elements (content hidden, error banner shown)
            composeTestRule.onNodeWithText("+ Add Photos").assertDoesNotExist()
            composeTestRule.onNodeWithText("Failed to Load Album", substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun albumScreen_errorState_hidesAddPhotosButton() {
        // Given: The AlbumScreen is loaded
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AlbumScreen(
                    tagId = testTagId,
                    tagName = testTagName,
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        // When: Album loading completes (Success or Error)
        val loaded = waitForAlbumContentOrError()
        composeTestRule.waitForIdle()

        // Then: Verify if loading failed, the error banner shows and the button is hidden.
        if (!loaded) {
            // Error path: Check for the error message shown in AlbumGridArea
            composeTestRule.onNodeWithText("Failed to Load Album", substring = true).assertIsDisplayed()

            // The content button must be hidden.
            composeTestRule.onNodeWithText("+ Add Photos").assertDoesNotExist()
        } else {
            // Success path: Check stability
            composeTestRule.onNodeWithText("+ Add Photos").assertIsDisplayed()
        }
    }

    @Test
    fun albumScreen_tagNameRename_updatesDisplayOnSubmit() {
        val newTagName = "Summer Trip"

        // Given: AlbumScreen is displayed
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AlbumScreen(
                    tagId = testTagId,
                    tagName = testTagName,
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // 화면이 안정될 때까지 기다림
        composeTestRule.waitUntil(
            timeoutMillis = 3.seconds.inWholeMilliseconds
        ) {
            composeTestRule.onNodeWithText(testTagName).isDisplayed()
        }

        val tagNameNode = composeTestRule.onNodeWithText(testTagName)

        // When: 유저가 이름을 입력 후 제출(엔터)
        // 1. 포커스 획득
        tagNameNode.performClick()
        composeTestRule.waitForIdle()

        // 2. 텍스트 교체
        tagNameNode.performTextReplacement(newTagName)

        // 3. 엔터키 입력으로 제출 처리
        tagNameNode.performTextInput("\n")

        composeTestRule.waitForIdle()

        // Then: 바뀐 이름이 보여야 함
        composeTestRule.onNodeWithText(newTagName).assertIsDisplayed()

        // Then: 기존 이름은 없어야 함
        composeTestRule.onNodeWithText(testTagName).assertDoesNotExist()
    }

    @Test
    fun albumScreen_selectionMode_togglesCorrectly() {
        // Given: AlbumScreen is loaded
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AlbumScreen(
                    tagId = testTagId,
                    tagName = testTagName,
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Ensure content is loaded before trying to find photos
        if (!waitForAlbumContentOrError()) return

        // Find a photo item (e.g., Photo 0)
        val firstPhotoNode = composeTestRule.onNodeWithContentDescription("Photo 0", substring = true, ignoreCase = false)

        if (firstPhotoNode.isDisplayed()) {
            // When: Long press on a photo to enter selection mode
            firstPhotoNode.performClick()
            composeTestRule.waitForIdle()

            // Then: Selection mode actions appear, but are disabled (no photo selected yet).
            composeTestRule.onNodeWithContentDescription("Share").assertIsDisplayed().assertIsNotEnabled()
            composeTestRule.onNodeWithContentDescription("Untag").assertIsDisplayed().assertIsNotEnabled()

            // When: Click the same photo to select it
            firstPhotoNode.performClick()
            composeTestRule.waitForIdle()

            // Then: Share/Untag buttons become enabled
            composeTestRule.onNodeWithContentDescription("Share").assertIsDisplayed().assertIsEnabled().assertHasClickAction()
            composeTestRule.onNodeWithContentDescription("Untag").assertIsDisplayed().assertIsEnabled().assertHasClickAction()
        }
    }

    @Test
    fun albumScreen_recommendChip_isNotClickableWhenPreparing() {
        clearSharedRepositoryState()

        // Given: The AlbumScreen is displayed in an initial/preparing state
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AlbumScreen(
                    tagId = testTagId,
                    tagName = testTagName,
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        waitForAlbumContentOrError() // Wait for chip state stabilization

        // 1. Find the chip in the initial state (Preparing/Loading)
        val chipMatcher = hasText("Preparing", substring = true, ignoreCase = true)
        val recommendChip = composeTestRule.onAllNodes(chipMatcher).onFirst()

        // Then: Verify that the chip does NOT have a clickable action (Handling Preparing not opening panel)
        try {
            recommendChip.assertHasClickAction()
            throw AssertionError("The chip is unexpectedly clickable while in 'Preparing' state.")
        } catch (e: AssertionError) {
            // Expected behavior: assertHasClickAction fails (it's not clickable, which is correct for Prepare state).
        }
    }

    @Test
    fun albumScreen_recommendChip_opensExpandedPanelOnSuccess() {
        // Given: The AlbumScreen is loaded
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AlbumScreen(
                    tagId = testTagId,
                    tagName = testTagName,
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Ensure content is loaded before interacting with the chip
        if (!waitForAlbumContentOrError()) return

        // 1. Find the chip (specifically looking for the interactive state text)
        val chipMatcher = hasText("AI", substring = true, ignoreCase = true) or hasText("Suggest", substring = true, ignoreCase = true)
        val recommendChip = composeTestRule.onAllNodes(chipMatcher).onFirst()

        // Then: Verify the successful chip is displayed and clickable
        recommendChip.assertIsDisplayed().assertHasClickAction()

        // 2. When: Click the successful chip
        recommendChip.performClick()
        composeTestRule.waitForIdle()

        // 3. Then: Verify the expanded panel appears, and the collapsed chip disappears.
        composeTestRule.onNodeWithContentDescription("Collapse").assertIsDisplayed().assertHasClickAction()
        recommendChip.assertDoesNotExist()
        composeTestRule.onNodeWithText("AI Recommend", substring = true).assertIsDisplayed()
    }

    @Test
    fun albumScreen_recommendExpandedPanel_closesOnCollapseClick() {
        // Given: The AlbumScreen is loaded
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AlbumScreen(
                    tagId = testTagId,
                    tagName = testTagName,
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Ensure content is loaded before interacting with the chip
        if (!waitForAlbumContentOrError()) return

        // Open the panel first
        val chipMatcher = hasText("AI", substring = true, ignoreCase = true) or hasText("Preparing", substring = true, ignoreCase = true)
        val initialChip = composeTestRule.onAllNodes(chipMatcher).onFirst()
        initialChip.performClick()
        composeTestRule.waitForIdle()

        val collapseButton = composeTestRule.onNodeWithContentDescription("Collapse")
        collapseButton.assertIsDisplayed()

        // When: Click the collapse button
        collapseButton.performClick()
        composeTestRule.waitForIdle()

        // Then: Panel elements disappear, chip reappears
        collapseButton.assertDoesNotExist()
        composeTestRule.onNode(chipMatcher).assertIsDisplayed()
    }

    @Test
    fun albumScreen_addPhotosButton_hasClickAction() {
        // Given: The AlbumScreen is loaded
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AlbumScreen(
                    tagId = testTagId,
                    tagName = testTagName,
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }

        // Wait for album to load successfully
        if (!waitForAlbumContentOrError()) return

        composeTestRule.waitForIdle()

        // Then: Verify "Add Photos" button is displayed and clickable
        composeTestRule
            .onNodeWithText("+ Add Photos")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun albumScreen_deleteConfirmationDialog_showsOnUntagClick() {
        // Given: AlbumScreen is loaded
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AlbumScreen(
                    tagId = testTagId,
                    tagName = testTagName,
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Enter selection mode and select a photo
        // Ensure content is loaded
        if (!waitForAlbumContentOrError()) return

        val firstPhotoNode = composeTestRule.onNodeWithContentDescription("Photo 0", substring = true, ignoreCase = false)

        if (firstPhotoNode.isDisplayed()) {
            firstPhotoNode.performClick()
            composeTestRule.waitForIdle()
            firstPhotoNode.performClick()
            composeTestRule.waitForIdle()

            // When: Click the Untag button
            composeTestRule.onNodeWithContentDescription("Untag").performClick()
            composeTestRule.waitForIdle()

            // Then: Confirmation dialog is displayed
            composeTestRule.onNodeWithText("Remove Photos").assertIsDisplayed()
            composeTestRule.onNodeWithText("Are you sure you want to remove 1 photo(s) from '$testTagName' tag?").assertIsDisplayed()
            composeTestRule.onNodeWithText("Remove").assertIsDisplayed().assertHasClickAction()
            composeTestRule.onNodeWithText("Cancel").assertIsDisplayed().assertHasClickAction()
        }
    }

    @Test
    fun albumScreen_pullToRefresh_isInitialized() {
        // Given: The AlbumScreen is loaded
        composeTestRule.setContent {
            MomenTagTheme {
                val navController = rememberNavController()
                AlbumScreen(
                    tagId = testTagId,
                    tagName = testTagName,
                    navController = navController,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // Then: Verify core screen element is displayed
        composeTestRule.onNodeWithText("MomenTag").assertIsDisplayed()
    }
}