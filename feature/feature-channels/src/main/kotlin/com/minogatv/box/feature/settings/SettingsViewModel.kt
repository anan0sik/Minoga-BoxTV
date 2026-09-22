package com.minogatv.box.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minogatv.box.core.data.repository.PlaylistRepository
import com.minogatv.box.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.minogatv.box.core.data.sync.SyncScheduler
import com.minogatv.box.core.data.sync.EpgSyncManager
import com.minogatv.box.core.database.dao.PlaylistDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── UI state ─────────────────────────────────────────────────────────────────

data class SettingsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    val refreshingIds: Set<Long> = emptySet(),
    val errorMessage: String? = null,
    val epgSyncProgress: Float? = null,
    val epgSyncStatus: String? = null,
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
) : ViewModel() {

    private val _refreshingIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _isLoading     = MutableStateFlow(false)
    private val _errorMessage  = MutableStateFlow<String?>(null)

    /** Hardcoded profile — replace with real ProfileRepository in Step 6 */
    private val currentProfileId = 1L

    val uiState: StateFlow<SettingsUiState> = combine(
        playlistRepository.observePlaylists(currentProfileId),
        _isLoading,
        _refreshingIds,
        _errorMessage,
        syncScheduler.observeEpgSyncProgressInfo()
    ) { playlists, loading, refreshing, error, epgSyncInfo ->
        SettingsUiState(
            playlists      = playlists,
            isLoading      = loading,
            refreshingIds  = refreshing,
            errorMessage   = error,
            epgSyncProgress = epgSyncInfo?.progress,
            epgSyncStatus   = epgSyncInfo?.status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(isLoading = true),
    )

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

    fun dismissError() {
        _errorMessage.value = null
    }
}
