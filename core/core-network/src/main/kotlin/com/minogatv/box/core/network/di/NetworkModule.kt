package com.minogatv.box.core.network.di

import com.minogatv.box.core.network.ProxyOkHttpClientFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt module providing base network components.
 *
 * Playlist-specific [OkHttpClient] instances (with proxy + custom user-agent)
 * are NOT provided here — they are created on-demand by [ProxyOkHttpClientFactory]
 * which is itself `@Singleton`-scoped.
 *
 * This module provides:
 * - A shared [Moshi] instance
 * - A base [OkHttpClient] for internal/generic API calls (e.g. TMDB)
 * - A base [Retrofit] builder factory method (used by repositories to build service instances)
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    /**
     * Base OkHttpClient used for non-IPTV API calls (TMDB, cloud sync, etc.).
     * Does NOT have a custom User-Agent or proxy — those are playlist-specific.
     */
    @Provides
    @Singleton
    @BaseOkHttpClient
    fun provideBaseOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                // TODO: gate this on BuildConfig.DEBUG
                level = HttpLoggingInterceptor.Level.NONE
            },
        )
        .build()

    @Provides
    @Singleton
    fun provideMoshiConverterFactory(moshi: Moshi): MoshiConverterFactory =
        MoshiConverterFactory.create(moshi)

    /**
     * Expose [ProxyOkHttpClientFactory] as a singleton.
     * Repositories inject this to build per-playlist clients.
     */
    @Provides
    @Singleton
    fun provideProxyClientFactory(): ProxyOkHttpClientFactory = ProxyOkHttpClientFactory()
}

/**
 * Qualifier for the base (non-proxy) [OkHttpClient].
 * Used in classes that need a plain client not associated with any playlist.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseOkHttpClient
