package com.minogatv.box.core.database.di

import android.content.Context
import androidx.room.Room
import com.minogatv.box.core.database.MinogaDatabase
import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.database.dao.ChannelSettingsDao
import com.minogatv.box.core.database.dao.DownloadedContentDao
import com.minogatv.box.core.database.dao.EpgDao
import com.minogatv.box.core.database.dao.FavoriteDao
import com.minogatv.box.core.database.dao.PlaylistDao
import com.minogatv.box.core.database.dao.UserProfileDao
import com.minogatv.box.core.database.dao.WatchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides the Room database and all DAOs as singletons.
 *
 * Installation: [SingletonComponent] — the database lives as long as the process.
 *
 * The database is opened with [Room.databaseBuilder] (not in-memory) and the
 * [MinogaDatabase.prepopulateCallback] is attached to seed the default profile.
 *
 * Fallback to destructive migration is **not** enabled — instead, proper
 * [androidx.room.migration.Migration] objects must be added for every schema bump.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMinogaDatabase(
        @ApplicationContext context: Context,
    ): MinogaDatabase = Room.databaseBuilder(
        context,
        MinogaDatabase::class.java,
        MinogaDatabase.DATABASE_NAME,
    )
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addCallback(MinogaDatabase.prepopulateCallback)
        .addCallback(object : androidx.room.RoomDatabase.Callback() {
            override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onOpen(db)
                // WAL is set above; these pragma tune write latency on Android TV eMMC storage.
                // synchronous=NORMAL: safe with WAL — flushes on each WAL checkpoint, not every write.
                // cache_size=-16000: 16 MB page cache in RAM (negative = kilobytes).
                // temp_store=MEMORY: use RAM for temp tables during complex EPG queries.
                db.execSQL("PRAGMA synchronous = NORMAL")
                db.execSQL("PRAGMA cache_size = -16000")
                db.execSQL("PRAGMA temp_store = MEMORY")
            }
        })
        .addMigrations(MinogaDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigrationOnDowngrade()
        .build()

    // ─── DAO bindings ─────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun providePlaylistDao(db: MinogaDatabase): PlaylistDao = db.playlistDao()

    @Provides
    @Singleton
    fun provideChannelDao(db: MinogaDatabase): ChannelDao = db.channelDao()

    @Provides
    @Singleton
    fun provideChannelSettingsDao(db: MinogaDatabase): ChannelSettingsDao =
        db.channelSettingsDao()

    @Provides
    @Singleton
    fun provideEpgDao(db: MinogaDatabase): EpgDao = db.epgDao()

    @Provides
    @Singleton
    fun provideFavoriteDao(db: MinogaDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    @Singleton
    fun provideWatchHistoryDao(db: MinogaDatabase): WatchHistoryDao = db.watchHistoryDao()

    @Provides
    @Singleton
    fun provideDownloadedContentDao(db: MinogaDatabase): DownloadedContentDao =
        db.downloadedContentDao()

    @Provides
    @Singleton
    fun provideUserProfileDao(db: MinogaDatabase): UserProfileDao = db.userProfileDao()
}
