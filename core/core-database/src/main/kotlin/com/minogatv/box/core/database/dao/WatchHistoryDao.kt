package com.minogatv.box.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minogatv.box.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [WatchHistoryEntity] — playback progress and "Continue Watching".
 *
 * Upsert strategy: on conflict (same profile + channel + vod combination),
 * replace the row so there is always only one position record per item per profile.
 */
@Dao
interface WatchHistoryDao {

    // ─── Write ───────────────────────────────────────────────────────────────

    /**
     * Save or update the playback position for an item.
     * Called every 10 seconds during active playback and on player pause/stop.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM watch_history WHERE profile_id = :profileId")
    suspend fun clearHistory(profileId: Long): Int

    /** Remove entries older than [cutoffMs] (e.g. 30 days ago). */
    @Query("DELETE FROM watch_history WHERE updated_at < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    // ─── Read (reactive) ─────────────────────────────────────────────────────

    /**
     * Observe the most recently watched items for a profile.
     * Used for the "Continue Watching" row on the home screen and the VOD screen.
     */
    @Query(
        """
        SELECT * FROM watch_history
        WHERE profile_id = :profileId
        ORDER BY updated_at DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(profileId: Long, limit: Int = 20): Flow<List<WatchHistoryEntity>>

    /**
     * Observe the playback position for a specific channel.
     * For live TV this is always 0; useful to detect "last watched" channel.
     */
    @Query(
        """
        SELECT * FROM watch_history
        WHERE profile_id = :profileId AND channel_id = :channelId
        LIMIT 1
        """,
    )
    fun observeChannelHistory(profileId: Long, channelId: Long): Flow<WatchHistoryEntity?>

    /**
     * Observe in-progress VOD items (position > 0 and < 95 % of total duration).
     */
    @Query(
        """
        SELECT * FROM watch_history
        WHERE profile_id = :profileId
          AND vod_id IS NOT NULL
          AND position_ms > 0
          AND duration_ms > 0
          AND CAST(position_ms AS REAL) / duration_ms < 0.95
        ORDER BY updated_at DESC
        LIMIT :limit
        """,
    )
    fun observeInProgressVod(profileId: Long, limit: Int = 20): Flow<List<WatchHistoryEntity>>

    // ─── Read (suspend) ───────────────────────────────────────────────────────

    @Query(
        """
        SELECT * FROM watch_history
        WHERE profile_id = :profileId AND channel_id = :channelId
        LIMIT 1
        """,
    )
    suspend fun getChannelHistory(profileId: Long, channelId: Long): WatchHistoryEntity?

    @Query(
        """
        SELECT * FROM watch_history
        WHERE profile_id = :profileId AND vod_id = :vodId
        LIMIT 1
        """,
    )
    suspend fun getVodHistory(profileId: Long, vodId: String): WatchHistoryEntity?

    /** Returns the last watched channel ID for a profile (for "resume on startup"). */
    @Query(
        """
        SELECT channel_id FROM watch_history
        WHERE profile_id = :profileId AND channel_id IS NOT NULL
        ORDER BY updated_at DESC
        LIMIT 1
        """,
    )
    suspend fun getLastWatchedChannelId(profileId: Long): Long?
}
