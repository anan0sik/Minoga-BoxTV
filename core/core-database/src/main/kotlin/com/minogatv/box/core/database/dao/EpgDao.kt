package com.minogatv.box.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.minogatv.box.core.database.entity.EpgProgramEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [EpgProgramEntity] and FTS search via [com.minogatv.box.core.database.entity.EpgFtsEntity].
 *
 * All bulk inserts are REPLACE — XMLTV data is idempotent.
 * Stale EPG pruning keeps the DB size bounded.
 */
@Dao
abstract class EpgDao {

    // ─── Write ───────────────────────────────────────────────────────────────

    /**
     * Bulk upsert EPG programmes — replaces existing rows with matching PK.
     * Called from a background WorkManager job after XMLTV parsing.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(programs: List<EpgProgramEntity>)

    /**
     * Mega-transaction bulk insert: inserts ALL [programs] in a single SQLite transaction.
     * This is the key performance optimization — instead of one transaction per batch
     * (10–50 open/close cycles), the entire EPG import is ONE atomic commit.
     * Chunks into groups of 5000 to stay within SQLite's variable limit.
     *
     * On Android TV eMMC storage: 15 000 rows ~ 1–2 s (vs 15–20 s with per-batch commits).
     */
    @androidx.room.Transaction
    open suspend fun bulkUpsert(programs: List<EpgProgramEntity>) {
        programs.chunked(5000).forEach { chunk -> upsertAll(chunk) }
    }

    /**
     * Delete all EPG events that ended before [cutoffMs].
     * Run nightly to keep the DB lean.
     */
    @Query("DELETE FROM epg_program WHERE end_ms < :cutoffMs")
    abstract suspend fun deleteBeforeTime(cutoffMs: Long): Int

    /** Remove all EPG data for a specific channel — used when a playlist is deleted. */
    @Query("DELETE FROM epg_program WHERE channel_epg_id = :channelEpgId")
    abstract suspend fun deleteByChannelEpgId(channelEpgId: String): Int

    // ─── Read (reactive) ─────────────────────────────────────────────────────

    /**
     * Observe all EPG events for [channelEpgId] within the [fromMs]..[toMs] window,
     * ordered by start time. Typical usage: load 24-hour EPG window for a channel.
     */
    @Query(
        """
        SELECT * FROM epg_program
        WHERE channel_epg_id = :channelEpgId
          AND end_ms > :fromMs
          AND start_ms < :toMs
        ORDER BY start_ms ASC
        """,
    )
    abstract fun observeByChannelAndTimeRange(
        channelEpgId: String,
        fromMs: Long,
        toMs: Long,
    ): Flow<List<EpgProgramEntity>>

    /**
     * Observe the currently airing programme for a channel at [nowMs].
     * Returns null when no EPG data is available.
     */
    @Query(
        """
        SELECT * FROM epg_program
        WHERE channel_epg_id = :channelEpgId
          AND start_ms <= :nowMs
          AND end_ms > :nowMs
        LIMIT 1
        """,
    )
    abstract fun observeCurrentProgram(channelEpgId: String, nowMs: Long): Flow<EpgProgramEntity?>

    /**
     * Observe the next programme after [nowMs] for a channel.
     */
    @Query(
        """
        SELECT * FROM epg_program
        WHERE channel_epg_id = :channelEpgId
          AND start_ms > :nowMs
        ORDER BY start_ms ASC
        LIMIT 1
        """,
    )
    abstract fun observeNextProgram(channelEpgId: String, nowMs: Long): Flow<EpgProgramEntity?>

    // ─── Read (suspend) ───────────────────────────────────────────────────────

    @Query(
        """
        SELECT * FROM epg_program
        WHERE channel_epg_id = :channelEpgId
          AND start_ms <= :nowMs
          AND end_ms > :nowMs
        LIMIT 1
        """,
    )
    abstract suspend fun getCurrentProgram(channelEpgId: String, nowMs: Long): EpgProgramEntity?

    /**
     * Fast batch query to fetch all currently airing programmes at [nowMs].
     * Avoids running thousands of single queries when populating the channel list.
     */
    @Query(
        """
        SELECT * FROM epg_program
        WHERE start_ms <= (:nowMs + 900000) AND end_ms >= (:nowMs - 900000)
        ORDER BY start_ms ASC
        """,
    )
    abstract suspend fun getAllCurrentPrograms(nowMs: Long): List<EpgProgramEntity>

