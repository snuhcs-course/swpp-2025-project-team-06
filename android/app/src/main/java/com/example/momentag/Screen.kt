package com.example.momentag

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(
    val route: String,
) {
    object Home : Screen("home_screen?show_auto_login_toast={show_auto_login_toast}") {
        fun createRoute(showAutoLoginToast: Boolean): String = "home_screen?show_auto_login_toast=$showAutoLoginToast"
    }

    object Album : Screen("album_screen/{tagId}/{tagName}") {
        fun createRoute(
            tagId: String,
            tagName: String,
        ): String {
            val encodedTagId = URLEncoder.encode(tagId, StandardCharsets.UTF_8.toString())
            val encodedTag = URLEncoder.encode(tagName, StandardCharsets.UTF_8.toString())
            return "album_screen/$encodedTagId/$encodedTag"
        }
    }

    object Image : Screen("image_screen/{imageId}/{imageUri}") {
        const val LOCAL_PHOTO_PLACEHOLDER = "LOCAL"

        fun createRoute(
            uri: Uri,
            imageId: String,
        ): String {
            val encodedUri = Uri.encode(uri.toString())
            // Use placeholder for empty photoId to prevent navigation breaking with empty path segments
            val safeImageId = imageId.ifBlank { LOCAL_PHOTO_PLACEHOLDER }
            val encodedImageId = URLEncoder.encode(safeImageId, StandardCharsets.UTF_8.toString())
            return "image_screen/$encodedImageId/$encodedUri"
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

    object SearchResult : Screen("search_result_screen?query={query}") {
        fun createRoute(query: String): String {
            val encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
            return "search_result_screen?query=$encodedQuery"
        }

        fun initialRoute(): String = "search_result_screen"
    }

    object Login : Screen("login_screen?show_expiration_warning={show_expiration_warning}") {
        fun createRoute(showExpirationWarning: Boolean): String = "login_screen?show_expiration_warning=$showExpirationWarning"
    }

    object Register : Screen("register_screen")

    object Story : Screen("story_screen")

    object AddTag : Screen("add_tag_screen")

    object SelectImage : Screen("select_image_screen")

    object MyTags : Screen("my_tags_screen")
}
