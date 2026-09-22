package com.minogatv.box.core.data.repository

import com.minogatv.box.core.database.dao.EpgDao
import com.minogatv.box.core.database.entity.EpgProgramEntity
import com.minogatv.box.core.model.EpgProgram
import com.minogatv.box.core.parser.CatchupUrlBuilder
import com.minogatv.box.core.model.enums.CatchupType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for EPG data operations.
 */
interface EpgRepository {
    fun observeCurrentProgram(channelEpgId: String, nowMs: Long): Flow<EpgProgram?>
    fun observeNextProgram(channelEpgId: String, nowMs: Long): Flow<EpgProgram?>
    suspend fun getProgramsForChannel(channelEpgId: String, fromMs: Long, toMs: Long): List<EpgProgram>
    suspend fun searchPrograms(query: String, afterMs: Long): List<EpgProgram>
    fun buildCatchupUrl(
        streamUrl: String,
        catchupType: CatchupType,
        catchupSource: String?,
        programStartMs: Long,
        programEndMs: Long,
    ): String?
    suspend fun syncMissingEpg(): Result<Int>
    suspend fun fetchMissingEpgForChannel(channelId: Long): Boolean
}

@Singleton
class EpgRepositoryImpl @Inject constructor(
    private val epgDao: EpgDao,
    private val epgSyncManager: com.minogatv.box.core.data.sync.EpgSyncManager,
) : EpgRepository {

    override suspend fun syncMissingEpg(): Result<Int> =
        epgSyncManager.syncMissingEpgAndLogos()

    override suspend fun fetchMissingEpgForChannel(channelId: Long): Boolean =
        epgSyncManager.fetchMissingEpgForChannel(channelId)

    override fun observeCurrentProgram(channelEpgId: String, nowMs: Long): Flow<EpgProgram?> =
        epgDao.observeCurrentProgram(channelEpgId, nowMs).map { it?.toDomain() }

    override fun observeNextProgram(channelEpgId: String, nowMs: Long): Flow<EpgProgram?> =
        epgDao.observeNextProgram(channelEpgId, nowMs).map { it?.toDomain() }

    override suspend fun getProgramsForChannel(
        channelEpgId: String,
        fromMs: Long,
        toMs: Long,
    ): List<EpgProgram> = epgDao.getProgramsInRange(channelEpgId, fromMs, toMs)
        .map { it.toDomain() }

    override suspend fun searchPrograms(query: String, afterMs: Long): List<EpgProgram> =
        epgDao.searchFtsOnce("$query*", afterMs).map { it.toDomain() }

    override fun buildCatchupUrl(
        streamUrl: String,
        catchupType: CatchupType,
        catchupSource: String?,
        programStartMs: Long,
        programEndMs: Long,
    ): String? = CatchupUrlBuilder.build(
        streamUrl      = streamUrl,
        catchupType    = catchupType,
        catchupSource  = catchupSource,
        programStartMs = programStartMs,
        programEndMs   = programEndMs,
    )

    // ── Mapper ────────────────────────────────────────────────────────────────

    private fun EpgProgramEntity.toDomain() = EpgProgram(
        id           = id,
        channelEpgId = channelEpgId,
        title        = title,
        description  = description,
        startMs      = startMs,
        endMs        = endMs,
        category     = category,
        iconUrl      = iconUrl,
        isNew        = isNew,
        rating       = rating,
    )
}
