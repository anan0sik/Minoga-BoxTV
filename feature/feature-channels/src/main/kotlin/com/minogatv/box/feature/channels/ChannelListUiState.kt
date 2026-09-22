package com.minogatv.box.feature.channels

import com.minogatv.box.core.model.Channel
import com.minogatv.box.core.model.EpgProgram

/**
 * Immutable UI state for the Channel List screen.
 *
 * The ViewModel exposes a [kotlinx.coroutines.flow.StateFlow] of this class.
 * Every field change produces a new state object (Compose diffing handles recomposition).
 */
data class ChannelListUiState(
    /** Current screen view: FOLDERS (1st window) or CHANNELS (folder content). */
    val viewMode: ScreenViewMode = ScreenViewMode.FOLDERS,

    /** Thematic folders / categories available in the playlist. */
    val folders: List<ChannelFolderItem> = emptyList(),

    /** D-Pad focused folder index in [folders]. */
    val focusedFolderIndex: Int = 0,

    /** All channels visible in the current group/folder, in sort order. */
    val channels: List<ChannelDisplayItem> = emptyList(),

    /** Index of the D-Pad focused channel in [channels]. -1 = nothing focused yet. */
    val focusedIndex: Int = -1,

    /** Whether the EPG side panel is currently visible. */
    val isSidePanelOpen: Boolean = false,

    /** Whether focus is inside the side panel (vs the channel list). */
    val isFocusInSidePanel: Boolean = false,

    /** Channel ID for which silent live preview is active. Null = no preview. */
    val livePreviewChannelId: Long? = null,

    /** EPG + catch-up items shown in the side panel for the focused channel. */
    val sidePanelItems: List<SidePanelItem> = emptyList(),

    /** Currently focused item index inside the side panel's lazy list. */
    val sidePanelFocusedIndex: Int = 0,

    /** Whether the context menu bottom sheet is visible. */
    val isContextMenuOpen: Boolean = false,

    /** Whether the screen is in a loading state (initial channel fetch). */
    val isLoading: Boolean = true,

    /** Non-null when a recoverable error occurred (shown as a snack-bar). */
    val errorMessage: String? = null,

    /** D-Pad focus inside the top bar (NONE if focus is in channel list or side panel). */
    val topBarFocus: TopBarFocus = TopBarFocus.NONE,

    /** Current group title displayed in the top bar. */
    val selectedGroupName: String = "",

    /** Whether the global search dialog is open. */
    val isSearchOpen: Boolean = false,

    /** Folder currently pending PIN unlock, or null if none. */
    val pendingPinFolder: ChannelFolderItem? = null,

    /** Set of folder titles that have been successfully unlocked this session. */
    val unlockedFolderTitles: Set<String> = emptySet(),

    /** Complete list of all channels across all playlists (for fast global search). */
    val allChannelsForSearch: List<ChannelDisplayItem> = emptyList(),

    /** EPG sync progress (0.0..1.0) when actively running, or null when idle. */
    val epgSyncProgress: Float? = null,

    /** EPG sync status message (e.g. "Загрузка EPG..."). */
    val epgSyncStatus: String? = null,

    /** Whether EPG is currently being searched online for the focused channel. */
    val isSidePanelEpgLoading: Boolean = false,

    /** Whether the expanded program details panel is open on the right (D-Pad RIGHT). */
    val isProgramDetailsOpen: Boolean = false,

    /** Whether the exit confirmation dialog is open. */
    val isExitConfirmOpen: Boolean = false,
) {
    /** Helper check for whether the settings icon is currently focused. */
    val isSettingsFocused: Boolean get() = topBarFocus == TopBarFocus.SETTINGS

    /** Helper check for whether the search button is currently focused. */
    val isSearchFocused: Boolean get() = topBarFocus == TopBarFocus.SEARCH

    /** Helper check for whether the group selector pill is currently focused. */
    val isGroupFocused: Boolean get() = topBarFocus == TopBarFocus.GROUP_SELECTOR
}

/**
 * Screen mode for the main window: thematic folders or channel list.
 */
enum class ScreenViewMode {
    FOLDERS,
    CHANNELS,
}

/**
 * A thematic folder / category item.
 */
data class ChannelFolderItem(
    val title: String,
    val channelCount: Int,
    val iconType: FolderIconType = FolderIconType.CATEGORY,
    val isLocked: Boolean = false,
    val isMostUsed: Boolean = false,
)

enum class FolderIconType {
    ALL,
    FAVORITES,
    CATEGORY,
}

/**
 * Elements in the top bar that can receive D-Pad focus.
 */
enum class TopBarFocus {
    NONE,
    GROUP_SELECTOR,
    SEARCH,
    SETTINGS,
}

/**
 * A single row in the channel list.
 *
 * Combines the [Channel] domain object with live EPG data so the list item
 * can render the programme title and progress bar without extra lookups.
 */
