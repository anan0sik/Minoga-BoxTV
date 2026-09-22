package com.minogatv.box.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.minogatv.box.core.database.entity.ChannelEntity
import com.minogatv.box.core.database.entity.FavoriteChannelEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [FavoriteChannelEntity] — profile-scoped favourite channels.
 *
 * Design notes:
 * - The composite PK (profile_id, channel_id) prevents duplicate favourites.
 * - After adding/removing a favourite, [ChannelDao.setFavoriteFlag] should be
 *   called to keep the denormalised [ChannelEntity.isFavorite] flag in sync.
 */
@Dao
interface FavoriteDao {

    // ─── Write ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(favorite: FavoriteChannelEntity)

    @Query(
        "DELETE FROM favorite_channel WHERE profile_id = :profileId AND channel_id = :channelId",
    )
    suspend fun removeFavorite(profileId: Long, channelId: Long): Int

    @Query("DELETE FROM favorite_channel WHERE profile_id = :profileId")
    suspend fun clearFavorites(profileId: Long): Int

    @Query(
        """
        UPDATE favorite_channel
        SET sort_order = :sortOrder
        WHERE profile_id = :profileId AND channel_id = :channelId
        """,
    )
    suspend fun updateSortOrder(profileId: Long, channelId: Long, sortOrder: Int): Int

    /** Bulk sort update after drag-and-drop reorder of the Favorites folder. */
    @Transaction
    suspend fun updateSortOrders(profileId: Long, orderedChannelIds: List<Long>) {
        orderedChannelIds.forEachIndexed { index, channelId ->
            updateSortOrder(profileId, channelId, index)
        }
    }

    // ─── Read (reactive) ─────────────────────────────────────────────────────

    /**
     * Observe the list of favourite [ChannelEntity] rows for [profileId].
     *
     * Uses a JOIN so callers get full channel data (name, logo, stream URL etc.)
     * sorted by the user's custom drag order.
     */
    @Query(
        """
        SELECT c.* FROM channel c
        INNER JOIN favorite_channel fc ON c.id = fc.channel_id
        WHERE fc.profile_id = :profileId
        ORDER BY fc.sort_order ASC, c.name ASC
        """,
    )
    fun observeFavoriteChannels(profileId: Long): Flow<List<ChannelEntity>>

    /**
     * Observe raw favourite join-table rows (used by the VM to check counts).
     */
    @Query(
        """
        SELECT * FROM favorite_channel
        WHERE profile_id = :profileId
        ORDER BY sort_order ASC
        """,
    )
    fun observeFavorites(profileId: Long): Flow<List<FavoriteChannelEntity>>

    /**
     * Observe whether a specific channel is in the current profile's favourites.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM favorite_channel
            WHERE profile_id = :profileId AND channel_id = :channelId
        )
        """,
    )
    fun observeIsFavorite(profileId: Long, channelId: Long): Flow<Boolean>

    // ─── Read (suspend) ───────────────────────────────────────────────────────

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM favorite_channel
            WHERE profile_id = :profileId AND channel_id = :channelId
        )
        """,
    )
    suspend fun isFavorite(profileId: Long, channelId: Long): Boolean

    @Query(
        "SELECT COUNT(*) FROM favorite_channel WHERE profile_id = :profileId",
    )
    suspend fun getFavoriteCount(profileId: Long): Int

    @Query(
        "SELECT channel_id FROM favorite_channel WHERE profile_id = :profileId",
    )
    suspend fun getFavoriteIds(profileId: Long): List<Long>
}
