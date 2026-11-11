package com.minogatv.box.feature.vod

import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.minogatv.box.core.model.Playlist
import com.minogatv.box.feature.settings.VodPortalConfigDialog
import com.minogatv.box.feature.ui.LocalScreenKeyDispatcher
import com.minogatv.box.feature.ui.RemoteControlHintBar
import com.minogatv.box.feature.ui.RemoteHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// ─── Design Tokens (Obsidian Cinematic Glassmorphism) ─────────────────────────

private val VodBg = Color(0xFF07090E)
private val VodSurface1 = Color(0xFF0D111A)
private val VodSurface2 = Color(0xFF151C2B)
private val VodCyan = Color(0xFF00E5FF)
private val VodGold = Color(0xFFF5A623)
private val VodTextPrim = Color(0xFFF8FAFC)
private val VodTextSec = Color(0xFF94A3B8)
private val VodDanger = Color(0xFFEF4444)

// ─── Domain Models ────────────────────────────────────────────────────────────

data class VodEpisodeItem(
    val id: String,
    val title: String,
    val season: Int = 1,
    val episode: Int = 1,
    val streamUrl: String,
    val requestJson: String = "",
)

data class VodMovieItem(
    val id: String,
    val title: String,
    val originalTitle: String = "",
    val year: String = "",
    val country: String = "",
    val genre: String = "",
    val category: String = "",
    val categoryId: String = "",
    val rating: String = "",
    val ratingKp: String = "",
    val ratingImdb: String = "",
    val quality: String = "HD",
    val duration: String = "",
    val age: String = "",
    val description: String = "",
    val director: String = "",
    val actors: String = "",
    val poster: String = "",
    val streamUrl: String = "",
    val cmd: String = "",
    val requestJson: String = "",
    val type: String = "movie",
    val addedAt: Long = 0L,
    val episodes: List<VodEpisodeItem> = emptyList(),
)

data class VodCategoryTab(
    val id: String,
    val title: String,
    val rawTitle: String = title,
    val requestJson: String = "",
)

data class VodCatalogResponse(
    val ok: Boolean,
    val error: String? = null,
    val categories: List<VodCategoryTab> = emptyList(),
    val genres: List<String> = emptyList(),
    val years: List<String> = emptyList(),
    val items: List<VodMovieItem> = emptyList(),
    val totalCount: Int = 0,
    val nextOffset: Int = 0,
    val hasMore: Boolean = false,
)

enum class VodSortOption(val id: String, val label: String) {
    ADDED("added", "По новизне"),
    RATING("rating", "По рейтингу"),
    YEAR("year", "По году"),
    TITLE("title", "По названию (А–Я)"),
}

// ─── Main Screen Composable ───────────────────────────────────────────────────

