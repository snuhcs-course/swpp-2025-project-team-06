package com.example.momentag

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.momentag.repository.TokenRepository
import com.example.momentag.view.AddTagScreen
import com.example.momentag.view.AlbumScreen
import com.example.momentag.view.HomeScreen
import com.example.momentag.view.ImageDetailScreen
import com.example.momentag.view.LocalAlbumScreen
import com.example.momentag.view.LocalGalleryScreen
import com.example.momentag.view.LoginScreen
import com.example.momentag.view.MyTagsScreen
import com.example.momentag.view.RegisterScreen
import com.example.momentag.view.SearchResultScreen
import com.example.momentag.view.SelectImageScreen
import com.example.momentag.view.StoryTagSelectionScreen
import com.example.momentag.viewmodel.StoryViewModel
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun AppNavigation(tokenRepository: TokenRepository) {
    val navController = rememberNavController()

    val isLoaded by tokenRepository.isSessionLoaded.collectAsState()
    val accessToken by tokenRepository.isLoggedIn.collectAsState()

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
            arguments =
                listOf(
                    navArgument("imageId") { type = NavType.StringType },
                    navArgument("imageUri") { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val encodedUriString = backStackEntry.arguments?.getString("imageUri")
            val decodedUri = encodedUriString?.let { Uri.decode(it).toUri() }

            val imageId = backStackEntry.arguments?.getString("imageId") ?: ""

            ImageDetailScreen(
                imageUri = decodedUri,
                imageId = imageId,
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
            arguments =
                listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
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

        composable(route = Screen.MyTags.route) {
            MyTagsScreen(
                navController = navController,
            )
        }

        // Story screen with ViewModel integration
        composable(
            route = Screen.Story.route,
        ) {
            val storyViewModel: StoryViewModel = hiltViewModel()

            StoryTagSelectionScreen(
                viewModel = storyViewModel,
                onBack = { navController.popBackStack() },
                navController = navController,
            )
        }
    }
}
