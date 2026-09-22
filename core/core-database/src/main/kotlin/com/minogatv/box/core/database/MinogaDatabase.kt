package com.minogatv.box.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.database.dao.ChannelSettingsDao
import com.minogatv.box.core.database.dao.DownloadedContentDao
import com.minogatv.box.core.database.dao.EpgDao
import com.minogatv.box.core.database.dao.FavoriteDao
import com.minogatv.box.core.database.dao.PlaylistDao
import com.minogatv.box.core.database.dao.UserProfileDao
import com.minogatv.box.core.database.dao.WatchHistoryDao
import com.minogatv.box.core.database.entity.ChannelEntity
import com.minogatv.box.core.database.entity.ChannelSettingsEntity
import com.minogatv.box.core.database.entity.DownloadedContentEntity
import com.minogatv.box.core.database.entity.EpgFtsEntity
import com.minogatv.box.core.database.entity.EpgProgramEntity
import com.minogatv.box.core.database.entity.FavoriteChannelEntity
import com.minogatv.box.core.database.entity.PlaylistEntity
import com.minogatv.box.core.database.entity.UserProfileEntity
import com.minogatv.box.core.database.entity.WatchHistoryEntity
import com.minogatv.box.core.model.enums.ProfileType

/**
 * The single Room database for the Minoga TV Box app.
 *
 * ## Schema version history
 * | Version | Change |
 * |---------|--------|
 * | 1       | Initial schema |
 *
 * ## Table list
 * - `user_profile`        — [UserProfileEntity]
 * - `playlist`            — [PlaylistEntity]
 * - `channel`             — [ChannelEntity]
 * - `channel_settings`    — [ChannelSettingsEntity]
 * - `epg_program`         — [EpgProgramEntity]
 * - `epg_fts` (virtual)   — [EpgFtsEntity] (FTS4 content table)
 * - `favorite_channel`    — [FavoriteChannelEntity]
 * - `watch_history`       — [WatchHistoryEntity]
 * - `downloaded_content`  — [DownloadedContentEntity]
 *
 * Access via the Hilt-provided singleton in [di.DatabaseModule].
 * Never instantiate directly.
 */
@Database(
    entities = [
        UserProfileEntity::class,
        PlaylistEntity::class,
        ChannelEntity::class,
        ChannelSettingsEntity::class,
        EpgProgramEntity::class,
        EpgFtsEntity::class,
        FavoriteChannelEntity::class,
        WatchHistoryEntity::class,
        DownloadedContentEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MinogaDatabase : RoomDatabase() {

    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun channelSettingsDao(): ChannelSettingsDao
    abstract fun epgDao(): EpgDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun downloadedContentDao(): DownloadedContentDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        const val DATABASE_NAME = "minoga_tv.db"

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_program_end_ms_start_ms` ON `epg_program` (`end_ms`, `start_ms`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_program_start_ms_end_ms` ON `epg_program` (`start_ms`, `end_ms`)")
            }
        }

        /**
         * Pre-populates the database with the default "Main" profile the very
         * first time the app is installed. This callback is invoked on a
         * background thread by Room after the database is created.
         */
        val prepopulateCallback = object : Callback() {
            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                super.onCreate(db)
                // Insert the default profile directly via raw SQL because DAOs
                // are not accessible during the Room Callback.
                db.execSQL(
                    """
                    INSERT INTO user_profile (name, type, is_active, created_at)
                    VALUES ('Main', '${ProfileType.MAIN.name}', 1, ${System.currentTimeMillis()})
                    """.trimIndent(),
                )
            }
        }
    }
}
