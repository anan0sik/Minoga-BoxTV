package com.minogatv.box.feature.channels

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coroutine-based long-press detector for remote control keys (e.g. Xiaomi Box D-Pad OK / Enter).
 *
 * Works by launching a [delay] coroutine when the key goes down.
 * If [cancel] is called before the delay expires, the key was a short tap.
 * If the delay expires and fires the [onLongPress] callback, the key was held.
 *
 * Thread-safety: All calls must happen on the same thread (the main thread).
 *
 * @param scope       [CoroutineScope] to launch the timer in.
 * @param thresholdMs Long-press threshold in milliseconds (default 500 ms).
 */
class LongPressDetector(
    private val scope: CoroutineScope,
    private val thresholdMs: Long = 500L,
) {
    private var job: Job? = null

    /** `true` after the long-press timer has fired (reset on [cancel] or [reset]). */
    private var longPressFired: Boolean = false

    /**
     * Start the long-press timer if not already running.
     * Subsequent calls while the timer is running (e.g. from TV remote key-repeat events)
     * are ignored so the timer is NOT constantly reset while holding the button.
     *
     * @param onLongPress Callback invoked when the threshold is reached.
     */
    fun start(onLongPress: () -> Unit) {
        if (job?.isActive == true) {
            // Already running for this key press / hold, do not cancel or restart!
            return
        }
        longPressFired = false
        job = scope.launch {
            delay(thresholdMs)
            longPressFired = true
            onLongPress()
        }
    }

    /**
     * Cancel the running timer (called on key-up of the target key).
     *
     * @return `true` if the long-press already fired (caller should ignore short-press action).
     *         `false` if the key was released before the threshold (caller handles short-press).
     */
    fun cancel(): Boolean {
        job?.cancel()
        job = null
        val fired = longPressFired
        longPressFired = false
        return fired
    }

    /**
     * Cancel any pending timer and reset the fired state unconditionally.
     * Called when any navigation key (e.g. D-Pad Down/Up/Left/Right) is pressed,
     * ensuring long-press never fires erroneously when moving between channels.
     */
    fun reset() {
        job?.cancel()
        job = null
        longPressFired = false
    }
}
