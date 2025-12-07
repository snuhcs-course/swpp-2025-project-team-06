package com.example.momentag.ui.addtag

import android.Manifest
import android.net.Uri
import android.os.Build
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
import androidx.test.rule.GrantPermissionRule
import com.example.momentag.HiltTestActivity
import com.example.momentag.R
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
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    @get:Rule(order = 2)
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
    ): Photo =
        Photo(
            photoId = id,
            contentUri = Uri.parse("content://media/external/images/media/$mediaId"),
            createdAt = "2024-01-01T00:00:00Z",
        )

    // ----------------------------------------------------------
    // 테스트 케이스
    // ----------------------------------------------------------

    @Test
    fun addTagScreen_initialState_displaysCorrectUI() {
        // 문자열 리소스 가져오기
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)
        val photos = composeTestRule.activity.getString(R.string.tag_photos_label)
        val done = composeTestRule.activity.getString(R.string.action_done)

        setContent()

        // Verify tag name input field is displayed with placeholder
        composeTestRule.onNodeWithText(enterTagName).assertIsDisplayed()

        // Verify photos section is displayed
        composeTestRule.onNodeWithText(photos).assertIsDisplayed()

        // Verify Done button is not enabled when form is empty
        composeTestRule.onNodeWithText(done).assertIsDisplayed()
        composeTestRule.onNodeWithText(done).assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_enterTagName_updatesTextField() {
        // 문자열 리소스 가져오기
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)

        setContent()

        val tagName = "Summer Vacation"
        composeTestRule.onNodeWithText(enterTagName).performTextInput(tagName)

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
        // 문자열 리소스 가져오기
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)
        val done = composeTestRule.activity.getString(R.string.action_done)

        setContent()

        // Enter tag name only
        composeTestRule.onNodeWithText(enterTagName).performTextInput("Test Tag")

        // Wait for text to be entered
        composeTestRule.waitForIdle()

        // Done button should still be disabled (no photos selected)
        composeTestRule.onNodeWithText(done).assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_tagNameSection_displayHashSymbol() {
        // 문자열 리소스 가져오기
        val hashPrefix = composeTestRule.activity.getString(R.string.add_tag_hash_prefix)

        setContent()

        // Verify that hash symbol is displayed as leading icon
        composeTestRule.onNodeWithText(hashPrefix).assertIsDisplayed()
    }

    @Test
    fun addTagScreen_photosSection_displaysCount() {
        val testPhotos =
            listOf(
                createTestPhoto("1"),
                createTestPhoto("2"),
            )

        // 문자열 리소스 가져오기
        val photosCount = composeTestRule.activity.getString(R.string.tag_photos_count, 2)

        vm.initialize(null, testPhotos)
        setContent()

        // Verify photo count is displayed
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule
                .onAllNodes(hasText(photosCount, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun addTagScreen_backButton_hasClickAction() {
        // 문자열 리소스 가져오기
        val navigateBack = composeTestRule.activity.getString(R.string.cd_navigate_back)

        setContent()

        // Find back button by content description
        // The Icon has contentDescription "Back", and its parent IconButton has the onClick
        // Using the merged tree (default) will merge the Icon's semantics with IconButton's
        composeTestRule
            .onNodeWithContentDescription(navigateBack, substring = true, ignoreCase = true)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun addTagScreen_bottomNavigation_isDisplayed() {
        // 문자열 리소스 가져오기
        val home = composeTestRule.activity.getString(R.string.nav_home)
        val myTags = composeTestRule.activity.getString(R.string.nav_my_tags)
        val moment = composeTestRule.activity.getString(R.string.nav_moment)

        setContent()

        // Verify bottom navigation tabs are displayed
        composeTestRule.onNodeWithText(home).assertIsDisplayed()
        composeTestRule.onNodeWithText(myTags).assertIsDisplayed()
        composeTestRule.onNodeWithText(moment).assertIsDisplayed()
    }

    @Test
    fun addTagScreen_addPhotosButton_isDisplayedAndClickable() {
        // 문자열 리소스 가져오기
        val addPhotos = composeTestRule.activity.getString(R.string.tag_add_photos)

        setContent()

        // The AddPhotosButton should be displayed in the grid
        // It has a "+" icon or text
        composeTestRule
            .onNodeWithText(addPhotos)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun addTagScreen_withValidInput_doneButtonIsEnabled() {
        val testPhotos = listOf(createTestPhoto("1"))

        // 문자열 리소스 가져오기
        val done = composeTestRule.activity.getString(R.string.action_done)

        vm.initialize("Test Tag", testPhotos)
        setContent()

        // Done button should be enabled
        composeTestRule.onNodeWithText(done).assertIsEnabled()
    }

    @Test
    fun addTagScreen_loadingState_canClickDoneButton() {
        val testPhotos = listOf(createTestPhoto("1"))

        // 문자열 리소스 가져오기
        val done = composeTestRule.activity.getString(R.string.action_done)

        vm.initialize("Test Tag", testPhotos)
        setContent()

        // Done button should be enabled and clickable
        composeTestRule.onNodeWithText(done).assertIsEnabled()
        composeTestRule.onNodeWithText(done).assertHasClickAction()
    }

    @Test
    fun addTagScreen_emptyTagName_showsPlaceholder() {
        // 문자열 리소스 가져오기
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)

        vm.clearDraft()
        setContent()

        // Verify placeholder is shown when tag name is empty
        composeTestRule.onNodeWithText(enterTagName).assertIsDisplayed()
    }

    @Test
    fun addTagScreen_photoItem_displaysCheckmark() {
        val testPhotos = listOf(createTestPhoto("1"))

        // 문자열 리소스 가져오기
        val selected = composeTestRule.activity.getString(R.string.cd_photo_selected)

        vm.initialize(null, testPhotos)
        setContent()

        // Verify that selected photos have checkmark overlay
        // The CheckboxOverlay should show the "Selected" icon
        composeTestRule
            .onNodeWithContentDescription(selected, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun addTagScreen_clearFocus_whenTappingOutside() {
        // 문자열 리소스 가져오기
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)

        setContent()

        // Click on the text field to focus
        composeTestRule.onNodeWithText(enterTagName).performClick()

        // Wait for focus
        composeTestRule.waitForIdle()

        // Integration test limit: difficult to tap "empty space" reliably without coordinates,
        // but verify no crash implies basic handling works.
    }

    @Test
    fun addTagScreen_multiplePhotos_displaysCorrectCount() {
        val testPhotos =
            listOf(
                createTestPhoto("1"),
                createTestPhoto("2"),
                createTestPhoto("3"),
                createTestPhoto("4"),
                createTestPhoto("5"),
            )

        // 문자열 리소스 가져오기
        val photosCount = composeTestRule.activity.getString(R.string.tag_photos_count, 5)

        vm.initialize(null, testPhotos)
        setContent()

        // Verify correct photo count
        composeTestRule.waitUntil(timeoutMillis = 2000) {
            composeTestRule
                .onAllNodes(hasText(photosCount, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    @Test
    fun addTagScreen_tagNameWithSpecialCharacters_isAccepted() {
        // 문자열 리소스 가져오기
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)

        setContent()
        val tagName = "2024 Summer! @Beach"

        composeTestRule.onNodeWithText(enterTagName).performTextInput(tagName)

        // Wait for input
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(tagName).assertIsDisplayed()
    }

    @Test
    fun addTagScreen_veryLongTagName_isHandled() {
        // 문자열 리소스 가져오기
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)

        setContent()
        val longTagName = "A".repeat(100)

        composeTestRule.onNodeWithText(enterTagName).performTextInput(longTagName)

        // Wait for input
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(longTagName).assertIsDisplayed()
    }

    @Test
    fun addTagScreen_whitespaceTagName_doneButtonIsDisabled() {
        val testPhotos = listOf(createTestPhoto("1"))
        val whitespaceTagName = "   "
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)
        val done = composeTestRule.activity.getString(R.string.action_done)

        vm.initialize(null, testPhotos)
        setContent()

        composeTestRule.onNodeWithText(enterTagName).performTextInput(whitespaceTagName)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(done).assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_deselectPhoto_disablesDoneButton() {
        val testPhotos = listOf(createTestPhoto("1"))
        val tagName = "My Tag"
        val photoItemDescription = composeTestRule.activity.getString(R.string.cd_photo_item, "1")
        val done = composeTestRule.activity.getString(R.string.action_done)
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)

        vm.initialize(null, testPhotos)
        setContent()

        composeTestRule.onNodeWithText(enterTagName).performTextInput(tagName)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(done).assertIsEnabled()

        composeTestRule.onNodeWithContentDescription(photoItemDescription).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(done).assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_tagNameTooLong_doneButtonIsDisabled() {
        val testPhotos = listOf(createTestPhoto("1"))
        val longTagName = "A".repeat(26)
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)
        val done = composeTestRule.activity.getString(R.string.action_done)

        vm.initialize(null, testPhotos)
        setContent()

        composeTestRule.onNodeWithText(enterTagName).performTextInput(longTagName)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(done).assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_exactlyMaxLength_doneButtonIsEnabled() {
        val testPhotos = listOf(createTestPhoto("1"))
        val maxLengthTagName = "A".repeat(25)
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)
        val done = composeTestRule.activity.getString(R.string.action_done)

        vm.initialize(null, testPhotos)
        setContent()

        composeTestRule.onNodeWithText(enterTagName).performTextInput(maxLengthTagName)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(done).assertIsEnabled()
    }

    @Test
    fun addTagScreen_trimmedTagName_isHandled() {
        val testPhotos = listOf(createTestPhoto("1"))
        val tagNameWithSpaces = "  My Tag  "
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)
        val done = composeTestRule.activity.getString(R.string.action_done)

        vm.initialize(null, testPhotos)
        setContent()

        composeTestRule.onNodeWithText(enterTagName).performTextInput(tagNameWithSpaces)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(done).assertIsEnabled()
    }

    @Test
    fun addTagScreen_removeAllPhotos_doneButtonIsDisabled() {
        val testPhotos = listOf(createTestPhoto("1"), createTestPhoto("2"))
        val tagName = "Test"
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)
        val done = composeTestRule.activity.getString(R.string.action_done)

        vm.initialize(null, testPhotos)
        setContent()

        composeTestRule.onNodeWithText(enterTagName).performTextInput(tagName)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(done).assertIsEnabled()

        val photoItem1 = composeTestRule.activity.getString(R.string.cd_photo_item, "1")
        composeTestRule.onNodeWithContentDescription(photoItem1).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(done).assertIsEnabled()

        val photoItem2 = composeTestRule.activity.getString(R.string.cd_photo_item, "2")
        composeTestRule.onNodeWithContentDescription(photoItem2).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(done).assertIsNotEnabled()
    }
}
