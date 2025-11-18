package com.example.momentag.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * AppStateModule
 *
 * Provides application-level state flows for communication between
 * Workers and ViewModels
 */
@Module
@InstallIn(SingletonComponent::class)
object AppStateModule {
    /**
     * Provides StateFlow for tracking album upload job count
     * Used by AlbumUploadWorker to signal upload progress
     */
    @Provides
    @Singleton
    @AlbumUploadJobCountQualifier
    fun provideAlbumUploadJobCountMutable(): MutableStateFlow<Int> = MutableStateFlow(0)

    @Provides
    @Singleton
    fun provideAlbumUploadJobCount(
        @AlbumUploadJobCountQualifier mutableFlow: MutableStateFlow<Int>,
    ): StateFlow<Int> = mutableFlow.asStateFlow()

    /**
     * Provides SharedFlow for album upload success events
     * Emits album ID when upload completes successfully
     */
    @Provides
    @Singleton
    @AlbumUploadSuccessEventQualifier
    fun provideAlbumUploadSuccessEventMutable(): MutableSharedFlow<Long> = MutableSharedFlow()

    @Provides
    @Singleton
    fun provideAlbumUploadSuccessEvent(
        @AlbumUploadSuccessEventQualifier mutableFlow: MutableSharedFlow<Long>,
    ): SharedFlow<Long> = mutableFlow.asSharedFlow()
}

/**
 * Qualifier annotations for disambiguating MutableStateFlow/StateFlow pairs
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AlbumUploadJobCountQualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AlbumUploadSuccessEventQualifier
