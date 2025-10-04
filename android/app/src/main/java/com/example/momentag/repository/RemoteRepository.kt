package com.example.momentag.repository

import com.example.momentag.model.Tag
import com.example.momentag.model.Photo
import com.example.momentag.network.ApiService

class RemoteRepository(private val apiService: ApiService) {
    suspend fun getAllTags(): List<Tag> = apiService.getHomeTags()
    suspend fun getPhotosByTag(tagName: String): List<Photo> = apiService.getPhotosByTag(tagName)
}