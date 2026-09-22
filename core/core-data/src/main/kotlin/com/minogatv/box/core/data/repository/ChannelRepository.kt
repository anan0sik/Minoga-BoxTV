package com.minogatv.box.core.data.repository

import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.database.dao.ChannelSettingsDao
import com.minogatv.box.core.database.entity.ChannelEntity
import com.minogatv.box.core.database.entity.ChannelSettingsEntity
import com.minogatv.box.core.model.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for channel data operations.
 *
 * Abstracts Room DAO calls from feature modules to keep them database-agnostic.
 */
interface ChannelRepository {
    fun observeChannels(profileId: Long, groupTitle: String? = null): Flow<List<Channel>>
    fun observeGroups(profileId: Long): Flow<List<Pair<String, Int>>>
    suspend fun getChannel(channelId: Long): Channel?
    suspend fun hideChannel(channelId: Long, hidden: Boolean)
    suspend fun setFavorite(channelId: Long, isFavorite: Boolean)
    suspend fun updateEpgMapping(channelId: Long, epgChannelId: String)
    suspend fun searchChannels(profileId: Long, query: String): List<Channel>
    suspend fun updateChannelLogo(channelId: Long, logoUrl: String)
    suspend fun resolveMissingLogos(): Int
}

@Singleton
class ChannelRepositoryImpl @Inject constructor(
    private val channelDao: ChannelDao,
    private val settingsDao: ChannelSettingsDao,
    private val piconResolver: com.minogatv.box.core.data.picon.PiconResolver,
) : ChannelRepository {

    override fun observeChannels(profileId: Long, groupTitle: String?): Flow<List<Channel>> {
        return if (groupTitle.isNullOrBlank()) {
            channelDao.observeAllByProfile(profileId)
        } else {
            channelDao.observeByGroup(profileId, groupTitle)
        }.map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeGroups(profileId: Long): Flow<List<Pair<String, Int>>> =
        channelDao.observeGroupsWithCount(profileId)
            .map { list -> list.map { row -> row.group_title to row.channel_count } }

    override suspend fun getChannel(channelId: Long): Channel? =
        channelDao.getById(channelId)?.toDomain()

    override suspend fun hideChannel(channelId: Long, hidden: Boolean) {
        channelDao.setHiddenFlag(channelId, hidden)
    }

    override suspend fun setFavorite(channelId: Long, isFavorite: Boolean) {
        channelDao.setFavoriteFlag(channelId, isFavorite)
    }

    override suspend fun updateEpgMapping(channelId: Long, epgChannelId: String) {
        settingsDao.updateEpgMapping(channelId, epgChannelId)
    }

    override suspend fun searchChannels(profileId: Long, query: String): List<Channel> =
        channelDao.searchByNameOnce(profileId, query).map { it.toDomain() }

    override suspend fun updateChannelLogo(channelId: Long, logoUrl: String) {
        channelDao.updateLogoUrl(channelId, logoUrl)
    }

    override suspend fun resolveMissingLogos(): Int {
        return piconResolver.resolveMissingLogos()
    }

    // ── Mapper (local until a dedicated mappers/ module is created) ───────────

    private fun ChannelEntity.toDomain() = Channel(
        id           = id,
        playlistId   = playlistId,
        epgChannelId = epgChannelId,
        name         = name,
        logoUrl      = logoUrl,
        streamUrl    = streamUrl,
        groupTitle   = groupTitle,
        catchupType  = catchupType,
        catchupDays  = catchupDays,
        catchupSource = catchupSource,
        isHidden     = isHidden,
        isFavorite   = isFavorite,
        sortOrder    = sortOrder,
        isAdult      = isAdult,
    )
}
