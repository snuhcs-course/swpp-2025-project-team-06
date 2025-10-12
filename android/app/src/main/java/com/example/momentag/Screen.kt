package com.example.momentag

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(
    val route: String,
) {
    object Home : Screen("home_screen")

    object Album : Screen("album_screen/{tagName}") {
        fun createRoute(tagName: String): String {
            val encodedTag = URLEncoder.encode(tagName, StandardCharsets.UTF_8.toString())
            return "album_screen/$encodedTag"
        }
    }

    object Image : Screen("image_screen/{imageUri}") {
        fun createRoute(uri: Uri): String {
            val encodedUri = Uri.encode(uri.toString())
            return "image_screen/$encodedUri"
        }
    }

    object LocalGallery : Screen("local_gallery_screen")

    object LocalAlbum : Screen("local_album_screen/{id}/{name}") {
        fun createRoute(
            id: Long,
            name: String,
        ): String {
            val encodedId = URLEncoder.encode(id.toString(), StandardCharsets.UTF_8.toString())
            val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.toString())
            return "local_album_screen/$encodedId/$encodedName"
        }
    }

    object SearchResult : Screen("search_result_screen/{query}") {
        fun createRoute(query: String): String {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            return "search_result_screen/$encodedQuery"
        }
    }

    object Login : Screen("login_screen")

    object Register : Screen("register_screen")
}
