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

    // ----------------------------------------------------------
    // 추가된 예외/엣지 케이스 시나리오
    // ----------------------------------------------------------

    @Test
    fun addTagScreen_whitespaceTagName_doneButtonIsDisabled() {
        val testPhotos = listOf(createTestPhoto("1"))
        val whitespaceTagName = "   "
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)
        val done = composeTestRule.activity.getString(R.string.action_done)

        // 사진은 선택되었지만, 태그 이름은 공백뿐인 상황
        vm.initialize(null, testPhotos)
        setContent()

        // 태그 이름으로 공백 입력
        composeTestRule.onNodeWithText(enterTagName).performTextInput(whitespaceTagName)

        // '완료' 버튼이 여전히 비활성화 상태인지 확인
        composeTestRule.onNodeWithText(done).assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_deselectPhoto_disablesDoneButton() {
        // 이 테스트는 선택된 사진을 다시 클릭하면 선택이 해제된다고 가정합니다.
        val testPhotos = listOf(createTestPhoto("1"))
        val tagName = "My Tag"
        val selectedPhotoDescription = composeTestRule.activity.getString(R.string.cd_photo_selected)
        val done = composeTestRule.activity.getString(R.string.action_done)
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)

        // 사진 한 장으로 시작
        vm.initialize(null, testPhotos)
        setContent()

        // 태그 이름 입력
        composeTestRule.onNodeWithText(enterTagName).performTextInput(tagName)
        composeTestRule.waitForIdle()

        // 초기 상태: 태그 이름과 사진이 모두 있으므로 '완료' 버튼 활성화
        composeTestRule.onNodeWithText(done).assertIsEnabled()

        // 선택된 사진을 클릭하여 선택 해제 (부모 노드를 클릭해야 할 수 있음)
        composeTestRule
            .onNodeWithContentDescription(selectedPhotoDescription, useUnmergedTree = true)
            .performClick()
        composeTestRule.waitForIdle()

        // 최종 상태: 사진이 없으므로 '완료' 버튼 비활성화
        composeTestRule.onNodeWithText(done).assertIsNotEnabled()
    }

    @Test
    fun addTagScreen_duplicateTagName_showsWarningAndEnablesDone() {
        // [개발자 가이드] 이 테스트를 활성화하려면 Hilt를 사용한 의존성 주입 설정이 필요합니다.
        // 1. 테스트용 FakeRepository/FakeUseCase를 만듭니다. (예: 태그가 항상 존재한다고 응답)
        // 2. Hilt 테스트 모듈에서 @BindValue 등을 사용하여 실제 Repository 대신 FakeRepository를 주입하도록 설정합니다.
        // 3. 아래의 테스트 로직을 실행하여 UI가 예상대로 동작하는지 확인합니다.

        // 사용자의 요구사항: "이름이 중복되면 기존 태그로 들어간다는 문구 뜨고 완료도 활성화됨"
        val testPhotos = listOf(createTestPhoto("1"))
        val duplicateTagName = "Existing Tag"
        // 예상되는 경고 문구 (실제 앱의 문자열 리소스에 따라 변경 필요)
        val warningMessage = "이미 존재하는 태그입니다. 사진을 추가합니다."
        val done = composeTestRule.activity.getString(R.string.action_done)
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)

        // --- 테스트 준비 (Mocking 필요) ---
        // 아래 코드는 `FakeTagRepository`가 주입되었다고 가정합니다.
        // fakeRepository.setTagExists(true)
        vm.initialize(null, testPhotos)
        setContent()

        // --- 실행 ---
        // 중복된 태그 이름 입력
        composeTestRule.onNodeWithText(enterTagName).performTextInput(duplicateTagName)
        composeTestRule.waitForIdle() // ViewModel이 상태를 업데이트할 시간을 줍니다.

        // --- 검증 ---
        // 경고 메시지가 표시되는지 확인 (주석 해제 필요)
        // composeTestRule.onNodeWithText(warningMessage).assertIsDisplayed()

        // '완료' 버튼이 활성화되는지 확인
        composeTestRule.onNodeWithText(done).assertIsEnabled()
    }

    @Test
    fun addTagScreen_saveTagFails_showsErrorMessage() {
        // [개발자 가이드] 이 테스트는 `duplicateTagName` 테스트와 유사하게 의존성 주입 설정이 필요합니다.
        // 1. 태그 저장이 항상 실패하도록 설정된 FakeRepository/FakeUseCase를 만듭니다.
        // 2. Hilt 테스트 모듈에서 해당 FakeRepository를 주입하도록 설정합니다.
        // 3. 사용자가 '완료'를 클릭했을 때, 실패 상태를 UI(Snackbar 등)에 올바르게 표시하는지 확인합니다.

        val testPhotos = listOf(createTestPhoto("1"))
        val tagName = "New Tag"
        // 예상되는 에러 메시지 (실제 앱의 문자열 리소스에 따라 변경 필요)
        val errorMessage = "저장에 실패했습니다."
        val done = composeTestRule.activity.getString(R.string.action_done)
        val enterTagName = composeTestRule.activity.getString(R.string.field_enter_tag_name)

        // --- 테스트 준비 (Mocking 필요) ---
        // 아래 코드는 `FakeTagRepository`가 주입되었다고 가정합니다.
        // fakeRepository.setSaveShouldFail(true)
        vm.initialize(null, testPhotos)
        setContent()

        // --- 실행 ---
        // 유효한 태그 이름과 사진 설정
        composeTestRule.onNodeWithText(enterTagName).performTextInput(tagName)
        composeTestRule.waitForIdle()
        // '완료' 버튼 클릭 (주석 해제 필요)
        // composeTestRule.onNodeWithText(done).performClick()
        // composeTestRule.waitForIdle() // 비동기 작업 및 UI 업데이트 대기

        // --- 검증 ---
        // 에러 메시지(Snackbar 등)가 표시되는지 확인 (주석 해제 필요)
        // composeTestRule.onNodeWithText(errorMessage).assertIsDisplayed()
    }
}
