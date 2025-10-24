package com.example.momentag

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.momentag.data.SessionManager
import com.example.momentag.model.StoryModel
import com.example.momentag.ui.storytag.StoryTagSelectionScreen
import com.example.momentag.viewmodel.ImageDetailViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun appNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }

    // ImageDetailViewModel을 Navigation 레벨에서 공유
    val imageDetailViewModel: ImageDetailViewModel = viewModel(factory = ViewModelFactory(context))

    val isLoaded by sessionManager.isLoaded.collectAsState()
    val accessToken by sessionManager.accessTokenFlow.collectAsState()

    if (!isLoaded) {
        // show loading screen or wait for data to load
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (accessToken != null) Screen.Home.route else Screen.Login.route,
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(
            route = Screen.Album.route,
            arguments = listOf(navArgument("tagName") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedTag = backStackEntry.arguments?.getString("tagName") ?: ""
            val tagName = URLDecoder.decode(encodedTag, StandardCharsets.UTF_8.toString())

            AlbumScreen(
                tagName = tagName,
                navController = navController,
                onNavigateBack = {
                    navController.popBackStack()
                },
                imageDetailViewModel = imageDetailViewModel,
            )
        }

        composable(
            route = Screen.Image.route,
            arguments = listOf(navArgument("imageUri") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedUriString = backStackEntry.arguments?.getString("imageUri")

            val decodedUri =
                encodedUriString?.let {
                    Uri.decode(it).toUri()
                }

            val imageContext by imageDetailViewModel.imageContext.collectAsState()

            ImageScreen(
                imageUri = decodedUri,
                imageUris = imageContext?.images,
                initialIndex = imageContext?.currentIndex ?: 0,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Screen.LocalGallery.route,
        ) {
            LocalGalleryScreen(
                navController = navController,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Screen.LocalAlbum.route,
            arguments =
                listOf(
                    navArgument("id") { type = NavType.LongType },
                    navArgument("name") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("id") ?: 0L
            val albumName = backStackEntry.arguments?.getString("name") ?: ""

            LocalAlbumScreen(
                navController = navController,
                albumId = albumId,
                albumName = albumName,
                onNavigateBack = {
                    navController.popBackStack()
                },
                imageDetailViewModel = imageDetailViewModel,
            )
        }

        composable(
            route = Screen.Login.route,
        ) {
            LoginScreen(
                navController = navController,
            )
        }

        composable(
            route = Screen.Register.route,
        ) {
            RegisterScreen(
                navController = navController,
            )
        }

        composable(
            route = Screen.SearchResult.route,
            arguments = listOf(navArgument("query") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedQuery = backStackEntry.arguments?.getString("query") ?: ""
            val query = URLDecoder.decode(encodedQuery, StandardCharsets.UTF_8.toString())
            SearchResultScreen(
                initialQuery = query,
                navController = navController,
                onNavigateBack = { navController.popBackStack() },
                imageDetailViewModel = imageDetailViewModel,
            )
        }
        // 🔥 여기 수정된 Story 라우트
        composable(
            route = Screen.Story.route,
        ) {
            // TODO : 샘플/임시 상태. 실제로는 ViewModel 주입.
            val mockStories = listOf(
                StoryModel(
                    id = "1",
                    images = listOf("https://images.unsplash.com/photo-1504674900247-0877df9cc836"),
                    date = "2024.10.15",
                    location = "강남 맛집",
                    suggestedTags = listOf("#food", "#맛집", "#행복", "+")
                ),
                StoryModel(
                    id = "2",
                    images = listOf("https://images.unsplash.com/photo-1501594907352-04cda38ebc29"),
                    date = "2024.09.22",
                    location = "제주도 여행",
                    suggestedTags = listOf("#여행", "#바다", "#힐링", "+")
                )
            )

            var selectedTags by remember {
                mutableStateOf<Map<String, Set<String>>>(emptyMap())
            }

            StoryTagSelectionScreen(
                stories = mockStories,
                selectedTags = selectedTags,
                onTagToggle = { storyId, tag ->
                    selectedTags = selectedTags.toMutableMap().apply {
                        val current = this[storyId] ?: emptySet()
                        this[storyId] =
                            if (tag in current) current - tag else current + tag
                    }
                },
                onDone = { storyId ->
                    // ex: 서버 업로드 등
                },
                onComplete = {
                    navController.popBackStack() // or go somewhere else
                },
                onBack = {
                    navController.popBackStack()
                },
                navController = navController
            )
        }
    }
}
