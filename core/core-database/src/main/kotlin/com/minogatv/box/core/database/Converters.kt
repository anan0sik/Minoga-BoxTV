package com.minogatv.box.core.database

import androidx.room.TypeConverter
import com.minogatv.box.core.model.enums.AspectRatio
import com.minogatv.box.core.model.enums.CatchupType
import com.minogatv.box.core.model.enums.DecoderType
import com.minogatv.box.core.model.enums.PlaylistType
import com.minogatv.box.core.model.enums.ProfileType
import com.minogatv.box.core.model.enums.ProxyType

/**
 * Room [TypeConverter]s for all custom enum types stored in the database.
 *
 * Enums are persisted by their [Enum.name] (string), not their ordinal.
 * This is intentional: ordinal values break silently when enum entries are reordered,
 * while names stay stable as long as we don't rename enum values.
 *
 * The converters are registered on [MinogaDatabase] via @TypeConverters.
 */
class Converters {

    // ─── PlaylistType ─────────────────────────────────────────────────────────

    @TypeConverter
    fun playlistTypeToString(value: PlaylistType): String = value.name

    @TypeConverter
    fun stringToPlaylistType(value: String): PlaylistType =
        runCatching { PlaylistType.valueOf(value) }.getOrDefault(PlaylistType.M3U)

    // ─── CatchupType ──────────────────────────────────────────────────────────

    @TypeConverter
    fun catchupTypeToString(value: CatchupType): String = value.name

    @TypeConverter
    fun stringToCatchupType(value: String): CatchupType =
        runCatching { CatchupType.valueOf(value) }.getOrDefault(CatchupType.AUTO)

    // ─── DecoderType ──────────────────────────────────────────────────────────

    @TypeConverter
    fun decoderTypeToString(value: DecoderType): String = value.name

    @TypeConverter
    fun stringToDecoderType(value: String): DecoderType =
        runCatching { DecoderType.valueOf(value) }.getOrDefault(DecoderType.AUTO)

    // ─── ProxyType ────────────────────────────────────────────────────────────

    @TypeConverter
    fun proxyTypeToString(value: ProxyType): String = value.name

    @TypeConverter
    fun stringToProxyType(value: String): ProxyType =
        runCatching { ProxyType.valueOf(value) }.getOrDefault(ProxyType.NONE)

    // ─── AspectRatio ──────────────────────────────────────────────────────────

    @TypeConverter
    fun aspectRatioToString(value: AspectRatio): String = value.name

    @TypeConverter
    fun stringToAspectRatio(value: String): AspectRatio =
        runCatching { AspectRatio.valueOf(value) }.getOrDefault(AspectRatio.FIT)

    // ─── ProfileType ──────────────────────────────────────────────────────────

    @TypeConverter
    fun profileTypeToString(value: ProfileType): String = value.name

    @TypeConverter
    fun stringToProfileType(value: String): ProfileType =
        runCatching { ProfileType.valueOf(value) }.getOrDefault(ProfileType.MAIN)
}