@Composable
fun VodLibraryScreen(
    onBackClick: () -> Unit,
    onOpenSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE) }

    var vodUrl by remember { mutableStateOf(prefs.getString("vod_portal_url", "") ?: "") }
    var vodLogin by remember { mutableStateOf(prefs.getString("vod_portal_login", "") ?: "") }
    var vodPassword by remember { mutableStateOf(prefs.getString("vod_portal_password", "") ?: "") }
    var vodKey by remember { mutableStateOf(prefs.getString("vod_portal_key", "") ?: "") }

    var favorites by remember {
        mutableStateOf(
            prefs.getStringSet("vod_favorites_ids", emptySet())?.toSet() ?: emptySet()
        )
    }
    var historyIds by remember {
        mutableStateOf(
            (prefs.getString("vod_history_csv", "") ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        )
    }

    var allItems by remember { mutableStateOf<List<VodMovieItem>>(emptyList()) }
    var savedItemsCache by remember { mutableStateOf<Map<String, VodMovieItem>>(emptyMap()) }
    var providerCategories by remember { mutableStateOf<List<VodCategoryTab>>(emptyList()) }
    var availableGenres by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableYears by remember { mutableStateOf<List<String>>(emptyList()) }

    var selectedCategory by remember { mutableStateOf("all") }
    var selectedGenre by remember { mutableStateOf("all") }
    var selectedYear by remember { mutableStateOf("all") }
    var selectedSort by remember { mutableStateOf(VodSortOption.ADDED) }
    var searchQuery by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(vodUrl.isNotBlank() || vodKey.isNotBlank()) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var totalCount by remember { mutableIntStateOf(0) }
    var nextOffset by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showPortalDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var detailsItem by remember { mutableStateOf<VodMovieItem?>(null) }
    var isLoadingDetailsEpisodes by remember { mutableStateOf(false) }

    // Active VOD playback state
    var playingItem by remember { mutableStateOf<VodMovieItem?>(null) }
    var playingEpisodeIdx by remember { mutableIntStateOf(-1) }
    var playingResumeSec by remember { mutableLongStateOf(0L) }
    var playingUrl by remember { mutableStateOf("") }
    var playingTitle by remember { mutableStateOf("") }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT || configuration.screenWidthDp < 600
    val hasPortalConfig = vodUrl.isNotBlank() || vodKey.isNotBlank()
    val gridState = rememberLazyGridState()
    val firstPosterFocusRequester = remember { FocusRequester() }

    fun resolveServerCategoryParam(cat: String): String {
        if (cat == "fav" || cat == "history" || cat == "uhd") return "all"
        if (cat.startsWith("cat:")) return cat.removePrefix("cat:")
        return cat
    }

    fun triggerCatalogReload(
        u: String = vodUrl,
        l: String = vodLogin,
        p: String = vodPassword,
        k: String = vodKey,
        append: Boolean = false,
    ) {
        if (u.isBlank() && k.isBlank()) return
        if (append && (isLoading || isLoadingMore || !hasMore)) return
        scope.launch {
            if (append) {
                isLoadingMore = true
            } else {
                isLoading = true
                errorMessage = null
                nextOffset = 0
                hasMore = false
            }
            val reqOffset = if (append) nextOffset else 0
            val res = fetchVodCatalog(
                webConfigUrl = prefs.getString("web_config_url", Playlist.DEFAULT_WEB_CONFIG_URL)
                    ?: Playlist.DEFAULT_WEB_CONFIG_URL,
                vodUrl = u,
                vodLogin = l,
                vodPassword = p,
                vodKey = k,
                category = resolveServerCategoryParam(selectedCategory),
                genre = if (selectedGenre == "all") "" else selectedGenre,
                year = if (selectedYear == "all") "" else selectedYear,
                sort = selectedSort.id,
                search = searchQuery,
                offset = reqOffset,
            )
            if (append) {
                isLoadingMore = false
            } else {
                isLoading = false
            }
            if (!res.ok) {
                if (!append) {
                    errorMessage = res.error ?: "Не удалось загрузить каталог фильмов с портала"
                }
            } else {
                val mergedCache = savedItemsCache.toMutableMap()
                res.items.forEach { mergedCache[it.id] = it }
                savedItemsCache = mergedCache

                if (append) {
                    val existingIds = allItems.map { it.id }.toHashSet()
                    val newItems = res.items.filter { existingIds.add(it.id) }
                    allItems = allItems + newItems
                } else {
                    allItems = res.items
                    launch {
                        if (gridState.layoutInfo.totalItemsCount > 0) {
                            runCatching { gridState.scrollToItem(0) }
                        }
                    }
                }
                if (res.categories.isNotEmpty()) {
                    providerCategories = res.categories
                }
                if (res.genres.isNotEmpty()) {
                    availableGenres = res.genres
                } else if (availableGenres.isEmpty()) {
                    availableGenres = res.items.flatMap { it.genre.split(",", "/") }
                        .map { it.trim() }
                        .filter { it.length > 1 }
                        .distinct()
                        .take(30)
                }
                if (res.years.isNotEmpty()) {
                    availableYears = res.years
                } else if (availableYears.isEmpty()) {
                    availableYears = res.items.map { it.year.trim() }
                        .filter { it.matches(Regex("^\\d{4}$")) }
                        .distinct()
                        .sortedDescending()
                }
                nextOffset = if (res.nextOffset > 0) res.nextOffset else allItems.size
                hasMore = res.hasMore && res.items.isNotEmpty()
                totalCount = if (res.totalCount > allItems.size) res.totalCount else allItems.size
            }
        }
    }

    LaunchedEffect(vodUrl, vodLogin, vodPassword, vodKey, selectedCategory, selectedGenre, selectedYear, selectedSort, searchQuery) {
        if ((vodUrl.isNotBlank() || vodKey.isNotBlank()) && selectedCategory != "fav" && selectedCategory != "history") {
            triggerCatalogReload(vodUrl, vodLogin, vodPassword, vodKey, append = false)
        } else if (gridState.layoutInfo.totalItemsCount > 0) {
            runCatching { gridState.scrollToItem(0) }
        }
    }

    fun toggleFavorite(itemId: String) {
        val next = if (favorites.contains(itemId)) favorites - itemId else favorites + itemId
        favorites = next
        prefs.edit().putStringSet("vod_favorites_ids", next).apply()
    }

    fun recordEpisodeWatched(itemId: String, epIdx: Int) {
        if (epIdx < 0) return
        val watchedSet = (prefs.getStringSet("vod_watched_eps_$itemId", emptySet()) ?: emptySet()).toMutableSet()
        watchedSet.add(epIdx.toString())
        prefs.edit()
            .putInt("vod_last_ep_idx_$itemId", epIdx)
            .putStringSet("vod_watched_eps_$itemId", watchedSet)
            .apply()
    }

    LaunchedEffect(detailsItem?.id) {
        val current = detailsItem ?: run {
            isLoadingDetailsEpisodes = false
            return@LaunchedEffect
        }
        if (current.episodes.isEmpty() && (current.requestJson.isNotBlank() || current.cmd.isNotBlank())) {
            isLoadingDetailsEpisodes = true
            val resolved = resolveVodStreamAndEpisodes(
                webConfigUrl = prefs.getString("web_config_url", Playlist.DEFAULT_WEB_CONFIG_URL)
                    ?: Playlist.DEFAULT_WEB_CONFIG_URL,
                vodUrl = vodUrl,
                vodLogin = vodLogin,
                vodPassword = vodPassword,
                vodKey = vodKey,
                cmd = current.cmd,
                requestJson = current.requestJson,
            )
            isLoadingDetailsEpisodes = false
            if (resolved.episodes.isNotEmpty() || resolved.streamUrl.isNotBlank()) {
                val updated = current.copy(
                    streamUrl = resolved.streamUrl.ifBlank { current.streamUrl },
                    episodes = if (resolved.episodes.isNotEmpty()) resolved.episodes else current.episodes,
                )
                if (detailsItem?.id == current.id) {
                    detailsItem = updated
                }
                allItems = allItems.map { if (it.id == current.id) updated else it }
                savedItemsCache = savedItemsCache + (current.id to updated)
            }
        } else {
            isLoadingDetailsEpisodes = false
        }
    }

    fun recordHistoryAndPlay(
        item: VodMovieItem,
        streamUrl: String,
        displayTitle: String,
        epRequestJson: String = "",
        epIdx: Int = -1,
        resumeSec: Long = 0L,
    ) {
        if (streamUrl.isBlank()) {
            val reqJson = epRequestJson.ifBlank { item.requestJson }
            if (item.cmd.isNotBlank() || reqJson.isNotBlank()) {
                Toast.makeText(context, "Получение видеопотока с портала…", Toast.LENGTH_SHORT).show()
                scope.launch {
                    val resolved = resolveVodStreamAndEpisodes(
                        webConfigUrl = prefs.getString("web_config_url", Playlist.DEFAULT_WEB_CONFIG_URL)
                            ?: Playlist.DEFAULT_WEB_CONFIG_URL,
                        vodUrl = vodUrl,
                        vodLogin = vodLogin,
                        vodPassword = vodPassword,
                        vodKey = vodKey,
                        cmd = item.cmd,
                        requestJson = reqJson,
                    )
                    if (resolved.streamUrl.isNotBlank()) {
                        val updatedItem = if (item.episodes.isEmpty() && resolved.episodes.isNotEmpty()) {
                            val copy = item.copy(
                                episodes = resolved.episodes,
                                streamUrl = resolved.streamUrl.ifBlank { item.streamUrl },
                            )
                            allItems = allItems.map { if (it.id == item.id) copy else it }
                            savedItemsCache = savedItemsCache + (item.id to copy)
                            copy
                        } else if (epIdx in item.episodes.indices) {
                            val updatedEps = item.episodes.mapIndexed { idx, ep ->
                                if (idx == epIdx && ep.streamUrl.isBlank()) ep.copy(streamUrl = resolved.streamUrl) else ep
                            }
                            item.copy(episodes = updatedEps)
                        } else {
                            item
                        }
                        val effectiveEpIdx = when {
                            epIdx >= 0 -> epIdx
                            updatedItem.episodes.size > 1 -> 0
                            else -> -1
                        }
                        val nextHist = (listOf(updatedItem.id) + historyIds.filter { it != updatedItem.id }).take(60)
                        historyIds = nextHist
                        prefs.edit().putString("vod_history_csv", nextHist.joinToString(",")).apply()
                        recordEpisodeWatched(updatedItem.id, effectiveEpIdx)
                        detailsItem = null
                        playingItem = updatedItem
                        playingEpisodeIdx = effectiveEpIdx
                        playingResumeSec = resumeSec
                        playingUrl = resolved.streamUrl
                        playingTitle = displayTitle
                    } else {
                        Toast.makeText(context, "Портал не вернул прямую ссылку на поток", Toast.LENGTH_SHORT).show()
                    }
                }
                return
            }
            Toast.makeText(context, "Портал не вернул прямую ссылку на поток", Toast.LENGTH_SHORT).show()
            return
        }
        val nextHist = (listOf(item.id) + historyIds.filter { it != item.id }).take(60)
        historyIds = nextHist
        prefs.edit().putString("vod_history_csv", nextHist.joinToString(",")).apply()
        recordEpisodeWatched(item.id, epIdx)

        detailsItem = null
        playingItem = item
        playingEpisodeIdx = epIdx
        playingResumeSec = resumeSec
        playingUrl = streamUrl
        playingTitle = displayTitle
    }

    // Build category tabs (favorites & history moved to Top Bar; "Продолжить просмотр" excluded)
    val displayTotalCount = if (totalCount > allItems.size) totalCount else allItems.size
    val categoryTabs = remember(displayTotalCount, providerCategories) {
        buildList {
            add(VodCategoryTab("all", "Все ($displayTotalCount)"))
            add(VodCategoryTab("movies", "🎥 Фильмы"))
            add(VodCategoryTab("series", "📺 Сериалы"))
            add(VodCategoryTab("cartoons", "🧸 Мультфильмы"))
            add(VodCategoryTab("uhd", "💎 4K / FHD"))
            val seen = mutableSetOf("все", "фильмы", "сериалы", "мультфильмы", "продолжить просмотр")
            providerCategories.forEach { cat ->
                val low = cat.title.lowercase().trim()
                if (cat.id != "all" &&
                    cat.id != "10001" &&
                    !low.contains("продолжить просмотр") &&
                    !low.contains("история") &&
                    seen.add(low)
                ) {
                    add(VodCategoryTab("cat:${cat.id}", cat.title, cat.title))
                }
            }
        }
    }

    // Filter & sort items
    val filteredItems = remember(
        allItems,
        savedItemsCache,
        selectedCategory,
        selectedGenre,
        selectedYear,
        selectedSort,
        searchQuery,
        favorites,
        historyIds,
        categoryTabs,
    ) {
        val q = searchQuery.trim().lowercase()
        val basePool = when (selectedCategory) {
            "fav" -> favorites.mapNotNull { id -> savedItemsCache[id] ?: allItems.firstOrNull { it.id == id } }
            "history" -> historyIds.mapNotNull { id -> savedItemsCache[id] ?: allItems.firstOrNull { it.id == id } }
            else -> allItems
        }
        val filtered = basePool.filter { it ->
            val catMatch = when {
                selectedCategory == "all" -> true
                selectedCategory == "fav" -> favorites.contains(it.id)
                selectedCategory == "history" -> historyIds.contains(it.id)
                selectedCategory == "movies" -> {
                    val cLow = it.category.lowercase()
                    val gLow = it.genre.lowercase()
                    it.type != "series" && !cLow.contains("сериал") && !gLow.contains("мульт")
                }
                selectedCategory == "series" -> {
                    val cLow = it.category.lowercase()
                    it.type == "series" || cLow.contains("сериал") || it.episodes.isNotEmpty()
                }
                selectedCategory == "cartoons" -> {
                    val cLow = it.category.lowercase()
                    val gLow = it.genre.lowercase()
                    cLow.contains("мульт") || gLow.contains("мульт") || cLow.contains("детск") || gLow.contains("анимац")
                }
                selectedCategory == "uhd" -> {
                    val qLow = it.quality.lowercase()
                    val tLow = it.title.lowercase()
                    val cLow = it.category.lowercase()
                    qLow.contains("4k") || qLow.contains("uhd") || qLow.contains("fhd") || qLow.contains("1080") || tLow.contains("4k") || cLow.contains("4k")
                }
                selectedCategory.startsWith("cat:") -> {
                    val rawId = selectedCategory.removePrefix("cat:")
                    val targetTitle = categoryTabs.firstOrNull { t -> t.id == selectedCategory }?.rawTitle?.lowercase().orEmpty()
                    it.categoryId == rawId || (targetTitle.isNotEmpty() && it.category.lowercase() == targetTitle)
                }
                else -> true
            }
            if (!catMatch) return@filter false

            if (selectedGenre != "all" && !it.genre.lowercase().contains(selectedGenre.lowercase())) {
                return@filter false
            }
            if (selectedYear != "all" && it.year.trim() != selectedYear.trim()) {
                return@filter false
            }
            if (q.isNotEmpty()) {
                val hay = "${it.title} ${it.originalTitle} ${it.actors} ${it.director}".lowercase()
                if (!hay.contains(q)) return@filter false
            }
            true
        }

        val yearRegex = Regex("\\b(19\\d{2}|20\\d{2})\\b")
        fun parseRatingValue(item: VodMovieItem): Double {
            val candidates = listOf(item.rating, item.ratingKp, item.ratingImdb)
            var best = 0.0
            for (raw in candidates) {
                if (raw.isBlank()) continue
                val clean = raw.replace(',', '.').trim()
                val num = Regex("\\d+(?:\\.\\d+)?").find(clean)?.value?.toDoubleOrNull() ?: 0.0
                if (num > best) best = num
            }
            return best
        }
        fun parseYearValue(item: VodMovieItem): Int {
            val direct = item.year.trim().toIntOrNull()
            if (direct != null && direct in 1900..2099) return direct
            val fromYear = yearRegex.find(item.year)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (fromYear != null) return fromYear
            val fromTitle = yearRegex.find(item.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (fromTitle != null) return fromTitle
            return yearRegex.find(item.description)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        }

        when (selectedSort) {
            VodSortOption.RATING -> filtered.sortedWith(
                compareByDescending<VodMovieItem> { parseRatingValue(it) }
                    .thenByDescending { parseYearValue(it) }
            )
            VodSortOption.YEAR -> filtered.sortedWith(
                compareByDescending<VodMovieItem> { parseYearValue(it) }
                    .thenByDescending { parseRatingValue(it) }
            )
            VodSortOption.TITLE -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            VodSortOption.ADDED -> {
                if (filtered.any { it.addedAt > 0L }) {
                    filtered.sortedByDescending { it.addedAt }
                } else {
                    filtered
                }
            }
        }
    }

    // If fullscreen player is active, render it on top
    if (playingItem != null && playingUrl.isNotBlank()) {
        val activeItem = playingItem!!
        VodFullscreenPlayerOverlay(
            item = activeItem,
            episodeIdx = playingEpisodeIdx,
            initialSeekSec = playingResumeSec,
            streamUrl = playingUrl,
            displayTitle = playingTitle,
            isFavorite = favorites.contains(activeItem.id),
            prefs = prefs,
            onToggleFavorite = { toggleFavorite(activeItem.id) },
            onStepEpisode = { delta ->
                val curItem = playingItem ?: activeItem
                if (curItem.episodes.size > 1) {
                    val epCount = curItem.episodes.size
                    val curEp = if (playingEpisodeIdx in 0 until epCount) playingEpisodeIdx else 0
                    val nextEpIdx = (curEp + delta + epCount) % epCount
                    val ep = curItem.episodes[nextEpIdx]
                    val savedEpSec = prefs.getLong("vod_pos_${curItem.id}_ep_$nextEpIdx", 0L)
                    recordHistoryAndPlay(
                        item = curItem,
                        streamUrl = ep.streamUrl,
                        displayTitle = "${curItem.title} — ${ep.title}",
                        epRequestJson = ep.requestJson.ifBlank { curItem.requestJson },
                        epIdx = nextEpIdx,
                        resumeSec = savedEpSec,
                    )
                } else {
                    val pool = if (filteredItems.size > 1) filteredItems else allItems
                    if (pool.size > 1) {
                        val curMovieIdx = pool.indexOfFirst { it.id == curItem.id }.let { if (it >= 0) it else 0 }
                        val nextMovieIdx = (curMovieIdx + delta + pool.size) % pool.size
                        val nextMovie = pool[nextMovieIdx]
                        val savedMovieSec = prefs.getLong("vod_pos_${nextMovie.id}", 0L)
                        val url = nextMovie.streamUrl.ifBlank { nextMovie.episodes.firstOrNull()?.streamUrl.orEmpty() }
                        val reqJson = nextMovie.episodes.firstOrNull()?.requestJson?.ifBlank { nextMovie.requestJson }
                            ?: nextMovie.requestJson
                        recordHistoryAndPlay(
                            item = nextMovie,
                            streamUrl = url,
                            displayTitle = nextMovie.title,
                            epRequestJson = reqJson,
                            epIdx = if (nextMovie.episodes.size > 1) 0 else -1,
                            resumeSec = savedMovieSec,
                        )
                    }
                }
            },
            onClose = {
                playingItem = null
                playingEpisodeIdx = -1
                playingResumeSec = 0L
                playingUrl = ""
                playingTitle = ""
            },
        )
        return
    }

    BackHandler {
        if (detailsItem != null) {
            detailsItem = null
        } else {
            onBackClick()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(VodBg),
    ) {
        // ── Top Bar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(VodSurface1)
                .then(if (isPortrait) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = if (isPortrait) 10.dp else 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isPortrait) Arrangement.spacedBy(8.dp) else Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 6.dp else 10.dp),
            ) {
                VodFocusableActionChip(
                    label = "← ТВ",
                    isActive = false,
                    onClick = onBackClick,
                )
                val headerCount = if (selectedCategory == "fav" || selectedCategory == "history") {
                    filteredItems.size
                } else {
                    maxOf(filteredItems.size, totalCount)
                }
                VodFocusableActionChip(
                    label = "🎬 Медиатека ($headerCount)",
                    isActive = selectedCategory != "fav" && selectedCategory != "history",
                    onClick = {
                        selectedGenre = "all"
                        selectedYear = "all"
                        searchQuery = ""
                        selectedCategory = "all"
                    },
                )
                VodFocusableActionChip(
                    label = "⭐ Избранное (${favorites.size})",
                    isActive = selectedCategory == "fav",
                    onClick = {
                        selectedGenre = "all"
                        selectedYear = "all"
                        searchQuery = ""
                        selectedCategory = if (selectedCategory == "fav") "all" else "fav"
                    },
                )
                VodFocusableActionChip(
                    label = "🕒 История (${historyIds.size})",
                    isActive = selectedCategory == "history",
                    onClick = {
                        selectedGenre = "all"
                        selectedYear = "all"
                        searchQuery = ""
                        selectedCategory = if (selectedCategory == "history") "all" else "history"
                    },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VodFocusableActionChip(
                    label = if (searchQuery.isNotBlank()) "🔍 «$searchQuery» ✕" else "🔍 Поиск",
                    isActive = searchQuery.isNotBlank(),
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            searchQuery = ""
                        } else {
                            showSearchDialog = true
                        }
                    },
                )
                VodFocusableActionChip(
                    label = "⚙ Настройки",
                    isActive = false,
                    onClick = { showPortalDialog = true },
                )
            }
        }

        // ── Category & Filter Header Panel ─────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(VodSurface1.copy(alpha = 0.7f))
                .padding(horizontal = if (isPortrait) 10.dp else 14.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (isPortrait) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(categoryTabs) { tab ->
                        VodFocusableActionChip(
                            label = tab.title,
                            isActive = selectedCategory == tab.id,
                            onClick = {
                                selectedGenre = "all"
                                selectedYear = "all"
                                searchQuery = ""
                                selectedCategory = tab.id
                            },
                        )
                    }
                }
            } else {
                // Render category chips in 1-2 compact rows without horizontal scrolling
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categoryTabs.chunked(7).forEach { rowTabs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            rowTabs.forEach { tab ->
                                VodFocusableActionChip(
                                    label = tab.title,
                                    isActive = selectedCategory == tab.id,
                                    onClick = {
                                        selectedGenre = "all"
                                        selectedYear = "all"
                                        searchQuery = ""
                                        selectedCategory = tab.id
                                    },
                                )
                            }
                        }
                    }
                }
            }

            var showGenreDropdown by remember { mutableStateOf(false) }
            var showYearDropdown by remember { mutableStateOf(false) }
            var showSortDropdown by remember { mutableStateOf(false) }
            val hasActiveFilters = selectedGenre != "all" || selectedYear != "all" ||
                selectedSort != VodSortOption.ADDED || searchQuery.isNotBlank()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isPortrait) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    val genreDisplay = if (selectedGenre == "all") "Все" else selectedGenre
                    VodFocusableActionChip(
                        label = "🎭 Жанр: $genreDisplay ▾",
                        isActive = selectedGenre != "all" || showGenreDropdown,
                        onClick = { showGenreDropdown = true },
                    )
                    DropdownMenu(
                        expanded = showGenreDropdown,
                        onDismissRequest = { showGenreDropdown = false },
                        modifier = Modifier
                            .background(VodSurface1)
                            .border(1.5.dp, VodCyan, RoundedCornerShape(8.dp))
                            .heightIn(max = 320.dp),
                    ) {
                        val list = listOf("all") + availableGenres
                        list.forEach { g ->
                            val label = if (g == "all") "Все жанры" else g
                            val active = selectedGenre == g
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (active) "✓ $label" else label,
                                        color = if (active) VodCyan else VodTextPrim,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    )
                                },
                                onClick = {
                                    selectedGenre = g
                                    showGenreDropdown = false
                                },
                            )
                        }
                    }
                }

                Box {
                    val yearDisplay = if (selectedYear == "all") "Все" else selectedYear
                    VodFocusableActionChip(
                        label = "📅 Год: $yearDisplay ▾",
                        isActive = selectedYear != "all" || showYearDropdown,
                        onClick = { showYearDropdown = true },
                    )
                    DropdownMenu(
                        expanded = showYearDropdown,
                        onDismissRequest = { showYearDropdown = false },
                        modifier = Modifier
                            .background(VodSurface1)
                            .border(1.5.dp, VodCyan, RoundedCornerShape(8.dp))
                            .heightIn(max = 320.dp),
                    ) {
                        val list = listOf("all") + availableYears
                        list.forEach { y ->
                            val label = if (y == "all") "Все годы" else y
                            val active = selectedYear == y
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (active) "✓ $label" else label,
                                        color = if (active) VodCyan else VodTextPrim,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    )
                                },
                                onClick = {
                                    selectedYear = y
                                    showYearDropdown = false
                                },
                            )
                        }
                    }
                }

                VodFocusableActionChip(
                    label = "✕ Сбросить фильтр",
                    isActive = hasActiveFilters,
                    onClick = {
                        selectedGenre = "all"
                        selectedYear = "all"
                        selectedSort = VodSortOption.ADDED
                        searchQuery = ""
                        runCatching { firstPosterFocusRequester.requestFocus() }
                    },
                )

                if (!isPortrait) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Box {
                    VodFocusableActionChip(
                        label = "⇅ Сортировка: ${selectedSort.label} ▾",
                        isActive = showSortDropdown,
                        onClick = { showSortDropdown = true },
                    )
                    DropdownMenu(
                        expanded = showSortDropdown,
                        onDismissRequest = { showSortDropdown = false },
                        modifier = Modifier
                            .background(VodSurface1)
                            .border(1.5.dp, VodCyan, RoundedCornerShape(8.dp)),
                    ) {
                        VodSortOption.entries.forEach { opt ->
                            val active = selectedSort == opt
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (active) "✓ ${opt.label}" else opt.label,
                                        color = if (active) VodCyan else VodTextPrim,
                                        fontSize = 13.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    )
                                },
                                onClick = {
                                    selectedSort = opt
                                    showSortDropdown = false
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── Main Content Area ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = if (isPortrait) 10.dp else 14.dp, vertical = 4.dp),
        ) {
            when {
                !hasPortalConfig -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(VodSurface1)
                            .border(1.5.dp, VodCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                            .padding(if (isPortrait) 16.dp else 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("🎬", fontSize = 36.sp)
                        Text(
                            text = "Подключите портал Медиатеки (Фильмы и Сериалы)",
                            color = VodTextPrim,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "Введите параметры доступа (URL портала, Логин, Пароль, Ключ) прямо на телевизоре или отправьте их с телефона через сайт minogatvbox.duckdns.org по 4-значному коду:",
                            color = VodTextSec,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                        )
                        if (isPortrait) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                VodFocusableActionChip(
                                    label = "⚙ Ввести данные портала",
                                    isActive = true,
                                    onClick = { showPortalDialog = true },
                                )
                                VodFocusableActionChip(
                                    label = "🌐 Открыть Настройки (Код с сайта)",
                                    isActive = false,
                                    onClick = onOpenSettingsClick,
                                )
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                VodFocusableActionChip(
                                    label = "⚙ Ввести данные портала на ТВ",
                                    isActive = true,
                                    onClick = { showPortalDialog = true },
                                )
                                VodFocusableActionChip(
                                    label = "🌐 Открыть Настройки (Код с сайта)",
                                    isActive = false,
                                    onClick = onOpenSettingsClick,
                                )
                            }
                        }
                    }
                }

                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = VodCyan)
                        Text(
                            text = "Загрузка каталога фильмов и сериалов…",
                            color = VodTextPrim,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                errorMessage != null && allItems.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(VodSurface1)
                            .border(1.5.dp, VodDanger.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(if (isPortrait) 16.dp else 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("⚠️ Ошибка подключения к порталу", color = VodTextPrim, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(errorMessage.orEmpty(), color = Color(0xFFFCA5A5), fontSize = 13.sp, textAlign = TextAlign.Center)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            VodFocusableActionChip(
                                label = "⚙ Изменить параметры",
                                isActive = true,
                                onClick = { showPortalDialog = true },
                            )
                            VodFocusableActionChip(
                                label = "↻ Повторить",
                                isActive = false,
                                onClick = { triggerCatalogReload() },
                            )
                        }
                    }
                }

                filteredItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "По выбранным фильтрам фильмы не найдены",
                            color = VodTextSec,
                            fontSize = 15.sp,
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(if (isPortrait) 3 else 7),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(filteredItems, key = { _, movie -> movie.id }) { index, movie ->
                            if (index >= filteredItems.size - 14 &&
                                hasMore &&
                                !isLoading &&
                                !isLoadingMore &&
                                selectedCategory != "fav" &&
                                selectedCategory != "history"
                            ) {
                                LaunchedEffect(filteredItems.size, nextOffset) {
                                    triggerCatalogReload(append = true)
                                }
                            }
                            VodPosterCard(
                                item = movie,
                                isFavorite = favorites.contains(movie.id),
                                onClick = { detailsItem = movie },
                                onToggleFavorite = { toggleFavorite(movie.id) },
                                modifier = if (index == 0) Modifier.focusRequester(firstPosterFocusRequester) else Modifier,
                            )
                        }
                    }
                }
            }
        }

        // ── Bottom Remote Control Hint Bar ─────────────────────────────────
        if (!isPortrait) {
            RemoteControlHintBar(
                hints = listOf(
                    RemoteHint("◄▲▼►", "Навигация"),
                    RemoteHint("OK", "Карточка фильма / Смотреть"),
                    RemoteHint("🔍 Поиск", "Найти фильм", onClick = { showSearchDialog = true }),
                    RemoteHint("⚙ Портал", "Настройки доступа", onClick = { showPortalDialog = true }),
                    RemoteHint("Назад", "К папкам", onClick = onBackClick),
                ),
            )
        }
    }

    // ── Movie Details Modal ────────────────────────────────────────────────
    if (detailsItem != null) {
        val item = detailsItem!!
        val lastEpIdx = prefs.getInt("vod_last_ep_idx_${item.id}", -1)
        val watchedEps = prefs.getStringSet("vod_watched_eps_${item.id}", emptySet()) ?: emptySet()
        val savedSec = if (item.episodes.isNotEmpty() && lastEpIdx in item.episodes.indices) {
            prefs.getLong("vod_pos_${item.id}_ep_$lastEpIdx", 0L)
        } else {
            prefs.getLong("vod_pos_${item.id}", 0L)
        }
        VodDetailsDialog(
            item = item,
            isFavorite = favorites.contains(item.id),
            isLoadingEpisodes = isLoadingDetailsEpisodes,
            isPortrait = isPortrait,
            savedPositionSec = savedSec,
            lastWatchedEpisodeIdx = lastEpIdx,
            watchedEpisodeIndices = watchedEps,
            onResumePlayback = {
                if (item.episodes.isNotEmpty() && lastEpIdx in item.episodes.indices) {
                    val ep = item.episodes[lastEpIdx]
                    recordHistoryAndPlay(
                        item = item,
                        streamUrl = ep.streamUrl,
                        displayTitle = "${item.title} — ${ep.title}",
                        epRequestJson = ep.requestJson.ifBlank { item.requestJson },
                        epIdx = lastEpIdx,
                        resumeSec = savedSec,
                    )
                } else {
                    val ep0 = item.episodes.firstOrNull()
                    val url = item.streamUrl.ifBlank { ep0?.streamUrl.orEmpty() }
                    val reqJson = ep0?.requestJson?.ifBlank { item.requestJson } ?: item.requestJson
                    recordHistoryAndPlay(
                        item = item,
                        streamUrl = url,
                        displayTitle = if (ep0 != null && item.episodes.size > 1) "${item.title} — ${ep0.title}" else item.title,
                        epRequestJson = reqJson,
                        epIdx = if (item.episodes.size > 1) 0 else -1,
                        resumeSec = savedSec,
                    )
                }
            },
            onPlayMain = {
                val ep0 = item.episodes.firstOrNull()
                val url = item.streamUrl.ifBlank { ep0?.streamUrl.orEmpty() }
                val reqJson = ep0?.requestJson?.ifBlank { item.requestJson } ?: item.requestJson
                recordHistoryAndPlay(
                    item = item,
                    streamUrl = url,
                    displayTitle = if (ep0 != null && item.episodes.size > 1) "${item.title} — ${ep0.title}" else item.title,
                    epRequestJson = reqJson,
                    epIdx = if (item.episodes.size > 1) 0 else -1,
                    resumeSec = 0L,
                )
            },
            onPlayEpisode = { epIdx, ep ->
                val epSavedSec = prefs.getLong("vod_pos_${item.id}_ep_$epIdx", 0L)
                recordHistoryAndPlay(
                    item = item,
                    streamUrl = ep.streamUrl,
                    displayTitle = "${item.title} — ${ep.title}",
                    epRequestJson = ep.requestJson.ifBlank { item.requestJson },
                    epIdx = epIdx,
                    resumeSec = epSavedSec,
                )
            },
            onToggleFavorite = { toggleFavorite(item.id) },
            onDismiss = { detailsItem = null },
        )
    }

    // ── Portal Config Dialog ───────────────────────────────────────────────
    if (showPortalDialog) {
        VodPortalConfigDialog(
            initialUrl = vodUrl,
            initialLogin = vodLogin,
            initialPassword = vodPassword,
            initialKey = vodKey,
            onSave = { u, l, p, k ->
                vodUrl = u
                vodLogin = l
                vodPassword = p
                vodKey = k
                prefs.edit()
                    .putString("vod_portal_url", u)
                    .putString("vod_portal_login", l)
                    .putString("vod_portal_password", p)
                    .putString("vod_portal_key", k)
                    .apply()
                showPortalDialog = false
                triggerCatalogReload(u, l, p, k)
            },
            onDismiss = { showPortalDialog = false },
        )
    }

    // ── Search Dialog (Classic АБВГД / ABCDEF TV Keyboard) ─────────────────
    if (showSearchDialog) {
        var draftQuery by remember { mutableStateOf(searchQuery) }
        var kbLang by remember { mutableStateOf("ru") }
        val ruRows = remember {
            listOf(
                listOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "Й"),
                listOf("К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У"),
                listOf("Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э"),
                listOf("Ю", "Я", "0", "1", "2", "3", "4", "5", "6", "7"),
            )
        }
        val enRows = remember {
            listOf(
                listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
                listOf("K", "L", "M", "N", "O", "P", "Q", "R", "S", "T"),
                listOf("U", "V", "W", "X", "Y", "Z", "0", "1", "2", "3"),
                listOf("4", "5", "6", "7", "8", "9", "-", ".", "⌫", "✕"),
            )
        }
        val activeRows = if (kbLang == "ru") ruRows else enRows

        Dialog(
            onDismissRequest = { showSearchDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = if (isPortrait) 12.dp else 32.dp)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(VodSurface1)
                    .border(1.5.dp, VodCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(if (isPortrait) 14.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "🔍 Поиск фильма или сериала",
                        color = VodCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    )
                    VodFocusableActionChip(
                        label = "← Закрыть",
                        isActive = false,
                        onClick = { showSearchDialog = false },
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(VodBg)
                        .border(1.5.dp, VodCyan.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = if (draftQuery.isEmpty()) "Введите название фильма…" else "$draftQuery|",
                        color = if (draftQuery.isEmpty()) VodTextSec else VodTextPrim,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Classic АБВГД / ABCDEF rows
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeRows.forEach { rowKeys ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rowKeys.forEach { keyLabel ->
                                VodFocusableActionChip(
                                    label = keyLabel,
                                    isActive = false,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        when (keyLabel) {
                                            "⌫" -> if (draftQuery.isNotEmpty()) draftQuery = draftQuery.dropLast(1)
                                            "✕" -> draftQuery = ""
                                            else -> draftQuery += keyLabel.lowercase()
                                        }
                                    },
                                )
                            }
                        }
                    }

                    // Bottom bar: Language switch + Long Spacebar + Backspace/Clear + ENTER (Найти)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        VodFocusableActionChip(
                            label = if (kbLang == "ru") "🌐 RU → EN" else "🌐 EN → RU",
                            isActive = true,
                            modifier = Modifier.weight(2f),
                            onClick = { kbLang = if (kbLang == "ru") "en" else "ru" },
                        )
                        VodFocusableActionChip(
                            label = "␣ ПРОБЕЛ",
                            isActive = false,
                            modifier = Modifier.weight(4f),
                            onClick = { draftQuery += " " },
                        )
                        VodFocusableActionChip(
                            label = "⌫",
                            isActive = false,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (draftQuery.isNotEmpty()) draftQuery = draftQuery.dropLast(1)
                            },
                        )
                        VodFocusableActionChip(
                            label = "✕",
                            isActive = false,
                            modifier = Modifier.weight(1f),
                            onClick = { draftQuery = "" },
                        )
                        VodFocusableActionChip(
                            label = "↵ ENTER (НАЙТИ)",
                            isActive = true,
                            modifier = Modifier.weight(2.6f),
                            onClick = {
                                searchQuery = draftQuery.trim()
                                showSearchDialog = false
                            },
                        )
                    }
                }
            }
        }
    }
}

