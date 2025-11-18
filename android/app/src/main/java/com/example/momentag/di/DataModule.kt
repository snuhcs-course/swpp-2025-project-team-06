package com.example.momentag.di

import com.example.momentag.data.SessionManager
import com.example.momentag.data.SessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindSessionStore(sessionManager: SessionManager): SessionStore
}
