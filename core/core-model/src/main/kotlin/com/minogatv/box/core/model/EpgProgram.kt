package com.minogatv.box.core.model

/**
 * Domain model for a single EPG programme event.
 *
 * @param id            Unique event ID (Room auto-generated)
 * @param channelEpgId  Matches [Channel.epgChannelId] (`tvg-id`)
 * @param title         Programme title
 * @param description   Extended description / synopsis
 * @param startMs       Event start time (epoch milliseconds, UTC)
 * @param endMs         Event end time (epoch milliseconds, UTC)
 * @param category      Genre/category string from XMLTV `<category>` tag
 * @param iconUrl       Optional programme poster / thumbnail URL
 * @param isNew         Whether this programme is flagged as new
 * @param rating        Content rating string (e.g. "PG-13", "18+")
 */
data class EpgProgram(
    val id: Long = 0,
    val channelEpgId: String,
    val title: String,
    val description: String = "",
    val startMs: Long,
    val endMs: Long,
    val category: String = "",
    val iconUrl: String? = null,
    val isNew: Boolean = false,
    val rating: String = "",
) {
    /** Duration of this programme in milliseconds. */
    val durationMs: Long get() = endMs - startMs

    /** Progress [0f..1f] at the given [nowMs] timestamp. */
    fun progressAt(nowMs: Long): Float =
        ((nowMs - startMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    /** Returns true if this programme is currently airing at [nowMs]. */
    fun isLiveAt(nowMs: Long): Boolean = nowMs in startMs until endMs
}
