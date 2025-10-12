package com.example.momentag

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun appNavigation() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
    ) {
        composable(route = Screen.Home.route) {
            homeScreen(navController = navController)
        }

        composable(
            route = Screen.Album.route,
            arguments = listOf(navArgument("tagName") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedTag = backStackEntry.arguments?.getString("tagName") ?: ""
            val tagName = URLDecoder.decode(encodedTag, StandardCharsets.UTF_8.toString())

            albumScreen(
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

            val decodedUri =
                encodedUriString?.let {
                    Uri.decode(it).toUri()
                }

            imageScreen(
                imageUri = decodedUri,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Screen.LocalGallery.route,
        ) {
            localGalleryScreen(
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

            localAlbumScreen(
                navController = navController,
                albumId = albumId,
                albumName = albumName,
                onNavigateBack = {
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Screen.SearchResult.route,
            arguments = listOf(navArgument("query") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encodedQuery = backStackEntry.arguments?.getString("query") ?: ""
            val query = URLDecoder.decode(encodedQuery, StandardCharsets.UTF_8.toString())
            searchResultScreen(
                initialQuery = query,
                navController = navController,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(route = Screen.Login.route) {
            loginScreen(navController = navController)
        }

        composable(route = Screen.Register.route) {
            registerScreen(navController = navController)
        }
    }
}

@Composable
fun registerScreen(navController: NavHostController) {
    TODO("Not yet implemented")
}

@Composable
fun loginScreen(navController: NavHostController) {
    TODO("Not yet implemented")
}
