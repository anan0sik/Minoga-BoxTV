package com.minogatv.box.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.minogatv.box.core.database.entity.DownloadedContentEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [DownloadedContentEntity] — offline download metadata.
 */
@Dao
interface DownloadedContentDao {

    // ─── Write ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadedContentEntity): Long

    @Update
    suspend fun update(download: DownloadedContentEntity): Int

    @Query("DELETE FROM downloaded_content WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM downloaded_content WHERE profile_id = :profileId")
    suspend fun deleteAllForProfile(profileId: Long): Int

    @Query("UPDATE downloaded_content SET progress_percent = :percent WHERE id = :id")
    suspend fun updateProgress(id: Long, percent: Int): Int

    // ─── Read (reactive) ─────────────────────────────────────────────────────

    @Query(
        """
        SELECT * FROM downloaded_content
        WHERE profile_id = :profileId
        ORDER BY created_at DESC
        """,
    )
    fun observeAllForProfile(profileId: Long): Flow<List<DownloadedContentEntity>>

    // ─── Read (suspend) ───────────────────────────────────────────────────────

    @Query("SELECT * FROM downloaded_content WHERE id = :id")
    suspend fun getById(id: Long): DownloadedContentEntity?

    @Query("SELECT * FROM downloaded_content WHERE download_id = :downloadId LIMIT 1")
    suspend fun getByDownloadId(downloadId: String): DownloadedContentEntity?

    /** Fetch expired downloads so the cleanup job can delete their files. */
    @Query(
        """
        SELECT * FROM downloaded_content
        WHERE expires_at IS NOT NULL AND expires_at < :nowMs
        """,
    )
    suspend fun getExpired(nowMs: Long): List<DownloadedContentEntity>
}
