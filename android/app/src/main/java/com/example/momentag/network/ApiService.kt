package com.example.momentag.network

import com.example.momentag.model.Tag
import com.example.momentag.model.Photo
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface ApiService {
    @GET("home/tags")
    suspend fun getHomeTags(): List<Tag>

    @GET("tags/{tagName}")
    suspend fun getPhotosByTag(@Path("tagName") tagName: String): List<Photo>
}
object RetrofitInstance {
    private const val BASE_URL = "https://"
    /*
    * TODO : INSERT URL
     */

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}