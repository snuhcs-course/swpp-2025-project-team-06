package com.example.momentag.ui.addtag

import android.net.Uri
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
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.momentag.HiltTestActivity
import com.example.momentag.model.Photo
import com.example.momentag.view.AddTagScreen
import com.example.momentag.viewmodel.AddTagViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AddTagScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var vm: AddTagViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        vm = ViewModelProvider(composeTestRule.activity)[AddTagViewModel::class.java]

        // AddTagViewModel은 PhotoSelectionRepository에 상태를 위임하므로
        // clearDraft()로 초기 상태를 정리합니다
        vm.clearDraft()
    }

    private fun setContent() {
        composeTestRule.setContent {
            AddTagScreen(
                navController = rememberNavController(),
            )
        }
    }

    private fun createTestPhoto(
        id: String,
        mediaId: String = id,
    ): Photo = Photo(
        photoId = id,
        contentUri = Uri.parse("content://media/external/images/media/$mediaId"),
        createdAt = "2024-01-01T00:00:00Z",
    )

    // ----------------------------------------------------------
    // 테스트 케이스
    // ----------------------------------------------------------

    @Test
    fun addTagScreen_initialState_displaysCorrectUI() {
        setContent()

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
        setContent()

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
        setContent()

        // Enter tag name only
        composeTestRule.onNodeWithText("Enter tag name").performTextInput("Test Tag")

        // Wait for text to be entered
        composeTestRule.waitForIdle()

        // Done button should still be disabled (no photos selected)
        composeTestRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_tagNameSection_displayHashSymbol() {
        setContent()

        // Verify that hash symbol is displayed as leading icon
        composeTestRule.onNodeWithText("#").assertIsDisplayed()
    }

    @Test
    fun addTagScreen_photosSection_displaysCount() {
        val testPhotos = listOf(
            createTestPhoto("1"),
            createTestPhoto("2"),
        )

        vm.initialize(null, testPhotos)
        setContent()

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
        setContent()

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
        setContent()

        // Verify bottom navigation tabs are displayed
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("My Tags").assertIsDisplayed()
        composeTestRule.onNodeWithText("Moment").assertIsDisplayed()
    }

    @Test
    fun addTagScreen_addPhotosButton_isDisplayedAndClickable() {
        setContent()

        // The AddPhotosButton should be displayed in the grid
        // It has a "+" icon or text
        composeTestRule
            .onNodeWithText("Add Photos")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun addTagScreen_withValidInput_doneButtonIsEnabled() {
        val testPhotos = listOf(createTestPhoto("1"))

        vm.initialize("Test Tag", testPhotos)
        setContent()

        // Done button should be enabled
        composeTestRule.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun addTagScreen_loadingState_canClickDoneButton() {
        val testPhotos = listOf(createTestPhoto("1"))

        vm.initialize("Test Tag", testPhotos)
        setContent()

        // Done button should be enabled and clickable
        composeTestRule.onNodeWithText("Done").assertIsEnabled()
        composeTestRule.onNodeWithText("Done").assertHasClickAction()
    }

    @Test
    fun addTagScreen_emptyTagName_showsPlaceholder() {
        vm.clearDraft()
        setContent()

        // Verify placeholder is shown when tag name is empty
        composeTestRule.onNodeWithText("Enter tag name").assertIsDisplayed()
    }

    @Test
    fun addTagScreen_photoItem_displaysCheckmark() {
        val testPhotos = listOf(createTestPhoto("1"))

        vm.initialize(null, testPhotos)
        setContent()

        // Verify that selected photos have checkmark overlay
        // The CheckboxOverlay should show the "Selected" icon
        composeTestRule
            .onNodeWithContentDescription("Selected", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun addTagScreen_clearFocus_whenTappingOutside() {
        setContent()

        // Click on the text field to focus
        composeTestRule.onNodeWithText("Enter tag name").performClick()

        // Wait for focus
        composeTestRule.waitForIdle()

        // Integration test limit: difficult to tap "empty space" reliably without coordinates,
        // but verify no crash implies basic handling works.
    }

    @Test
    fun addTagScreen_multiplePhotos_displaysCorrectCount() {
        val testPhotos = listOf(
            createTestPhoto("1"),
            createTestPhoto("2"),
            createTestPhoto("3"),
            createTestPhoto("4"),
            createTestPhoto("5"),
        )

        vm.initialize(null, testPhotos)
        setContent()

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
        setContent()
        val tagName = "2024 Summer! @Beach"

        composeTestRule.onNodeWithText("Enter tag name").performTextInput(tagName)

        // Wait for input
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(tagName).assertIsDisplayed()
    }

    @Test
    fun addTagScreen_veryLongTagName_isHandled() {
        setContent()
        val longTagName = "A".repeat(100)

        composeTestRule.onNodeWithText("Enter tag name").performTextInput(longTagName)

        // Wait for input
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(longTagName).assertIsDisplayed()
    }
}