package com.example.momentag.di

import android.content.Context
import com.example.momentag.repository.UploadStateRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideUploadStateRepository(
        @ApplicationContext context: Context,
    ): UploadStateRepository = UploadStateRepository(context)
}
