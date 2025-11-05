package com.example.momentag.viewmodel

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momentag.model.HomeScreenUiState
import com.example.momentag.repository.LocalRepository
import com.example.momentag.repository.RemoteRepository
import com.example.momentag.worker.AlbumUploadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class PhotoViewModel(
    private val remoteRepository: RemoteRepository,
    private val localRepository: LocalRepository,
    private val albumUploadJobCount: StateFlow<Int>,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeScreenUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            albumUploadJobCount.collect { count ->
                _uiState.update { it.copy(isLoading = count > 0) }
            }
        }
    }

    fun uploadPhotos() {
        viewModelScope.launch {
            // set to loading state
            _uiState.update { it.copy(isLoading = true, userMessage = null, isUploadSuccess = false) }

            try {
                val photoUploadData = localRepository.getPhotoUploadRequest()
                val response = remoteRepository.uploadPhotos(photoUploadData)

                // update ui state
                val message: String =
                    when (response) {
                        is RemoteRepository.Result.Success -> {
                            when (response.data) {
                                202 -> {
                                    _uiState.update { it.copy(isUploadSuccess = true) }
                                    "Success"
                                }
                                else -> "Upload successful (Code: ${response.data})"
                            }
                        }
                        is RemoteRepository.Result.BadRequest -> "Request form mismatch"
                        is RemoteRepository.Result.Unauthorized -> "The refresh token is expired"
                        is RemoteRepository.Result.Error -> "Unexpected error: ${response.code}"
                        is RemoteRepository.Result.Exception -> "Unknown error: ${response.e.message}"
                        is RemoteRepository.Result.NetworkError -> "Network error"
                    }

                _uiState.update { it.copy(isLoading = false, userMessage = message) }
            } catch (e: IOException) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Network error") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, userMessage = "Unknown error: ${e.message}") }
            }
        }
    }

    fun uploadPhotosForAlbums(
        albumIds: Set<Long>,
        context: Context,
    ) {
        if (albumIds.isEmpty()) {
            return
        }
        // 1. Service에 앨범 ID를 전달할 Intent 생성
        albumIds.forEach { albumId ->
            val intent =
                Intent(context, AlbumUploadService::class.java).apply {
                    putExtra("ALBUM_ID_KEY", albumId)
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        // 3. UI 상태 업데이트 (Service 시작을 알림)
        // 참고:HomeScreen과 uiState를 공유하므로, 두 업로드가 동시에 실행되면
        // 상태 메시지가 겹칠 수 있지만, 지금 구조에선 이게 최선입니다.
        _uiState.update { it.copy(userMessage = "백그라운드 업로드가 시작되었습니다.") }
    }

    // to show message and reset
    fun userMessageShown() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
