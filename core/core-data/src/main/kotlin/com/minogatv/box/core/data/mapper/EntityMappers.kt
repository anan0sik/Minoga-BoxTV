package com.minogatv.box.core.data.mapper

import com.minogatv.box.core.database.entity.ChannelEntity
import com.minogatv.box.core.database.entity.ChannelSettingsEntity
import com.minogatv.box.core.database.entity.EpgProgramEntity
import com.minogatv.box.core.database.entity.PlaylistEntity
import com.minogatv.box.core.database.entity.UserProfileEntity
import com.minogatv.box.core.model.Channel
import com.minogatv.box.core.model.ChannelSettings
import com.minogatv.box.core.model.EpgProgram
import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.model.UserProfile

/**
 * Extension functions to convert database entities ↔ domain models.
 *
 * Centralised here so repositories, workers, and view models all use the same mapping logic
 * without duplicating code.
 */

fun PlaylistEntity.toDomain() = Playlist(
    id                = id,
    profileId         = profileId,
    name              = name,
    type              = type,
    url               = url,
    userAgent         = userAgent,
    proxyType         = proxyType,
    proxyHost         = proxyHost,
    proxyPort         = proxyPort,
    proxyUsername     = proxyUsername,
    proxyPassword     = proxyPassword,
    epgUrl            = epgUrl,
    epgTimeShiftHours = epgTimeShiftHours,
    macAddress        = macAddress,
    xtreamUsername    = xtreamUsername,
    xtreamPassword    = xtreamPassword,
    lastRefreshedAt   = lastRefreshedAt,
    isEnabled         = isEnabled,
    sortOrder         = sortOrder,
)

fun Playlist.toEntity() = PlaylistEntity(
    id                = id,
    profileId         = profileId,
    name              = name,
    type              = type,
    url               = url,
    userAgent         = userAgent,
    proxyType         = proxyType,
    proxyPort         = proxyPort,
    proxyUsername     = proxyUsername,
    proxyPassword     = proxyPassword,
    epgUrl            = epgUrl,
    epgTimeShiftHours = epgTimeShiftHours,
    macAddress        = macAddress,
    xtreamUsername    = xtreamUsername,
    xtreamPassword    = xtreamPassword,
    lastRefreshedAt   = lastRefreshedAt,
    isEnabled         = isEnabled,
    sortOrder         = sortOrder,
)

fun ChannelEntity.toDomain() = Channel(
    id            = id,
    playlistId    = playlistId,
    epgChannelId  = epgChannelId,
    name          = name,
    logoUrl       = logoUrl ?: "",
    streamUrl     = streamUrl,
    groupTitle    = groupTitle,
    catchupType   = catchupType,
    catchupDays   = catchupDays,
    catchupSource = catchupSource ?: "",
    isHidden      = isHidden,
    isFavorite    = isFavorite,
    sortOrder     = sortOrder,
    isAdult       = isAdult,
)

fun Channel.toEntity() = ChannelEntity(
    id            = id,
    playlistId    = playlistId,
    epgChannelId  = epgChannelId,
    name          = name,
    logoUrl       = logoUrl,
    streamUrl     = streamUrl,
    groupTitle    = groupTitle,
    catchupType   = catchupType,
    catchupDays   = catchupDays,
    catchupSource = catchupSource,
    isHidden      = isHidden,
    isFavorite    = isFavorite,
    sortOrder     = sortOrder,
    isAdult       = isAdult,
)

fun EpgProgramEntity.toDomain() = EpgProgram(
    id           = id,
    channelEpgId = channelEpgId,
    title        = title,
    description  = description,
    startMs      = startMs,
    endMs        = endMs,
    category     = category,
    iconUrl      = iconUrl ?: "",
    isNew        = isNew,
    rating       = rating,
)

fun EpgProgram.toEntity() = EpgProgramEntity(
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

fun UserProfileEntity.toDomain() = UserProfile(
    id        = id,
    name      = name,
    type      = type,
    pinHash   = pinHash,
    avatarUri = avatarUri,
    createdAt = createdAt,
    isActive  = isActive,
)

fun UserProfile.toEntity() = UserProfileEntity(
    id        = id,
    name      = name,
    type      = type,
    pinHash   = pinHash,
    avatarUri = avatarUri,
    createdAt = createdAt,
    isActive  = isActive,
)

fun ChannelSettingsEntity.toDomain() = ChannelSettings(
    channelId            = channelId,
    decoderType          = decoderType,
    aspectRatio          = aspectRatio,
    epgTimeShiftHours    = epgTimeShiftHours,
    epgChannelIdOverride = epgChannelIdOverride,
    customStreamUrl      = customStreamUrl,
    isLocked             = isLocked,
)

fun ChannelSettings.toEntity() = ChannelSettingsEntity(
    channelId            = channelId,
    decoderType          = decoderType,
    aspectRatio          = aspectRatio,
    epgTimeShiftHours    = epgTimeShiftHours,
    epgChannelIdOverride = epgChannelIdOverride,
    customStreamUrl      = customStreamUrl,
    isLocked             = isLocked,
)
