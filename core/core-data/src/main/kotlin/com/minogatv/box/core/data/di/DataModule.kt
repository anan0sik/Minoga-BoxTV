package com.minogatv.box.core.data.di

import com.minogatv.box.core.data.repository.ChannelRepository
import com.minogatv.box.core.data.repository.ChannelRepositoryImpl
import com.minogatv.box.core.data.repository.EpgRepository
import com.minogatv.box.core.data.repository.EpgRepositoryImpl
import com.minogatv.box.core.data.repository.PlaylistRepository
import com.minogatv.box.core.data.repository.PlaylistRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding repository interfaces to their implementations.
 *
 * Uses `@Binds` (zero-overhead — no object instantiation at runtime) since all
 * implementations are `@Singleton`-scoped and injected via `@Inject constructor`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository

    @Binds
    @Singleton
    abstract fun bindEpgRepository(impl: EpgRepositoryImpl): EpgRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository
}
