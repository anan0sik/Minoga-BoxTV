package com.minogatv.box.feature.channels

import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * # KeyEventHandler
 *
 * Pure D-Pad / remote control input router for the Channel List screen.
 *
 * ## Design principles
 * - **No Android UI framework dependencies** (no Activity, no Compose APIs).
 *   This makes it trivially unit-testable with plain KeyEvent mocks.
 * - Translates raw [KeyEvent] key codes → [ChannelListIntent] values.
 * - Delegates actual long-press detection to [LongPressDetector].
 * - All state (panel open/closed, focus location) is read from the current
 *   [ChannelListUiState] snapshot passed on every call — the handler itself is stateless.
 *
 * ## Supported keys
 * | Key                                     | Action |
 * |-----------------------------------------|--------|
 * | DPAD_CENTER / ENTER (short)             | Play channel |
 * | DPAD_CENTER / ENTER (long ≥ 600 ms)     | Open context menu |
 * | DPAD_RIGHT                              | Open EPG side panel |
 * | DPAD_LEFT (panel open, focus in panel)  | Return focus to channel list |
 * | DPAD_LEFT (panel open, focus in list)   | Close panel |
 * | DPAD_UP / DPAD_DOWN                     | Move focus |
 * | MEDIA_PLAY_PAUSE / MEDIA_PAUSE / MEDIA_PLAY | Toggle playback |
 * | PROG_RED                                | Toggle favourite |
 * | CHANNEL_UP (PROG_GREEN)                 | Focus next channel |
 * | CHANNEL_DOWN (PROG_YELLOW)              | Focus previous channel |
 *
 * @param scope           [CoroutineScope] tied to the screen's lifecycle — used by [LongPressDetector].
 * @param longPressMs     Duration in ms to classify a key-hold as a long press (default 600 ms).
 * @param onIntent        Callback invoked for every resolved [ChannelListIntent].
 */
