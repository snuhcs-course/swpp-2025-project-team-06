package com.example.momentag.util

import android.content.Context
import android.content.Intent
import com.example.momentag.model.Photo

/**
 * Utility functions for sharing photos
 *
 * Example usage in a Composable:
 * ```
 * val context = LocalContext.current
 * val homeViewModel: HomeViewModel = viewModel(factory = ViewModelFactory.getInstance(context))
 * val selectedPhotos by homeViewModel.selectedPhotos.collectAsState()
 *
 * Button(
 *     onClick = {
 *         val photos = homeViewModel.getPhotosToShare()
 *         ShareUtils.sharePhotos(context, photos)
 *     },
 *     enabled = selectedPhotos.isNotEmpty()
 * ) {
 *     Text("Share ${selectedPhotos.size} photos")
 * }
 * ```
 */
object ShareUtils {
    /**
     * Share photos using Android ShareSheet
     *
     * @param context Android context
     * @param photos List of photos to share
     * @return true if sharing was initiated, false if no photos to share
     */
    fun sharePhotos(
        context: Context,
        photos: List<Photo>,
    ): Boolean {
        if (photos.isEmpty()) {
            return false
        }

        val shareIntent =
            Intent().apply {
                if (photos.size == 1) {
                    // Single photo
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, photos[0].contentUri)
                    type = "image/*"
                } else {
                    // Multiple photos
                    action = Intent.ACTION_SEND_MULTIPLE
                    val uris = ArrayList(photos.map { it.contentUri })
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    type = "image/*"
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        val chooserIntent = Intent.createChooser(shareIntent, null)
        context.startActivity(chooserIntent)
        return true
    }
}