// ─── Focusable Chip & Poster Card Composables ─────────────────────────────────

@Composable
private fun VodFocusableActionChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavoriteGold: Boolean = false,
    isFocusedOverride: Boolean = false,
) {
    var isFocused by remember { mutableStateOf(false) }
    val effectiveFocused = isFocused || isFocusedOverride
    val scale by animateFloatAsState(if (effectiveFocused) 1.07f else 1.0f, tween(120), label = "vod_chip_scale")
    val accentColor = if (isFavoriteGold) VodGold else VodCyan

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    effectiveFocused -> VodGold
                    isActive && isFavoriteGold -> VodGold.copy(alpha = 0.24f)
                    isActive -> accentColor.copy(alpha = 0.22f)
                    else -> VodSurface2
                }
            )
            .border(
                width = if (effectiveFocused) 2.5.dp else if (isActive && isFavoriteGold) 1.8.dp else 1.dp,
                color = when {
                    effectiveFocused -> Color.White
                    isActive -> accentColor.copy(alpha = if (isFavoriteGold) 0.9f else 0.7f)
                    else -> Color(0x22FFFFFF)
                },
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                effectiveFocused -> Color.Black
                isActive && isFavoriteGold -> VodGold
                isActive -> VodCyan
                else -> VodTextPrim
            },
            fontSize = 12.sp,
            fontWeight = if (effectiveFocused) FontWeight.ExtraBold else if (isActive) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun VodPosterCard(
    item: VodMovieItem,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1.0f, tween(140), label = "poster_scale")
    val ratingStr = item.rating.ifBlank { item.ratingKp.ifBlank { item.ratingImdb } }
    val metaStr = listOfNotNull(
        item.year.takeIf { it.isNotBlank() },
        item.genre.split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: item.category.takeIf { it.isNotBlank() }
    ).joinToString(" • ").ifBlank { "Фильм" }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) VodSurface2 else VodSurface1)
            .border(
                width = if (isFocused) 2.2.dp else 1.dp,
                color = if (isFocused) VodCyan else Color(0x18FFFFFF),
                shape = RoundedCornerShape(10.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_PROG_RED) {
                    onToggleFavorite()
                    true
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF101726), Color(0xFF070A12))
                    )
                ),
        ) {
            if (item.poster.isNotBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("🎬", fontSize = 20.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.title,
                        color = VodTextSec,
                        fontSize = 10.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (ratingStr.isNotBlank() && ratingStr != "0") {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xE007090E))
                        .border(1.dp, VodGold.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "★ $ratingStr",
                        color = VodGold,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            val qBadge = item.quality.ifBlank { if (item.type == "series") "Сериал" else "HD" }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(VodCyan.copy(alpha = 0.9f))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = qBadge.uppercase(),
                    color = Color(0xFF041019),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xD907090E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⭐", fontSize = 9.sp)
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        ) {
            Text(
                text = item.title,
                color = VodTextPrim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metaStr,
                color = VodTextSec,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─── Movie / Series Details Dialog ────────────────────────────────────────────

@Composable
private fun VodDetailsDialog(
    item: VodMovieItem,
    isFavorite: Boolean,
    isLoadingEpisodes: Boolean = false,
    isPortrait: Boolean = false,
    savedPositionSec: Long,
    lastWatchedEpisodeIdx: Int,
    watchedEpisodeIndices: Set<String>,
    onResumePlayback: () -> Unit,
    onPlayMain: () -> Unit,
    onPlayEpisode: (Int, VodEpisodeItem) -> Unit,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
) {
    val showEpisodesSection = item.episodes.size > 1 ||
        (item.episodes.size == 1 && (item.type == "series" || item.category.contains("сериал", ignoreCase = true)))
    val hasResume = (showEpisodesSection && lastWatchedEpisodeIdx in item.episodes.indices) || savedPositionSec > 10L
    val resumeLabel = if (showEpisodesSection && lastWatchedEpisodeIdx in item.episodes.indices) {
        val epTitle = item.episodes[lastWatchedEpisodeIdx].title
        if (savedPositionSec > 10L) {
            "▶ Продолжить просмотр: $epTitle (${formatDurationSec(savedPositionSec)})"
        } else {
            "▶ Продолжить просмотр: $epTitle"
        }
    } else {
        "▶ Продолжить просмотр (${formatDurationSec(savedPositionSec)})"
    }
    val playStartLabel = if (hasResume) {
        "▶ Смотреть с начала"
    } else {
        "▶ Смотреть (${item.quality.ifBlank { "FullHD" }})"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(VodSurface1)
                .border(1.5.dp, VodCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Poster
                Box(
                    modifier = Modifier
                        .width(175.dp)
                        .height(255.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VodSurface2)
                        .border(1.dp, VodCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.poster.isNotBlank()) {
                        AsyncImage(
                            model = item.poster,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text("🎬", fontSize = 48.sp)
                    }
                }

                // Info column (fits inside window cleanly without outer vertical scrollbar)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text(
                        text = item.title,
                        color = VodTextPrim,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.originalTitle.isNotBlank()) {
                        Text(
                            text = item.originalTitle,
                            color = VodTextSec,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val kp = item.ratingKp.ifBlank { item.rating }
                        if (kp.isNotBlank() && kp != "0") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(VodGold.copy(alpha = 0.18f))
                                    .border(1.dp, VodGold, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text("★ Рейтинг: $kp", color = VodGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (item.ratingImdb.isNotBlank() && item.ratingImdb != "0") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(VodCyan.copy(alpha = 0.15f))
                                    .border(1.dp, VodCyan, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text("IMDb: ${item.ratingImdb}", color = VodCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        listOfNotNull(
                            item.year.takeIf { it.isNotBlank() }?.let { "📅 $it" },
                            item.country.takeIf { it.isNotBlank() }?.let { "🌍 $it" },
                            item.genre.takeIf { it.isNotBlank() }?.let { "🎭 $it" },
                            item.duration.takeIf { it.isNotBlank() }?.let { "⏱ $it" },
                        ).forEach { chip ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(VodSurface2)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(chip, color = VodTextPrim, fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }

                    Text(
                        text = item.description.ifBlank { "Описание фильма предоставлено порталом провайдера." },
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        maxLines = if (showEpisodesSection || isLoadingEpisodes) 2 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (item.director.isNotBlank()) {
                        Text(
                            text = "Режиссёр: ${item.director}",
                            color = VodTextSec,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.actors.isNotBlank()) {
                        Text(
                            text = "В ролях: ${item.actors}",
                            color = VodTextSec,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    val primaryActionFocusRequester = remember { FocusRequester() }
                    val firstEpisodeFocusRequester = remember { FocusRequester() }

                    LaunchedEffect(item.id) {
                        delay(80L)
                        runCatching { primaryActionFocusRequester.requestFocus() }
                    }

                    Spacer(Modifier.height(2.dp))

                    val downToEpisodesModifier = Modifier.onPreviewKeyEvent { ev ->
                        if (ev.type == KeyEventType.KeyDown &&
                            ev.key == Key.DirectionDown &&
                            showEpisodesSection
                        ) {
                            runCatching { firstEpisodeFocusRequester.requestFocus() }
                            true
                        } else {
                            false
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasResume) {
                            VodFocusableActionChip(
                                label = resumeLabel,
                                isActive = true,
                                onClick = onResumePlayback,
                                modifier = downToEpisodesModifier.focusRequester(primaryActionFocusRequester),
                            )
                        }
                        VodFocusableActionChip(
                            label = playStartLabel,
                            isActive = !hasResume,
                            onClick = onPlayMain,
                            modifier = if (!hasResume) {
                                downToEpisodesModifier.focusRequester(primaryActionFocusRequester)
                            } else {
                                downToEpisodesModifier
                            },
                        )
                        VodFocusableActionChip(
                            label = if (isFavorite) "★ В избранном" else "☆ В избранное",
                            isActive = isFavorite,
                            isFavoriteGold = isFavorite,
                            onClick = onToggleFavorite,
                            modifier = downToEpisodesModifier,
                        )
                        VodFocusableActionChip(
                            label = "← Закрыть",
                            isActive = false,
                            onClick = onDismiss,
                            modifier = downToEpisodesModifier,
                        )
                    }

                    if (isLoadingEpisodes && !showEpisodesSection) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "⏳ Загрузка списка серий с портала…",
                            color = VodCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    } else if (showEpisodesSection) {
                        val colsCount = if (isPortrait) 2 else 5
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "📺 Серии (${item.episodes.size}) — переходите ◄ ► ▲ ▼:",
                            color = VodCyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(colsCount),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 175.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(item.episodes.take(200), key = { _, ep -> ep.id }) { epIdx, ep ->
                                val isWatched = watchedEpisodeIndices.contains(epIdx.toString()) || epIdx == lastWatchedEpisodeIdx
                                val isLastWatched = epIdx == lastWatchedEpisodeIdx
                                val prefix = if (isWatched) "✓ " else "▶ "
                                val epModifier = Modifier
                                    .let { mod -> if (epIdx == 0) mod.focusRequester(firstEpisodeFocusRequester) else mod }
                                    .onPreviewKeyEvent { ev ->
                                        if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionUp && epIdx < colsCount) {
                                            runCatching { primaryActionFocusRequester.requestFocus() }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                VodFocusableActionChip(
                                    label = "$prefix${ep.title}",
                                    isActive = isLastWatched,
                                    onClick = { onPlayEpisode(epIdx, ep) },
                                    modifier = epModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Fullscreen VOD Video Player (Media3 ExoPlayer) ───────────────────────────

@OptIn(UnstableApi::class)
@Composable
private fun VodFullscreenPlayerOverlay(
    item: VodMovieItem,
    episodeIdx: Int,
    initialSeekSec: Long,
    streamUrl: String,
    displayTitle: String,
    isFavorite: Boolean,
    prefs: android.content.SharedPreferences,
    onToggleFavorite: () -> Unit,
    onStepEpisode: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val screenKeyDispatcher = LocalScreenKeyDispatcher.current
    val rootFocusRequester = remember { FocusRequester() }
    val currentOnToggleFavorite by rememberUpdatedState(onToggleFavorite)
    val currentOnStepEpisode by rememberUpdatedState(onStepEpisode)
    val currentOnClose by rememberUpdatedState(onClose)
    val currentItem by rememberUpdatedState(item)
    val currentEpisodeIdx by rememberUpdatedState(episodeIdx)
    val currentStreamUrl by rememberUpdatedState(streamUrl)

    var osdVisible by remember { mutableStateOf(true) }
    var osdInteractionTick by remember { mutableIntStateOf(0) }
    var focusedControlIndex by remember { mutableIntStateOf(2) } // 2 = Play/Pause
    var positionMs by remember { mutableLongStateOf(if (initialSeekSec > 5L) initialSeekSec * 1000L else 0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(true) }
    var pendingSeekMs by remember {
        mutableLongStateOf(if (initialSeekSec > 5L) initialSeekSec * 1000L else 0L)
    }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    fun saveProgressSec(sec: Long) {
        if (sec <= 10L) return
        val activeItemId = currentItem.id
        val activeEpIdx = currentEpisodeIdx
        val editor = prefs.edit().putLong("vod_pos_$activeItemId", sec)
        if (activeEpIdx >= 0) {
            editor.putLong("vod_pos_${activeItemId}_ep_$activeEpIdx", sec)
            editor.putInt("vod_last_ep_idx_$activeItemId", activeEpIdx)
        }
        editor.apply()
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    LaunchedEffect(streamUrl, item.id, episodeIdx) {
        if (streamUrl.isNotBlank()) {
            val seekMs = if (initialSeekSec > 5L) initialSeekSec * 1000L else 0L
            pendingSeekMs = seekMs
            positionMs = seekMs
            durationMs = 0L
            isPlaying = true
            osdVisible = true
            osdInteractionTick++
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
            exoPlayer.prepare()
            if (seekMs > 0L) {
                exoPlayer.seekTo(seekMs)
            }
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        }
    }

    DisposableEffect(exoPlayer, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && pendingSeekMs > 0L) {
                    val targetMs = pendingSeekMs
                    pendingSeekMs = 0L
                    exoPlayer.seekTo(targetMs)
                    positionMs = targetMs
                }
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    val posSec = exoPlayer.currentPosition / 1000L
                    saveProgressSec(posSec)
                    pendingSeekMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                    exoPlayer.stop()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if ( (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.playbackState == Player.STATE_ENDED) &&
                        currentStreamUrl.isNotBlank()
                    ) {
                        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(currentStreamUrl)))
                        exoPlayer.prepare()
                        if (pendingSeekMs > 0L) {
                            exoPlayer.seekTo(pendingSeekMs)
                        }
                        exoPlayer.play()
                    }
                    runCatching { rootFocusRequester.requestFocus() }
                }
                else -> Unit
            }
        }
        exoPlayer.addListener(listener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            val posSec = exoPlayer.currentPosition / 1000L
            saveProgressSec(posSec)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (pendingSeekMs <= 0L) {
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            }
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (positionMs > 12_000L && pendingSeekMs <= 0L) {
                saveProgressSec(positionMs / 1000L)
            }
            delay(1000L)
        }
    }

    LaunchedEffect(osdVisible, osdInteractionTick) {
        if (osdVisible) {
            delay(5000L)
            osdVisible = false
        }
    }

    val triggerControlAction: (Int) -> Unit = { idx ->
        focusedControlIndex = idx.coerceIn(0, 7)
        osdVisible = true
        osdInteractionTick++
        when (idx) {
            0 -> currentOnStepEpisode(-1)
            1 -> {
                val next = (exoPlayer.currentPosition - 30_000L).coerceAtLeast(0L)
                exoPlayer.seekTo(next)
                positionMs = next
            }
            2 -> {
                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
            }
            3 -> {
                val dur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                val next = (exoPlayer.currentPosition + 30_000L).coerceAtMost(dur)
                exoPlayer.seekTo(next)
                positionMs = next
            }
            4 -> currentOnStepEpisode(1)
            5 -> currentOnToggleFavorite()
            6 -> {
                resizeMode = when (resizeMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
            7 -> currentOnClose()
        }
    }

    val handleVodPlayerKey by rememberUpdatedState { nativeEvent: KeyEvent ->
        when (nativeEvent.action) {
            KeyEvent.ACTION_DOWN -> {
                when (nativeEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (!osdVisible) {
                            osdVisible = true
                            focusedControlIndex = 2
                            osdInteractionTick++
                        } else {
                            triggerControlAction(focusedControlIndex)
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        osdInteractionTick++
                        if (osdVisible) {
                            focusedControlIndex = if (focusedControlIndex - 1 < 0) 7 else focusedControlIndex - 1
                        } else {
                            osdVisible = true
                            focusedControlIndex = 1
                            val next = (exoPlayer.currentPosition - 30_000L).coerceAtLeast(0L)
                            exoPlayer.seekTo(next)
                            positionMs = next
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        osdInteractionTick++
                        if (osdVisible) {
                            focusedControlIndex = (focusedControlIndex + 1) % 8
                        } else {
                            osdVisible = true
                            focusedControlIndex = 3
                            val dur = exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                            val next = (exoPlayer.currentPosition + 30_000L).coerceAtMost(dur)
                            exoPlayer.seekTo(next)
                            positionMs = next
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        osdInteractionTick++
                        if (!osdVisible) {
                            osdVisible = true
                            focusedControlIndex = 2
                        }
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        triggerControlAction(2)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND,
                    KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                        triggerControlAction(1)
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                        triggerControlAction(3)
                        true
                    }
                    KeyEvent.KEYCODE_PAGE_UP,
                    KeyEvent.KEYCODE_CHANNEL_UP -> {
                        triggerControlAction(0)
                        true
                    }
                    KeyEvent.KEYCODE_PAGE_DOWN,
                    KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                        triggerControlAction(4)
                        true
                    }
                    KeyEvent.KEYCODE_PROG_RED -> {
                        triggerControlAction(5)
                        true
                    }
                    KeyEvent.KEYCODE_BACK,
                    KeyEvent.KEYCODE_ESCAPE -> true
                    else -> false
                }
            }
            KeyEvent.ACTION_UP -> {
                if (nativeEvent.keyCode == KeyEvent.KEYCODE_BACK || nativeEvent.keyCode == KeyEvent.KEYCODE_ESCAPE) {
                    if (!nativeEvent.isCanceled) {
                        currentOnClose()
                    }
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    DisposableEffect(screenKeyDispatcher, lifecycleOwner) {
        val previousHandler = screenKeyDispatcher?.activeHandler
        val handler: (KeyEvent) -> Boolean = { ev -> handleVodPlayerKey(ev) }
        screenKeyDispatcher?.activeHandler = handler
        val resumeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                screenKeyDispatcher?.activeHandler = handler
            }
        }
        lifecycleOwner.lifecycle.addObserver(resumeObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(resumeObserver)
            if (screenKeyDispatcher?.activeHandler === handler) {
                screenKeyDispatcher.activeHandler = previousHandler
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(50L)
        runCatching { rootFocusRequester.requestFocus() }
    }

    BackHandler {
        currentOnClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                handleVodPlayerKey(ev.nativeKeyEvent)
            }
            .clickable {
                osdVisible = !osdVisible
                if (osdVisible) {
                    focusedControlIndex = 2
                    osdInteractionTick++
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
                view.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (osdVisible) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xCC07090E), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    VodFocusableActionChip(
                        label = "← Назад",
                        isActive = false,
                        onClick = currentOnClose,
                    )
                    Text(
                        text = displayTitle,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(VodCyan.copy(alpha = 0.2f))
                        .border(1.dp, VodCyan, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "🎬 КИНОЗАЛ • ${item.quality.ifBlank { "HD" }}",
                        color = VodCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Bottom progress & interactive player controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xE607090E))
                        )
                    )
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val curSec = positionMs / 1000L
                val durSec = durationMs / 1000L
                val timeLabel = if (durSec > 0L) {
                    "${formatDurationSec(curSec)} / ${formatDurationSec(durSec)}"
                } else {
                    formatDurationSec(curSec)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (isPlaying) "▶ Воспроизведение" else "⏸ Пауза",
                        color = VodCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = timeLabel,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                val progress = if (durationMs > 0L && pendingSeekMs <= 0L) {
                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = VodCyan,
                    trackColor = Color(0x33FFFFFF),
                )

                // Interactive Control Bar: Prev, -30s, Play/Pause, +30s, Next, Favorite, Aspect, Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VodFocusableActionChip(
                        label = if (item.episodes.size > 1) "⏮ Пред. серия" else "⏮ Пред.",
                        isActive = false,
                        isFocusedOverride = focusedControlIndex == 0,
                        onClick = { triggerControlAction(0) },
                    )
                    VodFocusableActionChip(
                        label = "⏪ -30с",
                        isActive = false,
                        isFocusedOverride = focusedControlIndex == 1,
                        onClick = { triggerControlAction(1) },
                    )
                    VodFocusableActionChip(
                        label = if (isPlaying) "⏸ Пауза" else "▶ Пуск",
                        isActive = true,
                        isFocusedOverride = focusedControlIndex == 2,
                        onClick = { triggerControlAction(2) },
                    )
                    VodFocusableActionChip(
                        label = "⏩ +30с",
                        isActive = false,
                        isFocusedOverride = focusedControlIndex == 3,
                        onClick = { triggerControlAction(3) },
                    )
                    VodFocusableActionChip(
                        label = if (item.episodes.size > 1) "⏭ След. серия" else "⏭ След.",
                        isActive = false,
                        isFocusedOverride = focusedControlIndex == 4,
                        onClick = { triggerControlAction(4) },
                    )
                    VodFocusableActionChip(
                        label = if (isFavorite) "★ В избранном" else "☆ В избранное",
                        isActive = isFavorite,
                        isFavoriteGold = isFavorite,
                        isFocusedOverride = focusedControlIndex == 5,
                        onClick = { triggerControlAction(5) },
                    )
                    val resizeLabel = when (resizeMode) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "📐 Увеличение"
                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "📐 Растянуть"
                        else -> "📐 Авто"
                    }
                    VodFocusableActionChip(
                        label = resizeLabel,
                        isActive = false,
                        isFocusedOverride = focusedControlIndex == 6,
                        onClick = { triggerControlAction(6) },
                    )
                    VodFocusableActionChip(
                        label = "✕ Закрыть",
                        isActive = false,
                        isFocusedOverride = focusedControlIndex == 7,
                        onClick = { triggerControlAction(7) },
                    )
                }
            }
        }
    }
}

private fun formatDurationSec(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0L)
    val hh = s / 3600L
    val mm = (s % 3600L) / 60L
    val ss = s % 60L
    return if (hh > 0L) {
        String.format("%02d:%02d:%02d", hh, mm, ss)
    } else {
        String.format("%02d:%02d", mm, ss)
    }
}

// ─── Network Fetcher via Unified /api/tv/vod Endpoint ─────────────────────────

private val VOD_PORTAL_STRING_REGEX = Regex("^(?:[a-z]+:/+)?portal::(?:\\[([^\\]]*)\\])?\\s*(.*)$", RegexOption.IGNORE_CASE)
private val VOD_HEX_SUBDOMAIN_REGEX = Regex("^https?://([0-9a-f]{8,20})\\.([a-z0-9.-]+\\.[a-z]{2,})(?:/.*)?$", RegexOption.IGNORE_CASE)

private fun normalizeVodPortalParams(rawUrl: String, rawKey: String): Pair<String, String> {
    var u = rawUrl.replace("\\/", "/").trim()
    var k = rawKey.replace("\\/", "/").trim()
    val mUrl = VOD_PORTAL_STRING_REGEX.find(u)
    if (mUrl != null) {
        val bracket = mUrl.groupValues.getOrNull(1).orEmpty()
        u = mUrl.groupValues.getOrNull(2).orEmpty().trim()
        bracket.split(";").forEach { part ->
            val p = part.trim()
            if (p.startsWith("key:", ignoreCase = true)) {
                val ek = p.substring(4).trim()
                if (ek.isNotEmpty()) k = ek
            }
        }
    } else {
        val mKey = VOD_PORTAL_STRING_REGEX.find(k)
        if (mKey != null) {
            val bracket = mKey.groupValues.getOrNull(1).orEmpty()
            val eu = mKey.groupValues.getOrNull(2).orEmpty().trim()
            if (eu.isNotEmpty()) u = eu
            bracket.split(";").forEach { part ->
                val p = part.trim()
                if (p.startsWith("key:", ignoreCase = true)) {
                    val ek = p.substring(4).trim()
                    if (ek.isNotEmpty()) k = ek
                }
            }
        }
    }
    if (u.isNotEmpty()) {
        while (u.matches(Regex("^[a-z]{3,7}:/+[a-z]{3,7}:/+.*", RegexOption.IGNORE_CASE))) {
            u = u.replaceFirst(Regex("^[a-z]{3,7}:/+", RegexOption.IGNORE_CASE), "")
        }
        u = when {
            u.matches(Regex("^h+t+p+s:/+.*", RegexOption.IGNORE_CASE)) ->
                u.replaceFirst(Regex("^h+t+p+s:/+", RegexOption.IGNORE_CASE), "https://")
            u.matches(Regex("^h+t+p+:/+.*", RegexOption.IGNORE_CASE)) ->
                u.replaceFirst(Regex("^h+t+p+:/+", RegexOption.IGNORE_CASE), "http://")
            !u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true) -> {
                val trimmed = u.trimStart('/')
                if (trimmed.contains("/api/v1", ignoreCase = true)) "http://$trimmed" else "https://$trimmed"
            }
            else -> u
        }
        val subMatch = VOD_HEX_SUBDOMAIN_REGEX.find(u)
        if (subMatch != null && k.isEmpty()) {
            val extractedKey = subMatch.groupValues.getOrNull(1).orEmpty().trim()
            if (extractedKey.isNotEmpty()) k = extractedKey
        }
    }
    return u to k
}

private suspend fun fetchVodCatalog(
    webConfigUrl: String,
    vodUrl: String,
    vodLogin: String,
    vodPassword: String,
    vodKey: String,
    category: String = "all",
    genre: String = "all",
    year: String = "all",
    sort: String = "added",
    search: String = "",
    offset: Int = 0,
): VodCatalogResponse = withContext(Dispatchers.IO) {
    val rawBase = webConfigUrl.trim().ifBlank { Playlist.DEFAULT_WEB_CONFIG_URL }.trimEnd('/')
    val candidateBases = buildList {
        if (rawBase.startsWith("http://", ignoreCase = true)) {
            add("https://" + rawBase.removePrefix("http://"))
            add(rawBase)
        } else if (rawBase.startsWith("https://", ignoreCase = true)) {
            add(rawBase)
            add("http://" + rawBase.removePrefix("https://"))
        } else {
            add("https://$rawBase")
            add("http://$rawBase")
        }
        add("https://minogatvbox.duckdns.org")
        add("http://10.0.2.2:8080")
    }.distinct()

    val (cleanVodUrl, cleanVodKey) = normalizeVodPortalParams(vodUrl, vodKey)

    val payload = JSONObject().apply {
        put("url", cleanVodUrl)
        put("vodUrl", cleanVodUrl)
        put("login", vodLogin.trim())
        put("vodLogin", vodLogin.trim())
        put("password", vodPassword.trim())
        put("vodPassword", vodPassword.trim())
        put("key", cleanVodKey)
        put("vodKey", cleanVodKey)
        put("category", category.trim().ifBlank { "all" })
        put("genre", genre.trim().ifBlank { "all" })
        put("year", year.trim().ifBlank { "all" })
        put("sort", sort.trim().ifBlank { "added" })
        put("search", search.trim())
        put("offset", offset.coerceAtLeast(0))
    }.toString().replace("\\/", "/")

    var lastError: String? = null
    for (base in candidateBases) {
        try {
            val endpoint = URL("$base/api/tv/vod")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            conn.disconnect()

            if (body.startsWith("{")) {
                val parsed = parseVodCatalogJson(body)
                if (parsed.ok || !parsed.error.isNullOrBlank()) {
                    return@withContext parsed
                }
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: e.message
        }
    }

    VodCatalogResponse(
        ok = false,
        error = lastError ?: "Не удалось связаться с сервером Медиатеки",
    )
}

private data class VodStreamResolution(
    val streamUrl: String = "",
    val episodes: List<VodEpisodeItem> = emptyList(),
)

private suspend fun resolveVodStreamAndEpisodes(
    webConfigUrl: String,
    vodUrl: String,
    vodLogin: String,
    vodPassword: String,
    vodKey: String,
    cmd: String,
    requestJson: String,
): VodStreamResolution = withContext(Dispatchers.IO) {
    val rawBase = webConfigUrl.trim().ifBlank { Playlist.DEFAULT_WEB_CONFIG_URL }.trimEnd('/')
    val candidateBases = buildList {
        if (rawBase.startsWith("http://", ignoreCase = true)) {
            add("https://" + rawBase.removePrefix("http://"))
            add(rawBase)
        } else if (rawBase.startsWith("https://", ignoreCase = true)) {
            add(rawBase)
            add("http://" + rawBase.removePrefix("https://"))
        } else {
            add("https://$rawBase")
            add("http://$rawBase")
        }
        add("https://minogatvbox.duckdns.org")
        add("http://10.0.2.2:8080")
    }.distinct()

    val (cleanVodUrl, cleanVodKey) = normalizeVodPortalParams(vodUrl, vodKey)

    val payload = JSONObject().apply {
        put("action", "stream")
        put("url", cleanVodUrl)
        put("vodUrl", cleanVodUrl)
        put("login", vodLogin.trim())
        put("password", vodPassword.trim())
        put("key", cleanVodKey)
        put("vodKey", cleanVodKey)
        put("cmd", cmd)
        put("requestJson", requestJson)
    }.toString().replace("\\/", "/")

    for (base in candidateBases) {
        try {
            val endpoint = URL("$base/api/tv/vod")
            val conn = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 14_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            conn.disconnect()
            if (body.startsWith("{")) {
                val obj = JSONObject(body)
                val resolvedUrl = obj.optString("streamUrl").ifBlank { obj.optString("url") }.trim()
                val epsArr = obj.optJSONArray("episodes") ?: JSONArray()
                val episodes = buildList {
                    for (j in 0 until epsArr.length()) {
                        val ep = epsArr.optJSONObject(j) ?: continue
                        val epStreamUrl = ep.optString("streamUrl").trim()
                        val epReq = ep.optString("requestJson").trim()
                        if (epStreamUrl.isNotEmpty() || epReq.isNotEmpty()) {
                            add(
                                VodEpisodeItem(
                                    id = ep.optString("id", "ep_${j + 1}"),
                                    title = ep.optString("title", "Серия ${j + 1}"),
                                    season = ep.optInt("season", 1),
                                    episode = ep.optInt("episode", j + 1),
                                    streamUrl = epStreamUrl,
                                    requestJson = epReq,
                                )
                            )
                        }
                    }
                }
                if (resolvedUrl.isNotEmpty() || episodes.isNotEmpty()) {
                    return@withContext VodStreamResolution(
                        streamUrl = resolvedUrl.ifBlank { episodes.firstOrNull()?.streamUrl.orEmpty() },
                        episodes = episodes,
                    )
                }
            }
        } catch (_: Exception) {
        }
    }
    VodStreamResolution()
}

private fun parseVodCatalogJson(rawJson: String): VodCatalogResponse {
    return try {
        val root = JSONObject(rawJson)
        val ok = root.optBoolean("ok", false) || root.optString("status").equals("OK", ignoreCase = true)
        if (!ok) {
            return VodCatalogResponse(
                ok = false,
                error = root.optString("error", "Ошибка загрузки каталога с портала"),
            )
        }

        val catsArr = root.optJSONArray("categories") ?: JSONArray()
        val categories = buildList {
            for (i in 0 until catsArr.length()) {
                val c = catsArr.optJSONObject(i) ?: continue
                val id = c.optString("id").trim()
                val title = c.optString("title").trim()
                val reqJson = c.optString("requestJson").trim()
                if (id.isNotEmpty() && title.isNotEmpty()) {
                    add(VodCategoryTab(id = id, title = title, rawTitle = title, requestJson = reqJson))
                }
            }
        }

        val genresArr = root.optJSONArray("genres") ?: JSONArray()
        val genres = buildList {
            for (i in 0 until genresArr.length()) {
                val g = genresArr.optString(i).trim()
                if (g.isNotEmpty()) add(g)
            }
        }

        val yearsArr = root.optJSONArray("years") ?: JSONArray()
        val years = buildList {
            for (i in 0 until yearsArr.length()) {
                val y = yearsArr.optString(i).trim()
                if (y.isNotEmpty()) add(y)
            }
        }

        val itemsArr = root.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (i in 0 until itemsArr.length()) {
                val obj = itemsArr.optJSONObject(i) ?: continue
                val epsArr = obj.optJSONArray("episodes") ?: JSONArray()
                val episodes = buildList {
                    for (j in 0 until epsArr.length()) {
                        val ep = epsArr.optJSONObject(j) ?: continue
                        val streamUrl = ep.optString("streamUrl").trim()
                        val epReq = ep.optString("requestJson").trim()
                        if (streamUrl.isNotEmpty() || epReq.isNotEmpty()) {
                            add(
                                VodEpisodeItem(
                                    id = ep.optString("id", "ep_${j + 1}"),
                                    title = ep.optString("title", "Серия ${j + 1}"),
                                    season = ep.optInt("season", 1),
                                    episode = ep.optInt("episode", j + 1),
                                    streamUrl = streamUrl,
                                    requestJson = epReq,
                                )
                            )
                        }
                    }
                }
                add(
                    VodMovieItem(
                        id = obj.optString("id", "vod_${i + 1}"),
                        title = obj.optString("title", "Фильм"),
                        originalTitle = obj.optString("originalTitle", ""),
                        year = obj.optString("year", ""),
                        country = obj.optString("country", ""),
                        genre = obj.optString("genre", ""),
                        category = obj.optString("category", ""),
                        categoryId = obj.optString("categoryId", ""),
                        rating = obj.optString("rating", ""),
                        ratingKp = obj.optString("ratingKp", ""),
                        ratingImdb = obj.optString("ratingImdb", ""),
                        quality = obj.optString("quality", "HD"),
                        duration = obj.optString("duration", ""),
                        age = obj.optString("age", ""),
                        description = obj.optString("description", ""),
                        director = obj.optString("director", ""),
                        actors = obj.optString("actors", ""),
                        poster = obj.optString("poster", ""),
                        streamUrl = obj.optString("streamUrl", ""),
                        cmd = obj.optString("cmd", ""),
                        requestJson = obj.optString("requestJson", ""),
                        type = obj.optString("type", "movie"),
                        addedAt = obj.optLong("addedAt", 0L),
                        episodes = episodes,
                    )
                )
            }
        }

        VodCatalogResponse(
            ok = true,
            categories = categories,
            genres = genres,
            years = years,
            items = items,
            totalCount = root.optInt("totalCount", items.size).coerceAtLeast(items.size),
            nextOffset = root.optInt("nextOffset", items.size),
            hasMore = root.optBoolean("hasMore", false),
        )
    } catch (e: Exception) {
        VodCatalogResponse(
            ok = false,
            error = "Ошибка разбора ответа портала: ${e.localizedMessage ?: e.message}",
        )
    }
}

