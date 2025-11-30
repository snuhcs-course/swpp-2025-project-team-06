package com.example.momentag.ui.localalbum

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.momentag.view.LocalAlbumScreen
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
class LocalAlbumScreenTest {
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
    fun localAlbumScreen_initialState_displaysCorrectUI() {
        val albumName = "Test Album"

        composeRule.setContent {
            LocalAlbumScreen(
                navController = rememberNavController(),
                albumId = 1L,
                albumName = albumName,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val appName = composeRule.activity.getString(R.string.app_name)

        // TopBar 제목 확인
        composeRule.onNodeWithText(appName).assertIsDisplayed()
        // Back 버튼 확인
        composeRule.onNodeWithContentDescription(composeRule.activity.getString(R.string.cd_navigate_back)).assertIsDisplayed()
        // 앨범 이름 확인
        composeRule.onNodeWithText(albumName).assertIsDisplayed()
    }

    // ---------- 2. 사진 표시 ----------

    @Test
    fun localAlbumScreen_withPhotos_displaysPhotos() {
        val albumName = "Test Album"
        val photos =
            listOf(
                Photo("p1", Uri.parse("content://1"), "2024"),
                Photo("p2", Uri.parse("content://2"), "2024"),
            )

        composeRule.setContent {
            LocalAlbumScreen(
                navController = rememberNavController(),
                albumId = 1L,
                albumName = albumName,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입하여 LaunchedEffect의 영향을 받지 않도록 함
        setFlow("_imagesInAlbum", photos)

        composeRule.waitForIdle()

        // 사진이 표시되는지 확인
        val photoP1 = composeRule.activity.getString(R.string.cd_photo_item, "p1")
        composeRule.onNodeWithContentDescription(photoP1).assertIsDisplayed()
    }

    // ---------- 3. 선택 모드 ----------

    @Test
    fun localAlbumScreen_selectionMode_showsCancelButton() {
        val albumName = "Test Album"
        val photoUri = Uri.parse("content://1")
        val photo = Photo("p1", photoUri, "2024")
        val photos = listOf(photo)

        composeRule.setContent {
            LocalAlbumScreen(
                navController = rememberNavController(),
                albumId = 1L,
                albumName = albumName,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_imagesInAlbum", photos)

        // Manually select a photo to enter selection mode
        setFlow("_selectedPhotosInAlbum", mapOf(photoUri to photo))

        composeRule.waitForIdle()

        // 문자열 리소스 가져오기
        val cancelSelection = composeRule.activity.getString(R.string.cd_cancel_selection)

        // Selection mode에서는 Cancel 버튼이 표시됨
        composeRule.onNodeWithContentDescription(cancelSelection).assertIsDisplayed()
    }

    // ---------- 4. Back 버튼 클릭 ----------

    @Test
    fun localAlbumScreen_backButton_isClickable() {
        val albumName = "Test Album"
        var backClicked = false

        composeRule.setContent {
            LocalAlbumScreen(
                navController = rememberNavController(),
                albumId = 1L,
                albumName = albumName,
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

    // ---------- 5. 빈 상태 ----------

    @Test
    fun localAlbumScreen_emptyPhotos_displaysAlbumName() {
        val albumName = "Empty Album"

        composeRule.setContent {
            LocalAlbumScreen(
                navController = rememberNavController(),
                albumId = 1L,
                albumName = albumName,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_imagesInAlbum", emptyList<Photo>())

        composeRule.waitForIdle()

        // 앨범 이름은 여전히 표시되어야 함
        composeRule.onNodeWithText(albumName).assertIsDisplayed()
    }

    // ---------- 6. 화면 안정성 ----------

    @Test
    fun localAlbumScreen_multiplePhotos_rendersWithoutCrashing() {
        val albumName = "Large Album"
        val photos =
            (1..10).map { index ->
                Photo("p$index", Uri.parse("content://$index"), "2024")
            }

        composeRule.setContent {
            LocalAlbumScreen(
                navController = rememberNavController(),
                albumId = 1L,
                albumName = albumName,
                onNavigateBack = {},
            )
        }

        composeRule.waitForIdle()

        // setContent 후에 데이터를 주입
        setFlow("_imagesInAlbum", photos)

        composeRule.waitForIdle()

        // 화면이 정상적으로 표시되어야 함
        composeRule.onNodeWithText(albumName).assertIsDisplayed()
    }
}
