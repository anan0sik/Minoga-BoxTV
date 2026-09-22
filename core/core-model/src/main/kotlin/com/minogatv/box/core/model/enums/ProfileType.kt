package com.minogatv.box.core.model.enums

/**
 * Preset profile types. Each profile carries its own favourites, watch history and settings.
 *
 * - [MAIN]   – Default adult profile (no content filter)
 * - [KIDS]   – Children profile; adult folders are auto-hidden
 * - [SPORTS] – Sports-focused profile with default sport-category filter
 * - [CUSTOM] – User-defined profile with custom name
 */
enum class ProfileType {
    MAIN,
    KIDS,
    SPORTS,
    CUSTOM,
}
