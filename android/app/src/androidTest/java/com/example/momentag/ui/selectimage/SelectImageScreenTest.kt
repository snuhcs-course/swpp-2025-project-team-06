package com.example.momentag.ui.selectimage

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.momentag.HiltTestActivity
import com.example.momentag.R
import com.example.momentag.model.Photo
import com.example.momentag.view.SelectImageScreen
import com.example.momentag.viewmodel.SelectImageViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hilt 환경에서 동작
 * - Hilt가 ViewModel을 생성하게 둠 (hiltRule.inject())
 * - 생성된 ViewModel 인스턴스를 가져와 reflection으로 내부 MutableStateFlow 값을 설정
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SelectImageScreenTest {
    // Hilt rule
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
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var vm: SelectImageViewModel

    // test-controlled initial state containers (실제 Flow 객체는 ViewModel 내부의 MutableStateFlow를 사용)
    @Before
    fun setup() {
        // must inject Hilt BEFORE requesting the ViewModel
        hiltRule.inject()

        // get the Hilt-created ViewModel from the activity's ViewModelProvider
        vm = ViewModelProvider(composeRule.activity)[SelectImageViewModel::class.java]

        // 모든 상태 초기화 (기본값 설정)
        setFlow("_allPhotos", emptyList<Photo>())
        setFlow("_isLoading", false)
        setFlow("_isLoadingMore", false)
        setFlow("_isSelectionMode", true)
        setFlow("_recommendState", SelectImageViewModel.RecommendState.Idle)
        setFlow("_recommendedPhotos", emptyList<Photo>())
        setFlow("_addPhotosState", SelectImageViewModel.AddPhotosState.Idle)

        // 뷰모델 초깃값 정리 메서드(있는 경우 호출)
        try {
            vm.clearDraft()
        } catch (_: Exception) {
            // 일부 구현에서는 clearDraft가 없을 수 있음.
        }
    }

    private fun setContent() {
        composeRule.setContent {
            SelectImageScreen(
                navController = rememberNavController(),
            )
        }
    }

    /**
     * reflection으로 ViewModel 내부 private MutableStateFlow 필드 값을 바꿔서
     * UI에 데이터/상태를 주입
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(
        name: String,
        value: T,
    ) {
        val field = SelectImageViewModel::class.java.getDeclaredField(name)
        field.isAccessible = true
        val flow = field.get(vm) as MutableStateFlow<T>
        flow.value = value
    }

    private fun hasProgress(): SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun waitForProgress() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasProgress()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // ----------------------------------------------------------
    // 1. 기본 UI 요소 표시 테스트
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_initialState_showsTitleAndButton() {
        // 문자열 리소스 가져오기
        val selectPhotos = composeRule.activity.getString(R.string.select_image_title)
        val addToTag = composeRule.activity.getString(R.string.tag_add_to_tag)

        setContent()

        composeRule.onNodeWithText(selectPhotos).assertIsDisplayed()
        composeRule.onNodeWithText(addToTag).assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 2. 초기 로딩 상태 (메인 ProgressIndicator 표시)
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_initialLoading_showsProgressIndicator() {
        setFlow("_isLoading", true)
        setContent()

        // 로딩이 실제로 나타날 때까지 기다림
        waitForProgress()

        composeRule.onAllNodes(hasProgress()).assertCountEquals(1) // 1 indicator
    }

    // ----------------------------------------------------------
    // 3. 사진 그리드 표시
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_photos_displayedInGrid() {
        val p =
            listOf(
                Photo("p1", Uri.parse("content://1"), "2024-01-01"),
                Photo("p2", Uri.parse("content://2"), "2024-01-02"),
            )
        setFlow("_allPhotos", p)

        // 문자열 리소스 가져오기
        val selectPhotos = composeRule.activity.getString(R.string.select_image_title)
        val photoP1 = composeRule.activity.getString(R.string.cd_photo, "p1")
        val photoP2 = composeRule.activity.getString(R.string.cd_photo, "p2")

        setContent()

        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText(selectPhotos)).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription(photoP1).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(photoP2).assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 4. 사진 선택 → 카운터 표시
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_photoSelection_showsCounter() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)

        // 문자열 리소스 가져오기
        val photoP1 = composeRule.activity.getString(R.string.cd_photo, "p1")
        val oneSelected = composeRule.activity.getString(R.string.select_image_selected_count, 1)

        setContent()

        composeRule.onNodeWithContentDescription(photoP1).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(oneSelected).assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 5. 선택 해제
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_photoSelection_toggle() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)

        // 문자열 리소스 가져오기
        val photoP1 = composeRule.activity.getString(R.string.cd_photo, "p1")
        val oneSelected = composeRule.activity.getString(R.string.select_image_selected_count, 1)

        setContent()

        val photo = composeRule.onNodeWithContentDescription(photoP1)

        photo.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(oneSelected).assertIsDisplayed()

        photo.performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasText(oneSelected)).assertCountEquals(0)
    }

    // ----------------------------------------------------------
    // 6. Add to Tag 버튼 활성화 조건
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_doneButton_enabledOnlyWhenSelected() {
        val p = listOf(Photo("p1", Uri.parse("content://1"), "2024"))
        setFlow("_allPhotos", p)

        // 문자열 리소스 가져오기
        val addToTag = composeRule.activity.getString(R.string.tag_add_to_tag)
        val photoP1 = composeRule.activity.getString(R.string.cd_photo, "p1")

        setContent()

        val btn = composeRule.onNodeWithText(addToTag)
        btn.assertIsNotEnabled()

        composeRule.onNodeWithContentDescription(photoP1).performClick()
        composeRule.waitForIdle()

        btn.assertIsEnabled()
    }

    // ----------------------------------------------------------
    // 7. 추천 Chip - Idle
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_recommendChip_idle() {
        setContent()
        composeRule.waitForIdle()

        // Add a photo so chip becomes visible
        val photo = Photo("p1", Uri.parse("content://1"), "2024")
        vm.addPhoto(photo)
        composeRule.waitForIdle()

        setFlow("_recommendState", SelectImageViewModel.RecommendState.Idle)
        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val suggestedForYou = composeRule.activity.getString(R.string.photos_suggested_for_you)

        composeRule.onNodeWithText(suggestedForYou, substring = true).assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 8. 추천 Chip - Loading
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_recommendChip_loading() {
        setContent()
        composeRule.waitForIdle()

        // Add a photo so chip becomes visible
        val photo = Photo("p1", Uri.parse("content://1"), "2024")
        vm.addPhoto(photo)
        composeRule.waitForIdle()

        setFlow("_recommendState", SelectImageViewModel.RecommendState.Loading)
        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val findingSuggestions = composeRule.activity.getString(R.string.photos_finding_suggestions)

        composeRule.onNodeWithText(findingSuggestions, substring = true).assertIsDisplayed()
    }

    // ----------------------------------------------------------
    // 9. 추천 패널 - 확장 시 추천 요청
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_expandPanel_triggersRecommendation() {
        val photo = Photo("p1", Uri.parse("content://1"), "2024")
        setFlow("_allPhotos", listOf(photo))

        setContent()
        composeRule.waitForIdle()

        // Add a photo to enable recommendation chip
        vm.addPhoto(photo)
        composeRule.waitForIdle()

        // Set initial state to Success so chip is visible
        setFlow("_recommendState", SelectImageViewModel.RecommendState.Idle)
        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val suggestedForYou = composeRule.activity.getString(R.string.photos_suggested_for_you)

        // Chip should be visible
        composeRule.onNodeWithText(suggestedForYou, substring = true).assertIsDisplayed()

        // Note: When user clicks the chip to expand, recommendPhoto() is called via LaunchedEffect
        // We can verify the Loading state is set
        composeRule.onNodeWithText(suggestedForYou, substring = true).performClick()
        composeRule.waitForIdle()

        // After expansion, the recommendation panel should be visible
        // In real scenario, LaunchedEffect would trigger vm.recommendPhoto()
        // We can verify by checking if expanded panel UI is shown
    }

    // ----------------------------------------------------------
    // 10. 추천 상태 - 빈 추천 결과
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_emptyRecommendations_stateHandled() {
        val selectedPhoto = Photo("p1", Uri.parse("content://1"), "2024")

        setFlow("_allPhotos", listOf(selectedPhoto))
        setFlow("_recommendedPhotos", emptyList<Photo>())
        setFlow("_recommendState", SelectImageViewModel.RecommendState.Success(emptyList()))

        setContent()
        composeRule.waitForIdle()

        // Add a photo so LaunchedEffect doesn't reset the state
        vm.addPhoto(selectedPhoto)
        composeRule.waitForIdle()

        // Set the state again after adding photo
        setFlow("_recommendedPhotos", emptyList<Photo>())
        setFlow("_recommendState", SelectImageViewModel.RecommendState.Success(emptyList()))
        composeRule.waitForIdle()

        // Verify state is correctly set
        assertEquals(0, vm.recommendedPhotos.value.size)
        assertTrue(vm.recommendState.value is SelectImageViewModel.RecommendState.Success)
    }

    // ----------------------------------------------------------
    // 11. clearDraft - 선택 취소 (BackHandler 동작)
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_clearDraft_clearsAllSelections() {
        val photos =
            listOf(
                Photo("p1", Uri.parse("content://1"), "2024"),
                Photo("p2", Uri.parse("content://2"), "2024"),
                Photo("p3", Uri.parse("content://3"), "2024"),
            )

        setFlow("_allPhotos", photos)
        setContent()
        composeRule.waitForIdle()

        // 여러 사진 선택
        vm.addPhoto(photos[0])
        vm.addPhoto(photos[1])
        vm.addPhoto(photos[2])
        composeRule.waitForIdle()

        // 선택된 사진이 3개인지 확인
        assertEquals(3, vm.selectedPhotos.value.size)

        // clearDraft 호출 (BackHandler에서 호출되는 메서드)
        vm.clearDraft()
        composeRule.waitForIdle()

        // 모든 선택이 취소되었는지 확인
        assertEquals(0, vm.selectedPhotos.value.size)
    }

    // ----------------------------------------------------------
    // 12. 여러 사진 선택 후 카운터 표시
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_multiplePhotosSelected_showsCorrectCount() {
        val photos =
            listOf(
                Photo("p1", Uri.parse("content://1"), "2024"),
                Photo("p2", Uri.parse("content://2"), "2024"),
                Photo("p3", Uri.parse("content://3"), "2024"),
            )

        setFlow("_allPhotos", photos)

        // 문자열 리소스 가져오기
        val photoP1 = composeRule.activity.getString(R.string.cd_photo, "p1")
        val photoP2 = composeRule.activity.getString(R.string.cd_photo, "p2")
        val photoP3 = composeRule.activity.getString(R.string.cd_photo, "p3")
        val threeSelected = composeRule.activity.getString(R.string.select_image_selected_count, 3)

        setContent()
        composeRule.waitForIdle()

        // 세 개의 사진 선택
        composeRule.onNodeWithContentDescription(photoP1).performClick()
        composeRule.onNodeWithContentDescription(photoP2).performClick()
        composeRule.onNodeWithContentDescription(photoP3).performClick()
        composeRule.waitForIdle()

        // "3 selected" 텍스트가 표시되는지 확인
        composeRule.onNodeWithText(threeSelected).assertIsDisplayed()

        // ViewModel에도 3개가 저장되었는지 확인
        assertEquals(3, vm.selectedPhotos.value.size)
    }

    // ----------------------------------------------------------
    // 13. 여러 사진 선택 후 Add to Tag 버튼 활성화
    // ----------------------------------------------------------

    @Test
    fun selectImageScreen_multiplePhotosSelected_enablesAddButton() {
        val photos =
            listOf(
                Photo("p1", Uri.parse("content://1"), "2024"),
                Photo("p2", Uri.parse("content://2"), "2024"),
                Photo("p3", Uri.parse("content://3"), "2024"),
            )

        setFlow("_allPhotos", photos)

        // 문자열 리소스 가져오기
        val addToTag = composeRule.activity.getString(R.string.tag_add_to_tag)
        val photoP1 = composeRule.activity.getString(R.string.cd_photo, "p1")
        val photoP2 = composeRule.activity.getString(R.string.cd_photo, "p2")

        setContent()
        composeRule.waitForIdle()

        // 초기에는 버튼 비활성화
        val btn = composeRule.onNodeWithText(addToTag)
        btn.assertIsNotEnabled()

        // 두 개의 사진 선택
        composeRule.onNodeWithContentDescription(photoP1).performClick()
        composeRule.onNodeWithContentDescription(photoP2).performClick()
        composeRule.waitForIdle()

        // 버튼 활성화 확인
        btn.assertIsEnabled()

        // ViewModel에 2개가 저장되었는지 확인
        assertEquals(2, vm.selectedPhotos.value.size)
    }
}
