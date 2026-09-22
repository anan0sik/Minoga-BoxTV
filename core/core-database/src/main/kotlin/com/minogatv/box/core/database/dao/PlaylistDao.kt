package com.minogatv.box.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.minogatv.box.core.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [PlaylistEntity] — IPTV provider / playlist management.
 */
@Dao
interface PlaylistDao {

    // ─── Write ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity): Int

    @Delete
    suspend fun delete(playlist: PlaylistEntity): Int

    @Query("DELETE FROM playlist WHERE id = :playlistId")
    suspend fun deleteById(playlistId: Long): Int

    // ─── Read (reactive) ─────────────────────────────────────────────────────

    /**
     * Observe all playlists belonging to [profileId], ordered by [PlaylistEntity.sortOrder].
     * Emits a new list whenever any playlist in the profile changes.
     */
    @Query(
        """
        SELECT * FROM playlist
        WHERE profile_id = :profileId
        ORDER BY sort_order ASC, name ASC
        """,
    )
    fun observeByProfile(profileId: Long): Flow<List<PlaylistEntity>>

    /**
     * Observe only enabled playlists for [profileId].
     * Used by the channel aggregation logic to skip disabled providers.
     */
    @Query(
        """
        SELECT * FROM playlist
        WHERE profile_id = :profileId AND is_enabled = 1
        ORDER BY sort_order ASC
        """,
    )
    fun observeEnabledByProfile(profileId: Long): Flow<List<PlaylistEntity>>

    // ─── Read (suspend) ───────────────────────────────────────────────────────

    @Query("SELECT * FROM playlist WHERE id = :playlistId")
    suspend fun getById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlist WHERE profile_id = :profileId ORDER BY sort_order ASC")
    suspend fun getAllByProfile(profileId: Long): List<PlaylistEntity>

    @Query("SELECT * FROM playlist ORDER BY sort_order ASC")
    suspend fun getAll(): List<PlaylistEntity>

    // ─── Sort order update ────────────────────────────────────────────────────

    @Query("UPDATE playlist SET sort_order = :sortOrder WHERE id = :playlistId")
    suspend fun updateSortOrder(playlistId: Long, sortOrder: Int): Int

    // ─── Refresh timestamp ────────────────────────────────────────────────────

    @Query("UPDATE playlist SET last_refreshed_at = :timestampMs WHERE id = :playlistId")
    suspend fun updateLastRefreshed(playlistId: Long, timestampMs: Long): Int

    /** Persist the EPG URL discovered in the M3U header back to the playlist row. */
    @Query("UPDATE playlist SET epg_url = :epgUrl WHERE id = :playlistId")
    suspend fun updateEpgUrl(playlistId: Long, epgUrl: String): Int
}
