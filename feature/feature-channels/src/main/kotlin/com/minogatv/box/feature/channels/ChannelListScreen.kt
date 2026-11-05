package com.minogatv.box.feature.channels

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import com.minogatv.box.feature.channels.ui.ExitConfirmDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.runtime.rememberUpdatedState
import com.minogatv.box.feature.ui.LocalScreenKeyDispatcher
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Archive
import com.minogatv.box.feature.channels.ui.ChannelSearchDialog
import com.minogatv.box.feature.channels.ui.ParentalPinDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.minogatv.box.feature.player.IptvMediaSourceHelper
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.minogatv.box.feature.ui.RemoteControlHintBar
import com.minogatv.box.feature.ui.RemoteHint
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Design tokens ────────────────────────────────────────────────────────────

private val ScreenBg           = ChannelDesignTokens.MidnightBg
private val ItemBgFocused      = ChannelDesignTokens.CardGlassFocused
private val ItemBorderFocused  = ChannelDesignTokens.NeonCyan
private val AccentBlue         = ChannelDesignTokens.ElectricBlue
private val TextPrimary        = ChannelDesignTokens.TextPrimary
private val TextSecondary      = ChannelDesignTokens.TextSecondary
private val CatchupGreen       = ChannelDesignTokens.CatchupGreen
private val FavRed             = ChannelDesignTokens.FavRed
private val LogoPlaceholderBg  = Color(0xFF131825)

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

