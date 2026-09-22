package com.minogatv.box.core.model

import com.minogatv.box.core.model.enums.AspectRatio
import com.minogatv.box.core.model.enums.DecoderType

/**
 * Per-channel player overrides. When present, these values take precedence
 * over the global settings defined in [com.minogatv.box.core.model.Playlist].
 *
 * @param channelId        The channel this override belongs to (1-to-1 with [Channel.id])
 * @param decoderType      Override video decoder ([DecoderType.AUTO] = use global)
 * @param aspectRatio      Override aspect-ratio / scaling mode
 * @param epgTimeShiftHours Per-channel EPG time correction (−12…+12 hours)
 * @param epgChannelIdOverride  Manually mapped EPG `tvg-id` (overrides [Channel.epgChannelId])
 * @param customStreamUrl  Manually overridden playback URL (replaces the M3U URL)
 * @param isLocked         Whether a PIN is required to open this channel
 */
data class ChannelSettings(
    val channelId: Long,
    val decoderType: DecoderType = DecoderType.AUTO,
    val aspectRatio: AspectRatio = AspectRatio.FIT,
    val epgTimeShiftHours: Float = 0f,
    val epgChannelIdOverride: String? = null,
    val customStreamUrl: String? = null,
    val isLocked: Boolean = false,
)
