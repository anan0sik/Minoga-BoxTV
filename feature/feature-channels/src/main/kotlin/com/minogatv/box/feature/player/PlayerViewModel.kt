package com.minogatv.box.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minogatv.box.core.database.dao.ChannelDao
import com.minogatv.box.core.database.dao.EpgDao
import com.minogatv.box.core.database.dao.FavoriteDao
import com.minogatv.box.core.database.dao.PlaylistDao
import com.minogatv.box.core.database.entity.FavoriteChannelEntity
import com.minogatv.box.core.data.catchup.CatchupUrlBuilder
import com.minogatv.box.core.data.mapper.toDomain
import com.minogatv.box.core.model.Channel
import com.minogatv.box.core.model.EpgProgram
import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.model.enums.CatchupType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.minogatv.box.feature.channels.ChannelSorter
import javax.inject.Inject

import com.minogatv.box.core.model.ChannelUtils

enum class VideoAspectRatio(val label: String) {
    FIT("По размеру"),
    RATIO_16_9("16:9"),
    FILL("Растянуть"),
    ZOOM("Увеличение"),
}

data class PlayerUiState(
    val channel: Channel? = null,
    val playlist: Playlist? = null,
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
    val epgProgress: Float = 0f,
    val isBuffering: Boolean = true,
    val isPlaying: Boolean = false,
    val isOsdVisible: Boolean = true,
    val errorMessage: String? = null,
    val catchupStartMs: Long? = null,
    /** Start time of the entire EPG programme being watched in archive (epoch ms). */
    val catchupProgramStartMs: Long? = null,
    /** End time of the entire EPG programme being watched in archive (epoch ms). */
    val catchupProgramEndMs: Long? = null,
    /** Timestamp in epoch ms requested from the archive server for the currently playing stream. */
    val catchupStreamStartMs: Long? = null,
    /** Generated catch-up URL when player is in archive mode. */
    val catchupUrl: String? = null,
    /** Whether the player is currently in catch-up/timeshift mode. */
    val isCatchupMode: Boolean = false,
    val channelList: List<Channel> = emptyList(),
    val channelIndex: Int = 0,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    val sleepTimerMinutes: Int = 0,
    /** Current reconnect attempt number (0 = no reconnect in progress). */
    val reconnectAttempt: Int = 0,
    /** Whether the OLED screensaver is active. */
    val isScreensaverActive: Boolean = false,
    /** Whether the current stream is audio-only (radio mode). */
    val isAudioOnly: Boolean = false,
    /** Whether archive/catch-up is supported for the current channel (taking settings override into account). */
    val isArchiveSupported: Boolean = false,
    /** Whether the current channel is in Favorites. */
    val isFavorite: Boolean = false,
    /** Whether the left channel zap overlay (▲/▼ in live mode) is open. */
    val isZapOpen: Boolean = false,
    /** Currently browsed folder title inside the zap overlay (◄/►). */
    val zapFolderTitle: String = "Все каналы",
    /** Channels of [zapFolderTitle] shown in the zap overlay. */
    val zapChannelList: List<Channel> = emptyList(),
    /** Available channel folders for cycling with ◄/► in the zap overlay. */
    val zapFolders: List<String> = emptyList(),
    /** Focused row in the zap overlay (index in [zapChannelList]). */
    val zapFocusedIndex: Int = 0,
    /** Current programme title per channel id for the zap overlay. */
    val zapPrograms: Map<Long, String> = emptyMap(),
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val playlistDao: PlaylistDao,
    private val favoriteDao: FavoriteDao,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialChannelId: Long = savedStateHandle.get<Any>("channelId")?.toString()?.toLongOrNull() ?: -1L
    private val initialCatchupStartMs: Long? = savedStateHandle.get<Any>("catchupStartMs")?.toString()?.toLongOrNull()?.takeIf { it > 0L }

    private val _exitPlayerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val exitPlayerEvent: SharedFlow<Unit> = _exitPlayerEvent.asSharedFlow()

    /** Event emitted when stream URL should change (e.g., catch-up switch). */
    private val _streamSwitchEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val streamSwitchEvent: SharedFlow<String> = _streamSwitchEvent.asSharedFlow()

    private var sleepTimerJob: Job? = null
    private var screensaverJob: Job? = null

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            catchupStartMs = initialCatchupStartMs,
            isOsdVisible = true,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var osdHideJob: Job? = null
    private val currentProfileId = 1L

    private var lastRequestedChannelId: Long = 0L
    private var lastRequestedCatchupMs: Long? = null
    private var loadChannelsJob: Job? = null

    companion object {
        /** Screensaver activates after 3 minutes of inactivity while paused. */
        private const val SCREENSAVER_DELAY_MS = 3 * 60 * 1000L
    }

    init {
        if (initialChannelId > 0L) {
            setChannelId(initialChannelId, initialCatchupStartMs)
        }
    }

    fun setChannelId(channelId: Long, catchupMs: Long? = null) {
        if (channelId <= 0L) return
        if (lastRequestedChannelId == channelId && lastRequestedCatchupMs == catchupMs && loadChannelsJob?.isActive == true) {
            return
        }
        val currentChannel = _uiState.value.channel
        if (currentChannel?.id != channelId || catchupMs != _uiState.value.catchupStartMs || loadChannelsJob == null) {
            lastRequestedChannelId = channelId
            lastRequestedCatchupMs = catchupMs
            _uiState.update { it.copy(catchupStartMs = catchupMs) }
            loadChannelsAndSelect(channelId, catchupMs)
        }
    }

    private fun loadChannelsAndSelect(targetChannelId: Long, catchupMs: Long? = null) {
        loadChannelsJob?.cancel()
        loadChannelsJob = viewModelScope.launch {
            // 1. Instantly get the targeted channel directly from DB to start playback without delay
            val directEntity = channelDao.getById(targetChannelId)
            val directChannel = directEntity?.toDomain()
            val directPlaylist = directEntity?.let { playlistDao.getById(it.playlistId)?.toDomain() }

            if (directChannel != null) {
                val forcedType = getForcedCatchupType()
                val isSupported = directChannel.hasArchive(forcedType)
                val enterCatchup = catchupMs != null && isSupported
                val directFav = runCatching {
                    favoriteDao.isFavorite(currentProfileId, directChannel.id)
                }.getOrDefault(directChannel.isFavorite)
                _uiState.update {
                    it.copy(
                        channel = directChannel,
                        playlist = directPlaylist,
                        catchupStartMs = if (enterCatchup) catchupMs else null,
                        isCatchupMode = enterCatchup,
                        isArchiveSupported = isSupported,
                        isFavorite = directFav,
                        isBuffering = true,
                        errorMessage = null,
                    )
                }
                if (enterCatchup) {
                    prepareAndStartCatchup(directChannel, catchupMs!!)
                } else {
                    loadEpgForChannel(directChannel)
                }
            }

            // 2. Load channels of the current folder in the user's sort order for zapping (▲▼ / overlay)
            val (activeFolder, channels, folders) = withContext(Dispatchers.IO) { loadZapChannelData(targetChannelId) }
            val index = channels.indexOfFirst { it.id == targetChannelId }.coerceAtLeast(0)
            val selected = channels.getOrNull(index) ?: directChannel
            val selectedPlaylist = if (selected?.id == directChannel?.id) {
                directPlaylist
            } else {
                selected?.let { playlistDao.getById(it.playlistId)?.toDomain() } ?: directPlaylist
            }
            val forcedType = getForcedCatchupType()
            val isSupported = selected?.hasArchive(forcedType) == true
            val isFav = selected?.let {
                runCatching { favoriteDao.isFavorite(currentProfileId, it.id) }.getOrDefault(it.isFavorite)
            } ?: false

            _uiState.update {
                it.copy(
                    channelList = channels,
                    zapFolderTitle = activeFolder,
                    zapChannelList = channels,
                    zapFolders = folders,
                    channelIndex = index,
                    channel = selected,
                    playlist = selectedPlaylist,
                    isArchiveSupported = isSupported,
                    isFavorite = isFav,
                )
            }
            if (pendingOpenZap && channels.isNotEmpty() && catchupMs == null) {
                pendingOpenZap = false
                openZapOverlay()
            }

            if (selected != null) {
                if (catchupMs == null) {
                    loadEpgForChannel(selected)
                }
                saveLastWatchedChannel(selected.id)
            } else {
                // Target channel does not exist and DB has no channels — exit back to channel list
                _exitPlayerEvent.tryEmit(Unit)
            }
            scheduleOsdHide()
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

    @Volatile
    private var cachedAllChannels: List<Channel> = emptyList()
    @Volatile
    private var cachedFavoriteIds: Set<Long> = emptySet()
    @Volatile
    private var activePlayingFolderTitle: String = "Все каналы"

    private val adultKeywords = listOf("18+", "adult", "xxx", "эротика", "взрослые", "порно", "erotic", "ххх")

    private fun isAdultFolderTitle(title: String): Boolean {
        val lower = title.lowercase(java.util.Locale.ROOT)
        return adultKeywords.any { lower.contains(it) }
    }

    private fun buildZapFoldersList(all: List<Channel>, favIds: Set<Long>, currentFolder: String): List<String> {
        val list = ArrayList<String>()
        list.add("Все каналы")
        if (favIds.isNotEmpty() || currentFolder == "Избранные каналы" || currentFolder == "Избранное") {
            list.add("Избранные каналы")
        }
        val seen = HashSet<String>()
        for (ch in all) {
            val grp = ch.groupTitle.trim()
            if (grp.isNotEmpty() && seen.add(grp)) {
                if (!isAdultFolderTitle(grp) || grp == currentFolder) {
                    list.add(grp)
                }
            }
        }
        return list
    }

    private fun filterAndSortChannelsForFolder(folderTitle: String, all: List<Channel>, favIds: Set<Long>): List<Channel> {
        val base = when (folderTitle) {
            "Все каналы" -> all
            "Избранное", "Избранные каналы" -> all.filter { it.id in favIds }
            else -> all.filter { it.groupTitle.trim() == folderTitle }
        }
        val mode = ChannelSorter.readMode(context)
        return ChannelSorter.sort(base, mode) { it.name }
    }

    /**
     * Channels of the folder the user opened last (prefs `last_selected_folder`), sorted by the
     * global channel sort mode — the same order as the channel list. Falls back to all channels
     * when the target channel isn't in that folder (e.g. opened from search / autostart).
     */
    private suspend fun loadZapChannelData(targetChannelId: Long): Triple<String, List<Channel>, List<String>> {
        val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        val savedFolder = prefs.getString("last_selected_folder", null) ?: "Все каналы"
        val normalizedSaved = if (savedFolder == "Избранное") "Избранные каналы" else savedFolder
        val all = channelDao.getAllByProfile(currentProfileId).map { it.toDomain() }
        val favIds = runCatching { favoriteDao.getFavoriteIds(currentProfileId).toSet() }.getOrDefault(emptySet())
        cachedAllChannels = all
        cachedFavoriteIds = favIds

        val folderChannels = withContext(Dispatchers.Default) {
            filterAndSortChannelsForFolder(normalizedSaved, all, favIds)
        }
        val effectiveFolder = if (folderChannels.any { it.id == targetChannelId }) normalizedSaved else "Все каналы"
        activePlayingFolderTitle = effectiveFolder
        val finalChannels = if (effectiveFolder == normalizedSaved) {
            folderChannels
        } else {
            withContext(Dispatchers.Default) { filterAndSortChannelsForFolder("Все каналы", all, favIds) }
        }
        val folders = buildZapFoldersList(all, favIds, effectiveFolder)
        return Triple(effectiveFolder, finalChannels, folders)
    }

    // ─── Channel zap overlay (▲/▼ in live mode, ◄/► switches folder) ────────

    private var zapHideJob: Job? = null
    private var zapProgramsLoadedAt = 0L
    @Volatile
    private var pendingOpenZap: Boolean = false

    fun openZapOverlay() {
        val state = _uiState.value
        if (state.isCatchupMode) return
        if (state.channelList.isEmpty()) {
            pendingOpenZap = true
            return
        }
        pendingOpenZap = false
        osdHideJob?.cancel()
        val all = cachedAllChannels.ifEmpty { state.channelList }
        val folders = buildZapFoldersList(all, cachedFavoriteIds, activePlayingFolderTitle)
        val playingIdx = state.channelList.indexOfFirst { it.id == state.channel?.id }
            .takeIf { it >= 0 } ?: state.channelIndex
        _uiState.update {
            it.copy(
                isZapOpen = true,
                isOsdVisible = false,
                zapFolderTitle = activePlayingFolderTitle,
                zapChannelList = state.channelList,
                zapFolders = folders,
                zapFocusedIndex = playingIdx.coerceIn(0, state.channelList.lastIndex),
            )
        }
        loadZapPrograms()
        scheduleZapHide()
    }

    fun closeZapOverlay() {
        zapHideJob?.cancel()
        _uiState.update { it.copy(isZapOpen = false) }
    }

    fun switchZapFolder(delta: Int) {
        val state = _uiState.value
        if (!state.isZapOpen) return
        val all = cachedAllChannels.ifEmpty { state.channelList }
        val folders = buildZapFoldersList(all, cachedFavoriteIds, activePlayingFolderTitle)
        if (folders.isEmpty()) return
        val curIdx = folders.indexOf(state.zapFolderTitle).takeIf { it >= 0 } ?: 0
        val nextIdx = (curIdx + delta + folders.size) % folders.size
        val nextFolder = folders[nextIdx]
        val nextChannels = filterAndSortChannelsForFolder(nextFolder, all, cachedFavoriteIds)
        val playingId = state.channel?.id ?: -1L
        val foundPlayingIdx = if (playingId > 0L) nextChannels.indexOfFirst { it.id == playingId } else -1
        val nextFocus = if (foundPlayingIdx >= 0) foundPlayingIdx else 0
        _uiState.update {
            it.copy(
                zapFolderTitle = nextFolder,
                zapChannelList = nextChannels,
                zapFolders = folders,
                zapFocusedIndex = nextFocus.coerceIn(0, (nextChannels.size - 1).coerceAtLeast(0)),
            )
        }
        scheduleZapHide()
    }

    fun moveZapFocus(delta: Int) {
        val list = _uiState.value.zapChannelList.ifEmpty { _uiState.value.channelList }
        if (list.isEmpty()) return
        val cur = _uiState.value.zapFocusedIndex
        val next = if (kotlin.math.abs(delta) == 1) {
            val raw = cur + delta
            when {
                raw < 0 -> list.lastIndex
                raw > list.lastIndex -> 0
                else -> raw
            }
        } else {
            (cur + delta).coerceIn(0, list.lastIndex)
        }
        _uiState.update { it.copy(zapFocusedIndex = next) }
        scheduleZapHide()
    }

    /** Plays the focused zap row (OK), commits the chosen folder, and closes the overlay. */
    fun confirmZapSelection() {
        val state = _uiState.value
        val zapList = state.zapChannelList.ifEmpty { state.channelList }
        val target = zapList.getOrNull(state.zapFocusedIndex)
        commitZapFolderSelection(state.zapFolderTitle, zapList)
        closeZapOverlay()
        if (target != null) playChannelById(target.id)
    }

    /** Plays a zap row tapped on a touch screen, committing the chosen folder. */
    fun playZapIndex(index: Int) {
        val state = _uiState.value
        val zapList = state.zapChannelList.ifEmpty { state.channelList }
        val target = zapList.getOrNull(index) ?: return
        commitZapFolderSelection(state.zapFolderTitle, zapList)
        closeZapOverlay()
        playChannelById(target.id)
    }

    private fun commitZapFolderSelection(folderTitle: String, zapList: List<Channel>) {
        if (zapList.isEmpty()) return
        activePlayingFolderTitle = folderTitle
        val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("last_selected_folder", folderTitle).apply()
        val currentId = _uiState.value.channel?.id
        val idxInNewList = currentId?.let { id -> zapList.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
        _uiState.update {
            it.copy(
                channelList = zapList,
                channelIndex = idxInNewList,
            )
        }
    }

    /** Switches playback to [channelId] within the current zap list (no-op if already playing live). */
    fun playChannelById(channelId: Long) {
        val state = _uiState.value
        val idx = state.channelList.indexOfFirst { it.id == channelId }
        if (idx < 0) return
        if (state.channel?.id == channelId && !state.isCatchupMode) {
            _uiState.update { it.copy(channelIndex = idx) }
            showOsd()
            return
        }
        switchToIndex(idx)
    }

    private fun scheduleZapHide() {
        zapHideJob?.cancel()
        zapHideJob = viewModelScope.launch {
            delay(6_000L)
            _uiState.update { it.copy(isZapOpen = false) }
        }
    }

    private fun loadZapPrograms() {
        val now = System.currentTimeMillis()
        if (now - zapProgramsLoadedAt < 60_000L && _uiState.value.zapPrograms.isNotEmpty()) return
        zapProgramsLoadedAt = now
        val channels = cachedAllChannels.ifEmpty { _uiState.value.channelList }
        viewModelScope.launch {
            val map = withContext(Dispatchers.IO) {
                runCatching {
                    val ids = buildList {
                        for (ch in channels) {
                            val raw = ch.epgChannelId.trim()
                            if (raw.isNotEmpty()) {
                                add(raw)
                                val lower = raw.lowercase(java.util.Locale.ROOT)
                                if (lower != raw) add(lower)
                            }
                        }
                    }.distinct()
                    val progs = ids.chunked(400).flatMap { epgDao.getCurrentProgramsForIds(it, now) }
                    val byId = HashMap<String, String>(progs.size * 2)
                    for (p in progs) {
                        byId[p.channelEpgId] = p.title
                        byId[p.channelEpgId.lowercase(java.util.Locale.ROOT)] = p.title
                    }
                    val result = HashMap<Long, String>(channels.size * 2)
                    for (ch in channels) {
                        val raw = ch.epgChannelId.trim()
                        if (raw.isEmpty()) continue
                        val title = byId[raw] ?: byId[raw.lowercase(java.util.Locale.ROOT)]
                        if (title != null) result[ch.id] = title
                    }
                    result as Map<Long, String>
                }.getOrDefault(emptyMap())
            }
            _uiState.update { it.copy(zapPrograms = map) }
        }
    }

    private suspend fun prepareAndStartCatchup(channel: Channel, catchupMs: Long) {
        val candidateIds = buildList {
            if (channel.epgChannelId.isNotBlank()) {
                add(channel.epgChannelId)
                add(channel.epgChannelId.lowercase(java.util.Locale.ROOT))
            }
            val rawName = channel.name.trim()
            if (rawName.isNotEmpty()) {
                add(rawName)
                add(rawName.lowercase(java.util.Locale.ROOT))
                add(ChannelUtils.normalizeChannelName(rawName))
            }
        }.distinct()

        val programs = if (candidateIds.isNotEmpty()) {
            epgDao.getProgramsForChannelIds(candidateIds, catchupMs - 3600_000L, catchupMs + 48L * 3600_000L)
        } else emptyList()

        val matchingProg = programs.firstOrNull { catchupMs in it.startMs until it.endMs }
            ?: programs.firstOrNull { it.startMs == catchupMs }
            ?: programs.minByOrNull { kotlin.math.abs(it.startMs - catchupMs) }

        val progStart = matchingProg?.startMs ?: catchupMs
        val progEnd = if (matchingProg != null && matchingProg.endMs > matchingProg.startMs) {
            matchingProg.endMs
        } else {
            catchupMs + 3600_000L
        }
        val targetPlayMs = catchupMs.coerceIn(progStart, progEnd)
        val domainProg = matchingProg?.toDomain() ?: EpgProgram(
            id = 0L,
            channelEpgId = channel.epgChannelId,
            title = "Архивная передача",
            description = "",
            category = "",
            iconUrl = "",
            startMs = progStart,
            endMs = progEnd,
        )

        val forcedType = getForcedCatchupType()
        val catchupUrl = CatchupUrlBuilder.buildUrl(
            channel = channel,
            programStartMs = targetPlayMs,
            programEndMs = progEnd,
            overrideCatchupType = forcedType,
        ) ?: channel.streamUrl

        _uiState.update {
            it.copy(
                isCatchupMode = true,
                catchupUrl = catchupUrl,
                catchupStartMs = progStart,
                catchupStreamStartMs = targetPlayMs,
                catchupProgramStartMs = progStart,
                catchupProgramEndMs = progEnd,
                currentProgram = domainProg,
                nextProgram = null,
                isBuffering = true,
                errorMessage = null,
            )
        }

        _streamSwitchEvent.emit(catchupUrl)
    }

    private suspend fun loadEpgForChannel(channel: Channel) {
        val now = System.currentTimeMillis()
        val epgId = channel.epgChannelId.ifBlank { channel.name }
        val curr = if (epgId.isNotBlank()) {
            epgDao.getCurrentProgram(epgId, now) ?: epgDao.getCurrentProgram(channel.name, now)
        } else {
            epgDao.getCurrentProgram(channel.name, now)
        }
        val targetEpgId = curr?.channelEpgId ?: epgId
        val nxt = curr?.let { epgDao.getCurrentProgram(targetEpgId, it.endMs) }
        val progress = curr?.let {
            if (it.endMs > it.startMs) {
                ((now - it.startMs).toFloat() / (it.endMs - it.startMs).toFloat()).coerceIn(0f, 1f)
            } else 0f
        } ?: 0f

        _uiState.update {
            it.copy(
                currentProgram = curr?.toDomain(),
                nextProgram = nxt?.toDomain(),
                epgProgress = progress,
            )
        }
    }

    fun nextChannel() {
        val list = _uiState.value.channelList
        if (list.isEmpty()) return
        val nextIdx = (_uiState.value.channelIndex + 1) % list.size
        switchToIndex(nextIdx)
    }

    fun previousChannel() {
        val list = _uiState.value.channelList
        if (list.isEmpty()) return
        val prevIdx = if (_uiState.value.channelIndex - 1 < 0) list.size - 1 else _uiState.value.channelIndex - 1
        switchToIndex(prevIdx)
    }

    private fun switchToIndex(index: Int) {
        val list = _uiState.value.channelList
        val target = list.getOrNull(index) ?: return
        lastRequestedChannelId = target.id
        lastRequestedCatchupMs = null
        _uiState.update {
            it.copy(
                channelIndex = index,
                channel = target,
                isCatchupMode = false,
                catchupUrl = null,
                catchupStartMs = null,
                catchupStreamStartMs = null,
                catchupProgramStartMs = null,
                catchupProgramEndMs = null,
                isBuffering = true,
                errorMessage = null,
                isOsdVisible = true,
                currentProgram = null,
                nextProgram = null,
            )
        }
        viewModelScope.launch {
            val playlist = playlistDao.getById(target.playlistId)?.toDomain()
            val isFav = runCatching { favoriteDao.isFavorite(currentProfileId, target.id) }.getOrDefault(target.isFavorite)
            val forcedType = getForcedCatchupType()
            val isSupported = target.hasArchive(forcedType)
            _uiState.update {
                it.copy(
                    playlist = playlist ?: it.playlist,
                    isFavorite = isFav,
                    isArchiveSupported = isSupported,
                )
            }
            loadEpgForChannel(target)
            saveLastWatchedChannel(target.id)
        }
        scheduleOsdHide()
    }

    fun toggleFavorite() {
        val currentChannel = _uiState.value.channel ?: return
        viewModelScope.launch {
            val isFavNow = runCatching { favoriteDao.isFavorite(currentProfileId, currentChannel.id) }
                .getOrDefault(_uiState.value.isFavorite)
            val nextFav = !isFavNow
            if (nextFav) {
                val count = runCatching { favoriteDao.getFavoriteCount(currentProfileId) }.getOrDefault(0)
                favoriteDao.addFavorite(
                    FavoriteChannelEntity(
                        profileId = currentProfileId,
                        channelId = currentChannel.id,
                        sortOrder = count,
                    )
                )
                channelDao.setFavoriteFlag(currentChannel.id, true)
            } else {
                favoriteDao.removeFavorite(currentProfileId, currentChannel.id)
                channelDao.setFavoriteFlag(currentChannel.id, false)
            }
            val updatedChannel = currentChannel.copy(isFavorite = nextFav)
            cachedFavoriteIds = if (nextFav) cachedFavoriteIds + currentChannel.id else cachedFavoriteIds - currentChannel.id
            cachedAllChannels = cachedAllChannels.map { if (it.id == currentChannel.id) updatedChannel else it }
            val updatedList = _uiState.value.channelList.map {
                if (it.id == currentChannel.id) updatedChannel else it
            }
            val updatedZapList = _uiState.value.zapChannelList.map {
                if (it.id == currentChannel.id) updatedChannel else it
            }
            _uiState.update {
                it.copy(
                    isFavorite = nextFav,
                    channel = updatedChannel,
                    channelList = updatedList,
                    zapChannelList = updatedZapList,
                    isOsdVisible = true,
                )
            }
            scheduleOsdHide()
        }
    }

    fun toggleOsd() {
        if (_uiState.value.isOsdVisible) {
            hideOsd()
        } else {
            showOsd()
        }
    }

    fun showOsd() {
        _uiState.update { it.copy(isOsdVisible = true) }
        scheduleOsdHide()
    }

    fun hideOsd() {
        osdHideJob?.cancel()
        _uiState.update { it.copy(isOsdVisible = false) }
    }

    fun scheduleOsdHide() {
        osdHideJob?.cancel()
        osdHideJob = viewModelScope.launch {
            delay(5_000)
            _uiState.update { it.copy(isOsdVisible = false) }
        }
    }

    fun onPlayerStateChanged(isPlaying: Boolean, isBuffering: Boolean) {
        _uiState.update {
            it.copy(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                // Reset reconnect counter when playback resumes successfully
                reconnectAttempt = if (isPlaying) 0 else it.reconnectAttempt,
            )
        }
        // Manage screensaver: start timer when paused, cancel when playing
        if (isPlaying) {
            cancelScreensaver()
        } else if (!isBuffering) {
            startScreensaverTimer()
        }
    }

    fun onPlayerError(message: String) {
        _uiState.update {
            it.copy(
                errorMessage = message,
                isBuffering = false,
                isPlaying = false,
                isOsdVisible = true,
            )
        }
    }

    /**
     * Called by [MinogaLoadErrorPolicy] on each reconnection attempt.
     * Updates the UI to show the attempt counter.
     */
    fun onReconnectAttempt(attempt: Int) {
        _uiState.update {
            it.copy(
                reconnectAttempt = attempt,
                isBuffering = true,
                errorMessage = null,
            )
        }
    }

    fun retry() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                isBuffering = true,
                reconnectAttempt = 0,
            )
        }
    }

    /**
     * Detect audio-only (radio) streams based on ExoPlayer track info.
     * Called from PlayerScreen after tracks are detected.
     */
    fun onTracksDetected(hasVideo: Boolean) {
        _uiState.update { it.copy(isAudioOnly = !hasVideo) }
    }

    // ─── Catch-up / Timeshift ──────────────────────────────────────────────────

    /**
     * Switch to catch-up mode for the currently playing channel.
     * Builds the timeshift URL from the current EPG program and notifies the player.
     */
    fun switchToCatchup() {
        val state = _uiState.value
        val channel = state.channel ?: return
        val forcedType = getForcedCatchupType()
        if (!channel.hasArchive(forcedType)) return

        val now = System.currentTimeMillis()
        val program = state.currentProgram
        val progStart = program?.startMs ?: (now - 3600_000L)
        val progEnd = program?.endMs ?: now
        val playStartMs = if (now in progStart until progEnd) now else progStart

        val catchupUrl = CatchupUrlBuilder.buildUrl(
            channel = channel,
            programStartMs = playStartMs,
            programEndMs = progEnd,
            overrideCatchupType = forcedType,
        ) ?: return

        _uiState.update {
            it.copy(
                isCatchupMode = true,
                catchupUrl = catchupUrl,
                catchupStartMs = progStart,
                catchupStreamStartMs = playStartMs,
                catchupProgramStartMs = progStart,
                catchupProgramEndMs = progEnd,
                isBuffering = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            _streamSwitchEvent.emit(catchupUrl)
        }
    }

    /**
     * Seek to a specific timestamp in epoch milliseconds within the archive.
     * Allows continuous seeking across the entire archive timeline without being
     * locked to a single programme.
     */
    fun seekCatchupTo(targetTimestampMs: Long) {
        val channel = _uiState.value.channel ?: return
        val forcedType = getForcedCatchupType()
        if (!channel.hasArchive(forcedType)) return

        val nowMs = System.currentTimeMillis()
        val archiveDays = if (channel.catchupDays > 0) channel.catchupDays else 7
        val minArchiveMs = nowMs - (archiveDays * 86_400_000L)
        val maxArchiveMs = nowMs - 15_000L

        // Seek across the full timeline of the archive, not locked to a single programme
        val clampedTimestamp = targetTimestampMs.coerceIn(minArchiveMs, maxArchiveMs)

        viewModelScope.launch {
            val epgId = channel.epgChannelId
            val progEntity = if (epgId.isNotBlank()) {
                epgDao.getCurrentProgram(epgId, clampedTimestamp) ?: epgDao.getCurrentProgram(channel.name, clampedTimestamp)
            } else {
                epgDao.getCurrentProgram(channel.name, clampedTimestamp)
            }
            val prog = progEntity?.toDomain()
            val progStart = prog?.startMs ?: clampedTimestamp
            val progEnd = prog?.endMs ?: (clampedTimestamp + 14_400_000L).coerceAtMost(nowMs)

            val forcedType = getForcedCatchupType()
            val newUrl = CatchupUrlBuilder.buildUrl(
                channel = channel,
                programStartMs = clampedTimestamp,
                programEndMs = progEnd,
                overrideCatchupType = forcedType,
            ) ?: return@launch

            _uiState.update {
                it.copy(
                    isCatchupMode = true,
                    catchupUrl = newUrl,
                    catchupStreamStartMs = clampedTimestamp,
                    catchupProgramStartMs = progStart,
                    catchupProgramEndMs = progEnd,
                    currentProgram = prog ?: it.currentProgram,
                    isBuffering = true,
                    errorMessage = null,
                )
            }

            _streamSwitchEvent.emit(newUrl)
        }
    }

    /**
     * Return from catch-up mode to live stream.
     */
    fun switchToLive() {
        val state = _uiState.value
        val liveUrl = state.channel?.streamUrl ?: return

        _uiState.update {
            it.copy(
                isCatchupMode = false,
                catchupUrl = null,
                catchupStartMs = null,
                catchupStreamStartMs = null,
                catchupProgramStartMs = null,
                catchupProgramEndMs = null,
                isBuffering = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            _streamSwitchEvent.emit(liveUrl)
        }
        state.channel?.let {
            viewModelScope.launch {
                loadEpgForChannel(it)
            }
        }
    }

    // ─── Screensaver ──────────────────────────────────────────────────────────

    private fun startScreensaverTimer() {
        screensaverJob?.cancel()
        screensaverJob = viewModelScope.launch {
            delay(SCREENSAVER_DELAY_MS)
            _uiState.update { it.copy(isScreensaverActive = true) }
        }
    }

    private fun cancelScreensaver() {
        screensaverJob?.cancel()
        _uiState.update { it.copy(isScreensaverActive = false) }
    }

    /**
     * Called on any user input (remote key press, touch) to reset the screensaver timer.
     */
    fun onUserInteraction() {
        cancelScreensaver()
        if (!_uiState.value.isPlaying) {
            startScreensaverTimer()
        }
    }

    // ─── Aspect Ratio & Sleep Timer ───────────────────────────────────────────

    fun cycleAspectRatio() {
        val next = when (_uiState.value.aspectRatio) {
            VideoAspectRatio.FIT -> VideoAspectRatio.RATIO_16_9
            VideoAspectRatio.RATIO_16_9 -> VideoAspectRatio.FILL
            VideoAspectRatio.FILL -> VideoAspectRatio.ZOOM
            VideoAspectRatio.ZOOM -> VideoAspectRatio.FIT
        }
        _uiState.update { it.copy(aspectRatio = next) }
        scheduleOsdHide()
    }

    fun cycleSleepTimer() {
        val next = when (_uiState.value.sleepTimerMinutes) {
            0 -> 15
            15 -> 30
            30 -> 60
            60 -> 120
            else -> 0
        }
        setSleepTimer(next)
        scheduleOsdHide()
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(sleepTimerMinutes = minutes) }
        if (minutes > 0) {
            sleepTimerJob = viewModelScope.launch {
                delay(minutes * 60_000L)
                _exitPlayerEvent.emit(Unit)
            }
        }
    }

    private fun saveLastWatchedChannel(channelId: Long) {
        try {
            val prefs = context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong("last_watched_channel_id", channelId).apply()
        } catch (_: Exception) {}
    }
}