class KeyEventHandler(
    private val scope: CoroutineScope,
    private val longPressMs: Long = LONG_PRESS_THRESHOLD_MS,
    private val onIntent: (ChannelListIntent) -> Unit,
    private val onOpenSettings: () -> Unit = {},
    private val onScrollProgramDetails: ((Int) -> Unit)? = null,
) {
    private val longPressDetector = LongPressDetector(scope, longPressMs)

    /**
     * Call this from `Modifier.onPreviewKeyEvent { }` in the Composable.
     *
     * @param event       The raw [KeyEvent] from the system.
     * @param currentState The latest snapshot of [ChannelListUiState].
     * @return `true` if the event was consumed, `false` to let it propagate.
     */
    fun onKeyEvent(event: KeyEvent, currentState: ChannelListUiState): Boolean {
        val focusedChannelId = currentState.channels
            .getOrNull(currentState.focusedIndex)
            ?.channel
            ?.id

        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleKeyDown(event.keyCode, focusedChannelId, currentState)
            KeyEvent.ACTION_UP   -> handleKeyUp(event.keyCode, focusedChannelId, currentState)
            else                 -> false
        }
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private fun handleKeyDown(
        keyCode: Int,
        focusedChannelId: Long?,
        state: ChannelListUiState,
    ): Boolean {
        if (state.isExitConfirmOpen) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                onIntent(ChannelListIntent.DismissExitConfirm)
                return true
            }
            return false
        }

        if (state.isContextMenuOpen) {
            if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
                onIntent(ChannelListIntent.DismissContextMenu)
                return true
            }
            return false
        }

        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            when {
                state.isProgramDetailsOpen -> {
                    onIntent(ChannelListIntent.CloseProgramDetails)
                    return true
                }
                state.isFocusInSidePanel -> {
                    onIntent(ChannelListIntent.CloseEpgSidePanel)
                    return true
                }
                state.topBarFocus != TopBarFocus.NONE -> {
                    onIntent(ChannelListIntent.ExitTopBarFocus)
                    return true
                }
                state.viewMode == ScreenViewMode.CHANNELS -> {
                    onIntent(ChannelListIntent.ReturnToFolders)
                    return true
                }
                else -> {
                    onIntent(ChannelListIntent.ShowExitConfirm)
                    return true
                }
            }
        }

        // Cancel and reset long press timer immediately on ANY navigation or action key other than Enter
        if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER &&
            keyCode != KeyEvent.KEYCODE_ENTER &&
            keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER
        ) {
            longPressDetector.reset()
        }

        return when (keyCode) {

            // ── OK / Enter — start long-press timer; fire short-press on KEY_UP ──
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when {
                    state.isSearchFocused -> {
                        longPressDetector.reset()
                        onIntent(ChannelListIntent.OpenSearch)
                        true
                    }
                    state.isSettingsFocused -> {
                        longPressDetector.reset()
                        onOpenSettings()
                        true
                    }
                    state.isGroupFocused -> {
                        longPressDetector.reset()
                        if (state.viewMode == ScreenViewMode.CHANNELS) {
                            onIntent(ChannelListIntent.ReturnToFolders)
                        }
                        true
                    }
                    state.viewMode == ScreenViewMode.FOLDERS -> {
                        // In FOLDERS mode, Enter selects folder on KEY_UP. Do NOT start channel long-press!
                        longPressDetector.reset()
                        true
                    }
                    state.viewMode == ScreenViewMode.CHANNELS -> {
                        if (state.isFocusInSidePanel) {
                            longPressDetector.reset()
                            true
                        } else {
                            focusedChannelId?.let { channelId ->
                                longPressDetector.start {
                                    // Long press fired — open context menu ("В избранное")
                                    onIntent(ChannelListIntent.OpenContextMenu(channelId))
                                }
                            }
                            true
                        }
                    }
                    else -> true
                }
            }

        // ── D-Pad RIGHT ───────────────────────────────────────────────────────
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            when {
                state.isGroupFocused -> onIntent(ChannelListIntent.MoveTopBarFocus(TopBarFocus.SEARCH))
                state.isSearchFocused -> onIntent(ChannelListIntent.MoveTopBarFocus(TopBarFocus.SETTINGS))
                state.viewMode == ScreenViewMode.FOLDERS -> {
                    val count = state.folders.size
                    val cur = state.focusedFolderIndex
                    // In 4-column grid, move right if not at right edge
                    if (count > 0 && cur % 4 < 3 && cur + 1 < count) {
                        onIntent(ChannelListIntent.MoveFolderFocus(1))
                    }
                }
                state.viewMode == ScreenViewMode.CHANNELS -> {
                    if (state.isProgramDetailsOpen) {
                        // Already in rightmost program details panel
                        Unit
                    } else if (state.isFocusInSidePanel && state.sidePanelItems.isNotEmpty()) {
                        // User requested: From EPG program list, pressing RIGHT opens full program details on the right
                        onIntent(ChannelListIntent.OpenProgramDetails)
                    } else if (!state.isFocusInSidePanel && state.sidePanelItems.isNotEmpty()) {
                        onIntent(ChannelListIntent.OpenEpgSidePanel)
                    }
                }
            }
            true
        }

        // ── D-Pad LEFT ────────────────────────────────────────────────────────
        KeyEvent.KEYCODE_DPAD_LEFT -> {
            when {
                state.isSettingsFocused -> onIntent(ChannelListIntent.MoveTopBarFocus(TopBarFocus.SEARCH))
                state.isSearchFocused -> onIntent(ChannelListIntent.MoveTopBarFocus(TopBarFocus.GROUP_SELECTOR))
                state.viewMode == ScreenViewMode.FOLDERS -> {
                    val cur = state.focusedFolderIndex
                    // In 4-column grid, move left if not at left edge
                    if (cur % 4 > 0) {
                        onIntent(ChannelListIntent.MoveFolderFocus(-1))
                    }
                }
                state.viewMode == ScreenViewMode.CHANNELS -> {
                    if (state.isProgramDetailsOpen) {
                        // User requested: Exit program description on D-Pad LEFT or Back
                        onIntent(ChannelListIntent.CloseProgramDetails)
                    } else if (state.isFocusInSidePanel) {
                        onIntent(ChannelListIntent.CloseEpgSidePanel)
                    } else {
                        // Per user requirement: In channel list, pressing LEFT returns to folders list!
                        onIntent(ChannelListIntent.ReturnToFolders)
                    }
                }
                else -> false
            }
            true
        }

        // ── D-Pad UP — move focus up or into top bar ──────────────────────────
        KeyEvent.KEYCODE_DPAD_UP -> {
            when {
                state.topBarFocus != TopBarFocus.NONE -> Unit
                state.viewMode == ScreenViewMode.FOLDERS -> {
                    val cur = state.focusedFolderIndex
                    // In 4-column grid, step is 4
                    if (cur < 4) {
                        onIntent(ChannelListIntent.FocusTopBar(TopBarFocus.SETTINGS))
                    } else {
                        onIntent(ChannelListIntent.MoveFolderFocus(-4))
                    }
                }
                state.viewMode == ScreenViewMode.CHANNELS -> {
                    if (state.isProgramDetailsOpen) {
                        // In program details panel, UP scrolls details
                        onScrollProgramDetails?.invoke(-120)
                    } else if (state.channels.isEmpty()) {
                        onIntent(ChannelListIntent.FocusTopBar(TopBarFocus.SETTINGS))
                    } else if (state.isFocusInSidePanel) {
                        if (state.sidePanelFocusedIndex <= 0) {
                            onIntent(ChannelListIntent.FocusTopBar(TopBarFocus.SETTINGS))
                        } else {
                            onIntent(ChannelListIntent.MoveSidePanelFocus(-1))
                        }
                    } else if (state.focusedIndex <= 0) {
                        onIntent(ChannelListIntent.FocusTopBar(TopBarFocus.SETTINGS))
                    } else {
                        onIntent(ChannelListIntent.MoveFocus(-1))
                    }
                }
            }
            true
        }

        // ── D-Pad DOWN — move focus down or return from top bar ───────────────
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            when {
                state.topBarFocus != TopBarFocus.NONE -> {
                    onIntent(ChannelListIntent.ExitTopBarFocus)
                }
                state.viewMode == ScreenViewMode.FOLDERS -> {
                    val count = state.folders.size
                    val cur = state.focusedFolderIndex
                    // In 4-column grid, step is 4
                    if (cur + 4 < count) {
                        onIntent(ChannelListIntent.MoveFolderFocus(4))
                    } else if (cur < count - 1) {
                        onIntent(ChannelListIntent.MoveFolderFocus(count - 1 - cur))
                    }
                }
                state.viewMode == ScreenViewMode.CHANNELS -> {
                    if (state.isProgramDetailsOpen) {
                        // In program details panel, DOWN scrolls details
                        onScrollProgramDetails?.invoke(120)
                    } else if (state.isFocusInSidePanel) {
                        onIntent(ChannelListIntent.MoveSidePanelFocus(1))
                    } else {
                        onIntent(ChannelListIntent.MoveFocus(1))
                    }
                }
            }
            true
        }

        // ── CH+ / CH- (also PROG_GREEN / PROG_YELLOW on some remotes) ─────────
        KeyEvent.KEYCODE_CHANNEL_UP,
        KeyEvent.KEYCODE_PAGE_UP -> {
            if (state.isProgramDetailsOpen) {
                onScrollProgramDetails?.invoke(-250)
            } else {
                onIntent(ChannelListIntent.MoveFocus(-1))
            }
            true
        }

        KeyEvent.KEYCODE_CHANNEL_DOWN,
        KeyEvent.KEYCODE_PAGE_DOWN -> {
            if (state.isProgramDetailsOpen) {
                onScrollProgramDetails?.invoke(250)
            } else {
                onIntent(ChannelListIntent.MoveFocus(1))
            }
            true
        }

        // ── Play / Pause ──────────────────────────────────────────────────────
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY -> {
            onIntent(ChannelListIntent.TogglePlayback); true
        }

        // ── Red button — toggle favourite ─────────────────────────────────────
        KeyEvent.KEYCODE_PROG_RED -> {
            focusedChannelId?.let { onIntent(ChannelListIntent.ToggleFavorite(it)) }
            true
        }

        // ── Back button ────────────────────────────────────────────────────────
        KeyEvent.KEYCODE_BACK -> {
            when {
                state.isContextMenuOpen -> {
                    onIntent(ChannelListIntent.DismissContextMenu)
                    true
                }
                state.isProgramDetailsOpen -> {
                    onIntent(ChannelListIntent.CloseProgramDetails)
                    true
                }
                state.isFocusInSidePanel -> {
                    onIntent(ChannelListIntent.CloseEpgSidePanel)
                    true
                }
                state.viewMode == ScreenViewMode.CHANNELS -> {
                    onIntent(ChannelListIntent.ReturnToFolders)
                    true
                }
                else -> false
            }
        }

        else -> false
    }
}

    private fun handleKeyUp(
        keyCode: Int,
        focusedChannelId: Long?,
        state: ChannelListUiState,
    ): Boolean {
        if (state.isContextMenuOpen) {
            return false
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (state.isSearchFocused) {
                    longPressDetector.reset()
                    onIntent(ChannelListIntent.OpenSearch)
                    true
                } else if (state.isSettingsFocused || state.isGroupFocused) {
                    longPressDetector.reset()
                    if (state.isSettingsFocused) onOpenSettings()
                    true
                } else if (state.viewMode == ScreenViewMode.FOLDERS) {
                    longPressDetector.reset()
                    val selectedFolder = state.folders.getOrNull(state.focusedFolderIndex)
                    if (selectedFolder != null) {
                        onIntent(ChannelListIntent.SelectFolder(selectedFolder))
                    }
                    true
                } else {
                    val wasLongPress = longPressDetector.cancel()
                    if (!wasLongPress && focusedChannelId != null) {
                        // Short press — play the channel
                        if (state.isFocusInSidePanel) {
                            val selectedSlot = state.sidePanelItems.getOrNull(state.sidePanelFocusedIndex)
                            val channelItem = state.channels.getOrNull(state.focusedIndex)
                            val channelHasCatchup = channelItem?.hasCatchup == true

                            when (selectedSlot) {
                                is SidePanelItem.CatchupSlot -> {
                                    if (channelHasCatchup) {
                                        onIntent(
                                            ChannelListIntent.PlayCatchup(
                                                channelId = focusedChannelId,
                                                programStartMs = selectedSlot.program.startMs,
                                            ),
                                        )
                                    }
                                }
                                is SidePanelItem.Programme -> {
                                    if (selectedSlot.program.endMs <= System.currentTimeMillis()) {
                                        if (channelHasCatchup) {
                                            onIntent(
                                                ChannelListIntent.PlayCatchup(
                                                    channelId = focusedChannelId,
                                                    programStartMs = selectedSlot.program.startMs,
                                                ),
                                            )
                                        }
                                    } else {
                                        onIntent(ChannelListIntent.PlayChannel(focusedChannelId))
                                    }
                                }
                                null -> Unit
                            }
                        } else {
                            onIntent(ChannelListIntent.PlayChannel(focusedChannelId))
                        }
                    }
                    true
                }
            }

            else -> {
                longPressDetector.reset()
                false
            }
        }
    }

    companion object {
        /** Long press threshold in milliseconds. */
        const val LONG_PRESS_THRESHOLD_MS = 500L
    }
}