/**
 * # ChannelListScreen
 *
 * The main channel-list screen for the Minoga TV Box app.
 *
 * ## Layout
 * ```
 * ┌─────────────────────────────┬──────────────┐
 * │   Channel list (LazyColumn) │  EPG Side    │
 * │                             │  Panel       │
 * │   [ch logo] [name]          │  (1/3 width, │
 * │   [programme] ════ 45%      │  slide-in)   │
 * │                             │              │
 * └─────────────────────────────┴──────────────┘
 * ```
 *
 * ## D-Pad behaviour
 * All key events are intercepted via `Modifier.onPreviewKeyEvent` and routed
 * through [KeyEventHandler] → [ChannelListViewModel.onIntent].
 *
 * @param onNavigateToPlayer  Called with (channelId, catchupStartMs?) when the user
 *                            plays a channel or a catch-up slot.
 * @param viewModel           Injected [ChannelListViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun ChannelListScreen(
    onNavigateToPlayer: (channelId: Long, catchupStartMs: Long?) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: ChannelListViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listFocusRequester = remember { FocusRequester() }
    val sidePanelFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val screenKeyDispatcher = LocalScreenKeyDispatcher.current
    val currentUiState by rememberUpdatedState(uiState)

    // ── Live Preview background player (muted, low overhead, hardware codec safe) ──
    val previewPlayer = remember(context) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1000,
                /* maxBufferMs = */ 3000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 500,
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                volume = 0f
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        // Suppress player errors in preview to prevent crashing on TV box
                        stop()
                        clearMediaItems()
                    }
                })
            }
    }

    val prefs = remember {
        context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE).also { sp ->
            if (!sp.getBoolean("ui_scale_default_40_applied", false)) {
                sp.edit()
                    .putInt("ui_scale_percent", 40)
                    .putBoolean("ui_scale_default_40_applied", true)
                    .apply()
            }
        }
    }
    var uiScalePercent by remember { mutableStateOf(prefs.getInt("ui_scale_percent", 40)) }
    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "ui_scale_percent") {
                uiScalePercent = prefs.getInt("ui_scale_percent", 40)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val scaleFactor = (uiScalePercent / 100f).coerceIn(0.4f, 1.0f)
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val isCompactScreen = isPortrait || configuration.screenHeightDp < 500 || configuration.screenWidthDp < 750
    val currentFolderCols by rememberUpdatedState(if (isPortrait) 2 else 4)

    // Release hardware codecs on lifecycle pause/stop so fullscreen player doesn't crash
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, previewPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                previewPlayer.stop()
                previewPlayer.clearMediaItems()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            previewPlayer.stop()
            previewPlayer.release()
        }
    }

    val navigateToPlayerSafe: (Long, Long?) -> Unit = remember(onNavigateToPlayer, previewPlayer) {
        { channelId, catchupStartMs ->
            try {
                previewPlayer.stop()
                previewPlayer.clearMediaItems()
            } catch (_: Exception) {}
            onNavigateToPlayer(channelId, catchupStartMs)
        }
    }

    LaunchedEffect(uiState.livePreviewChannelId, uiState.viewMode) {
        val isPreviewEnabled = try {
            val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("channel_preview_enabled", true)
        } catch (_: Exception) { true }

        val previewId = if (isPreviewEnabled) uiState.livePreviewChannelId else null
        if (uiState.viewMode == ScreenViewMode.CHANNELS && previewId != null) {
            val previewChannel = uiState.channels.firstOrNull { it.channel.id == previewId }?.channel
            if (previewChannel != null && previewChannel.streamUrl.isNotBlank()) {
                previewPlayer.setMediaItem(MediaItem.fromUri(previewChannel.streamUrl))
                previewPlayer.prepare()
                previewPlayer.play()
            } else {
                previewPlayer.stop()
                previewPlayer.clearMediaItems()
            }
        } else {
            previewPlayer.stop()
            previewPlayer.clearMediaItems()
        }
    }

    val detailsScrollState = rememberScrollState()

    // Reset details scroll position to top whenever details open or focused item changes
    LaunchedEffect(uiState.isProgramDetailsOpen, uiState.sidePanelFocusedIndex) {
        if (uiState.isProgramDetailsOpen) {
            detailsScrollState.scrollTo(0)
        }
    }

    // ── KeyEventHandler — one instance per screen, stable across recompositions
    val keyHandler = remember(onNavigateToSettings, detailsScrollState) {
        KeyEventHandler(
            scope = scope,
            onIntent = viewModel::onIntent,
            onOpenSettings = onNavigateToSettings,
            onScrollProgramDetails = { delta ->
                scope.launch {
                    val target = (detailsScrollState.value + delta).coerceIn(0, detailsScrollState.maxValue)
                    detailsScrollState.animateScrollTo(target)
                }
            },
            folderGridColumnsProvider = { currentFolderCols },
        )
    }

    BackHandler(enabled = uiState.isExitConfirmOpen) {
        viewModel.onIntent(ChannelListIntent.DismissExitConfirm)
    }

    BackHandler(enabled = !uiState.isExitConfirmOpen && uiState.isContextMenuOpen) {
        viewModel.onIntent(ChannelListIntent.DismissContextMenu)
    }

    BackHandler(enabled = !uiState.isExitConfirmOpen && uiState.isProgramDetailsOpen) {
        viewModel.onIntent(ChannelListIntent.CloseProgramDetails)
    }

    BackHandler(enabled = !uiState.isExitConfirmOpen && !uiState.isProgramDetailsOpen && uiState.isFocusInSidePanel) {
        viewModel.onIntent(ChannelListIntent.CloseEpgSidePanel)
    }

    BackHandler(enabled = !uiState.isExitConfirmOpen && !uiState.isContextMenuOpen && !uiState.isProgramDetailsOpen && !uiState.isFocusInSidePanel && uiState.viewMode == ScreenViewMode.CHANNELS) {
        viewModel.onIntent(ChannelListIntent.ReturnToFolders)
    }

    BackHandler(enabled = !uiState.isExitConfirmOpen && !uiState.isContextMenuOpen && !uiState.isProgramDetailsOpen && !uiState.isFocusInSidePanel && !uiState.isSearchOpen && uiState.pendingPinFolder == null && uiState.viewMode == ScreenViewMode.FOLDERS) {
        viewModel.onIntent(ChannelListIntent.ShowExitConfirm)
    }

    // ── Navigation events from ViewModel ─────────────────────────────────────
    LaunchedEffect(viewModel) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is ChannelListNavEvent.NavigateToPlayer -> navigateToPlayerSafe(event.channelId, event.catchupStartMs)
            }
        }
    }

    // ── Error snack-bar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onIntent(ChannelListIntent.DismissError)
        }
    }

    // ── Register screen key handler so D-Pad & gesture keys work even in InputMode.Touch
    DisposableEffect(screenKeyDispatcher, keyHandler) {
        val handler: (KeyEvent) -> Boolean = { event ->
            val state = currentUiState
            if (state.isSearchOpen || state.pendingPinFolder != null || state.isExitConfirmOpen || state.isContextMenuOpen) {
                false
            } else {
                keyHandler.onKeyEvent(event, state)
            }
        }
        screenKeyDispatcher?.activeHandler = handler
        onDispose {
            if (screenKeyDispatcher?.activeHandler === handler) {
                screenKeyDispatcher.activeHandler = null
            }
        }
    }

    // ── Transfer focus when side panel opens/closes or view mode changes
    LaunchedEffect(uiState.isFocusInSidePanel, uiState.viewMode, uiState.channels.isEmpty()) {
        runCatching {
            when {
                uiState.viewMode == ScreenViewMode.CHANNELS && uiState.isFocusInSidePanel ->
                    sidePanelFocusRequester.requestFocus()
                uiState.viewMode == ScreenViewMode.CHANNELS && uiState.channels.isNotEmpty() ->
                    listFocusRequester.requestFocus()
                else ->
                    rootFocusRequester.requestFocus()
            }
        }
    }

    // ── Root container — intercepts ALL key events before any child sees them
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChannelDesignTokens.BackgroundGradient)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                keyHandler.onKeyEvent(keyEvent.nativeKeyEvent, uiState)
            },
    ) {
        // ── Main column layout: TopBar header + Content area
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top Bar with Group pill, Search & D-Pad focusable Settings ─
            TopBar(
                viewMode = uiState.viewMode,
                selectedGroupName = uiState.selectedGroupName.ifBlank { "Все каналы" },
                channelCount = if (uiState.viewMode == ScreenViewMode.FOLDERS) {
                    uiState.folders.firstOrNull { it.iconType == FolderIconType.ALL }?.channelCount ?: uiState.channels.size
                } else {
                    uiState.channels.size
                },
                folderCount = uiState.folders.size,
                isSettingsFocused = uiState.isSettingsFocused,
                isSearchFocused = uiState.isSearchFocused,
                isGroupFocused = uiState.isGroupFocused,
                isCompact = isCompactScreen,
                isPortrait = isPortrait,
                onSearchClick = { viewModel.onIntent(ChannelListIntent.OpenSearch) },
                onSettingsClick = onNavigateToSettings,
                onBackToFoldersClick = { viewModel.onIntent(ChannelListIntent.ReturnToFolders) },
                onGroupClick = {
                    if (uiState.viewMode == ScreenViewMode.CHANNELS) {
                        viewModel.onIntent(ChannelListIntent.ReturnToFolders)
                    } else {
                        viewModel.onIntent(ChannelListIntent.FocusTopBar(TopBarFocus.GROUP_SELECTOR))
                    }
                },
            )

            // ── EPG Sync Progress Bar UNDER TopBar (Search / Folders row) ─────
            val syncProgress = uiState.epgSyncProgress
            val syncStatus = uiState.epgSyncStatus
            AnimatedVisibility(
                visible = syncProgress != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                if (syncProgress != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xD90A0E1A))
                            .padding(horizontal = 24.dp, vertical = 6.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = syncStatus ?: "Загрузка и обработка телепрограммы (EPG)…",
                                color = ChannelDesignTokens.NeonCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${(syncProgress * 100).toInt()}%",
                                color = ChannelDesignTokens.AccentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { syncProgress },
                            color = ChannelDesignTokens.NeonCyan,
                            trackColor = Color(0x3300E5FF),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }

            // ── Main content: Folders grid OR Channels 2-column split ─
            if (uiState.folders.isEmpty() && uiState.channels.isEmpty()) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = ChannelDesignTokens.NeonCyan,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Загрузка каналов…",
                                color = ChannelDesignTokens.TextSecondary,
                                fontSize = 16.sp,
                            )
                        }
                    }
                } else {
                    // Empty state when no channels or playlists are loaded
                    EmptyChannelsView(
                        isSettingsFocused = uiState.isSettingsFocused,
                        onOpenSettings = onNavigateToSettings,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            } else if (uiState.viewMode == ScreenViewMode.FOLDERS) {
                // Окно 1: Сетка тематических папок (Stitch Category Grid — 2 колонки в портрете, 4 в ландшафте)
                FolderGrid(
                    uiState = uiState,
                    columnsCount = if (isPortrait) 2 else 4,
                    onFolderClick = { folder ->
                        viewModel.onIntent(ChannelListIntent.SelectFolder(folder))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else if (isPortrait) {
                // Окно 2 (Портретный режим для смартфона):
                // Верх: 16:9 Живое превью во всю ширину
                // Середина: Вкладки [📺 Каналы] | [📅 Программа и Архив]
                // Низ: Полноширинный список каналов ИЛИ телепрограммы
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StitchLivePreviewCard(
                        uiState = uiState,
                        previewPlayer = previewPlayer,
                        isCompact = true,
                        onPlayLive = { channelId -> navigateToPlayerSafe(channelId, null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (uiState.isProgramDetailsOpen) 88.dp else 132.dp),
                    )

                    // Переключатель вкладок: Каналы / Телепрограмма и Архив
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xCC0D1322))
                            .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(10.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val isChannelsTab = !uiState.isFocusInSidePanel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isChannelsTab) Color(0x3300E5FF) else Color.Transparent)
                                .border(
                                    width = if (isChannelsTab) 1.dp else 0.dp,
                                    color = if (isChannelsTab) ChannelDesignTokens.NeonCyan else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    if (uiState.isProgramDetailsOpen) {
                                        viewModel.onIntent(ChannelListIntent.CloseProgramDetails)
                                    }
                                    if (uiState.isFocusInSidePanel) {
                                        viewModel.onIntent(ChannelListIntent.CloseEpgSidePanel)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "📺 Каналы (${uiState.channels.size})",
                                color = if (isChannelsTab) Color.White else TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isChannelsTab) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        val isEpgTab = uiState.isFocusInSidePanel
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isEpgTab) Color(0x3300E5FF) else Color.Transparent)
                                .border(
                                    width = if (isEpgTab) 1.dp else 0.dp,
                                    color = if (isEpgTab) ChannelDesignTokens.NeonCyan else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable {
                                    viewModel.onIntent(ChannelListIntent.OpenEpgSidePanel)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "📅 Программа и Архив (${uiState.sidePanelItems.size})",
                                color = if (isEpgTab) Color.White else TextSecondary,
                                fontSize = 11.5.sp,
                                fontWeight = if (isEpgTab) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }

                    // Контент активной вкладки во всю ширину экрана
                    if (!uiState.isFocusInSidePanel) {
                        ChannelList(
                            uiState = uiState,
                            listFocusRequester = listFocusRequester,
                            scaleFactor = scaleFactor,
                            onChannelSelect = { index ->
                                viewModel.onIntent(ChannelListIntent.SelectChannelIndex(index))
                            },
                            onChannelClick = { channelId ->
                                navigateToPlayerSafe(channelId, null)
                            },
                            onChannelLongClick = { index, channelId ->
                                viewModel.onIntent(ChannelListIntent.SelectChannelIndex(index))
                                viewModel.onIntent(ChannelListIntent.OpenContextMenu(channelId))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                    } else {
                        EpgSidePanel(
                            isVisible = true,
                            items = uiState.sidePanelItems,
                            focusedIndex = uiState.sidePanelFocusedIndex,
                            channelName = uiState.channels
                                .getOrNull(uiState.focusedIndex)?.channel?.name ?: "",
                            isFocusInSidePanel = uiState.isFocusInSidePanel,
                            isProgramDetailsOpen = uiState.isProgramDetailsOpen,
                            isLoadingEpg = uiState.isSidePanelEpgLoading,
                            scaleFactor = scaleFactor,
                            detailsScrollState = detailsScrollState,
                            onItemFocused = { idx ->
                                viewModel.onIntent(ChannelListIntent.SelectSidePanelIndex(idx))
                            },
                            onHeaderActionClick = {
                                when {
                                    uiState.isProgramDetailsOpen ->
                                        viewModel.onIntent(ChannelListIntent.CloseProgramDetails)
                                    uiState.isFocusInSidePanel ->
                                        viewModel.onIntent(ChannelListIntent.OpenProgramDetails)
                                    uiState.sidePanelItems.isNotEmpty() ->
                                        viewModel.onIntent(ChannelListIntent.OpenEpgSidePanel)
                                }
                            },
                            onItemSelected = { item ->
                                val currentItem = uiState.channels.getOrNull(uiState.focusedIndex) ?: return@EpgSidePanel
                                val channelId = currentItem.channel.id
                                when (item) {
                                    is SidePanelItem.Programme -> {
                                        if (item.program.endMs <= System.currentTimeMillis()) {
                                            if (currentItem.hasCatchup) {
                                                navigateToPlayerSafe(channelId, item.program.startMs)
                                            } else {
                                                viewModel.onIntent(ChannelListIntent.OpenProgramDetails)
                                            }
                                        } else if (item.isLive) {
                                            navigateToPlayerSafe(channelId, null)
                                        } else {
                                            viewModel.onIntent(ChannelListIntent.OpenProgramDetails)
                                        }
                                    }
                                    is SidePanelItem.CatchupSlot -> {
                                        if (currentItem.hasCatchup) {
                                            onNavigateToPlayer(channelId, item.program.startMs)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(sidePanelFocusRequester),
                        )
                    }
                }
            } else {
                // Окно 2: Двухпанельный ТВ-макет (адаптивный для смартфонов и ТВ)
                val previewHeight = when {
                    isCompactScreen && uiState.isProgramDetailsOpen -> 52.dp
                    isCompactScreen -> 68.dp
                    uiState.isProgramDetailsOpen -> (120 * scaleFactor).coerceAtLeast(64f).dp
                    else -> (170 * scaleFactor).coerceAtLeast(84f).dp
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isCompactScreen) 8.dp else 16.dp,
                            vertical = if (isCompactScreen) 3.dp else 6.dp,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompactScreen) 8.dp else 16.dp),
                ) {
                    // ── Левая колонка: Список каналов с фокусом и бейджами ──
                    ChannelList(
                        uiState = uiState,
                        listFocusRequester = listFocusRequester,
                        scaleFactor = scaleFactor,
                        onChannelSelect = { index ->
                            viewModel.onIntent(ChannelListIntent.SelectChannelIndex(index))
                        },
                        onChannelClick = { channelId ->
                            if (uiState.isFocusInSidePanel) {
                                viewModel.onIntent(ChannelListIntent.CloseEpgSidePanel)
                            }
                            navigateToPlayerSafe(channelId, null)
                        },
                        onChannelLongClick = { index, channelId ->
                            viewModel.onIntent(ChannelListIntent.SelectChannelIndex(index))
                            viewModel.onIntent(ChannelListIntent.OpenContextMenu(channelId))
                        },
                        modifier = Modifier
                            .weight(if (uiState.isProgramDetailsOpen) 0.34f else 0.46f)
                            .fillMaxHeight(),
                    )

                    // ── Правая колонка: 16:9 Живое Превью + Телепрограмма и архив ──
                    Column(
                        modifier = Modifier
                            .weight(if (uiState.isProgramDetailsOpen) 0.66f else 0.54f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(
                            if (isCompactScreen || uiState.isProgramDetailsOpen) 4.dp else 10.dp
                        ),
                    ) {
                        // 16:9 Мини-плеер живого превью (уменьшенный для экрана смартфона)
                        StitchLivePreviewCard(
                            uiState = uiState,
                            previewPlayer = previewPlayer,
                            isCompact = isCompactScreen || scaleFactor <= 0.65f,
                            onPlayLive = { channelId -> navigateToPlayerSafe(channelId, null) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(previewHeight),
                        )

                        // Телепрограмма и архив выбранного канала
                        EpgSidePanel(
                            isVisible = true,
                            items = uiState.sidePanelItems,
                            focusedIndex = uiState.sidePanelFocusedIndex,
                            channelName = uiState.channels
                                .getOrNull(uiState.focusedIndex)?.channel?.name ?: "",
                            isFocusInSidePanel = uiState.isFocusInSidePanel,
                            isProgramDetailsOpen = uiState.isProgramDetailsOpen,
                            isLoadingEpg = uiState.isSidePanelEpgLoading,
                            scaleFactor = scaleFactor,
                            detailsScrollState = detailsScrollState,
                            onItemFocused = { idx ->
                                viewModel.onIntent(ChannelListIntent.SelectSidePanelIndex(idx))
                            },
                            onHeaderActionClick = {
                                when {
                                    uiState.isProgramDetailsOpen ->
                                        viewModel.onIntent(ChannelListIntent.CloseProgramDetails)
                                    uiState.isFocusInSidePanel ->
                                        viewModel.onIntent(ChannelListIntent.OpenProgramDetails)
                                    uiState.sidePanelItems.isNotEmpty() ->
                                        viewModel.onIntent(ChannelListIntent.OpenEpgSidePanel)
                                }
                            },
                            onItemSelected = { item ->
                                val currentItem = uiState.channels.getOrNull(uiState.focusedIndex) ?: return@EpgSidePanel
                                val channelId = currentItem.channel.id
                                when (item) {
                                    is SidePanelItem.Programme -> {
                                        if (item.program.endMs <= System.currentTimeMillis()) {
                                            if (currentItem.hasCatchup) {
                                                navigateToPlayerSafe(channelId, item.program.startMs)
                                            } else {
                                                viewModel.onIntent(ChannelListIntent.OpenProgramDetails)
                                            }
                                        } else if (item.isLive) {
                                            navigateToPlayerSafe(channelId, null)
                                        } else {
                                            viewModel.onIntent(ChannelListIntent.OpenProgramDetails)
                                        }
                                    }
                                    is SidePanelItem.CatchupSlot -> {
                                        if (currentItem.hasCatchup) {
                                            onNavigateToPlayer(channelId, item.program.startMs)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .focusRequester(sidePanelFocusRequester),
                        )
                    }
                }
            }

            // ── Remote control guide bar (also tappable on touch screens) ─────
            val focusedChannelId = uiState.channels.getOrNull(uiState.focusedIndex)?.channel?.id
            val hints = if (uiState.viewMode == ScreenViewMode.FOLDERS) {
                listOf(
                    RemoteHint("◄▲▼►", "Папки"),
                    RemoteHint("OK", "Открыть") {
                        if (uiState.folders.isEmpty() && uiState.channels.isEmpty()) {
                            onNavigateToSettings()
                        } else {
                            uiState.folders.getOrNull(uiState.focusedFolderIndex)?.let {
                                viewModel.onIntent(ChannelListIntent.SelectFolder(it))
                            }
                        }
                    },
                    RemoteHint("🔍", "Поиск") {
                        viewModel.onIntent(ChannelListIntent.OpenSearch)
                    },
                    RemoteHint("⚙", "Настройки") {
                        onNavigateToSettings()
                    },
                )
            } else if (uiState.isProgramDetailsOpen) {
                listOf(
                    RemoteHint("▲▼", "Прокрутка"),
                    RemoteHint("OK", "Смотреть / Архив") {
                        focusedChannelId?.let { navigateToPlayerSafe(it, null) }
                    },
                    RemoteHint("◀ / Back", "К передачам") {
                        viewModel.onIntent(ChannelListIntent.CloseProgramDetails)
                    },
                    RemoteHint("🔴", "Избранное", keyColor = Color(0xFFFF5252)) {
                        focusedChannelId?.let { viewModel.onIntent(ChannelListIntent.ToggleFavorite(it)) }
                    },
                )
            } else if (uiState.isFocusInSidePanel) {
                listOf(
                    RemoteHint("▲▼", "Передачи"),
                    RemoteHint("▶", "Инфо") {
                        viewModel.onIntent(ChannelListIntent.OpenProgramDetails)
                    },
                    RemoteHint("OK", "Смотреть / Архив") {
                        focusedChannelId?.let { navigateToPlayerSafe(it, null) }
                    },
                    RemoteHint("◀", "Каналы") {
                        viewModel.onIntent(ChannelListIntent.CloseEpgSidePanel)
                    },
                    RemoteHint("🔴", "Избранное", keyColor = Color(0xFFFF5252)) {
                        focusedChannelId?.let { viewModel.onIntent(ChannelListIntent.ToggleFavorite(it)) }
                    },
                )
            } else {
                listOf(
                    RemoteHint("▲▼", "Каналы"),
                    RemoteHint("OK", "Смотреть") {
                        focusedChannelId?.let { navigateToPlayerSafe(it, null) }
                    },
                    RemoteHint("◀", "Папки") {
                        viewModel.onIntent(ChannelListIntent.ReturnToFolders)
                    },
                    RemoteHint("▶", "Программа") {
                        if (uiState.sidePanelItems.isNotEmpty()) {
                            viewModel.onIntent(ChannelListIntent.OpenEpgSidePanel)
                        }
                    },
                    RemoteHint("🔴", "Избранное", keyColor = Color(0xFFFF5252)) {
                        focusedChannelId?.let { viewModel.onIntent(ChannelListIntent.ToggleFavorite(it)) }
                    },
                )
            }
            RemoteControlHintBar(hints = hints)
        }

        // ── Global Search Dialog
        if (uiState.isSearchOpen) {
            ChannelSearchDialog(
                allChannels = uiState.channels,
                epgSyncProgress = uiState.epgSyncProgress,
                epgSyncStatus = uiState.epgSyncStatus,
                onSelectChannel = { channelId ->
                    viewModel.onIntent(ChannelListIntent.CloseSearch)
                    navigateToPlayerSafe(channelId, null)
                },
                onDismiss = { viewModel.onIntent(ChannelListIntent.CloseSearch) },
            )
        }

        // ── Parental Control PIN Dialog
        uiState.pendingPinFolder?.let { lockedFolder ->
            ParentalPinDialog(
                folderTitle = lockedFolder.title,
                onSuccess = {
                    viewModel.onIntent(ChannelListIntent.UnlockFolder(lockedFolder.title))
                },
                onDismiss = {
                    viewModel.onIntent(ChannelListIntent.DismissPinDialog)
                },
            )
        }

        // ── Exit Confirmation Dialog
        if (uiState.isExitConfirmOpen) {
            val context = LocalContext.current
            ExitConfirmDialog(
                onConfirmExit = {
                    val activity = context.findActivity() ?: (context as? Activity)
                    activity?.finish()
                },
                onDismiss = {
                    viewModel.onIntent(ChannelListIntent.DismissExitConfirm)
                },
            )
        }

        // ── Loading overlay (only when initial data is completely empty)
        if (uiState.isLoading && uiState.folders.isEmpty() && uiState.channels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ScreenBg.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AccentBlue)
            }
        }

        // ── Snack-bar for errors
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data -> Snackbar(snackbarData = data) }
    }

    // ── Context menu bottom-sheet (long-press OK)
    if (uiState.isContextMenuOpen) {
        val focused = uiState.channels.getOrNull(uiState.focusedIndex)
        if (focused != null) {
            ContextMenuSheet(
                item = focused,
                uiScalePercent = uiScalePercent,
                onSelectScale = { scale ->
                    uiScalePercent = scale
                    prefs.edit().putInt("ui_scale_percent", scale).apply()
                },
                onDismiss = { viewModel.onIntent(ChannelListIntent.DismissContextMenu) },
                onToggleFavorite = {
                    viewModel.onIntent(ChannelListIntent.ToggleFavorite(focused.channel.id))
                },
                onPlay = {
                    viewModel.onIntent(ChannelListIntent.DismissContextMenu)
                    navigateToPlayerSafe(focused.channel.id, null)
                },
            )
        }
    }
}

// ─── Channel list ─────────────────────────────────────────────────────────────

@Composable
private fun ChannelList(
    uiState: ChannelListUiState,
    listFocusRequester: FocusRequester,
    scaleFactor: Float = 1.0f,
    onChannelSelect: (Int) -> Unit = {},
    onChannelClick: (Long) -> Unit = {},
    onChannelLongClick: (Int, Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    if (uiState.channels.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "В этой папке нет каналов",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = (16 * scaleFactor).coerceAtLeast(12f).sp,
            )
        }
        return
    }

    val listState = rememberLazyListState()

    // Fast auto-scroll to keep focused item visible without lagging animations
    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.channels.isNotEmpty()) {
            val target = uiState.focusedIndex.coerceIn(0, uiState.channels.lastIndex)
            val visible = listState.layoutInfo.visibleItemsInfo
            val firstVisible = visible.firstOrNull()?.index ?: -1
            val lastVisible = visible.lastOrNull()?.index ?: -1
            if (visible.isEmpty() || target <= firstVisible || target >= lastVisible) {
                listState.scrollToItem(target)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .focusRequester(listFocusRequester)
            .focusable()
            .padding(horizontal = 2.dp, vertical = (4 * scaleFactor).coerceAtLeast(2f).dp),
        verticalArrangement = Arrangement.spacedBy((5 * scaleFactor).coerceAtLeast(2f).dp),
    ) {
        itemsIndexed(
            items = uiState.channels,
            key = { _, item -> item.channel.id },
        ) { index, item ->
            val isChannelFocused = index == uiState.focusedIndex && !uiState.isFocusInSidePanel && uiState.topBarFocus == TopBarFocus.NONE
            ChannelListItem(
                item = item,
                index = index,
                isFocused = isChannelFocused,
                hasLivePreview = uiState.livePreviewChannelId == item.channel.id,
                scaleFactor = scaleFactor,
                onClick = {
                    if (isChannelFocused) {
                        onChannelClick(item.channel.id)
                    } else {
                        onChannelSelect(index)
                    }
                },
                onDoubleClick = { onChannelClick(item.channel.id) },
                onLongClick = { onChannelLongClick(index, item.channel.id) },
            )
        }
    }
}

// ─── Channel list item ────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelListItem(
    item: ChannelDisplayItem,
    index: Int,
    isFocused: Boolean,
    hasLivePreview: Boolean,
    scaleFactor: Float = 1.0f,
    onClick: () -> Unit = {},
    onDoubleClick: () -> Unit = onClick,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .tvFocusCard(isFocused = isFocused, cornerRadius = (14 * scaleFactor).coerceAtLeast(8f).dp)
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = onDoubleClick,
                onLongClick = onLongClick,
            ),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // High-visibility left indicator bar for TV remote navigation
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .width((5 * scaleFactor).coerceAtLeast(3f).dp)
                        .height((38 * scaleFactor).coerceAtLeast(16f).dp)
                        .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        .background(ChannelDesignTokens.NeonCyan),
                )
            } else {
                Spacer(Modifier.width((5 * scaleFactor).coerceAtLeast(3f).dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(
                        start = (8 * scaleFactor).coerceAtLeast(4f).dp,
                        end = (10 * scaleFactor).coerceAtLeast(5f).dp,
                        top = (8 * scaleFactor).coerceAtLeast(3f).dp,
                        bottom = (8 * scaleFactor).coerceAtLeast(3f).dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ── Channel Number Pill (Concept 2 style)
                    Box(
                        modifier = Modifier
                            .width((28 * scaleFactor).coerceAtLeast(18f).dp)
                            .padding(end = (3 * scaleFactor).coerceAtLeast(2f).dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d", index + 1),
                            color = if (isFocused) ChannelDesignTokens.NeonCyan else ChannelDesignTokens.TextMuted,
                            fontSize = if (isFocused) (15 * scaleFactor).coerceAtLeast(9.5f).sp else (14 * scaleFactor).coerceAtLeast(9f).sp,
                            fontWeight = if (isFocused) FontWeight.Black else FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    // ── Channel logo in rounded container
                    ChannelLogo(
                        logoUrl = item.channel.logoUrl,
                        isFocused = isFocused,
                        size = ((if (isFocused) 44 else 38) * scaleFactor).coerceAtLeast(22f).dp,
                    )

                    Spacer(Modifier.width((10 * scaleFactor).coerceAtLeast(5f).dp))

                    // ── Name + EPG info
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((6 * scaleFactor).coerceAtLeast(3f).dp),
                        ) {
                            Text(
                                text = item.channel.name,
                                color = if (isFocused) Color.White else TextPrimary,
                                fontSize = if (isFocused) (16 * scaleFactor).coerceAtLeast(10.5f).sp else (15 * scaleFactor).coerceAtLeast(10f).sp,
                                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )

                        // Catch-up badge
                        if (item.hasCatchup) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0x2E10B981),
                                border = BorderStroke(0.8.dp, Color(0x8010B981)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = (4 * scaleFactor).coerceAtLeast(2.5f).dp, vertical = (1.5f * scaleFactor).coerceAtLeast(0.5f).dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Archive,
                                        contentDescription = "Catch-up available",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size((10 * scaleFactor).coerceAtLeast(7.5f).dp),
                                    )
                                    Text(
                                        text = if (scaleFactor <= 0.65f) "АРХ" else "АРХИВ",
                                        color = Color(0xFF10B981),
                                        fontSize = (8.5f * scaleFactor).coerceAtLeast(6.5f).sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }

                        // Live Preview badge
                        if (hasLivePreview) {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = ChannelDesignTokens.NeonCyan.copy(alpha = 0.2f),
                                border = BorderStroke(0.8.dp, ChannelDesignTokens.NeonCyan.copy(alpha = 0.6f)),
                            ) {
                                Text(
                                    text = if (scaleFactor <= 0.65f) "● LIVE" else "● PREVIEW",
                                    color = ChannelDesignTokens.NeonCyan,
                                    fontSize = (8.5f * scaleFactor).coerceAtLeast(6.5f).sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = (3.5f * scaleFactor).coerceAtLeast(2f).dp, vertical = (1f * scaleFactor).coerceAtLeast(0.5f).dp),
                                )
                            }
                        }

                        // Favourite icon
                        if (item.isFavorite) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Favourite",
                                tint = FavRed,
                                modifier = Modifier.size((14 * scaleFactor).coerceAtLeast(9.5f).dp),
                            )
                        }
                    }

                    // Current programme (Time + Title always fit on 1 line)
                    item.currentProgram?.let { prog ->
                        Spacer(Modifier.height((2.5f * scaleFactor).coerceAtLeast(1f).dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy((4 * scaleFactor).coerceAtLeast(3f).dp),
                        ) {
                            Text(
                                text = "${timeFmt.format(Date(prog.startMs))}–${timeFmt.format(Date(prog.endMs))}",
                                color = if (isFocused) ChannelDesignTokens.NeonCyan else AccentBlue,
                                fontSize = (11.5f * scaleFactor).coerceAtLeast(9f).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = prog.title,
                                color = if (isFocused) TextPrimary else TextSecondary,
                                fontSize = (12f * scaleFactor).coerceAtLeast(9.5f).sp,
                                fontWeight = if (isFocused) FontWeight.Medium else FontWeight.Normal,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // ── Live EPG progress bar (Concept 2 cyan neon glow)
            if (item.currentProgram != null && item.epgProgress > 0f) {
                LinearProgressIndicator(
                    progress = { item.epgProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ChannelDesignTokens.NeonCyan,
                    trackColor = Color(0x2B2A374E),
                )
            }

            // ── Live preview indicator glow line at bottom of focused item
            if (isFocused && hasLivePreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    ChannelDesignTokens.NeonCyan,
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }
    }
}
}

// ─── Channel logo ─────────────────────────────────────────────────────────────

@Composable
private fun ChannelLogo(
    logoUrl: String?,
    isFocused: Boolean,
    size: androidx.compose.ui.unit.Dp? = null,
) {
    val actualSize = size ?: if (isFocused) 52.dp else 44.dp
    Box(
        modifier = Modifier
            .size(actualSize)
            .clip(RoundedCornerShape(8.dp))
            .background(LogoPlaceholderBg),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = "Channel logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            )
        } else {
            // Fallback placeholder dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TextSecondary),
            )
        }
    }
}

