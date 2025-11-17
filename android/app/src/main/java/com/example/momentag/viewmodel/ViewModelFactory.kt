package com.example.momentag.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.momentag.data.SessionManager
import com.example.momentag.network.RetrofitInstance
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.PhotoSelectionRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.repository.TokenRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.ExperimentalUuidApi

/**
 * ViewModelFactory
 *
 * ViewModel 생성을 위한 Factory
 * - 의존성 주입을 수동으로 처리
 * - SessionStore, TokenRepository, RemoteRepository 생성 및 주입
 *
 * Singleton pattern to ensure all repositories are shared across ViewModels
 */
class ViewModelFactory private constructor(
    private val context: Context,
) : ViewModelProvider.Factory {
    // 1. Companion object
    companion object {
        @Volatile
        private var instance: ViewModelFactory? = null

        fun getInstance(context: Context): ViewModelFactory =
            instance ?: synchronized(this) {
                instance ?: ViewModelFactory(context.applicationContext).also {
                    android.util.Log.d("ViewModelFactory", "Creating singleton ViewModelFactory instance")
                    instance = it
                }
            }
    }

    // 2. Public 속성
    val albumUploadJobCount = MutableStateFlow(0)
    val albumUploadSuccessEvent = MutableSharedFlow<Long>()
    val remoteRepository by lazy {
        RemoteRepository(RetrofitInstance.getApiService(context.applicationContext))
    }
    val localRepository by lazy {
        LocalRepository(context.applicationContext)
    }

    // 3. Private 속성 (Repositories)
    private val sessionStore by lazy {
        SessionManager.getInstance(context.applicationContext)
    }

    private val tokenRepository by lazy {
        TokenRepository(
            RetrofitInstance.getApiService(context.applicationContext),
            sessionStore,
        )
    }

    private val searchRepository by lazy {
        com.example.momentag.repository.SearchRepository(
            RetrofitInstance.getApiService(context.applicationContext),
        )
    }

    private val recommendRepository by lazy {
        RecommendRepository(RetrofitInstance.getApiService(context.applicationContext))
    }

    private val imageBrowserRepository by lazy {
        ImageBrowserRepository()
    }

    private val photoSelectionRepository by lazy {
        PhotoSelectionRepository()
    }

    // 4. Override 함수
    @OptIn(ExperimentalUuidApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(localRepository, remoteRepository, recommendRepository, photoSelectionRepository, imageBrowserRepository) as T
            }
            modelClass.isAssignableFrom(LocalViewModel::class.java) -> {
                LocalViewModel(localRepository, imageBrowserRepository, albumUploadSuccessEvent) as T
            }
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(tokenRepository) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(
                    searchRepository,
                    photoSelectionRepository,
                    localRepository,
                    imageBrowserRepository,
                    tokenRepository,
                    remoteRepository,
                ) as T
            }
            modelClass.isAssignableFrom(ImageDetailViewModel::class.java) -> {
                ImageDetailViewModel(imageBrowserRepository, remoteRepository, recommendRepository) as T
            }
            modelClass.isAssignableFrom(PhotoViewModel::class.java) -> {
                PhotoViewModel(remoteRepository, localRepository, albumUploadJobCount) as T
            }
            modelClass.isAssignableFrom(AddTagViewModel::class.java) -> {
                AddTagViewModel(photoSelectionRepository, localRepository, remoteRepository) as T
            }
            modelClass.isAssignableFrom(SelectImageViewModel::class.java) -> {
                SelectImageViewModel(
                    photoSelectionRepository,
                    localRepository,
                    remoteRepository,
                    imageBrowserRepository,
                    recommendRepository,
                ) as T
            }
            modelClass.isAssignableFrom(AlbumViewModel::class.java) -> {
                AlbumViewModel(
                    localRepository,
                    remoteRepository,
                    recommendRepository,
                    imageBrowserRepository,
                    photoSelectionRepository,
                ) as T
            }
            modelClass.isAssignableFrom(StoryViewModel::class.java) -> {
                StoryViewModel(recommendRepository, localRepository, remoteRepository, imageBrowserRepository) as T
            }
            modelClass.isAssignableFrom(MyTagsViewModel::class.java) -> {
                MyTagsViewModel(remoteRepository, photoSelectionRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
}