data class ChannelDisplayItem(
    val channel: Channel,
    /** Currently airing programme, or null if no EPG is available. */
    val currentProgram: EpgProgram? = null,
    /** Next programme, shown as secondary text. */
    val nextProgram: EpgProgram? = null,
    /** Progress of the current programme [0f..1f] for the live progress bar. */
    val epgProgress: Float = 0f,
    /** Whether this channel supports catch-up / archive playback. */
    val hasCatchup: Boolean = false,
    /** Whether this channel is in the current profile's favourites. */
    val isFavorite: Boolean = false,
)

/**
 * An item in the EPG side panel — either a current/future EPG programme
 * or a catch-up archive slot.
 */
sealed class SidePanelItem {

    /** A future or currently-live EPG programme. */
    data class Programme(
        val program: EpgProgram,
        val isLive: Boolean,
        val progress: Float,
    ) : SidePanelItem()

    /** A catch-up archive slot (past programme). */
    data class CatchupSlot(
        val program: EpgProgram,
        /** Archive download is available for this slot. */
        val isAvailable: Boolean,
    ) : SidePanelItem()
}

/**
 * One-shot intents dispatched from the UI (key events, clicks) to the ViewModel.
 *
 * Sealed class ensures exhaustive `when` handling in the ViewModel.
 */
sealed class ChannelListIntent {

    /** Play the stream for the given channel. */
    data class PlayChannel(val channelId: Long) : ChannelListIntent()

    /** Open the context bottom-sheet for the given channel (long-press OK). */
    data class OpenContextMenu(val channelId: Long) : ChannelListIntent()

    /** Dismiss the context menu. */
    data object DismissContextMenu : ChannelListIntent()

    /** Slide in the EPG side panel (D-Pad RIGHT). */
    data object OpenEpgSidePanel : ChannelListIntent()

    /** Slide out the EPG side panel and return focus to the channel list (D-Pad LEFT). */
    data object CloseEpgSidePanel : ChannelListIntent()

    /** Open detailed program description panel on the right (D-Pad RIGHT from EPG list). */
    data object OpenProgramDetails : ChannelListIntent()

    /** Close detailed program description panel and return to EPG list (D-Pad LEFT or Back). */
    data object CloseProgramDetails : ChannelListIntent()

    /** Move D-Pad focus up or down within the channel list. */
    data class MoveFocus(val delta: Int) : ChannelListIntent()

    /** Move D-Pad focus within the thematic folders list. */
    data class MoveFolderFocus(val delta: Int) : ChannelListIntent()

    /** Select a thematic folder and open its channel list. */
    data class SelectFolder(val folder: ChannelFolderItem) : ChannelListIntent()

    /** Return back from channel list to thematic folders list (D-Pad LEFT / Back). */
    data object ReturnToFolders : ChannelListIntent()

    /** Move focus within the side panel. */
    data class MoveSidePanelFocus(val delta: Int) : ChannelListIntent()

    /** Start silent background live-preview (triggered after 1.5 s hover). */
    data class StartLivePreview(val channelId: Long) : ChannelListIntent()

    /** Stop live preview (focus moved away). */
    data object StopLivePreview : ChannelListIntent()

    /** Toggle favourite status for the focused channel. */
    data class ToggleFavorite(val channelId: Long) : ChannelListIntent()

    /** Toggle play/pause for the active stream. */
    data object TogglePlayback : ChannelListIntent()

    /** User played a catch-up slot from the side panel. */
    data class PlayCatchup(val channelId: Long, val programStartMs: Long) : ChannelListIntent()

    /** Dismiss a transient error snack-bar. */
    data object DismissError : ChannelListIntent()

    /** Move focus to an element in the top bar (e.g. from Channel 0 pressing UP). */
    data class FocusTopBar(val target: TopBarFocus = TopBarFocus.SETTINGS) : ChannelListIntent()

    /** Move focus between elements within the top bar (LEFT/RIGHT). */
    data class MoveTopBarFocus(val target: TopBarFocus) : ChannelListIntent()

    /** Exit top bar focus and return to channel list (e.g. pressing DOWN). */
    data object ExitTopBarFocus : ChannelListIntent()

    /** Open global channel/EPG search dialog. */
    data object OpenSearch : ChannelListIntent()

    /** Close global channel/EPG search dialog. */
    data object CloseSearch : ChannelListIntent()

    /** Dismiss Parental Control PIN dialog without unlocking. */
    data object DismissPinDialog : ChannelListIntent()

    /** Unlock a protected folder after successful PIN entry. */
    data class UnlockFolder(val folderTitle: String) : ChannelListIntent()

    /** Show exit confirmation dialog before quitting the app. */
    data object ShowExitConfirm : ChannelListIntent()

    /** Dismiss exit confirmation dialog without quitting. */
    data object DismissExitConfirm : ChannelListIntent()
}

/**
 * One-shot navigation events emitted by [ChannelListViewModel] to [ChannelListScreen].
 */
sealed interface ChannelListNavEvent {
    data class NavigateToPlayer(val channelId: Long, val catchupStartMs: Long? = null) : ChannelListNavEvent
}