    /**
     * Targeted indexed query for currently airing programmes for a specific set of channel IDs.
     * Uses (channel_epg_id, start_ms, end_ms) index for sub-millisecond execution.
     */
    @Query(
        """
        SELECT * FROM epg_program
        WHERE channel_epg_id IN (:channelEpgIds)
          AND start_ms <= :nowMs
          AND end_ms > :nowMs
        """,
    )
    abstract suspend fun getCurrentProgramsForIds(channelEpgIds: List<String>, nowMs: Long): List<EpgProgramEntity>


    @Query("SELECT * FROM epg_program WHERE id = :id")
    abstract suspend fun getById(id: Long): EpgProgramEntity?

    /** Suspend range query — load 24-h EPG window for channel page. */
    @Query(
        """
        SELECT * FROM epg_program
        WHERE channel_epg_id = :channelEpgId
          AND end_ms > :fromMs
          AND start_ms < :toMs
        ORDER BY start_ms ASC
        """,
    )
    abstract suspend fun getProgramsInRange(
        channelEpgId: String,
        fromMs: Long,
        toMs: Long,
    ): List<EpgProgramEntity>

    /** Suspend range query for multiple candidate channel EPG IDs. */
    @Query(
        """
        SELECT * FROM epg_program
        WHERE channel_epg_id IN (:channelEpgIds)
          AND end_ms > :fromMs
          AND start_ms < :toMs
        ORDER BY start_ms ASC
        """,
    )
    abstract suspend fun getProgramsForChannelIds(
        channelEpgIds: List<String>,
        fromMs: Long,
        toMs: Long,
    ): List<EpgProgramEntity>

    /** Returns count of current/future valid programs in the database. */
    @Query("SELECT COUNT(*) FROM epg_program WHERE end_ms > :nowMs")
    abstract suspend fun getValidProgramsCount(nowMs: Long): Int

    /** Returns the maximum end_ms among all programmes in the database, or null if empty. */
    @Query("SELECT MAX(end_ms) FROM epg_program")
    abstract suspend fun getLatestProgramEndMs(): Long?

    // ─── FTS full-text search ─────────────────────────────────────────────────

    /**
     * Ultra-fast full-text search across EPG titles and descriptions.
     *
     * The FTS MATCH query uses the FTS4 virtual table [EpgFtsEntity] as an index.
     * Results are joined back to [EpgProgramEntity] for full column access.
     *
     * Query format: plain search terms are automatically tokenised.
     * Wrap in quotes for exact phrase search: `"Breaking Bad"`.
     * Append `*` for prefix search: `break*`.
     *
     * @param query  Raw search string from the user (will be sanitised before passing here).
     * @param fromMs Only return programmes ending after this time (avoid past results).
     */
    @Query(
        """
        SELECT epg_program.* FROM epg_program
        WHERE epg_program.rowid IN (
            SELECT rowid FROM epg_fts WHERE epg_fts MATCH :query
        )
        AND epg_program.end_ms > :fromMs
        ORDER BY epg_program.start_ms ASC
        LIMIT 200
        """,
    )
    abstract fun searchFts(query: String, fromMs: Long): Flow<List<EpgProgramEntity>>

    /** One-shot suspend version of [searchFts] for the repository search method. */
    @Query(
        """
        SELECT epg_program.* FROM epg_program
        WHERE epg_program.rowid IN (
            SELECT rowid FROM epg_fts WHERE epg_fts MATCH :query
        )
        AND epg_program.end_ms > :fromMs
        ORDER BY epg_program.start_ms ASC
        LIMIT 200
        """,
    )
    abstract suspend fun searchFtsOnce(query: String, fromMs: Long): List<EpgProgramEntity>

    // ─── Stats ────────────────────────────────────────────────────────────────

    @Query("SELECT COUNT(*) FROM epg_program")
    abstract suspend fun getTotalCount(): Long

    @Query(
        """
        SELECT COUNT(*) FROM epg_program
        WHERE channel_epg_id = :channelEpgId
        """,
    )
    abstract suspend fun getCountForChannel(channelEpgId: String): Long
}
