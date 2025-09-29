package com.example.momentag

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
    object Image: Screen("image_screen/{imagePath}") {
        fun createRoute(imagePath: String): String {
            val encodedPath = URLEncoder.encode(imagePath, StandardCharsets.UTF_8.toString())
            return "image_screen/$encodedPath"
        }
    }
}