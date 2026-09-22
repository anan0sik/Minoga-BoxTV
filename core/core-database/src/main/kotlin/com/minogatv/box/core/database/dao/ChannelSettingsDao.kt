package com.minogatv.box.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minogatv.box.core.database.entity.ChannelSettingsEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [ChannelSettingsEntity] — per-channel player/EPG overrides.
 */
@Dao
interface ChannelSettingsDao {

    // ─── Write ───────────────────────────────────────────────────────────────

    /** Insert or fully replace the settings row for a channel. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: ChannelSettingsEntity)

    @Query("DELETE FROM channel_settings WHERE channel_id = :channelId")
    suspend fun deleteByChannelId(channelId: Long): Int

    // ─── Read (reactive) ─────────────────────────────────────────────────────

    /**
     * Observe settings for a specific channel.
     * Emits null if no overrides exist (caller uses defaults from global settings).
     */
    @Query("SELECT * FROM channel_settings WHERE channel_id = :channelId")
    fun observeByChannelId(channelId: Long): Flow<ChannelSettingsEntity?>

    // ─── Read (suspend) ───────────────────────────────────────────────────────

    @Query("SELECT * FROM channel_settings WHERE channel_id = :channelId")
    suspend fun getByChannelId(channelId: Long): ChannelSettingsEntity?

    // ─── Partial updates ──────────────────────────────────────────────────────

    @Query("UPDATE channel_settings SET epg_channel_id_override = :epgId WHERE channel_id = :channelId")
    suspend fun updateEpgMapping(channelId: Long, epgId: String?): Int

    @Query("UPDATE channel_settings SET epg_time_shift_hours = :hours WHERE channel_id = :channelId")
    suspend fun updateEpgTimeShift(channelId: Long, hours: Float): Int

    @Query("UPDATE channel_settings SET is_locked = :locked WHERE channel_id = :channelId")
    suspend fun setLocked(channelId: Long, locked: Boolean): Int

    @Query("UPDATE channel_settings SET custom_stream_url = :url WHERE channel_id = :channelId")
    suspend fun setCustomStreamUrl(channelId: Long, url: String?): Int
}
