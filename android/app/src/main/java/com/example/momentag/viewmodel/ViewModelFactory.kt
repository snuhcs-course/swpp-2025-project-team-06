package com.example.momentag.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.momentag.data.SessionManager
import com.example.momentag.network.RetrofitInstance
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import kotlin.uuid.ExperimentalUuidApi

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @OptIn(ExperimentalUuidApi::class)
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ServerViewModel::class.java) -> {
                ServerViewModel(RemoteRepository(RetrofitInstance.getApiService(context.applicationContext))) as T
            }
            modelClass.isAssignableFrom(LocalViewModel::class.java) -> {
                LocalViewModel(LocalRepository(context.applicationContext)) as T
            }
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(
                    RemoteRepository(RetrofitInstance.getApiService(context.applicationContext)),
                    SessionManager(context.applicationContext)
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
