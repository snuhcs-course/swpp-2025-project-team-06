package com.example.momentag

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Home : Screen("home_screen")
    object Album : Screen("album_screen/{tagName}") {
        fun createRoute(tagName: String): String {
            val encodedTag = URLEncoder.encode(tagName, StandardCharsets.UTF_8.toString())
            return "album_screen/$encodedTag"
        }
    }
    object Image: Screen("image_screen/{imageUri}") {
        fun createRoute(uri: Uri): String {
            val encodedUri = Uri.encode(uri.toString())
            return "image_screen/$encodedUri"
        }
    }
    object LocalAlbum : Screen("local_album_screen")
}