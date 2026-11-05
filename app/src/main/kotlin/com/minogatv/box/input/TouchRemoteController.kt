package com.minogatv.box.input

import android.app.Activity
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import kotlin.math.abs

/**
 * # TouchRemoteController
 *
 * Non-blocking hybrid touch + gesture controller that lets standard Android/Compose touch
 * interactions (single tap on buttons/cards/channels, vertical list scrolling, long-press
 * context menus) work natively while ALSO translating remote-style gestures into D-pad keys:
 *
 * | Gesture                              | Remote key / Action                  |
 * |--------------------------------------|--------------------------------------|
 * | Single tap on any button / item      | Native tap (select / open / click)   |
 * | Double tap                           | OK (`KEYCODE_DPAD_CENTER`)           |
 * | Swipe left (horizontal)              | Right (`KEYCODE_DPAD_RIGHT`)         |
 * | Swipe right (horizontal)             | Left (`KEYCODE_DPAD_LEFT`)           |
 * | Swipe up / down (in Player)          | Next / Prev channel (`DPAD_DOWN/UP`) |
 * | Vertical drag (in Lists / Settings)  | Native smooth list scrolling         |
 * | Two-finger tap                       | Back (`KEYCODE_BACK`)                |
 */
class TouchRemoteController(private val activity: Activity) {

    private val density = activity.resources.displayMetrics.density
    private val minSwipePx = MIN_SWIPE_DP * density
    private val stepPx = STEP_DISTANCE_DP * density

    private var downX = 0f
    private var downY = 0f
    private var multiTouch = false
    private var swipeHandled = false
    private var maxTravel = 0f

    private var lastTapUpTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop * 1.5f
    private val doubleTapSlop = DOUBLE_TAP_SLOP_DP * density

    /**
     * Observes the touch [event] and dispatches synthetic D-pad [KeyEvent]s for remote gestures.
     *
     * @param isPlayerActive `true` when the full-screen video player is active (enables vertical
     *                       channel-switching swipes; on list screens, vertical drag scrolls lists).
     * @return `true` if a swipe or multi-touch gesture was triggered on `ACTION_UP` and the
     *         underlying native click should be cancelled (`ACTION_CANCEL`).
     */
    fun onTouchEvent(event: MotionEvent, isPlayerActive: Boolean = false): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                maxTravel = 0f
                multiTouch = false
                swipeHandled = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
            }
            MotionEvent.ACTION_MOVE -> {
                maxTravel = maxOf(maxTravel, abs(event.x - downX), abs(event.y - downY))
            }
            MotionEvent.ACTION_UP -> {
                maxTravel = maxOf(maxTravel, abs(event.x - downX), abs(event.y - downY))
                return when {
                    multiTouch -> {
                        sendKeyClick(KeyEvent.KEYCODE_BACK)
                        true
                    }
                    maxTravel <= touchSlop -> {
                        onTap(event)
                        false
                    }
                    else -> {
                        handleSwipe(
                            dx = event.x - downX,
                            dy = event.y - downY,
                            allowVerticalDpad = isPlayerActive,
                        )
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                multiTouch = false
                swipeHandled = false
            }
        }
        return false
    }

    private fun onTap(event: MotionEvent) {
        val isDouble = lastTapUpTime != 0L &&
            event.eventTime - lastTapUpTime <= DOUBLE_TAP_MS &&
            abs(event.x - lastTapX) <= doubleTapSlop &&
            abs(event.y - lastTapY) <= doubleTapSlop
        if (isDouble) {
            lastTapUpTime = 0L
            sendKeyClick(KeyEvent.KEYCODE_DPAD_CENTER)
        } else {
            lastTapUpTime = event.eventTime
            lastTapX = event.x
            lastTapY = event.y
        }
    }

    private fun handleSwipe(dx: Float, dy: Float, allowVerticalDpad: Boolean): Boolean {
        if (swipeHandled) return true
        val absX = abs(dx)
        val absY = abs(dy)

        // Horizontal swipe: left -> DPAD_RIGHT, right -> DPAD_LEFT
        if (absX >= minSwipePx && absX > absY * 1.25f) {
            val keyCode = if (dx < 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
            sendKeyClick(keyCode)
            swipeHandled = true
            return true
        }

        // Vertical swipe in Player: up -> DPAD_DOWN (next channel), down -> DPAD_UP (prev channel)
        if (allowVerticalDpad && absY >= minSwipePx && absY > absX * 1.25f) {
            val keyCode = if (dy < 0) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_UP
            val steps = (1 + ((absY - minSwipePx) / stepPx).toInt()).coerceIn(1, MAX_STEPS)
            repeat(steps) { sendKeyClick(keyCode) }
            swipeHandled = true
            return true
        }

        return false
    }

    private fun sendKeyClick(keyCode: Int) {
        sendKey(KeyEvent.ACTION_DOWN, keyCode)
        sendKey(KeyEvent.ACTION_UP, keyCode)
    }

    private fun sendKey(action: Int, keyCode: Int) {
        ensureKeyFocus()
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(
            now, now, action, keyCode, 0, 0,
            KeyEvent.KEYCODE_UNKNOWN, 0, KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_DPAD,
        )
        activity.dispatchKeyEvent(event)
    }

    fun ensureKeyFocus() {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val host = findComposeHost(content) ?: return
        if (!host.isFocusableInTouchMode) {
            host.isFocusable = true
            host.isFocusableInTouchMode = true
        }
        if (!host.hasFocus()) host.requestFocus()
    }

    private fun findComposeHost(view: View): View? {
        if (view.javaClass.name.contains("AndroidComposeView")) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findComposeHost(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }

    companion object {
        const val PREF_KEY = "touch_gestures_enabled"
        private const val MIN_SWIPE_DP = 48f
        private const val STEP_DISTANCE_DP = 160f
        private const val MAX_STEPS = 4
        private const val DOUBLE_TAP_MS = 400L
        private const val DOUBLE_TAP_SLOP_DP = 72f
    }
}