// ─── Context menu bottom-sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextMenuSheet(
    item: ChannelDisplayItem,
    uiScalePercent: Int,
    onSelectScale: (Int) -> Unit,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1C1F2E),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            // Header
            Text(
                text = item.channel.name,
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            item.currentProgram?.let { prog ->
                Text(
                    text = prog.title,
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Actions
            TextButton(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("▶  Смотреть канал", color = TextPrimary, fontSize = 15.sp)
            }

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = {
                    onToggleFavorite()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                val icon = if (item.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
                Icon(icon, contentDescription = null, tint = FavRed)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (item.isFavorite) "Удалить из избранного" else "Добавить в избранное",
                    color = TextPrimary,
                    fontSize = 15.sp,
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x2200E5FF)),
            )
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Размер шрифта и строк ($uiScalePercent%):",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(100, 80, 60, 40).forEach { scale ->
                    val isSelected = uiScalePercent == scale
                    Button(
                        onClick = { onSelectScale(scale) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) ChannelDesignTokens.NeonCyan else Color(0xFF2C3246),
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "$scale%",
                            color = if (isSelected) Color.Black else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ─── Top Bar ──────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(
    viewMode: ScreenViewMode,
    selectedGroupName: String,
    channelCount: Int,
    folderCount: Int,
    isSettingsFocused: Boolean,
    isSearchFocused: Boolean,
    isGroupFocused: Boolean,
    isCompact: Boolean = false,
    isPortrait: Boolean = false,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBackToFoldersClick: () -> Unit,
    onGroupClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xCC090C14))
            .padding(
                horizontal = if (isCompact) 10.dp else 24.dp,
                vertical = if (isCompact) 5.dp else 12.dp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Brand Logo & Mode Pill (Concept 1 & 2)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 16.dp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 6.dp),
        ) {
            // Minoga TV Glowing Brand
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Tv,
                    contentDescription = "Minoga TV",
                    tint = ChannelDesignTokens.AccentGold,
                    modifier = Modifier.size(if (isCompact) 18.dp else 24.dp),
                )
                if (!isPortrait) {
                    Text(
                        text = "Minoga TV",
                        color = ChannelDesignTokens.AccentGold,
                        fontSize = if (isCompact) 15.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // Category/Folder Pill
            if (viewMode == ScreenViewMode.CHANNELS) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isGroupFocused) Color(0x332979FF) else Color(0x80131826))
                        .border(
                            width = if (isGroupFocused) 2.dp else 1.dp,
                            color = if (isGroupFocused) ChannelDesignTokens.NeonCyan else Color(0x3300E5FF),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable(onClick = onBackToFoldersClick)
                        .padding(
                            horizontal = if (isCompact) 10.dp else 14.dp,
                            vertical = if (isCompact) 3.dp else 6.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 5.dp else 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад к папкам",
                        tint = ChannelDesignTokens.NeonCyan,
                        modifier = Modifier.size(if (isCompact) 13.dp else 16.dp),
                    )
                    Text(
                        text = if (isPortrait) selectedGroupName else "Папки / $selectedGroupName",
                        color = if (isGroupFocused) Color.White else TextPrimary,
                        fontSize = if (isCompact) 11.5.sp else 13.sp,
                        fontWeight = if (isGroupFocused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (channelCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isGroupFocused) ChannelDesignTokens.NeonCyan else Color(0x3300E5FF))
                                .padding(horizontal = if (isCompact) 5.dp else 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = channelCount.toString(),
                                color = if (isGroupFocused) Color.Black else Color.White,
                                fontSize = if (isCompact) 10.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isGroupFocused) Color(0x332979FF) else Color(0x80131826))
                        .border(
                            width = if (isGroupFocused) 2.dp else 1.dp,
                            color = if (isGroupFocused) ChannelDesignTokens.NeonCyan else Color(0x3300E5FF),
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable(onClick = onGroupClick)
                        .padding(
                            horizontal = if (isCompact) 10.dp else 14.dp,
                            vertical = if (isCompact) 3.dp else 6.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 5.dp else 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = "Тематические папки",
                        tint = ChannelDesignTokens.NeonCyan,
                        modifier = Modifier.size(if (isCompact) 13.dp else 16.dp),
                    )
                    Text(
                        text = if (isPortrait) "Minoga TV • Папки" else "Тематические папки",
                        color = if (isGroupFocused) Color.White else TextPrimary,
                        fontSize = if (isCompact) 11.5.sp else 13.sp,
                        fontWeight = if (isGroupFocused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (folderCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isGroupFocused) ChannelDesignTokens.NeonCyan else Color(0x3300E5FF))
                                .padding(horizontal = if (isCompact) 5.dp else 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "$folderCount",
                                color = if (isGroupFocused) Color.Black else Color.White,
                                fontSize = if (isCompact) 10.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }

        // ── Actions (Search & Settings)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 10.dp),
        ) {
            // Search button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSearchFocused) Color(0x4000E5FF) else Color(0xCC131826))
                    .border(
                        width = if (isSearchFocused) 2.dp else 1.dp,
                        color = if (isSearchFocused) ChannelDesignTokens.NeonCyan else Color(0x2B00E5FF),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable(onClick = onSearchClick)
                    .padding(
                        horizontal = if (isPortrait) 8.dp else if (isCompact) 10.dp else 14.dp,
                        vertical = if (isCompact) 4.dp else 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Поиск",
                    tint = if (isSearchFocused) Color.White else ChannelDesignTokens.NeonCyan,
                    modifier = Modifier.size(if (isCompact) 15.dp else 18.dp),
                )
                if (!isPortrait) {
                    Text(
                        text = "Поиск",
                        color = if (isSearchFocused) Color.White else TextPrimary,
                        fontSize = if (isCompact) 11.5.sp else 13.sp,
                        fontWeight = if (isSearchFocused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }

            // Settings button (Right)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSettingsFocused) Color(0x402979FF) else Color(0xCC131826))
                    .border(
                        width = if (isSettingsFocused) 2.dp else 1.dp,
                        color = if (isSettingsFocused) ChannelDesignTokens.NeonCyan else Color(0x2B00E5FF),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable(onClick = onSettingsClick)
                    .padding(
                        horizontal = if (isPortrait) 8.dp else if (isCompact) 10.dp else 14.dp,
                        vertical = if (isCompact) 4.dp else 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Настройки",
                    tint = if (isSettingsFocused) Color.White else ChannelDesignTokens.NeonCyan,
                    modifier = Modifier.size(if (isCompact) 15.dp else 18.dp),
                )
                if (!isPortrait) {
                    Text(
                        text = "Настройки",
                        color = if (isSettingsFocused) Color.White else TextPrimary,
                        fontSize = if (isCompact) 11.5.sp else 13.sp,
                        fontWeight = if (isSettingsFocused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

// ─── Thematic Folders Grid (Stitch 3-Column TV Grid) ─────────────────────────

@Composable
private fun FolderGrid(
    uiState: ChannelListUiState,
    onFolderClick: (ChannelFolderItem) -> Unit,
    columnsCount: Int = 4,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    // Fast auto-scroll for folders without lagging animations
    LaunchedEffect(uiState.focusedFolderIndex) {
        if (uiState.folders.isNotEmpty()) {
            val target = uiState.focusedFolderIndex.coerceIn(0, uiState.folders.lastIndex)
            val visible = gridState.layoutInfo.visibleItemsInfo
            val firstVisible = visible.firstOrNull()?.index ?: -1
            val lastVisible = visible.lastOrNull()?.index ?: -1
            if (visible.isEmpty() || target <= firstVisible || target >= lastVisible) {
                gridState.scrollToItem(target)
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columnsCount),
        state = gridState,
        modifier = modifier
            .padding(horizontal = if (columnsCount <= 2) 10.dp else 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItemsIndexed(
            items = uiState.folders,
            key = { _, folder -> folder.title },
        ) { index, folder ->
            val isFocused = index == uiState.focusedFolderIndex && uiState.topBarFocus == TopBarFocus.NONE
            FolderGridCard(
                folder = folder,
                isFocused = isFocused,
                isUnlocked = uiState.unlockedFolderTitles.contains(folder.title),
                onClick = { onFolderClick(folder) },
            )
        }
    }
}

@Composable
private fun FolderGridCard(
    folder: ChannelFolderItem,
    isFocused: Boolean,
    isUnlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMostUsed = folder.isMostUsed
    val cornerRadius = 10.dp
    val shape = RoundedCornerShape(cornerRadius)

    // User requirement: Focused folder background is light/bright ("подкрашивай фон выделенной папки светлым")
    val cardBrush = when {
        isFocused -> Brush.verticalGradient(
            colors = listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0)), // Bright luminous silver/slate
        )
        isMostUsed -> Brush.verticalGradient(
            colors = listOf(Color(0xFF162542), Color(0xFF0D1627)),
        )
        else -> Brush.verticalGradient(
            colors = listOf(Color(0xEE111726), Color(0xEE0A0E18)),
        )
    }

    val scaleAnim by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        animationSpec = tween(40),
        label = "folderCardScale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(74.dp)
            .scale(scaleAnim)
            .clip(shape)
            .border(
                width = if (isFocused) 2.5.dp else if (isMostUsed) 1.2.dp else 0.8.dp,
                color = if (isFocused) Color(0xFF0284C7)
                        else if (isMostUsed) Color(0x6600E5FF)
                        else Color(0x2200E5FF),
                shape = shape,
            )
            .background(cardBrush, shape = shape)
            .clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
            // High-visibility left indicator bar for focused TV card
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp))
                        .background(Color(0xFF0284C7)),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (isFocused) 6.dp else 0.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top row: Category Icon + Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isFocused) Color(0xFF0284C7)
                                else if (isMostUsed) Color(0xFF1E2A44)
                                else Color(0xFF161C2C),
                            )
                            .border(
                                width = 0.8.dp,
                                color = if (isFocused) Color(0xFF0284C7)
                                        else if (isMostUsed) Color(0x6600E5FF)
                                        else Color(0x1F00E5FF),
                                shape = RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val (icon, tint) = when (folder.iconType) {
                            FolderIconType.ALL -> Icons.Filled.Tv to (if (isFocused) Color.White else ChannelDesignTokens.NeonCyan)
                            FolderIconType.FAVORITES -> Icons.Filled.Favorite to (if (isFocused) Color(0xFFFFD700) else ChannelDesignTokens.AccentGold)
                            FolderIconType.CATEGORY -> Icons.Filled.Folder to (if (isFocused) Color.White else Color(0xFF8193B2))
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(15.dp),
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isMostUsed) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isFocused) Color(0xFFCBD5E1) else Color(0x3300E5FF))
                                    .border(0.8.dp, if (isFocused) Color(0xFF0284C7) else Color(0x6600E5FF), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = if (isFocused) Color(0xFFD97706) else ChannelDesignTokens.AccentGold,
                                    modifier = Modifier.size(8.dp),
                                )
                                Text(
                                    text = "ЧАСТО",
                                    color = if (isFocused) Color(0xFF0F172A) else ChannelDesignTokens.NeonCyan,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }

                        if (folder.isLocked && !isUnlocked) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE5A00D).copy(alpha = 0.2f))
                                    .border(0.8.dp, Color(0xFFE5A00D).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "PIN",
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(10.dp),
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isFocused) Color(0xFFCBD5E1) else Color(0x40131826),
                            border = BorderStroke(0.8.dp, if (isFocused) Color(0xFF94A3B8) else Color(0x3300E5FF)),
                        ) {
                            Text(
                                text = "${folder.channelCount}",
                                color = if (isFocused) Color(0xFF0F172A) else TextSecondary,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                // Bottom row: Title & count with crisp high contrast text against light background
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = folder.title,
                        color = if (isFocused) Color(0xFF0F172A) else TextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = if (isMostUsed || isFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${folder.channelCount} кан.",
                        color = if (isFocused) Color(0xFF0284C7) else TextSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ─── Stitch 16:9 Live Mini-Player Card ────────────────────────────────────────

@Composable
private fun StitchLivePreviewCard(
    uiState: ChannelListUiState,
    previewPlayer: ExoPlayer,
    onPlayLive: (Long) -> Unit,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentItem = uiState.channels.getOrNull(uiState.focusedIndex)
    val isPreviewPrefEnabled = remember {
        try {
            val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("channel_preview_enabled", true)
        } catch (_: Exception) { true }
    }

    val isVideoPlaying = isPreviewPrefEnabled &&
        uiState.livePreviewChannelId != null &&
        currentItem?.channel?.id == uiState.livePreviewChannelId &&
        currentItem.channel.streamUrl.isNotBlank()

    val cardRadius = if (isCompact) 10.dp else 16.dp
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(cardRadius))
            .border(
                width = if (isCompact) 1.dp else 1.5.dp,
                color = if (isVideoPlaying) ChannelDesignTokens.NeonCyan.copy(alpha = 0.5f) else Color(0x3300E5FF),
                shape = RoundedCornerShape(cardRadius),
            )
            .clickable {
                currentItem?.let { onPlayLive(it.channel.id) }
            },
        color = Color(0xFF0B101D),
        shape = RoundedCornerShape(cardRadius),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isVideoPlaying) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            keepScreenOn = true
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            player = previewPlayer
                        }
                    },
                    update = { pv ->
                        pv.keepScreenOn = true
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Scrim overlay top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompact) 26.dp else 48.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xCC070A12), Color.Transparent),
                            ),
                        ),
                )

                // Scrim overlay bottom
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isCompact) 36.dp else 60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xF2070A12)),
                            ),
                        ),
                )
            } else {
                // Static cinematic preview card
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF142038), Color(0xFF090E1A)),
                            ),
                        ),
                )
            }

            // Top bar badges: LIVE badge + Format chip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = if (isCompact) 8.dp else 12.dp,
                        vertical = if (isCompact) 4.dp else 10.dp,
                    )
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(if (isCompact) 4.dp else 6.dp))
                        .background(if (isVideoPlaying) Color(0x33EF4444) else Color(0x3300E5FF))
                        .border(1.dp, if (isVideoPlaying) Color(0x80EF4444) else Color(0x4D00E5FF), RoundedCornerShape(if (isCompact) 4.dp else 6.dp))
                        .padding(
                            horizontal = if (isCompact) 5.dp else 8.dp,
                            vertical = if (isCompact) 1.dp else 3.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 5.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (isVideoPlaying) Color(0xFFEF4444) else ChannelDesignTokens.NeonCyan),
                    )
                    Text(
                        text = if (isVideoPlaying) "● ПРЯМОЙ ЭФИР" else "ЖИВОЕ ПРЕВЬЮ",
                        color = Color.White,
                        fontSize = if (isCompact) 8.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(if (isCompact) 4.dp else 6.dp),
                    color = Color(0x66070A12),
                    border = BorderStroke(1.dp, Color(0x3300E5FF)),
                ) {
                    Text(
                        text = "1080p FHD",
                        color = ChannelDesignTokens.NeonCyan,
                        fontSize = if (isCompact) 8.sp else 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(
                            horizontal = if (isCompact) 5.dp else 7.dp,
                            vertical = if (isCompact) 1.dp else 2.dp,
                        ),
                    )
                }
            }

            // Bottom info banner
            currentItem?.let { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isCompact) 8.dp else 14.dp,
                            vertical = if (isCompact) 3.dp else 8.dp,
                        )
                        .align(Alignment.BottomCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isCompact) 6.dp else 10.dp),
                ) {
                    ChannelLogo(
                        logoUrl = item.channel.logoUrl,
                        isFocused = true,
                        size = if (isCompact) 22.dp else 32.dp,
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.channel.name,
                            color = Color.White,
                            fontSize = if (isCompact) 11.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.currentProgram?.let { prog ->
                            Text(
                                text = prog.title,
                                color = ChannelDesignTokens.NeonCyan,
                                fontSize = if (isCompact) 9.sp else 11.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Empty Channels State View ────────────────────────────────────────────────

@Composable
private fun EmptyChannelsView(
    isSettingsFocused: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF111318))
                .border(
                    width = if (isSettingsFocused) 2.dp else 1.dp,
                    color = if (isSettingsFocused) ItemBorderFocused else Color(0xFF1C1F2E),
                    shape = RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Tv,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(56.dp),
            )

            Text(
                text = "Каналы не найдены",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Для начала работы добавьте плейлист\n(M3U, Xtream или Stalker) в Настройках",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSettingsFocused) AccentBlue else Color(0xFF1C1F2E),
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .border(
                        width = if (isSettingsFocused) 2.dp else 1.dp,
                        color = if (isSettingsFocused) Color.White else Color(0x444F8EF7),
                        shape = RoundedCornerShape(12.dp),
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Открыть настройки",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

