package com.example.momentag.di

import android.content.Context
import com.example.momentag.R
import com.example.momentag.data.SessionStore
import com.example.momentag.network.ApiService
import com.example.momentag.network.AuthInterceptor
import com.example.momentag.network.SslHelper
import com.example.momentag.network.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideAuthInterceptor(sessionStore: SessionStore): AuthInterceptor = AuthInterceptor(sessionStore)

    @Provides
    @Singleton
    fun provideTokenAuthenticator(
        @ApplicationContext context: Context,
        sessionStore: SessionStore,
    ): TokenAuthenticator = TokenAuthenticator(context, sessionStore)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient {
        val loggingInterceptor =
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

        return SslHelper
            .configureToTrustCertificate(
                OkHttpClient.Builder(),
                context,
            ).addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): Retrofit {
        val baseUrl = context.getString(R.string.API_BASE_URL)

        return Retrofit
            .Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)
}
