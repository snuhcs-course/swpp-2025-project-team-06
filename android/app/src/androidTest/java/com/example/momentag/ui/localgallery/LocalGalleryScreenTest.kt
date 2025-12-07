package com.example.momentag.ui.localgallery

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.example.momentag.HiltTestActivity
import com.example.momentag.R
import com.example.momentag.model.Album
import com.example.momentag.view.LocalGalleryScreen
import com.example.momentag.viewmodel.LocalViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class LocalGalleryScreenTest {
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

    private lateinit var localViewModel: LocalViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        localViewModel = ViewModelProvider(composeRule.activity)[LocalViewModel::class.java]
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> setFlow(
        name: String,
        value: T,
    ) {
        val field = LocalViewModel::class.java.getDeclaredField(name)
        field.isAccessible = true
        val flow = field.get(localViewModel) as MutableStateFlow<T>
        flow.value = value
    }

    // ---------- 1. 초기 화면 상태 ----------

    @Test
    fun localGalleryScreen_initialState_displaysCorrectUI() {
        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeRule.activity.getString(R.string.app_name)
        val albumsTitle = composeRule.activity.getString(R.string.gallery_albums_title)

        // TopBar 제목 확인
        composeRule.onNodeWithText(appName).assertIsDisplayed()
        // Back 버튼 확인
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.cd_navigate_back)).assertIsDisplayed()
        // 앨범 타이틀 확인
        composeRule.onNodeWithText(albumsTitle).assertIsDisplayed()
    }

    // ---------- 2. 앨범 표시 ----------

    @Test
    fun localGalleryScreen_withAlbums_displaysAlbums() {
        val albums =
            listOf(
                Album(1L, "Album 1", Uri.parse("content://1")),
                Album(2L, "Album 2", Uri.parse("content://2")),
            )

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 앨범이 표시되는지 확인
        val album1 = composeRule.activity.getString(R.string.cd_album, "Album 1")
        composeRule.onNodeWithContentDescription(album1).assertIsDisplayed()
    }

    // ---------- 3. 선택 모드 ----------

    @Test
    fun localGalleryScreen_selectionMode_showsSelectedCount() {
        val albums =
            listOf(
                Album(1L, "Album 1", Uri.parse("content://1")),
            )

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 앨범을 long click하여 선택 모드 활성화
        val album1 = composeRule.activity.getString(R.string.cd_album, "Album 1")
        composeRule.onNodeWithContentDescription(album1).performTouchInput { longClick() }

        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val selectedCount = composeRule.activity.getString(R.string.photos_selected_count, 1)

        // 선택된 개수가 표시되는지 확인
        composeRule.onNodeWithText(selectedCount).assertIsDisplayed()
    }

    // ---------- 4. Select All 버튼 ----------

    @Test
    fun localGalleryScreen_selectionMode_showsSelectAllButton() {
        val albums =
            listOf(
                Album(1L, "Album 1", Uri.parse("content://1")),
            )

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 앨범을 long click하여 선택 모드 활성화
        val album1 = composeRule.activity.getString(R.string.cd_album, "Album 1")
        composeRule.onNodeWithContentDescription(album1).performTouchInput { longClick() }

        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val selectAll = composeRule.activity.getString(R.string.cd_select_all)

        // Select All 버튼이 표시되는지 확인
        composeRule.onNodeWithContentDescription(selectAll).assertIsDisplayed()
    }

    // ---------- 5. Back 버튼 클릭 ----------

    @Test
    fun localGalleryScreen_backButton_isClickable() {
        var backClicked = false

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = { backClicked = true },
            )
        }

        composeRule.waitForIdle()

        // Back 버튼 클릭
        composeRule
            .onNodeWithContentDescription(composeRule.activity.getString(R.string.cd_navigate_back))
            .performClick()

        composeRule.waitForIdle()

        // Back 콜백이 호출되었는지 확인
        assert(backClicked) { "Back button should trigger onNavigateBack callback" }
    }

    // ---------- 6. 빈 상태 ----------

    @Test
    fun localGalleryScreen_emptyAlbums_displaysTitleOnly() {
        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", emptyList<Album>())

        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val albumsTitle = composeRule.activity.getString(R.string.gallery_albums_title)

        // 타이틀은 여전히 표시되어야 함
        composeRule.onNodeWithText(albumsTitle).assertIsDisplayed()
    }

    // ---------- 7. 화면 안정성 ----------

    @Test
    fun localGalleryScreen_multipleAlbums_rendersWithoutCrashing() {
        val albums =
            (1..10)
                .map { index ->
                    Album(index.toLong(), "Album $index", Uri.parse("content://$index"))
                }

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val albumsTitle = composeRule.activity.getString(R.string.gallery_albums_title)

        // 화면이 정상적으로 표시되어야 함
        composeRule.onNodeWithText(albumsTitle).assertIsDisplayed()
    }

    // ---------- 8. TopBar 전환 ----------

    @Test
    fun localGalleryScreen_selectionModeToggle_switchesTopBar() {
        val albums =
            listOf(
                Album(1L, "Album 1", Uri.parse("content://1")),
            )

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeRule.activity.getString(R.string.app_name)

        // 초기 상태: 앱 이름 표시
        composeRule.onNodeWithText(appName).assertIsDisplayed()

        // 앨범을 long click하여 선택 모드로 전환
        val album1 = composeRule.activity.getString(R.string.cd_album, "Album 1")
        composeRule.onNodeWithContentDescription(album1).performTouchInput { longClick() }

        composeRule.waitForIdle()

        // Selection 모드: 선택된 개수 표시
        val selectedCount = composeRule.activity.getString(R.string.photos_selected_count, 1)
        composeRule.onNodeWithText(selectedCount).assertIsDisplayed()
    }

    // ---------- 9. Select All 버튼 클릭 ----------

    @Test
    fun localGalleryScreen_selectAllButton_selectsAllAlbums() {
        val albums =
            listOf(
                Album(1L, "Album 1", Uri.parse("content://1")),
                Album(2L, "Album 2", Uri.parse("content://2")),
                Album(3L, "Album 3", Uri.parse("content://3")),
            )

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 첫 번째 앨범을 long click하여 선택 모드 진입
        val album1 = composeRule.activity.getString(R.string.cd_album, "Album 1")
        composeRule.onNodeWithContentDescription(album1).performTouchInput { longClick() }

        composeRule.waitForIdle()

        // Select All 버튼 클릭
        val selectAll = composeRule.activity.getString(R.string.cd_select_all)
        composeRule.onNodeWithContentDescription(selectAll).performClick()

        composeRule.waitForIdle()

        // 모든 앨범이 선택되었는지 확인 (3개 선택됨)
        val selectedCount = composeRule.activity.getString(R.string.photos_selected_count, 3)
        composeRule.onNodeWithText(selectedCount).assertIsDisplayed()
    }

    // ---------- 10. Deselect All 버튼 클릭 ----------

    @Test
    fun localGalleryScreen_deselectAllButton_deselectsAllAlbums() {
        val albums =
            listOf(
                Album(1L, "Album 1", Uri.parse("content://1")),
                Album(2L, "Album 2", Uri.parse("content://2")),
            )

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 첫 번째 앨범을 long click하여 선택 모드 진입
        val album1 = composeRule.activity.getString(R.string.cd_album, "Album 1")
        composeRule.onNodeWithContentDescription(album1).performTouchInput { longClick() }

        composeRule.waitForIdle()

        // Select All 버튼 클릭하여 모두 선택
        val selectAll = composeRule.activity.getString(R.string.cd_select_all)
        composeRule.onNodeWithContentDescription(selectAll).performClick()

        composeRule.waitForIdle()

        // Select All 버튼 다시 클릭하여 모두 해제
        composeRule.onNodeWithContentDescription(selectAll).performClick()

        composeRule.waitForIdle()

        // 선택 모드가 해제되고 일반 TopBar로 돌아왔는지 확인
        val appName = composeRule.activity.getString(R.string.app_name)
        composeRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // ---------- 11. 여러 앨범 선택/해제 ----------

    @Test
    fun localGalleryScreen_multipleSelection_updatesCount() {
        val albums =
            listOf(
                Album(1L, "Album 1", Uri.parse("content://1")),
                Album(2L, "Album 2", Uri.parse("content://2")),
                Album(3L, "Album 3", Uri.parse("content://3")),
            )

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 첫 번째 앨범 long click으로 선택 모드 진입
        val album1 = composeRule.activity.getString(R.string.cd_album, "Album 1")
        composeRule.onNodeWithContentDescription(album1).performTouchInput { longClick() }

        composeRule.waitForIdle()

        // 1개 선택 확인
        var selectedCount = composeRule.activity.getString(R.string.photos_selected_count, 1)
        composeRule.onNodeWithText(selectedCount).assertIsDisplayed()

        // 두 번째 앨범 클릭하여 추가 선택
        val album2 = composeRule.activity.getString(R.string.cd_album, "Album 2")
        composeRule.onNodeWithContentDescription(album2).performClick()

        composeRule.waitForIdle()

        // 2개 선택 확인
        selectedCount = composeRule.activity.getString(R.string.photos_selected_count, 2)
        composeRule.onNodeWithText(selectedCount).assertIsDisplayed()

        // 세 번째 앨범 클릭하여 추가 선택
        val album3 = composeRule.activity.getString(R.string.cd_album, "Album 3")
        composeRule.onNodeWithContentDescription(album3).performClick()

        composeRule.waitForIdle()

        // 3개 선택 확인
        selectedCount = composeRule.activity.getString(R.string.photos_selected_count, 3)
        composeRule.onNodeWithText(selectedCount).assertIsDisplayed()
    }

    // ---------- 12. 선택 모드에서 앨범 토글 ----------

    @Test
    fun localGalleryScreen_selectionMode_togglesAlbumSelection() {
        val albums =
            listOf(
                Album(1L, "Album 1", Uri.parse("content://1")),
            )

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 앨범을 long click하여 선택
        val album1 = composeRule.activity.getString(R.string.cd_album, "Album 1")
        composeRule.onNodeWithContentDescription(album1).performTouchInput { longClick() }

        composeRule.waitForIdle()

        // 1개 선택 확인
        val selectedCount = composeRule.activity.getString(R.string.photos_selected_count, 1)
        composeRule.onNodeWithText(selectedCount).assertIsDisplayed()

        // 같은 앨범을 다시 클릭하여 선택 해제
        composeRule.onNodeWithContentDescription(album1).performClick()

        composeRule.waitForIdle()

        // 선택 모드가 해제되고 일반 TopBar로 돌아왔는지 확인
        val appName = composeRule.activity.getString(R.string.app_name)
        composeRule.onNodeWithText(appName).assertIsDisplayed()
    }

    // ---------- 13. 선택 모드 진입 및 UI 변경 확인 ----------

    @Test
    fun localGalleryScreen_selectionMode_showsSelectionUI() {
        val albums =
            listOf(
                Album(1L, "Album 1", Uri.parse("content://1")),
            )

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 앨범을 long click하여 선택 모드 진입
        val album1 = composeRule.activity.getString(R.string.cd_album, "Album 1")
        composeRule.onNodeWithContentDescription(album1).performTouchInput { longClick() }

        composeRule.waitForIdle()

        // 선택 모드 UI 확인 (선택 카운트 표시)
        val selectedCount = composeRule.activity.getString(R.string.photos_selected_count, 1)
        composeRule.onNodeWithText(selectedCount).assertIsDisplayed()

        // Select All 버튼 표시 확인
        val selectAll = composeRule.activity.getString(R.string.cd_select_all)
        composeRule.onNodeWithContentDescription(selectAll).assertIsDisplayed()
    }

    // ---------- 14. Close 버튼으로 선택 모드 해제 ----------

    @Test
    fun localGalleryScreen_closeButton_exitsSelectionMode() {
        val albums =
            listOf(
                Album(1L, "Album 1", Uri.parse("content://1")),
            )

        composeRule.setContent {
            LocalGalleryScreen(
                navController = rememberNavController(),
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_albums", albums)

        composeRule.waitForIdle()

        // 앨범을 long click하여 선택 모드 진입
        val album1 = composeRule.activity.getString(R.string.cd_album, "Album 1")
        composeRule.onNodeWithContentDescription(album1).performTouchInput { longClick() }

        composeRule.waitForIdle()

        // Close 버튼 클릭
        val deselectAll = composeRule.activity.getString(R.string.cd_deselect_all)
        composeRule.onNodeWithContentDescription(deselectAll).performClick()

        composeRule.waitForIdle()

        // 선택 모드가 해제되고 일반 TopBar로 돌아왔는지 확인
        val appName = composeRule.activity.getString(R.string.app_name)
        composeRule.onNodeWithText(appName).assertIsDisplayed()
    }
}
