package com.example.momentag.repository

import com.example.momentag.model.LoginRequest
import com.example.momentag.model.LoginResponse
import com.example.momentag.model.Photo
import com.example.momentag.model.PhotoUploadData
import com.example.momentag.model.RefreshRequest
import com.example.momentag.model.RefreshResponse
import com.example.momentag.model.RegisterRequest
import com.example.momentag.model.RegisterResponse
import com.example.momentag.model.Tag
import com.example.momentag.network.ApiService
import retrofit2.Response

class RemoteRepository(
    private val apiService: ApiService,
) {
    suspend fun getAllTags(): List<Tag> = apiService.getHomeTags()

    suspend fun getPhotosByTag(tagName: String): List<Photo> = apiService.getPhotosByTag(tagName)

    suspend fun login(loginRequest: LoginRequest): Response<LoginResponse> = apiService.login(loginRequest)

    suspend fun register(registerRequest: RegisterRequest): Response<RegisterResponse> = apiService.register(registerRequest)

    suspend fun refreshToken(refreshToken: RefreshRequest): Response<RefreshResponse> = apiService.refreshToken(refreshToken)

    suspend fun logout(refreshToken: RefreshRequest): Response<Unit> = apiService.logout(refreshToken)

    suspend fun uploadPhotos(photoUploadData: PhotoUploadData): Response<Unit> =
        apiService.uploadPhotos(
            photo = photoUploadData.photo,
            metadata = photoUploadData.metadata,
        )
}
