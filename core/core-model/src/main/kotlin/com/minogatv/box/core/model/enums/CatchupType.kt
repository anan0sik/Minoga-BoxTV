package com.minogatv.box.core.model.enums

/**
 * Describes how time-shift / catch-up archive URLs are constructed for this channel.
 *
 * | Value      | URL pattern example |
 * |------------|---------------------|
 * | [AUTO]     | App detects via stream URL hints |
 * | [FLUSSONIC]| `{base}/{channel}/timeshift_abs/{utcStart}/{duration}/index.m3u8` |
 * | [XTREAM]   | `{server}/timeshift/{user}/{pass}/{duration}/{start}/{stream}.ts` |
 * | [SHIFT]    | `{streamUrl}?utc={utcStart}&lutc={utcNow}` |
 * | [APPEND]   | `{streamUrl}?catchup-back={offsetSeconds}` |
 */
enum class CatchupType {
    AUTO,
    FLUSSONIC,
    XTREAM,
    SHIFT,
    APPEND,
    NONE,
}
