package com.minogatv.box.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.database.dao.EpgDao
import com.minogatv.box.core.database.dao.FavoriteDao
import com.minogatv.box.core.database.entity.ChannelEntity
import com.minogatv.box.core.database.entity.EpgProgramEntity
import com.minogatv.box.core.database.entity.FavoriteChannelEntity
import com.minogatv.box.core.model.Channel
import com.minogatv.box.core.model.ChannelUtils
import com.minogatv.box.core.model.EpgProgram
import com.minogatv.box.core.model.enums.CatchupType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import com.minogatv.box.core.database.dao.PlaylistDao
import com.minogatv.box.core.data.repository.ChannelRepository
import com.minogatv.box.core.data.repository.EpgRepository
import com.minogatv.box.core.data.sync.SyncScheduler
import com.minogatv.box.core.model.Playlist
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * ViewModel for the Channel List screen.
 *
 * Responsibilities:
 * 1. Aggregate channels + live EPG + favourite state into [ChannelListUiState].
 * 2. Process [ChannelListIntent]s dispatched by the UI and the [KeyEventHandler].
 * 3. Manage the 1.5-second live-preview hover timer.
 * 4. Load the EPG side panel data when focus changes.
 *
 * @param profileId   ID of the active user profile (injected or passed via SavedStateHandle).
 * @param channelDao  Room DAO for channel queries.
 * @param epgDao      Room DAO for EPG queries.
 * @param favoriteDao Room DAO for favourite queries.
 */
