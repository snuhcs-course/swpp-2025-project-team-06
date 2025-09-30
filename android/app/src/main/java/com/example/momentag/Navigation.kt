package com.example.momentag

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import androidx.core.net.toUri

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
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
                uri = decodedUri,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.LocalAlbum.route
        ) {
            LocalAlbumScreen(
                navController = navController,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}