package com.minogatv.box.core.model.enums

/**
 * Video aspect-ratio / scaling mode for the player.
 *
 * - [FIT]       – Letterbox / pillarbox (default, content-safe)
 * - [FILL]      – Stretch to fill screen (may distort)
 * - [CROP_16_9] – Crop to 16:9, no black bars
 * - [ZOOM]      – Uniform scale until the shorter axis fills the screen
 */
enum class AspectRatio {
    FIT,
    FILL,
    CROP_16_9,
    ZOOM,
}