@HiltViewModel
class ChannelListViewModel @Inject constructor(
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val favoriteDao: FavoriteDao,
    private val playlistDao: PlaylistDao,
    private val syncScheduler: SyncScheduler,
    private val channelRepository: ChannelRepository,
    private val epgRepository: EpgRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    // ─── Mutable state ────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow(ChannelListUiState(isLoading = false))
    val uiState: StateFlow<ChannelListUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<ChannelListNavEvent>(extraBufferCapacity = 1)
    val navigationEvent: SharedFlow<ChannelListNavEvent> = _navigationEvent.asSharedFlow()

    /** Currently active profile (set from SavedStateHandle in production). */
    private val activeProfileId = MutableStateFlow(DEFAULT_PROFILE_ID)

    /** Currently selected group/folder title (empty = "All Channels"). */
    private val selectedGroup = MutableStateFlow("")

    /** EPG "now" timestamp updated every minute by a ticker. */
    private val nowMs = MutableStateFlow(System.currentTimeMillis())

    /** Live-preview hover timer job. Replaced on every focus change. */
    private var previewTimerJob: Job? = null

    /** Job for loading side panel EPG items. */
    private var sidePanelJob: Job? = null

    /** Job for fetching missing EPG from the internet. */
    private var sidePanelFetchJob: Job? = null

    /** Job for loading folder channels immediately. */
    private var selectFolderJob: Job? = null

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        startNowTicker()
        observeFolders()
        observeChannels()
        observeEpgSyncProgress()
        checkAndAutoLoadEpg()
        resolveMissingLogosAndEpg()
        restoreLastFolder()
    }

    private fun resolveMissingLogosAndEpg() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(15000L) // Wait for UI and initial channels list to render smoothly
            try {
                // Auto-resolve picons/logos for channels without logo in background batches
                channelRepository.resolveMissingLogos()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Instantly restores the last selected folder and cached channels from Room on app start.
     * Channels appear immediately within 50 ms without waiting for network or EPG.
     */
    private fun restoreLastFolder() {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            val lastFolder = prefs.getString("last_selected_folder", null) ?: "Все каналы"
            val profileId = activeProfileId.value
            val favIds = favoriteDao.getFavoriteIds(profileId).toSet()

            // Query channels from Room for the target folder immediately
            val rawChannels = when (lastFolder) {
                "Все каналы" -> channelDao.getAllByProfile(profileId)
                "Избранное" -> channelDao.getAllByProfile(profileId).filter { it.id in favIds }
                else -> {
                    val byGroup = channelDao.getByGroup(profileId, lastFolder)
                    if (byGroup.isNotEmpty()) byGroup else channelDao.getAllByProfile(profileId)
                }
            }

            val effectiveFolder = if (lastFolder != "Все каналы" && rawChannels.isEmpty()) {
                "Все каналы"
            } else {
                lastFolder
            }

            val targetChannels = if (effectiveFolder == "Все каналы" && rawChannels.isEmpty()) {
                channelDao.getAllByProfile(profileId)
            } else {
                rawChannels
            }

            if (targetChannels.isNotEmpty()) {
                val forcedCatchup = getForcedCatchupType()
                val initialItems = targetChannels.map { entity ->
                    val dom = entity.toDomain()
                    ChannelDisplayItem(
                        channel = dom,
                        currentProgram = null,
                        nextProgram = null,
                        epgProgress = 0f,
                        hasCatchup = dom.hasArchive(forcedCatchup),
                        isFavorite = entity.id in favIds,
                    )
                }

                withContext(Dispatchers.Main) {
                    selectedGroup.value = effectiveFolder
                    _uiState.update { state ->
                        state.copy(
                            viewMode = ScreenViewMode.CHANNELS,
                            selectedGroupName = effectiveFolder,
                            channels = initialItems,
                            focusedIndex = if (initialItems.isNotEmpty()) 0 else -1,
                            isLoading = false,
                        )
                    }
                    if (initialItems.isNotEmpty()) {
                        startLivePreviewTimer()
                    }
                }

                // Phase 2: Enrich with EPG in background
                val enrichedItems = buildDisplayItems(targetChannels, favIds, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    if (selectedGroup.value == effectiveFolder) {
                        _uiState.update { state ->
                            state.copy(channels = enrichedItems)
                        }
                    }
                }
            }
        }
    }


    private fun observeEpgSyncProgress() {
        viewModelScope.launch {
            var hadProgress = false
            syncScheduler.observeEpgSyncProgressInfo().collect { info ->
                _uiState.update {
                    it.copy(
                        epgSyncProgress = info?.progress,
                        epgSyncStatus = info?.status,
                    )
                }
                if (info != null) {
                    hadProgress = true
                } else if (hadProgress) {
                    // Sync finished: refresh current programs on channels immediately
                    hadProgress = false
                    nowMs.value = System.currentTimeMillis()
                }
            }
        }
    }

    private fun checkAndAutoLoadEpg() {
        viewModelScope.launch(Dispatchers.IO) {
            delay(3_000L) // Brief pause to avoid startup I/O contention with UI rendering
            val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            val isAutoEpgEnabled = prefs.getBoolean("auto_epg_sync", true)
            if (!isAutoEpgEnabled) return@launch

            // Check if there are current valid programmes in DB
            val validCount = epgDao.getValidProgramsCount(System.currentTimeMillis())
            if (validCount == 0) {
                val playlists = playlistDao.getAll()
                val customEpg = prefs.getString("custom_epg_url", null)?.trim()?.takeIf { it.isNotBlank() }
                val defaultEpg = customEpg?.let { Playlist.normalizeUrl(it) } ?: Playlist.DEFAULT_EPG_URL
                if (playlists.isNotEmpty()) {
                    for (p in playlists) {
                        val playlistEpg = p.epgUrl?.takeIf { it.isNotBlank() }
                        val rawUrl = customEpg ?: playlistEpg ?: defaultEpg
                        val effectiveUrl = Playlist.normalizeUrl(rawUrl)
                        syncScheduler.triggerManualEpgSync(p.id, effectiveUrl)
                    }
                } else {
                    syncScheduler.triggerManualEpgSync(1L, defaultEpg)
                }
            }
        }
    }

    // ─── Intent handler ───────────────────────────────────────────────────────

    fun onIntent(intent: ChannelListIntent) {
        when (intent) {
            is ChannelListIntent.MoveFocus          -> handleMoveFocus(intent.delta)
            is ChannelListIntent.MoveFolderFocus    -> moveFolderFocus(intent.delta)
            is ChannelListIntent.SelectFolder       -> selectFolder(intent.folder)
            is ChannelListIntent.ReturnToFolders    -> returnToFolders()
            is ChannelListIntent.MoveSidePanelFocus -> handleMoveSidePanelFocus(intent.delta)
            is ChannelListIntent.OpenEpgSidePanel   -> openSidePanel()
            is ChannelListIntent.CloseEpgSidePanel  -> closeSidePanel()
            is ChannelListIntent.PlayChannel        -> handlePlay(intent.channelId)
            is ChannelListIntent.OpenContextMenu    -> openContextMenu(intent.channelId)
            is ChannelListIntent.DismissContextMenu -> _uiState.update { it.copy(isContextMenuOpen = false) }
            is ChannelListIntent.StartLivePreview   -> {
                if (isLivePreviewEnabled()) {
                    _uiState.update { it.copy(livePreviewChannelId = intent.channelId) }
                } else {
                    _uiState.update { it.copy(livePreviewChannelId = null) }
                }
            }
            is ChannelListIntent.StopLivePreview    -> _uiState.update { it.copy(livePreviewChannelId = null) }
            is ChannelListIntent.ToggleFavorite     -> toggleFavorite(intent.channelId)
            is ChannelListIntent.TogglePlayback     -> { /* delegated to the Player feature */ }
            is ChannelListIntent.PlayCatchup        -> handlePlayCatchup(intent.channelId, intent.programStartMs)
            is ChannelListIntent.DismissError       -> _uiState.update { it.copy(errorMessage = null) }
            is ChannelListIntent.FocusTopBar        -> _uiState.update { it.copy(topBarFocus = intent.target) }
            is ChannelListIntent.MoveTopBarFocus    -> _uiState.update { it.copy(topBarFocus = intent.target) }
            is ChannelListIntent.ExitTopBarFocus    -> {
                _uiState.update {
                    it.copy(
                        topBarFocus = TopBarFocus.NONE,
                        focusedIndex = if (it.channels.isNotEmpty()) 0 else -1,
                    )
                }
                if (_uiState.value.channels.isNotEmpty()) {
                    startLivePreviewTimer()
                }
            }
            is ChannelListIntent.OpenSearch         -> _uiState.update { it.copy(isSearchOpen = true) }
            is ChannelListIntent.CloseSearch        -> _uiState.update { it.copy(isSearchOpen = false) }
            is ChannelListIntent.DismissPinDialog   -> _uiState.update { it.copy(pendingPinFolder = null) }
            is ChannelListIntent.UnlockFolder       -> unlockFolder(intent.folderTitle)
            is ChannelListIntent.OpenProgramDetails -> _uiState.update { it.copy(isProgramDetailsOpen = true) }
            is ChannelListIntent.CloseProgramDetails -> _uiState.update { it.copy(isProgramDetailsOpen = false) }
            is ChannelListIntent.ShowExitConfirm    -> _uiState.update { it.copy(isExitConfirmOpen = true) }
            is ChannelListIntent.DismissExitConfirm -> _uiState.update { it.copy(isExitConfirmOpen = false) }
        }
    }

    // ─── Active profile / group selection ────────────────────────────────────

    fun selectProfile(profileId: Long) {
        activeProfileId.value = profileId
    }

    fun selectGroup(groupTitle: String) {
        selectedGroup.value = groupTitle
        _uiState.update { it.copy(focusedIndex = 0) }
    }

    // ─── Private: folder observation ─────────────────────────────────────────

    private fun observeFolders() {
        viewModelScope.launch {
            combine(
                activeProfileId,
                channelDao.observeGroupsWithCount(DEFAULT_PROFILE_ID),
                channelDao.observeTotalCountByProfile(DEFAULT_PROFILE_ID),
                favoriteDao.observeFavorites(DEFAULT_PROFILE_ID),
            ) { profileId, groups, totalCount, favorites ->
                val list = mutableListOf<ChannelFolderItem>()

                if (totalCount > 0) {
                    list.add(
                        ChannelFolderItem(
                            title = "Все каналы",
                            channelCount = totalCount,
                            iconType = FolderIconType.ALL,
                        ),
                    )
                }

                if (favorites.isNotEmpty()) {
                    list.add(
                        ChannelFolderItem(
                            title = "Избранное",
                            channelCount = favorites.size,
                            iconType = FolderIconType.FAVORITES,
                        ),
                    )
                }

                groups.forEach { g ->
                    if (g.group_title.isNotBlank()) {
                        val isLocked = isFolderLockedByDefault(g.group_title)
                        list.add(
                            ChannelFolderItem(
                                title = g.group_title,
                                channelCount = g.channel_count,
                                iconType = FolderIconType.CATEGORY,
                                isLocked = isLocked,
                            ),
                        )
                    }
                }
                val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
                val mostUsedTitle = list.maxByOrNull { prefs.getInt("folder_usage_${it.title}", 0) }?.title
                    ?: list.firstOrNull()?.title

                list.map { folder ->
                    folder.copy(isMostUsed = (folder.title == mostUsedTitle))
                }
            }
                .catch { /* ignore */ }
                .collect { folders ->
                    _uiState.update { state ->
                        state.copy(
                            folders = folders,
                            isLoading = false,
                            focusedFolderIndex = state.focusedFolderIndex.coerceIn(0, (folders.size - 1).coerceAtLeast(0)),
                        )
                    }
                }
        }
    }

    // ─── Private: channel observation ────────────────────────────────────────

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeChannels() {
        viewModelScope.launch {
            combine(
                activeProfileId,
                selectedGroup,
            ) { profileId, group -> Pair(profileId, group) }
                .distinctUntilChanged()
                .flatMapLatest { (profileId, group) ->
                    if (group.isEmpty()) {
                        flowOf(emptyList<ChannelEntity>() to emptySet<Long>())
                    } else {
                        val channelsFlow = when {
                            group == "Все каналы" -> channelDao.observeAllByProfile(profileId)
                            group == "Избранное" -> channelDao.observeAllByProfile(profileId)
                            else -> channelDao.observeByGroup(profileId, group)
                        }
                        val favoritesFlow = favoriteDao.observeFavorites(profileId)

                        combine(channelsFlow, favoritesFlow) { channels, favorites ->
                            val favSet = favorites.map { it.channelId }.toSet()
                            val filteredChannels = if (group == "Избранное") {
                                channels.filter { it.id in favSet }
                            } else {
                                channels
                            }
                            Pair(filteredChannels, favSet)
                        }
                    }
                }
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
                .collect { (filteredChannels, favSet) ->
                    if (selectedGroup.value.isNotEmpty() && filteredChannels.isNotEmpty()) {
                        val forcedCatchup = getForcedCatchupType()
                        // If current UI list is empty or channel IDs changed, emit channels immediately
                        if (_uiState.value.channels.isEmpty() || _uiState.value.channels.size != filteredChannels.size) {
                            val initialItems = filteredChannels.map { entity ->
                                val dom = entity.toDomain()
                                ChannelDisplayItem(
                                    channel = dom,
                                    currentProgram = null,
                                    nextProgram = null,
                                    epgProgress = 0f,
                                    hasCatchup = dom.hasArchive(forcedCatchup),
                                    isFavorite = entity.id in favSet,
                                )
                            }
                            _uiState.update { state ->
                                val newFocused = if (state.focusedIndex in initialItems.indices) state.focusedIndex else 0
                                state.copy(
                                    channels = initialItems,
                                    isLoading = false,
                                    focusedIndex = newFocused,
                                )
                            }
                        }

                        // Enrich with EPG
                        val enriched = withContext(Dispatchers.Default) {
                            runCatching {
                                buildDisplayItems(filteredChannels, favSet, System.currentTimeMillis())
                            }.getOrNull()
                        }
                        if (enriched != null) {
                            _uiState.update { state ->
                                state.copy(
                                    channels = enriched,
                                    isLoading = false,
                                )
                            }
                        }
                    }
                }
        }
    }

    private val adultKeywords = listOf("18+", "adult", "xxx", "эротика", "взрослые", "порно", "erotic", "ххх")

    private fun isFolderLockedByDefault(title: String): Boolean {
        val lower = title.lowercase()
        return adultKeywords.any { lower.contains(it) }
    }

    private fun selectFolder(folder: ChannelFolderItem) {
        if (folder.isLocked && !_uiState.value.unlockedFolderTitles.contains(folder.title)) {
            _uiState.update { it.copy(pendingPinFolder = folder) }
            return
        }

        // Cancel previous timer, folder and epg loading jobs
        previewTimerJob?.cancel()
        sidePanelJob?.cancel()
        sidePanelFetchJob?.cancel()
        selectFolderJob?.cancel()

        // Record folder usage for Stitch dynamic card sizing
        val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        val currentUsage = prefs.getInt("folder_usage_${folder.title}", 0)
        prefs.edit()
            .putInt("folder_usage_${folder.title}", currentUsage + 1)
            .putString("last_selected_folder", folder.title)
            .apply()

        val updatedFolders = _uiState.value.folders.let { currentList ->
            val newMostUsed = currentList.maxByOrNull { prefs.getInt("folder_usage_${it.title}", 0) }?.title
            currentList.map { it.copy(isMostUsed = (it.title == newMostUsed)) }
        }

        // 1. Immediate UI state transition into channels view:
        // Clear sidePanelItems and channels to avoid showing stale data from previous folder
        _uiState.update {
            it.copy(
                viewMode = ScreenViewMode.CHANNELS,
                selectedGroupName = folder.title,
                folders = updatedFolders,
                sidePanelItems = emptyList(),
                focusedIndex = 0,
                isSidePanelOpen = true,
                isFocusInSidePanel = false,
                isProgramDetailsOpen = false,
                topBarFocus = TopBarFocus.NONE,
                livePreviewChannelId = null,
                isLoading = it.channels.isEmpty(),
            )
        }

        selectedGroup.value = folder.title

        selectFolderJob = viewModelScope.launch(Dispatchers.IO) {
            val profileId = activeProfileId.value
            val favIds = favoriteDao.getFavoriteIds(profileId).toSet()
            val rawChannels = when (folder.title) {
                "Все каналы" -> channelDao.getAllByProfile(profileId)
                "Избранное" -> channelDao.getAllByProfile(profileId).filter { it.id in favIds }
                else -> channelDao.getByGroup(profileId, folder.title)
            }

            val forcedCatchup = getForcedCatchupType()

            // ── PHASE 1: CHANNELS APPEAR FIRST IMMEDIATELY ──────────────────
            val initialItems = rawChannels.map { entity ->
                val dom = entity.toDomain()
                ChannelDisplayItem(
                    channel = dom,
                    currentProgram = null,
                    nextProgram = null,
                    epgProgress = 0f,
                    hasCatchup = dom.hasArchive(forcedCatchup),
                    isFavorite = entity.id in favIds,
                )
            }

            withContext(Dispatchers.Main) {
                if (selectedGroup.value == folder.title) {
                    _uiState.update { state ->
                        state.copy(
                            channels = initialItems,
                            isLoading = false,
                            focusedIndex = if (initialItems.isNotEmpty()) 0 else -1,
                        )
                    }
                    if (initialItems.isNotEmpty()) {
                        startLivePreviewTimer()
                    }
                }
            }

            // ── PHASE 2: TELEPROGRAM / CURRENT EPG ENRICHED IN BACKGROUND ───
            if (rawChannels.isNotEmpty()) {
                val enrichedItems = buildDisplayItems(rawChannels, favIds, System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    if (selectedGroup.value == folder.title) {
                        _uiState.update { state ->
                            state.copy(channels = enrichedItems)
                        }
                    }
                }
            }
        }
    }

    private fun unlockFolder(folderTitle: String) {
        val updatedUnlocked = _uiState.value.unlockedFolderTitles + folderTitle
        _uiState.update {
            it.copy(
                unlockedFolderTitles = updatedUnlocked,
                pendingPinFolder = null,
            )
        }
        val folder = _uiState.value.folders.find { it.title == folderTitle }
        if (folder != null) {
            selectFolder(folder)
        }
    }

    private fun returnToFolders() {
        selectFolderJob?.cancel()
        previewTimerJob?.cancel()
        sidePanelJob?.cancel()
        sidePanelFetchJob?.cancel()
        selectedGroup.value = ""
        _uiState.update {
            it.copy(
                viewMode = ScreenViewMode.FOLDERS,
                sidePanelItems = emptyList(),
                isSidePanelOpen = false,
                isFocusInSidePanel = false,
                isProgramDetailsOpen = false,
                topBarFocus = TopBarFocus.NONE,
                livePreviewChannelId = null,
            )
        }
    }


    private fun moveFolderFocus(delta: Int) {
        val count = _uiState.value.folders.size
        if (count == 0) return
        val next = (_uiState.value.focusedFolderIndex + delta).coerceIn(0, count - 1)
        _uiState.update { it.copy(focusedFolderIndex = next, topBarFocus = TopBarFocus.NONE) }
    }

    private suspend fun buildDisplayItems(
        channels: List<ChannelEntity>,
        favoriteIds: Set<Long>,
        now: Long,
    ): List<ChannelDisplayItem> = withContext(Dispatchers.IO) {
        if (channels.isEmpty()) return@withContext emptyList()

        // Build candidate EPG ID list using ONLY the actual epgChannelId field.
        // Previously 6 variants per channel (id, id.lower, name, name.lower, norm, norm.lower)
        // caused candidateIds.size > 250 even for small playlists, forcing the slow
        // getAllCurrentPrograms() path. Now we stay within the indexed query threshold.
        val candidateIds: List<String> = buildList {
            for (ch in channels) {
                val rawId = ch.epgChannelId.trim()
                if (rawId.isNotEmpty()) {
                    add(rawId)
                    val lower = rawId.lowercase(java.util.Locale.ROOT)
                    if (lower != rawId) add(lower)
                }
            }
        }.distinct()

        // Ultra-fast indexed query in chunks of 400.
        // Avoids table-scan getAllCurrentPrograms() which freezes on 400,000+ entries.
        val activePrograms = runCatching {
            if (candidateIds.isNotEmpty()) {
                candidateIds.chunked(400).flatMap { chunk ->
                    epgDao.getCurrentProgramsForIds(chunk, now)
                }
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        val activeByEpgId = HashMap<String, EpgProgramEntity>(activePrograms.size * 2)
        val activeByName = HashMap<String, EpgProgramEntity>(activePrograms.size * 2)
        for (prog in activePrograms) {
            if (prog.startMs <= now && prog.endMs > now) {
                activeByEpgId[prog.channelEpgId] = prog
                val lower = prog.channelEpgId.lowercase(java.util.Locale.ROOT)
                activeByName[lower] = prog
            }
        }

        val forcedCatchup = getForcedCatchupType()
        channels.map { entity ->
            val normName = entity.name.trim().lowercase(java.util.Locale.ROOT)
            val simplified = ChannelUtils.normalizeChannelName(entity.name).lowercase(java.util.Locale.ROOT)

            val current = (if (entity.epgChannelId.isNotBlank()) {
                activeByEpgId[entity.epgChannelId] ?: activeByName[entity.epgChannelId.lowercase(java.util.Locale.ROOT)]
            } else null)
                ?: activeByName[normName]
                ?: activeByName[simplified]

            val channelDomain = entity.toDomain()
            ChannelDisplayItem(
                channel = channelDomain,
                currentProgram = current?.toDomain(),
                nextProgram = null,
                epgProgress = current?.let { prog ->
                    if (prog.endMs > prog.startMs) {
                        ((now - prog.startMs).toFloat() / (prog.endMs - prog.startMs).toFloat())
                            .coerceIn(0f, 1f)
                    } else 0f
                } ?: 0f,
                hasCatchup = channelDomain.hasArchive(forcedCatchup),
                isFavorite = entity.id in favoriteIds,
            )
        }
    }

    // ─── Private: focus movement ──────────────────────────────────────────────

    private fun handleMoveFocus(delta: Int) {
        // If there are no channels, focus belongs in the top bar
        if (_uiState.value.channels.isEmpty()) {
            _uiState.update { it.copy(topBarFocus = TopBarFocus.SETTINGS) }
            return
        }

        // Moving UP from the first channel navigates to the Top Bar Settings icon
        if (delta < 0 && _uiState.value.focusedIndex == 0) {
            _uiState.update { it.copy(topBarFocus = TopBarFocus.SETTINGS) }
            return
        }

        // Cancel any in-flight live preview & side panel fetch
        previewTimerJob?.cancel()
        sidePanelJob?.cancel()
        sidePanelFetchJob?.cancel()
        _uiState.update {
            it.copy(
                livePreviewChannelId = null,
                topBarFocus = TopBarFocus.NONE,
                isSidePanelEpgLoading = true,
            )
        }

        _uiState.update { state ->
            val newIndex = (state.focusedIndex + delta)
                .coerceIn(0, (state.channels.size - 1).coerceAtLeast(0))
            state.copy(focusedIndex = newIndex)
        }

        // Start live preview first, followed by EPG schedule request
        startLivePreviewTimer()
    }

    private fun handleMoveSidePanelFocus(delta: Int) {
        _uiState.update { state ->
            val newIndex = (state.sidePanelFocusedIndex + delta)
                .coerceIn(0, (state.sidePanelItems.size - 1).coerceAtLeast(0))
            state.copy(sidePanelFocusedIndex = newIndex)
        }
    }

    // ─── Private: side panel ─────────────────────────────────────────────────

    private fun openSidePanel() {
        val currentItems = _uiState.value.sidePanelItems
        val liveIndex = currentItems.indexOfFirst { it is SidePanelItem.Programme && it.isLive }
        val targetIndex = when {
            _uiState.value.sidePanelFocusedIndex in currentItems.indices -> _uiState.value.sidePanelFocusedIndex
            liveIndex >= 0 -> liveIndex
            else -> 0
        }
        _uiState.update {
            it.copy(
                isSidePanelOpen = true,
                isFocusInSidePanel = true,
                sidePanelFocusedIndex = targetIndex,
            )
        }
        if (currentItems.isEmpty()) {
            viewModelScope.launch { loadSidePanelForFocused() }
        }
    }

    private fun closeSidePanel() {
        _uiState.update {
            it.copy(
                isSidePanelOpen = true,
                isFocusInSidePanel = false,
                isProgramDetailsOpen = false,
            )
        }
    }

    private suspend fun loadSidePanelForFocused() {
        val focused = _uiState.value.channels.getOrNull(_uiState.value.focusedIndex) ?: return

        val dbEpgId = if (focused.channel.epgChannelId.isBlank()) {
            withContext(Dispatchers.IO) {
                runCatching { channelDao.getById(focused.channel.id)?.epgChannelId }.getOrNull()
            }
        } else null

        val candidateIds = buildSet {
            val rawId = (dbEpgId?.takeIf { it.isNotBlank() } ?: focused.channel.epgChannelId).trim()
            if (rawId.isNotEmpty()) {
                add(rawId)
                add(rawId.lowercase(java.util.Locale.ROOT))
            }
            val curProgId = focused.currentProgram?.channelEpgId?.trim()
            if (!curProgId.isNullOrEmpty()) {
                add(curProgId)
                add(curProgId.lowercase(java.util.Locale.ROOT))
            }
            val rawName = focused.channel.name.trim()
            if (rawName.isNotEmpty()) {
                add(rawName)
                add(rawName.lowercase(java.util.Locale.ROOT))
                val normalized = ChannelUtils.normalizeChannelName(rawName)
                if (normalized.isNotEmpty()) {
                    add(normalized)
                    add(normalized.lowercase(java.util.Locale.ROOT))
                }
            }
        }.toList()

        if (candidateIds.isEmpty()) {
            _uiState.update { it.copy(sidePanelItems = emptyList(), isSidePanelEpgLoading = false) }
            return
        }

        val now = nowMs.value
        // Range: previous 7 days (full week of archive) to next 48 hours
        val pastMs = 7L * 24L * 3600_000L
        val windowStart = now - pastMs
        val windowEnd = now + 48L * 3600_000L

        val entities = withContext(Dispatchers.IO) {
            epgDao.getProgramsForChannelIds(candidateIds, windowStart, windowEnd)
        }.distinctBy { it.startMs }.sortedBy { it.startMs }

        val forcedCatchup = getForcedCatchupType()
        val isCatchupSupported = focused.channel.hasArchive(forcedCatchup)
        val archiveDays = if (focused.channel.catchupDays > 0) focused.channel.catchupDays else 7
        val archiveWindowStart = now - (archiveDays.toLong() * 24L * 3600_000L)

        val items = entities.map { entity ->
            val prog = entity.toDomain()
            val isLive = now in prog.startMs..prog.endMs
            val isPast = prog.endMs <= now
            val withinArchiveWindow = prog.startMs >= archiveWindowStart

            if (isPast && isCatchupSupported && withinArchiveWindow) {
                SidePanelItem.CatchupSlot(
                    program = prog,
                    isAvailable = true,
                )
            } else {
                val progress = if (isLive && prog.endMs > prog.startMs) {
                    ((now - prog.startMs).toFloat() / (prog.endMs - prog.startMs).toFloat()).coerceIn(0f, 1f)
                } else 0f

                SidePanelItem.Programme(
                    program = prog,
                    isLive = isLive,
                    progress = progress,
                )
            }
        }

        if (items.isEmpty()) {
            sidePanelFetchJob?.cancel()
            _uiState.update {
                it.copy(
                    sidePanelItems = emptyList(),
                    sidePanelFocusedIndex = 0,
                    isSidePanelEpgLoading = false,
                )
            }
        } else {
            sidePanelFetchJob?.cancel()
            val liveIndex = items.indexOfFirst { it is SidePanelItem.Programme && it.isLive }
            val focusIndex = if (liveIndex >= 0) liveIndex else 0

            _uiState.update {
                it.copy(
                    sidePanelItems = items,
                    sidePanelFocusedIndex = focusIndex,
                    isSidePanelEpgLoading = false,
                )
            }
        }
    }

    private fun getForcedCatchupType(): CatchupType {
        return try {
            val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            val name = prefs.getString("forced_catchup_type", "AUTO") ?: "AUTO"
            CatchupType.valueOf(name)
        } catch (_: Exception) {
            CatchupType.AUTO
        }
    }

    // ─── Private: live preview ────────────────────────────────────────────────

    private fun isLivePreviewEnabled(): Boolean {
        return try {
            val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            prefs.getBoolean("channel_preview_enabled", true)
        } catch (_: Exception) {
            true
        }
    }

    private fun startLivePreviewTimer() {
        previewTimerJob?.cancel()
        sidePanelJob?.cancel()

        val channelId = _uiState.value.channels
            .getOrNull(_uiState.value.focusedIndex)?.channel?.id ?: return

        if (!isLivePreviewEnabled()) {
            if (_uiState.value.livePreviewChannelId != null) {
                _uiState.update { it.copy(livePreviewChannelId = null) }
            }
            sidePanelJob = viewModelScope.launch {
                delay(PREVIEW_FOCUS_DEBOUNCE_MS)
                loadSidePanelForFocused()
            }
            return
        }

        previewTimerJob = viewModelScope.launch {
            // Быстрый дебаунс для пульта TV (60мс), чтобы не нагружать декодер при пролистывании
            delay(PREVIEW_FOCUS_DEBOUNCE_MS)
            // 1. СНАЧАЛА активируем и показываем видеопревью канала
            onIntent(ChannelListIntent.StartLivePreview(channelId))

            // 2. ЗАТЕМ, когда плеер уже запущен, запрашиваем список телепрограммы
            delay(EPG_REQUEST_DELAY_AFTER_PREVIEW_MS)
            loadSidePanelForFocused()
        }
    }

    // ─── Private: favourites ──────────────────────────────────────────────────

    private fun toggleFavorite(channelId: Long) {
        viewModelScope.launch {
            val profileId = activeProfileId.value
            val isFav = favoriteDao.isFavorite(profileId, channelId)
            if (isFav) {
                favoriteDao.removeFavorite(profileId, channelId)
                channelDao.setFavoriteFlag(channelId, false)
            } else {
                val currentCount = favoriteDao.getFavoriteCount(profileId)
                favoriteDao.addFavorite(
                    FavoriteChannelEntity(
                        profileId = profileId,
                        channelId = channelId,
                        sortOrder = currentCount,
                    ),
                )
                channelDao.setFavoriteFlag(channelId, true)
            }
        }
    }

    // ─── Private: playback ────────────────────────────────────────────────────

    private fun handlePlay(channelId: Long) {
        _navigationEvent.tryEmit(ChannelListNavEvent.NavigateToPlayer(channelId, null))
    }

    private fun handlePlayCatchup(channelId: Long, programStartMs: Long) {
        val forcedCatchup = getForcedCatchupType()
        val ch = _uiState.value.channels.firstOrNull { it.channel.id == channelId }
        val canPlay = ch?.channel?.hasArchive(forcedCatchup) ?: (forcedCatchup != CatchupType.NONE)
        if (canPlay) {
            _navigationEvent.tryEmit(ChannelListNavEvent.NavigateToPlayer(channelId, programStartMs))
        }
    }

    private fun openContextMenu(channelId: Long) {
        _uiState.update { it.copy(isContextMenuOpen = true) }
    }

    // ─── Private: now ticker ─────────────────────────────────────────────────

    private fun startNowTicker() {
        viewModelScope.launch {
            while (true) {
                delay(60_000L) // update every minute
                val now = System.currentTimeMillis()
                nowMs.value = now
                _uiState.update { state ->
                    if (state.channels.isEmpty()) state
                    else {
                        val updated = state.channels.map { item ->
                            val prog = item.currentProgram
                            if (prog != null && prog.endMs > prog.startMs) {
                                val progress = ((now - prog.startMs).toFloat() / (prog.endMs - prog.startMs).toFloat()).coerceIn(0f, 1f)
                                item.copy(epgProgress = progress)
                            } else {
                                item
                            }
                        }
                        state.copy(channels = updated)
                    }
                }
            }
        }
    }

    // ─── Entity → Domain mappers (inline for now; move to mappers/ later) ────

    private fun ChannelEntity.toDomain() = Channel(
        id = id,
        playlistId = playlistId,
        epgChannelId = epgChannelId,
        name = name,
        logoUrl = logoUrl,
        streamUrl = streamUrl,
        groupTitle = groupTitle,
        catchupType = catchupType,
        catchupDays = catchupDays,
        catchupSource = catchupSource,
        isHidden = isHidden,
        isFavorite = isFavorite,
        sortOrder = sortOrder,
        isAdult = isAdult,
    )

    private fun EpgProgramEntity.toDomain() = EpgProgram(
        id = id,
        channelEpgId = channelEpgId,
        title = title,
        description = description,
        startMs = startMs,
        endMs = endMs,
        category = category,
        iconUrl = iconUrl,
        isNew = isNew,
        rating = rating,
    )

    companion object {
        private const val DEFAULT_PROFILE_ID = 1L
        private const val PREVIEW_FOCUS_DEBOUNCE_MS = 60L
        private const val EPG_REQUEST_DELAY_AFTER_PREVIEW_MS = 180L
    }
}
