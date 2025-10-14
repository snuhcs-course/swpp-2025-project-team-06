package com.example.momentag.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.momentag.data.SessionManager
import com.example.momentag.network.RetrofitInstance
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.repository.TokenRepository
import kotlin.uuid.ExperimentalUuidApi

/**
 * ViewModelFactory
 *
 * ViewModel 생성을 위한 Factory
 * - 의존성 주입을 수동으로 처리
 * - SessionStore, TokenRepository, RemoteRepository 생성 및 주입
 */
class ViewModelFactory(
    private val context: Context,
) : ViewModelProvider.Factory {
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

    // SearchRepository (검색 비즈니스 로직)
    private val searchRepository by lazy {
        com.example.momentag.repository.SearchRepository(
            RetrofitInstance.getApiService(context.applicationContext),
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(ServerViewModel::class.java) -> {
                // RemoteRepository는 Feature API만 담당
                ServerViewModel(
                    RemoteRepository(RetrofitInstance.getApiService(context.applicationContext)),
                ) as T
            }
            modelClass.isAssignableFrom(LocalViewModel::class.java) -> {
                LocalViewModel(LocalRepository(context.applicationContext)) as T
            }
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                // AuthViewModel은 TokenRepository만 의존
                AuthViewModel(tokenRepository) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                // SearchViewModel은 SearchRepository에 의존
                SearchViewModel(searchRepository, context.applicationContext) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
}
