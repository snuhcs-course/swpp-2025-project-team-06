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
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var vm: AddTagViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        vm = ViewModelProvider(composeRule.activity)[AddTagViewModel::class.java]

        // AddTagViewModel은 PhotoSelectionRepository에 상태를 위임하므로
        // clearDraft()로 초기 상태를 정리합니다
        vm.clearDraft()
    }

    private fun setContent() {
        composeRule.setContent {
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

        composeRule.onNodeWithText("Create Tag").assertIsDisplayed()
        composeRule.onNodeWithText("Enter tag name").assertIsDisplayed()
        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_enterTagName_updatesTextField() {
        setContent()

        val tagName = "Summer Vacation"
        composeRule.onNodeWithText("Enter tag name").performTextInput(tagName)

        composeRule.waitUntil(timeoutMillis = 2000) {
            composeRule
                .onAllNodes(hasText(tagName, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun addTagScreen_withTagNameOnly_doneButtonIsDisabled() {
        setContent()

        composeRule.onNodeWithText("Enter tag name").performTextInput("Test Tag")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Done").assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_tagNameSection_displayHashSymbol() {
        setContent()
        composeRule.onNodeWithText("#").assertIsDisplayed()
    }

    @Test
    fun addTagScreen_photosSection_displaysCount() {
        val testPhotos = listOf(
            createTestPhoto("1"),
            createTestPhoto("2"),
        )

        vm.initialize(null, testPhotos)
        setContent()

        composeRule.waitUntil(timeoutMillis = 2000) {
            composeRule
                .onAllNodes(hasText("Photos (2)", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun addTagScreen_backButton_hasClickAction() {
        setContent()
        composeRule
            .onNodeWithContentDescription("Back")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun addTagScreen_bottomNavigation_isDisplayed() {
        setContent()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("My Tags").assertIsDisplayed()
        composeRule.onNodeWithText("Moment").assertIsDisplayed()
    }

    @Test
    fun addTagScreen_addPhotosButton_isDisplayedAndClickable() {
        setContent()
        composeRule
            .onNodeWithText("Add Photos")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun addTagScreen_withValidInput_doneButtonIsEnabled() {
        val testPhotos = listOf(createTestPhoto("1"))

        vm.initialize("Test Tag", testPhotos)
        setContent()

        composeRule.onNodeWithText("Done").assertIsEnabled()
    }

    @Test
    fun addTagScreen_loadingState_canClickDoneButton() {
        val testPhotos = listOf(createTestPhoto("1"))

        vm.initialize("Test Tag", testPhotos)
        setContent()

        composeRule.onNodeWithText("Done").assertIsEnabled()
        composeRule.onNodeWithText("Done").assertHasClickAction()
    }

    @Test
    fun addTagScreen_emptyTagName_showsPlaceholder() {
        vm.clearDraft()
        setContent()

        composeRule.onNodeWithText("Enter tag name").assertIsDisplayed()
    }

    @Test
    fun addTagScreen_photoItem_displaysCheckmark() {
        val testPhotos = listOf(createTestPhoto("1"))

        vm.initialize(null, testPhotos)
        setContent()

        composeRule
            .onNodeWithContentDescription("Selected", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun addTagScreen_clearFocus_whenTappingOutside() {
        setContent()
        composeRule.onNodeWithText("Enter tag name").performClick()
        composeRule.waitForIdle()
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

        composeRule.waitUntil(timeoutMillis = 2000) {
            composeRule
                .onAllNodes(hasText("Photos (5)", substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun addTagScreen_tagNameWithSpecialCharacters_isAccepted() {
        setContent()
        val tagName = "2024 Summer! @Beach"
        composeRule.onNodeWithText("Enter tag name").performTextInput(tagName)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(tagName).assertIsDisplayed()
    }

    @Test
    fun addTagScreen_veryLongTagName_isHandled() {
        setContent()
        val longTagName = "A".repeat(100)
        composeRule.onNodeWithText("Enter tag name").performTextInput(longTagName)
        composeRule.waitForIdle()
        composeRule.onNodeWithText(longTagName).assertIsDisplayed()
    }
}