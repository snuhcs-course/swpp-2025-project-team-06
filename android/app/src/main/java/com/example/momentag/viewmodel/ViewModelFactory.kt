package com.example.momentag.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.momentag.data.SessionManager
import com.example.momentag.network.RetrofitInstance
import com.example.momentag.repository.DraftTagRepository
import com.example.momentag.repository.ImageBrowserRepository
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RecommendRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.repository.TokenRepository
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

    // 싱글톤으로 관리되는 SessionStore (앱 전체에서 하나만 존재)
    private val sessionStore by lazy {
        SessionManager.getInstance(context.applicationContext)
    }

    // TokenRepository (인증 비즈니스 로직)
    private val tokenRepository by lazy {
        TokenRepository(
            RetrofitInstance.getApiService(context.applicationContext),
            sessionStore,
        )
    }

    // RemoteRepository (Feature API)
    private val remoteRepository by lazy {
        RemoteRepository(RetrofitInstance.getApiService(context.applicationContext))
    }

    // LocalRepository (MediaStore 접근)
    private val localRepository by lazy {
        LocalRepository(context.applicationContext)
    }

    // SearchRepository (검색 비즈니스 로직)
    private val searchRepository by lazy {
        com.example.momentag.repository.SearchRepository(
            RetrofitInstance.getApiService(context.applicationContext),
        )
    }

    // RecommendRepository (추천 비즈니스 로직)
    private val recommendRepository by lazy {
        RecommendRepository(RetrofitInstance.getApiService(context.applicationContext))
    }

    // ImageBrowserRepository (이미지 브라우징 세션 관리)
    private val imageBrowserRepository by lazy {
        ImageBrowserRepository()
    }

    // DraftTagRepository (태그 생성 워크플로우 상태 관리)
    private val draftTagRepository by lazy {
        DraftTagRepository()
    }

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(localRepository, remoteRepository) as T
            }
            modelClass.isAssignableFrom(LocalViewModel::class.java) -> {
                LocalViewModel(localRepository, imageBrowserRepository) as T
            }
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(tokenRepository) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(searchRepository, draftTagRepository, localRepository, imageBrowserRepository) as T
            }
            modelClass.isAssignableFrom(ImageDetailViewModel::class.java) -> {
                ImageDetailViewModel(imageBrowserRepository, remoteRepository, recommendRepository) as T
            }
            modelClass.isAssignableFrom(PhotoViewModel::class.java) -> {
                PhotoViewModel(remoteRepository, localRepository) as T
            }
            modelClass.isAssignableFrom(AddTagViewModel::class.java) -> {
                AddTagViewModel(draftTagRepository, recommendRepository, localRepository, remoteRepository) as T
            }
            modelClass.isAssignableFrom(SelectImageViewModel::class.java) -> {
                SelectImageViewModel(draftTagRepository, localRepository, remoteRepository) as T
            }
            modelClass.isAssignableFrom(AlbumViewModel::class.java) -> {
                AlbumViewModel(localRepository, remoteRepository, recommendRepository, imageBrowserRepository) as T
            }
            modelClass.isAssignableFrom(StoryViewModel::class.java) -> {
                StoryViewModel(recommendRepository, localRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
}
