package com.minogatv.box.feature.player

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.IOException

/**
 * # MinogaLoadErrorPolicy
 *
 * Custom [LoadErrorHandlingPolicy] for ExoPlayer that silently retries stream failures
 * up to [MAX_RETRIES] times with exponential backoff before surfacing the error to the UI.
 *
 * This prevents transient IPTV stream interruptions (ISP hiccups, CDN switchovers,
 * UDP packet loss) from immediately showing error screens to the user.
 *
 * **Retry schedule:**
 * | Attempt | Delay  |
 * |---------|--------|
 * | 1       | 1.0 s  |
 * | 2       | 2.0 s  |
 * | 3       | 4.0 s  |
 * | 4       | 8.0 s  |
 * | 5       | 16.0 s |
 *
 * After the 5th failed attempt, returns [C.TIME_UNSET] to signal ExoPlayer
 * that the error should be propagated to the [Player.Listener].
 *
 * Thread-safe: ExoPlayer calls these methods on its internal playback thread.
 *
 * @param onRetryAttempt  Optional callback invoked on each retry (attempt number, 1-indexed).
 *                        Runs on ExoPlayer's playback thread — dispatch to main if updating UI.
 */
@UnstableApi
class MinogaLoadErrorPolicy(
    private val onRetryAttempt: ((attempt: Int) -> Unit)? = null,
) : LoadErrorHandlingPolicy {

    companion object {
        /** Maximum number of silent reconnection attempts before giving up. */
        const val MAX_RETRIES = 3

        /** Base delay for exponential backoff (milliseconds). */
        private const val BASE_DELAY_MS = 1_000L

        /** Maximum individual retry delay (milliseconds). */
        private const val MAX_DELAY_MS = 16_000L
    }

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val errorCount = loadErrorInfo.errorCount
        // loadErrorInfo.exception is already an IOException in Media3
        return if (errorCount <= MAX_RETRIES) {
            val delayMs = (BASE_DELAY_MS * (1L shl (errorCount - 1))).coerceAtMost(MAX_DELAY_MS)
            onRetryAttempt?.invoke(errorCount)
            delayMs
        } else {
            // Exceeded max retries — let ExoPlayer propagate the error
            C.TIME_UNSET
        }
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int {
        // Return our custom max retries for all data types
        return MAX_RETRIES
    }

    override fun getFallbackSelectionFor(
        fallbackOptions: LoadErrorHandlingPolicy.FallbackOptions,
        loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo,
    ): LoadErrorHandlingPolicy.FallbackSelection? {
        // No track/location fallback — just retry the same resource
        return null
    }
}
