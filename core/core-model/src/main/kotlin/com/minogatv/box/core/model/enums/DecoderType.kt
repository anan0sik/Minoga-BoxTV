package com.minogatv.box.core.model.enums

/**
 * Video decoder preference for a channel or globally.
 *
 * - [AUTO]     – ExoPlayer decides (hardware first, fallback to software)
 * - [HARDWARE] – Force MediaCodec hardware decoder; higher performance
 * - [SOFTWARE] – Force Libgav1 / FFmpeg software decoder; better compatibility
 */
enum class DecoderType {
    AUTO,
    HARDWARE,
    SOFTWARE,
}
