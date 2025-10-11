package com.example.momentag

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.momentag.data.SessionManager
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.core.net.toUri

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }

    val isLoaded by sessionManager.isLoaded.collectAsState()
    val accessToken by sessionManager.accessTokenFlow.collectAsState()

    if (!isLoaded) {
        // show loading screen or wait for data to load
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (accessToken != null) Screen.Home.route else Screen.Login.route
    ) {
        composable(route = Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(
            route = Screen.Album.route,
            arguments = listOf(navArgument("tagName") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedTag = backStackEntry.arguments?.getString("tagName") ?: ""
            val tagName = URLDecoder.decode(encodedTag, StandardCharsets.UTF_8.toString())

            AlbumScreen(
                tagName = tagName,
                navController = navController,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Image.route,
            arguments = listOf(navArgument("imageUri") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedUriString = backStackEntry.arguments?.getString("imageUri")

            val decodedUri = encodedUriString?.let {
                Uri.decode(it).toUri()
            }

            ImageScreen(
                imageUri = decodedUri,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.LocalGallery.route
        ) {
            LocalGalleryScreen(
                navController = navController,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.LocalAlbum.route,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val albumId = backStackEntry.arguments?.getLong("id") ?: 0L
            val albumName = backStackEntry.arguments?.getString("name") ?: ""

            LocalAlbumScreen(
                navController = navController,
                albumId = albumId,
                albumName = albumName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Login.route
        ) {
            LoginScreen(
                navController = navController
            )
        }

        composable(
            route = Screen.Register.route,
        ) {
            RegisterScreen(
                navController = navController,
            )
        }
    }
}