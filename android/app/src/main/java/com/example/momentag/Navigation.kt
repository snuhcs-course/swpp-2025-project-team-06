package com.example.momentag

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.example.momentag.ui.storytag.StoryTagSelectionScreen
import com.example.momentag.viewmodel.StoryViewModel
import com.example.momentag.viewmodel.ViewModelFactory
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun appNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }

    val isLoaded by sessionManager.isLoaded.collectAsState()
    val accessToken by sessionManager.accessTokenFlow.collectAsState()

    if (!isLoaded) {
        // show loading screen or wait for data to load
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (accessToken != null) Screen.Home.route else Screen.Login.route,
//        startDestination = Screen.Home.route,
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(
            route = Screen.Album.route,
            arguments =
                listOf(
                    navArgument("tagId") { type = NavType.StringType },
                    navArgument("tagName") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val encodedTagId = backStackEntry.arguments?.getString("tagId") ?: ""
            val tagId = URLDecoder.decode(encodedTagId, StandardCharsets.UTF_8.toString())

            val encodedTag = backStackEntry.arguments?.getString("tagName") ?: ""
            val tagName = URLDecoder.decode(encodedTag, StandardCharsets.UTF_8.toString())

            AlbumScreen(
                tagId = tagId,
                tagName = tagName,
                navController = navController,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Screen.Image.route,
            arguments = listOf(navArgument("imageUri") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedUriString = backStackEntry.arguments?.getString("imageUri")
            val decodedUri = encodedUriString?.let { Uri.decode(it).toUri() }

            ImageDetailScreen(
                imageUri = decodedUri,
                onNavigateBack = { navController.popBackStack() },
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
            )
        }

        composable(route = Screen.AddTag.route) {
            AddTagScreen(
                navController = navController,
            )
        }

        composable(route = Screen.SelectImage.route) {
            SelectImageScreen(
                navController = navController,
            )
        }
        // Story screen with ViewModel integration
        composable(
            route = Screen.Story.route,
        ) {
            val factory = ViewModelFactory.getInstance(LocalContext.current)
            val storyViewModel: StoryViewModel = viewModel(factory = factory)

            StoryTagSelectionScreen(
                viewModel = storyViewModel,
                onBack = { navController.popBackStack() },
                navController = navController,
            )
        }
    }
}
