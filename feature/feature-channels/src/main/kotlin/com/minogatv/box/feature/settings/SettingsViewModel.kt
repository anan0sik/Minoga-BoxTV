package com.minogatv.box.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minogatv.box.core.data.backup.BackupManager
import com.minogatv.box.core.data.repository.PlaylistRepository
import com.minogatv.box.core.data.sync.EpgSyncManager
import com.minogatv.box.core.data.sync.SyncScheduler
import com.minogatv.box.core.database.dao.PlaylistDao
import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.model.WebConfig
import com.minogatv.box.core.model.WebPairingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── UI state ─────────────────────────────────────────────────────────────────

data class WebPairingDialogState(
    val isVisible: Boolean = false,
    val code: String = "",
    val status: WebPairingStatus = WebPairingStatus.WAITING,
    val statusText: String = "Ожидание ввода кода на веб-странице...",
    val isInstalling: Boolean = false,
)

data class SettingsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val refreshingIds: Set<Long> = emptySet(),
    val errorMessage: String? = null,
    val epgSyncProgress: Float? = null,
    val epgSyncStatus: String? = null,
    val backupMessage: String? = null,
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

/**
 * ViewModel for [SettingsScreen].
 *
 * Uses [PlaylistRepository] for all CRUD operations — WorkManager sync jobs are
 * automatically scheduled/cancelled inside the repository on add/update/delete.
 *
 * Profile ID is hardcoded to 1L until multi-profile support is added in Step 6.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val syncScheduler: SyncScheduler,
    private val playlistDao: PlaylistDao,
    private val epgSyncManager: EpgSyncManager,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _refreshingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isLoading     = MutableStateFlow(false)
    private val _errorMessage  = MutableStateFlow<String?>(null)
    private val _backupMessage = MutableStateFlow<String?>(null)

    private val _webPairingState = MutableStateFlow(WebPairingDialogState())
    val webPairingState: StateFlow<WebPairingDialogState> = _webPairingState.asStateFlow()

    private var pairingJob: Job? = null

    /** Hardcoded profile — replace with real ProfileRepository in Step 6 */
    private val currentProfileId = 1L

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            playlistRepository.observePlaylists(currentProfileId),
            _isLoading,
            _refreshingIds,
        ) { playlists, loading, refreshing ->
            Triple(playlists, loading, refreshing)
        },
        _errorMessage,
        syncScheduler.observeEpgSyncProgressInfo(),
        _backupMessage,
    ) { (playlists, loading, refreshing), error, epgSyncInfo, backupMsg ->
        SettingsUiState(
            playlists       = playlists,
            isLoading       = loading,
            refreshingIds   = refreshing,
            errorMessage    = error,
            epgSyncProgress = epgSyncInfo?.progress,
            epgSyncStatus   = epgSyncInfo?.status,
            backupMessage   = backupMsg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(isLoading = true),
    )

    // ── Web Pairing by 4-Digit Code ───────────────────────────────────────────

    fun startWebPairingSession(
        pageUrl: String = Playlist.DEFAULT_WEB_CONFIG_URL,
        onConfigApplied: (WebConfig) -> Unit = {},
    ) {
        pairingJob?.cancel()
        val generatedCode = (1000..9999).random().toString()
        _webPairingState.value = WebPairingDialogState(
            isVisible = true,
            code = generatedCode,
            status = WebPairingStatus.WAITING,
            statusText = "Подключение к серверу $pageUrl...",
            isInstalling = false,
        )

        pairingJob = viewModelScope.launch {
            val regResult = playlistRepository.registerWebPairingCode(generatedCode, pageUrl)
            val activeServerUrl = regResult.getOrNull() ?: pageUrl

            _webPairingState.update {
                it.copy(
                    statusText = if (regResult.isSuccess) {
                        "Откройте minogatvbox.duckdns.org и введите код с экрана"
                    } else {
                        "Ожидание соединения с minogatvbox.duckdns.org..."
                    }
                )
            }

            while (isActive) {
                delay(2_000L)
                val pollRes = playlistRepository.pollWebPairingCode(generatedCode, activeServerUrl)
                val pollData = pollRes.getOrNull() ?: continue

                when (pollData.status) {
                    WebPairingStatus.WAITING -> {
                        _webPairingState.update {
                            if (it.status != WebPairingStatus.VERIFIED && !it.isInstalling) {
                                it.copy(
                                    status = WebPairingStatus.WAITING,
                                    statusText = "Откройте minogatvbox.duckdns.org и введите код с экрана",
                                )
                            } else {
                                it
                            }
                        }
                    }

                    WebPairingStatus.VERIFIED -> {
                        _webPairingState.update {
                            it.copy(
                                status = WebPairingStatus.VERIFIED,
                                statusText = "Код введён на сайте! Заполните Название, Плейлист и EPG и нажмите «Установить»...",
                            )
                        }
                    }

                    WebPairingStatus.READY -> {
                        val submittedConfig = pollData.config ?: continue
                        _webPairingState.update {
                            it.copy(
                                status = WebPairingStatus.READY,
                                isInstalling = true,
                                statusText = "Загрузка плейлиста «${submittedConfig.name}» и телепрограммы EPG...",
                            )
                        }

                        val applyRes = playlistRepository.applySubmittedWebConfig(submittedConfig, currentProfileId)
                        applyRes.onSuccess { cfg ->
                            backupManager.autoBackup()
                            _webPairingState.value = WebPairingDialogState(isVisible = false)
                            val epgDisplay = cfg.epgUrl ?: Playlist.DEFAULT_EPG_URL
                            _backupMessage.value =
                                "Плейлист и EPG успешно установлены по коду $generatedCode:\n" +
                                "• Название: ${cfg.name}\n" +
                                "• Плейлист: ${cfg.playlistUrl}\n" +
                                "• Телепрограмма (EPG): $epgDisplay"
                            onConfigApplied(cfg)
                        }.onFailure { err ->
                            val msg = err.localizedMessage ?: err.message ?: "Ошибка загрузки плейлиста"
                            _webPairingState.value = WebPairingDialogState(isVisible = false)
                            _errorMessage.value = msg
                        }
                        break
                    }
                }
            }
        }
    }

    fun stopWebPairingSession() {
        pairingJob?.cancel()
        pairingJob = null
        _webPairingState.value = WebPairingDialogState(isVisible = false)
    }

    // ── Playlist CRUD ─────────────────────────────────────────────────────────

    fun validateAndSavePlaylist(
        playlist: Playlist,
        isNew: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Validate playlist link availability
                val validation = playlistRepository.validatePlaylistAvailability(playlist)
                if (validation.isFailure) {
                    val msg = validation.exceptionOrNull()?.localizedMessage ?: "Плейлист недоступен"
                    _errorMessage.value = msg
                    onError(msg)
                    _isLoading.value = false
                    return@launch
                }

                // 2. Save and immediately download/sync channels
                if (isNew) {
                    playlistRepository.addPlaylist(playlist.copy(profileId = currentProfileId))
                } else {
                    playlistRepository.updatePlaylist(playlist)
                }
                backupManager.autoBackup()
                onSuccess()
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Ошибка сохранения: ${e.message}"
                _errorMessage.value = msg
                onError(msg)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addPlaylist(playlist: Playlist) {
        validateAndSavePlaylist(
            playlist = playlist,
            isNew = true,
            onSuccess = {},
            onError = {},
        )
    }

    fun updatePlaylist(playlist: Playlist) {
        validateAndSavePlaylist(
            playlist = playlist,
            isNew = false,
            onSuccess = {},
            onError = {},
        )
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            runCatching {
                playlistRepository.deletePlaylist(playlistId)
                backupManager.autoBackup()
            }.onFailure { e ->
                _errorMessage.value = "Ошибка удаления: ${e.localizedMessage}"
            }
        }
    }

    fun refreshPlaylist(playlistId: Long) {
        viewModelScope.launch {
            _refreshingIds.update { it + playlistId }
            val result = playlistRepository.refreshNow(playlistId)
            result.onFailure { e ->
                _errorMessage.value = "Ошибка обновления: ${e.localizedMessage ?: e.message}"
            }
            _refreshingIds.update { it - playlistId }
        }
    }

    fun refreshEpg(epgUrl: String) {
        val cleanUrl = Playlist.normalizeUrl(epgUrl).ifBlank { Playlist.DEFAULT_EPG_URL }
        viewModelScope.launch {
            epgSyncManager.clearHttpCacheForUrl(cleanUrl)
            val playlists = runCatching { playlistRepository.observePlaylists(currentProfileId).first() }.getOrDefault(emptyList())
            if (playlists.isNotEmpty()) {
                playlists.forEach { p ->
                    playlistDao.updateEpgUrl(p.id, cleanUrl)
                }
                syncScheduler.triggerManualEpgSync(playlists.first().id, cleanUrl)
            } else {
                syncScheduler.triggerManualEpgSync(1L, cleanUrl)
            }
        }
    }

    fun exportBackup(onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = backupManager.exportBackup()
            _isLoading.value = false
            res.onSuccess { file ->
                val msg = "Резервная копия успешно сохранена в:\n${file.absolutePath}"
                _backupMessage.value = msg
                onComplete?.invoke(true, msg)
            }.onFailure { err ->
                val msg = "Ошибка экспорта: ${err.localizedMessage ?: err.message}"
                _errorMessage.value = msg
                onComplete?.invoke(false, msg)
            }
        }
    }

    fun importBackup(onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val res = backupManager.importBackup()
            _isLoading.value = false
            res.onSuccess { stats ->
                val msg = "Успешно восстановлено из ${stats.filePath}:\n• Плейлистов: ${stats.playlistsRestored}\n• Настроек: ${stats.settingsRestored}\n• Избранных каналов: ${stats.favoritesRestored}"
                _backupMessage.value = msg
                onComplete?.invoke(true, msg)
            }.onFailure { err ->
                val msg = err.localizedMessage ?: err.message ?: "Ошибка импорта"
                _errorMessage.value = msg
                onComplete?.invoke(false, msg)
            }
        }
    }

    fun dismissBackupMessage() {
        _backupMessage.value = null
    }

    fun dismissError() {
        _errorMessage.value = null
    }
}
