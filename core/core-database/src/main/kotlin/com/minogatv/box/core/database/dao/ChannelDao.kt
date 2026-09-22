package com.minogatv.box.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.minogatv.box.core.database.entity.ChannelEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [ChannelEntity] — channel list queries, search, and sort management.
 */
@Dao
interface ChannelDao {

    // ─── Write ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: ChannelEntity): Long

    /**
     * Bulk upsert — used when importing a full M3U playlist.
     * REPLACE strategy updates changed fields without breaking FK-referencing rows.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Update
    suspend fun update(channel: ChannelEntity): Int

    @Query("DELETE FROM channel WHERE id = :channelId")
    suspend fun deleteById(channelId: Long): Int

    /** Remove all channels for a playlist (called before re-importing). */
    @Query("DELETE FROM channel WHERE playlist_id = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long): Int

    @Query("SELECT * FROM channel")
    suspend fun getAll(): List<ChannelEntity>

    @Query("UPDATE channel SET epg_channel_id = :epgChannelId WHERE id = :channelId")
    suspend fun updateEpgChannelId(channelId: Long, epgChannelId: String): Int

    data class ChannelEpgLogoUpdate(
        val id: Long,
        val epgChannelId: String? = null,
        val logoUrl: String? = null,
    )

    @Transaction
    suspend fun updateEpgAndLogos(updates: List<ChannelEpgLogoUpdate>) {
        updates.chunked(250).forEach { chunk ->
            chunk.forEach { update ->
                if (update.epgChannelId != null) {
                    updateEpgChannelId(update.id, update.epgChannelId)
                }
                if (update.logoUrl != null) {
                    updateLogoUrl(update.id, update.logoUrl)
                }
            }
        }
    }

    @Query("SELECT * FROM channel WHERE playlist_id = :playlistId")
    suspend fun getByPlaylist(playlistId: Long): List<ChannelEntity>

    @Query("DELETE FROM channel WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int

    /** Atomic smart replace: preserves channel IDs, favorites, and settings, and inserts new ones. */
    @Transaction
    suspend fun replaceAll(playlistId: Long, channels: List<ChannelEntity>) {
        val existing = getByPlaylist(playlistId)
        if (existing.isEmpty()) {
            channels.chunked(250).forEach { chunk ->
                upsertAll(chunk)
            }
            return
        }

        val existingByUrl = existing.associateBy { it.streamUrl }
        val existingByName = existing.associateBy { it.name }

        val newChannels = ArrayList<ChannelEntity>(channels.size)
        val matchedIds = HashSet<Long>()

        for (ch in channels) {
            val prev = existingByUrl[ch.streamUrl] ?: existingByName[ch.name]
            if (prev != null) {
                matchedIds.add(prev.id)
                newChannels.add(
                    ch.copy(
                        id = prev.id,
                        isFavorite = prev.isFavorite,
                        isHidden = prev.isHidden,
                        epgChannelId = ch.epgChannelId.ifBlank { prev.epgChannelId },
                        logoUrl = ch.logoUrl ?: prev.logoUrl,
                    ),
                )
            } else {
                newChannels.add(ch)
            }
        }

        val toDelete = existing.map { it.id }.filter { it !in matchedIds }
        if (toDelete.isNotEmpty()) {
            toDelete.chunked(250).forEach { chunk ->
                deleteByIds(chunk)
            }
        }

        newChannels.chunked(250).forEach { chunk ->
            upsertAll(chunk)
        }
    }

    // ─── Visibility / hide ────────────────────────────────────────────────────

    @Query("UPDATE channel SET is_hidden = :hidden WHERE id = :channelId")
    suspend fun setHidden(channelId: Long, hidden: Boolean): Int

    // ─── Sort order ───────────────────────────────────────────────────────────

    @Query("UPDATE channel SET sort_order = :sortOrder WHERE id = :channelId")
    suspend fun updateSortOrder(channelId: Long, sortOrder: Int): Int

    /** Bulk sort update — called after drag-and-drop reorder. */
    @Transaction
    suspend fun updateSortOrders(updates: List<Pair<Long, Int>>) {
        updates.forEach { (id, order) -> updateSortOrder(id, order) }
    }

    // ─── Denormalised favourite flag ──────────────────────────────────────────

    @Query("UPDATE channel SET is_favorite = :isFavorite WHERE id = :channelId")
    suspend fun setFavoriteFlag(channelId: Long, isFavorite: Boolean): Int

    @Query("UPDATE channel SET is_hidden = :hidden WHERE id = :channelId")
    suspend fun setHiddenFlag(channelId: Long, hidden: Boolean): Int

    @Query("UPDATE channel SET logo_url = :logoUrl WHERE id = :channelId")
    suspend fun updateLogoUrl(channelId: Long, logoUrl: String): Int

    @androidx.room.Transaction
    suspend fun updateLogoUrls(updates: List<Pair<Long, String>>) {
        for ((id, logo) in updates) {
            updateLogoUrl(id, logo)
        }
    }

    @Query(
        """
        SELECT c.* FROM channel c
        WHERE c.is_hidden = 0
          AND (
            c.epg_channel_id = '' 
            OR NOT EXISTS (
                SELECT 1 FROM epg_program ep 
                WHERE ep.channel_epg_id = c.epg_channel_id 
                  AND ep.end_ms > :nowMs
            )
          )
        ORDER BY c.sort_order ASC
        """,
    )
    suspend fun getChannelsWithoutEpg(nowMs: Long): List<ChannelEntity>

    @Query("SELECT * FROM channel WHERE is_hidden = 0 AND (logo_url IS NULL OR logo_url = '')")
    suspend fun getChannelsWithoutLogo(): List<ChannelEntity>

    @Query("SELECT * FROM channel WHERE is_hidden = 0 AND (logo_url LIKE 'http://%' OR logo_url LIKE 'https://%')")
    suspend fun getChannelsWithRemoteLogo(): List<ChannelEntity>

    @Query("SELECT * FROM channel WHERE id = :id")
    suspend fun getById(id: Long): ChannelEntity?

    @Query("SELECT * FROM channel WHERE id = :id")
    fun observeById(id: Long): Flow<ChannelEntity?>

    // ─── Read (reactive) ─────────────────────────────────────────────────────

    /**
     * Observe all visible channels for a playlist, sorted by drag-and-drop order then name.
     */
    @Query(
        """
        SELECT * FROM channel
        WHERE playlist_id = :playlistId AND is_hidden = 0
        ORDER BY sort_order ASC, name ASC
        """,
    )
    fun observeByPlaylist(playlistId: Long): Flow<List<ChannelEntity>>

    /**
     * Observe all visible channels across all playlists for a profile.
     * JOIN with playlist table to filter by profile without a FK chain.
     */
    @Query(
        """
        SELECT c.* FROM channel c
        INNER JOIN playlist p ON c.playlist_id = p.id
        WHERE p.profile_id = :profileId
          AND p.is_enabled = 1
          AND c.is_hidden = 0
        ORDER BY c.sort_order ASC, c.name ASC
        """,
    )
    fun observeAllByProfile(profileId: Long): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT COUNT(c.id) FROM channel c
        INNER JOIN playlist p ON c.playlist_id = p.id
        WHERE p.profile_id = :profileId
          AND p.is_enabled = 1
          AND c.is_hidden = 0
        """,
    )
    fun observeTotalCountByProfile(profileId: Long): Flow<Int>


    @Query(
        """
        SELECT c.* FROM channel c
        INNER JOIN playlist p ON c.playlist_id = p.id
        WHERE p.profile_id = :profileId
          AND p.is_enabled = 1
          AND c.is_hidden = 0
        ORDER BY c.sort_order ASC, c.name ASC
        """,
    )
    suspend fun getAllByProfile(profileId: Long): List<ChannelEntity>

    /**
     * Observe all channels in a specific group/category for a profile.
     */
    @Query(
        """
        SELECT c.* FROM channel c
        INNER JOIN playlist p ON c.playlist_id = p.id
        WHERE p.profile_id = :profileId
          AND p.is_enabled = 1
          AND c.group_title = :groupTitle
          AND c.is_hidden = 0
        ORDER BY c.sort_order ASC, c.name ASC
        """,
    )
    fun observeByGroup(profileId: Long, groupTitle: String): Flow<List<ChannelEntity>>

    @Query(
        """
        SELECT c.* FROM channel c
        INNER JOIN playlist p ON c.playlist_id = p.id
        WHERE p.profile_id = :profileId
          AND p.is_enabled = 1
          AND c.group_title = :groupTitle
          AND c.is_hidden = 0
        ORDER BY c.sort_order ASC, c.name ASC
        """,
    )
    suspend fun getByGroup(profileId: Long, groupTitle: String): List<ChannelEntity>

    /**
     * Observe distinct group titles (categories/folders) for a profile, sorted by
     * the minimum sort_order of any channel in that group.
     */
    @Query(
        """
        SELECT DISTINCT c.group_title, COUNT(c.id) as channel_count
        FROM channel c
        INNER JOIN playlist p ON c.playlist_id = p.id
        WHERE p.profile_id = :profileId
          AND p.is_enabled = 1
          AND c.is_hidden = 0
          AND c.group_title != ''
        GROUP BY c.group_title
        ORDER BY MIN(c.sort_order) ASC
        """,
    )
    fun observeGroupsWithCount(profileId: Long): Flow<List<GroupWithCount>>

    // ─── Read (suspend) ───────────────────────────────────────────────────────


    @Query("SELECT * FROM channel WHERE epg_channel_id = :epgId LIMIT 1")
    suspend fun getByEpgId(epgId: String): ChannelEntity?

    // ─── Name search (non-FTS, for simple substring matching) ─────────────────

    /**
     * Fast LIKE search on channel name within a profile's channels.
     * For full-text EPG search use [EpgDao.searchFts].
     */
    @Query(
        """
        SELECT c.* FROM channel c
        INNER JOIN playlist p ON c.playlist_id = p.id
        WHERE p.profile_id = :profileId
          AND p.is_enabled = 1
          AND c.name LIKE '%' || :query || '%'
        ORDER BY c.name ASC
        LIMIT 100
        """,
    )
    fun searchByName(profileId: Long, query: String): Flow<List<ChannelEntity>>

    /** One-shot suspend version of [searchByName] for imperative callers. */
    @Query(
        """
        SELECT c.* FROM channel c
        INNER JOIN playlist p ON c.playlist_id = p.id
        WHERE p.profile_id = :profileId
          AND p.is_enabled = 1
          AND c.name LIKE '%' || :query || '%'
        ORDER BY c.name ASC
        LIMIT 100
        """,
    )
    suspend fun searchByNameOnce(profileId: Long, query: String): List<ChannelEntity>
}

/** Projection for group/folder list with channel counts. */
data class GroupWithCount(
    val group_title: String,
    val channel_count: Int,
)
