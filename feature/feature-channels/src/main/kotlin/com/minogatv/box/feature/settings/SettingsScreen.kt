package com.minogatv.box.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.gestures.animateScrollBy
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import com.minogatv.box.core.model.enums.CatchupType
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.minogatv.box.feature.update.AppUpdateManager
import com.minogatv.box.feature.update.AppUpdateState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.minogatv.box.feature.ui.RemoteControlHintBar
import com.minogatv.box.feature.ui.RemoteHint
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.minogatv.box.core.model.Playlist
import com.minogatv.box.core.model.WebPairingStatus
import com.minogatv.box.core.model.enums.PlaylistType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

// ─── Design tokens ────────────────────────────────────────────────────────────

private val Bg         = Color(0xFF0B0D14)
private val Surface1   = Color(0xFF111318)
private val Surface2   = Color(0xFF1C1F2E)
private val Accent     = Color(0xFF4F8EF7)
private val TextPrim   = Color(0xFFE8E8F0)
private val TextSec    = Color(0xFF8888A8)
private val DangerRed  = Color(0xFFE53935)

// ─── Focus & TV Navigation Helpers ──────────────────────────────────────────

private val FocusGold  = Color(0xFFFBBF24)
private val FocusBg    = Color(0xFF1E3A5F)

@Composable
private fun SettingRowContainer(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.(isFocused: Boolean) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f, animationSpec = tween(150), label = "row_scale")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) FocusBg else Surface1)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusGold else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFocused) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FocusGold)
            )
            Spacer(Modifier.width(10.dp))
        }
        content(isFocused)
    }
}

@Composable
private fun SettingCardContainer(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.(isFocused: Boolean) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f, animationSpec = tween(150), label = "card_scale")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) FocusBg else Surface1)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusGold else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        content(isFocused)
    }
}

@Composable
private fun FocusableSettingButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.06f else 1.0f, animationSpec = tween(150), label = "btn_scale")

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = when {
                isFocused -> FocusGold
                isSelected -> Color(0xFF00E5FF)
                else -> Surface2
            },
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 1.dp,
                color = when {
                    isFocused -> Color.White
                    isSelected -> Color(0xFF00E5FF)
                    else -> Color(0x33FFFFFF)
                },
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        Text(
            text = text,
            color = if (isFocused || isSelected) Color.Black else TextPrim,
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.ExtraBold else if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun FocusableIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.2f else 1.0f, animationSpec = tween(150), label = "icon_btn_scale")

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(38.dp)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                color = if (isFocused) FocusGold else Color.Transparent,
                shape = CircleShape,
            )
            .border(
                width = if (isFocused) 2.5.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = CircleShape,
            ),
    ) {
        Icon(icon, contentDescription, tint = if (isFocused) Color.Black else tint, modifier = Modifier.size(18.dp))
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

/**
 * # SettingsScreen
 *
 * Entry point for all app configuration:
 * - **Playlists** — add / edit / delete / manual refresh
 * - (future) Player, EPG, Parental lock, Backup
 *
 * @param onBack Called when the user presses Back / the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenVodLibrary: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val pairingState by viewModel.webPairingState.collectAsState()
    val updateState by AppUpdateManager.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = remember { context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE) }

    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdates(context, force = false)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AppUpdateManager.onResumeCheckPendingInstall(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var autoStartOnBoot by remember { mutableStateOf(prefs.getBoolean("auto_start_on_boot", false)) }
    var autoPlayLast by remember { mutableStateOf(prefs.getBoolean("auto_play_last_channel", false)) }
    var channelPreviewEnabled by remember { mutableStateOf(prefs.getBoolean("channel_preview_enabled", true)) }
    var forcedCatchupType by remember {
        mutableStateOf(
            try {
                val name = prefs.getString("forced_catchup_type", "AUTO") ?: "AUTO"
                CatchupType.valueOf(name)
            } catch (_: Exception) {
                CatchupType.AUTO
            }
        )
    }
    var customEpgUrl by remember { mutableStateOf(prefs.getString("custom_epg_url", "") ?: "") }
    val webConfigUrl = remember {
        prefs.getString("web_config_url", Playlist.DEFAULT_WEB_CONFIG_URL)
            ?.takeIf { it.isNotBlank() } ?: Playlist.DEFAULT_WEB_CONFIG_URL
    }
    var autoEpgSync  by remember { mutableStateOf(prefs.getBoolean("auto_epg_sync", true)) }
    var showEpgDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var currentPin by remember { mutableStateOf(prefs.getString("parental_pin", "0000") ?: "0000") }
    var seekStepSeconds by remember { mutableStateOf(prefs.getInt("seek_step_seconds", 10)) }
    var pipEnabled by remember { mutableStateOf(prefs.getBoolean("pip_enabled", true)) }
    var touchGesturesEnabled by remember { mutableStateOf(prefs.getBoolean("touch_gestures_enabled", true)) }
    var uiScalePercent by remember { mutableStateOf(prefs.getInt("ui_scale_percent", 40)) }

    var showAddDialog      by remember { mutableStateOf(false) }
    var editingPlaylist    by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget       by remember { mutableStateOf<Playlist?>(null) }

    var vodPortalUrl by remember { mutableStateOf(prefs.getString("vod_portal_url", "") ?: "") }
    var vodPortalLogin by remember { mutableStateOf(prefs.getString("vod_portal_login", "") ?: "") }
    var vodPortalPassword by remember { mutableStateOf(prefs.getString("vod_portal_password", "") ?: "") }
    var vodPortalKey by remember { mutableStateOf(prefs.getString("vod_portal_key", "") ?: "") }
    var showVodConfigDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT || configuration.screenWidthDp < 600

    BackHandler(
        enabled = !showAddDialog && editingPlaylist == null && deleteTarget == null && !showEpgDialog && !showPinChangeDialog && !showVodConfigDialog
    ) {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg),
    ) {

        // ── Top bar ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface1)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Accent)
            }
            Text(
                text = "Настройки",
                color = TextPrim,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }

        // D-pad scrolling: LazyColumn only composes visible items, so focus search can't find
        // the next focusable item off-screen and the key press is lost. Scroll manually and retry.
        val settingsListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val settingsScope = androidx.compose.runtime.rememberCoroutineScope()
        val settingsFocusManager = androidx.compose.ui.platform.LocalFocusManager.current
        val scrollStepPx = with(androidx.compose.ui.platform.LocalDensity.current) { 160.dp.toPx() }

        LazyColumn(
            state = settingsListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = if (isPortrait) 10.dp else 16.dp)
                .onPreviewKeyEvent { event ->
                    val down = event.key == androidx.compose.ui.input.key.Key.DirectionDown
                    val up = event.key == androidx.compose.ui.input.key.Key.DirectionUp
                    if ((!down && !up) || event.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                        false
                    } else {
                        val dir = if (down) androidx.compose.ui.focus.FocusDirection.Down
                        else androidx.compose.ui.focus.FocusDirection.Up
                        if (settingsFocusManager.moveFocus(dir)) {
                            true
                        } else {
                            settingsScope.launch {
                                settingsListState.animateScrollBy(if (down) scrollStepPx * 3 else -scrollStepPx * 3)
                                settingsFocusManager.moveFocus(dir)
                            }
                            val canScroll = if (down) settingsListState.canScrollForward else settingsListState.canScrollBackward
                            canScroll
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {

            // ── Top Priority Banner if an Update is Available ──────────────
            if (updateState.isUpdateAvailable) {
                item {
                    Spacer(Modifier.height(12.dp))
                    AppUpdateSettingsCard(
                        updateState = updateState,
                        onCheckUpdates = {
                            AppUpdateManager.checkForUpdates(context, force = true)
                        },
                        onDownloadAndInstall = {
                            AppUpdateManager.downloadAndInstall(context)
                        },
                    )
                }
            }

            // ── Section header: Внешний вид и размер шрифта ───────────────
            item {
                Text(
                    text = "ВНЕШНИЙ ВИД И РАЗМЕР ШРИФТА",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
            }

            item {
                var showScaleDropdown by remember { mutableStateOf(false) }
                val scaleOptions = listOf(
                    40 to "40% — Максимально компактный",
                    60 to "60% — Мелкий (для смартфонов)",
                    80 to "80% — Уменьшенный",
                    100 to "100% — Стандартный (Full HD)",
                    150 to "150% — Увеличенный (1.5x)",
                    200 to "200% — Крупный (2x)",
                    250 to "250% — Очень крупный (2.5x)",
                    300 to "300% — Огромный (4K ТВ)",
                )

                SettingCardContainer { isFocused ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isPortrait) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column {
                                    Text(
                                        "Размер шрифта и высота строк",
                                        color = if (isFocused) Color.White else TextPrim,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Выберите комфортный масштаб интерфейса из выпадающего списка",
                                        color = TextSec,
                                        fontSize = 12.sp,
                                    )
                                }
                                FocusableSettingButton(
                                    text = "Размер: $uiScalePercent% ▾",
                                    isSelected = true,
                                    onClick = { showScaleDropdown = !showScaleDropdown },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Размер шрифта и высота строк",
                                        color = if (isFocused) Color.White else TextPrim,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "Выберите комфортный масштаб интерфейса из выпадающего списка",
                                        color = TextSec,
                                        fontSize = 12.sp,
                                    )
                                }
                                FocusableSettingButton(
                                    text = "Размер: $uiScalePercent% ▾",
                                    isSelected = true,
                                    onClick = { showScaleDropdown = !showScaleDropdown },
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = showScaleDropdown,
                            enter = expandVertically(),
                            exit = shrinkVertically(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0A0F1A))
                                    .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                scaleOptions.chunked(if (isPortrait) 1 else 2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        pair.forEach { (scale, label) ->
                                            val isSelected = uiScalePercent == scale
                                            FocusableSettingButton(
                                                text = if (isSelected) "✓ $label" else label,
                                                isSelected = isSelected,
                                                onClick = {
                                                    uiScalePercent = scale
                                                    prefs.edit().putInt("ui_scale_percent", scale).apply()
                                                    showScaleDropdown = false
                                                },
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Section header: Плейлисты ──────────────────────────────────
            item {
                SectionHeader(
                    title = "Плейлисты",
                    actionLabel = "+ Добавить",
                    onAction = { showAddDialog = true },
                )
            }

            // ── Remote Web Page Loader Card (minogatvbox.duckdns.org) ──────
            item {
                SettingCardContainer { isFocused ->
                    if (isPortrait) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Загрузка плейлиста и EPG по коду с веб-страницы",
                                    color = if (isFocused) Color.White else TextPrim,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Откройте minogatvbox.duckdns.org, введите 4-значный код и укажите Название, Плейлист и EPG",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                )
                            }
                            FocusableSettingButton(
                                text = "🌐 Загрузить с ВЕБ-страницы",
                                isSelected = true,
                                onClick = {
                                    viewModel.startWebPairingSession(
                                        pageUrl = webConfigUrl,
                                        onConfigApplied = { cfg ->
                                            cfg.epgUrl?.let { customEpgUrl = it }
                                        },
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "Загрузка плейлиста и EPG по коду с веб-страницы",
                                    color = if (isFocused) Color.White else TextPrim,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Откройте minogatvbox.duckdns.org, введите 4-значный код и укажите Название, Плейлист и EPG",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            FocusableSettingButton(
                                text = "🌐 Загрузить с ВЕБ-страницы",
                                isSelected = true,
                                onClick = {
                                    viewModel.startWebPairingSession(
                                        pageUrl = webConfigUrl,
                                        onConfigApplied = { cfg ->
                                            cfg.epgUrl?.let { customEpgUrl = it }
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Playlist rows ──────────────────────────────────────────────
            if (uiState.playlists.isEmpty() && !uiState.isLoading) {
                item {
                    EmptyPlaylistHint(
                        onAdd = { showAddDialog = true },
                        onLoadFromWeb = {
                            viewModel.startWebPairingSession(
                                pageUrl = webConfigUrl,
                                onConfigApplied = { cfg ->
                                    cfg.epgUrl?.let { customEpgUrl = it }
                                },
                            )
                        },
                    )
                }
            }

            items(uiState.playlists, key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    isRefreshing = uiState.refreshingIds.contains(playlist.id),
                    onEdit   = { editingPlaylist = playlist },
                    onDelete = { deleteTarget = playlist },
                    onRefresh = { viewModel.refreshPlaylist(playlist.id) },
                )
                HorizontalDivider(color = Surface2, thickness = 1.dp)
            }

            // ── Loading indicator ─────────────────────────────────────────
            if (uiState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
            }

            // ── Section header: Медиатека (Портал фильмов и сериалов) ─────
            item {
                Text(
                    text = "🎬 МЕДИАТЕКА (ПОРТАЛ ФИЛЬМОВ И СЕРИАЛОВ)",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }

            item {
                val hasVodPortal = vodPortalUrl.isNotBlank() || vodPortalKey.isNotBlank()
                val statusLabel = if (hasVodPortal) {
                    buildString {
                        append("✅ Подключен: ")
                        append(if (vodPortalUrl.isNotBlank()) vodPortalUrl else "Портал по ключу")
                        if (vodPortalLogin.isNotBlank()) append(" • Логин: $vodPortalLogin")
                        if (vodPortalKey.isNotBlank()) append(" • Ключ: ${vodPortalKey.take(4)}***")
                    }
                } else {
                    "Портал не настроен • Укажите URL, Логин, Пароль или Ключ (либо отправьте через minogatvbox.duckdns.org)"
                }

                SettingCardContainer(
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = Color(0x5500E5FF),
                        shape = RoundedCornerShape(12.dp),
                    ),
                ) { isFocused ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text(
                                text = "Параметры доступа к каталогу фильмов (VOD / Кинозал)",
                                color = if (isFocused) Color.White else TextPrim,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = statusLabel,
                                color = if (hasVodPortal) Color(0xFF4ADE80) else TextSec,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        if (isPortrait) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FocusableSettingButton(
                                    text = "⚙ Настроить портал",
                                    isSelected = true,
                                    onClick = { showVodConfigDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    FocusableSettingButton(
                                        text = "🎬 Открыть медиатеку",
                                        isSelected = false,
                                        onClick = onOpenVodLibrary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (hasVodPortal) {
                                        FocusableSettingButton(
                                            text = "🗑 Очистить",
                                            isSelected = false,
                                            onClick = {
                                                vodPortalUrl = ""
                                                vodPortalLogin = ""
                                                vodPortalPassword = ""
                                                vodPortalKey = ""
                                                prefs.edit()
                                                    .remove("vod_portal_url")
                                                    .remove("vod_portal_login")
                                                    .remove("vod_portal_password")
                                                    .remove("vod_portal_key")
                                                    .apply()
                                                Toast.makeText(context, "Настройки портала Медиатеки очищены", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(0.75f),
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FocusableSettingButton(
                                    text = "⚙ Настроить портал",
                                    isSelected = true,
                                    onClick = { showVodConfigDialog = true },
                                    modifier = Modifier.weight(1f),
                                )
                                FocusableSettingButton(
                                    text = "🎬 Открыть медиатеку",
                                    isSelected = false,
                                    onClick = onOpenVodLibrary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (hasVodPortal) {
                                    FocusableSettingButton(
                                        text = "🗑 Очистить",
                                        isSelected = false,
                                        onClick = {
                                            vodPortalUrl = ""
                                            vodPortalLogin = ""
                                            vodPortalPassword = ""
                                            vodPortalKey = ""
                                            prefs.edit()
                                                .remove("vod_portal_url")
                                                .remove("vod_portal_login")
                                                .remove("vod_portal_password")
                                                .remove("vod_portal_key")
                                                .apply()
                                            Toast.makeText(context, "Настройки портала Медиатеки очищены", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(0.75f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Section header: Воспроизведение ───────────────────────────
            item {
                Text(
                    text = "ВОСПРОИЗВЕДЕНИЕ И АВТОЗАПУСК",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }

            item {
                val toggleBootAutoStart: (Boolean) -> Unit = { checked ->
                    autoStartOnBoot = checked
                    prefs.edit().putBoolean("auto_start_on_boot", checked).apply()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        runCatching {
                            context.createDeviceProtectedStorageContext()
                                .getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
                                .edit()
                                .putBoolean("auto_start_on_boot", checked)
                                .apply()
                        }
                    }
                    runCatching {
                        val svcIntent = Intent().setClassName(context.packageName, "com.minogatv.box.WakeUpAutoStartService")
                        if (checked) {
                            context.startService(svcIntent)
                        } else {
                            context.stopService(svcIntent)
                        }
                    }
                    if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                        Toast.makeText(
                            context,
                            "Для автозапуска на Android TV разрешите «Поверх других окон»",
                            Toast.LENGTH_LONG
                        ).show()
                        runCatching {
                            val overlayIntent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(overlayIntent)
                        }.onFailure {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                }
                SettingRowContainer(onClick = { toggleBootAutoStart(!autoStartOnBoot) }) { isFocused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Автозапуск приложения с запуском устройства",
                            color = if (isFocused) Color.White else TextPrim,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Автоматически открывать Minoga TV при включении устройства и при выходе из режима сна",
                            color = TextSec,
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = autoStartOnBoot,
                        onCheckedChange = toggleBootAutoStart,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = TextSec,
                            uncheckedTrackColor = Surface2,
                        ),
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingRowContainer(
                    onClick = {
                        val next = !autoPlayLast
                        autoPlayLast = next
                        prefs.edit().putBoolean("auto_play_last_channel", next).apply()
                    }
                ) { isFocused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Автозапуск последнего канала", color = if (isFocused) Color.White else TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Запускать воспроизведение сразу при открытии приложения", color = TextSec, fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoPlayLast,
                        onCheckedChange = { checked ->
                            autoPlayLast = checked
                            prefs.edit().putBoolean("auto_play_last_channel", checked).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = TextSec,
                            uncheckedTrackColor = Surface2,
                        ),
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingRowContainer { isFocused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Живое превью каналов", color = if (isFocused) Color.White else TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Фоновое воспроизведение выбранного канала при наведении в списке", color = TextSec, fontSize = 12.sp)
                    }
                    Switch(
                        checked = channelPreviewEnabled,
                        onCheckedChange = { checked ->
                            channelPreviewEnabled = checked
                            prefs.edit().putBoolean("channel_preview_enabled", checked).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = TextSec,
                            uncheckedTrackColor = Surface2,
                        ),
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingRowContainer { isFocused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Картинка в картинке (PiP)", color = if (isFocused) Color.White else TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Воспроизведение видео в плавающем мини-окне при сворачивании или кнопке PiP", color = TextSec, fontSize = 12.sp)
                    }
                    Switch(
                        checked = pipEnabled,
                        onCheckedChange = { checked ->
                            pipEnabled = checked
                            prefs.edit().putBoolean("pip_enabled", checked).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = TextSec,
                            uncheckedTrackColor = Surface2,
                        ),
                    )
                }
            }

            // Touch-screen gesture control (emulates the D-pad remote)
            item {
                Spacer(Modifier.height(8.dp))
                SettingRowContainer { isFocused ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Управление жестами (сенсорный экран)", color = if (isFocused) Color.White else TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Двойной тап — ОК, удержание — долгое ОК, свайп влево/вправо/вверх/вниз — стрелки пульта, тап двумя пальцами — Назад", color = TextSec, fontSize = 12.sp)
                    }
                    Switch(
                        checked = touchGesturesEnabled,
                        onCheckedChange = { checked ->
                            touchGesturesEnabled = checked
                            prefs.edit().putBoolean("touch_gestures_enabled", checked).apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = TextSec,
                            uncheckedTrackColor = Surface2,
                        ),
                    )
                }
            }

            // Catch-up protocol selection
            item {
                Spacer(Modifier.height(8.dp))
                SettingCardContainer { isFocused ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Тип архива передач (Catch-up)", color = if (isFocused) Color.White else TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isFocused) Color(0xFF234273) else Surface2)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = forcedCatchupType.name,
                                    color = if (isFocused) Color.White else Accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        val catchupDescription = when (forcedCatchupType) {
                            CatchupType.AUTO -> "Авто: берется из плейлиста или автоопределение по структуре URL канала"
                            CatchupType.SHIFT -> "Shift: принудительно формирует архив через параметры ?utc={utcStart}&lutc={utcNow} (самый частый протокол)"
                            CatchupType.FLUSSONIC -> "Flussonic: принудительно формирует ссылку /timeshift_abs/{utcStart}/{duration}/index.m3u8"
                            CatchupType.XTREAM -> "Xtream: принудительно формирует ссылку Xtream Codes /timeshift/{user}/{pass}/..."
                            CatchupType.APPEND -> "Append: принудительно формирует ссылку со смещением ?catchup-back={offsetSeconds}"
                            CatchupType.NONE -> "Архив отключен"
                        }
                        Text(
                            text = catchupDescription,
                            color = if (forcedCatchupType != CatchupType.AUTO) Color(0xFF00E5FF) else TextSec,
                            fontSize = 12.sp,
                        )
                    }

                    val catchupTypes = listOf(
                        CatchupType.AUTO to "Авто",
                        CatchupType.SHIFT to "Shift",
                        CatchupType.FLUSSONIC to "Flussonic",
                        CatchupType.XTREAM to "Xtream",
                        CatchupType.APPEND to "Append",
                    )

                    if (isPortrait) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            catchupTypes.chunked(3).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    rowItems.forEach { (type, label) ->
                                        val isSelected = forcedCatchupType == type
                                        FocusableSettingButton(
                                            text = label,
                                            isSelected = isSelected,
                                            onClick = {
                                                forcedCatchupType = type
                                                prefs.edit().putString("forced_catchup_type", type.name).apply()
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            catchupTypes.forEach { (type, label) ->
                                val isSelected = forcedCatchupType == type
                                FocusableSettingButton(
                                    text = label,
                                    isSelected = isSelected,
                                    onClick = {
                                        forcedCatchupType = type
                                        prefs.edit().putString("forced_catchup_type", type.name).apply()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SettingCardContainer { isFocused ->
                    Column {
                        Text("Шаг перемотки в архиве", color = if (isFocused) Color.White else TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Интервал перемотки при нажатии кнопок Вправо / Влево на пульте", color = TextSec, fontSize = 12.sp)
                    }

                    val stepOptions = listOf(
                        5 to "5 сек",
                        10 to "10 сек",
                        30 to "30 сек",
                        60 to "1 мин",
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        stepOptions.forEach { (seconds, label) ->
                            val isSelected = seekStepSeconds == seconds
                            FocusableSettingButton(
                                text = label,
                                isSelected = isSelected,
                                onClick = {
                                    seekStepSeconds = seconds
                                    prefs.edit().putInt("seek_step_seconds", seconds).apply()
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }



            // ── Section header: Источник телепрограммы (EPG) ─────────────
            item {
                Text(
                    text = "ИСТОЧНИК ТЕЛЕПРОГРАММЫ (EPG)",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }

            item {
                val isLiveServerEpg = customEpgUrl.isBlank() || customEpgUrl.equals(Playlist.LIVE_SERVER_EPG, ignoreCase = true)
                SettingCardContainer { isFocused ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Источник телепрограммы (EPG)", color = if (isFocused) Color.White else TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                val displayUrl = if (isLiveServerEpg) {
                                    "⚡ Live EPG — готовая программа с сервера (как в Media Station X)"
                                } else {
                                    customEpgUrl
                                }
                                Text(
                                    text = displayUrl,
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (isPortrait) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FocusableSettingButton(
                                    text = "⚡ Live EPG (С сервера)",
                                    isSelected = isLiveServerEpg,
                                    onClick = {
                                        customEpgUrl = Playlist.LIVE_SERVER_EPG
                                        prefs.edit().putString("custom_epg_url", Playlist.LIVE_SERVER_EPG).apply()
                                        viewModel.refreshEpg(Playlist.LIVE_SERVER_EPG)
                                        Toast.makeText(context, "Включен Live EPG (готовая программа с сервера)", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                FocusableSettingButton(
                                    text = if (isLiveServerEpg) "🌐 Другой источник (XMLTV)" else "✏ Изменить источник",
                                    isSelected = !isLiveServerEpg,
                                    onClick = { showEpgDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FocusableSettingButton(
                                    text = "⚡ Live EPG (С сервера)",
                                    isSelected = isLiveServerEpg,
                                    onClick = {
                                        customEpgUrl = Playlist.LIVE_SERVER_EPG
                                        prefs.edit().putString("custom_epg_url", Playlist.LIVE_SERVER_EPG).apply()
                                        viewModel.refreshEpg(Playlist.LIVE_SERVER_EPG)
                                        Toast.makeText(context, "Включен Live EPG (готовая программа с сервера)", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                FocusableSettingButton(
                                    text = if (isLiveServerEpg) "🌐 Другой источник (XMLTV)" else "✏ Изменить источник",
                                    isSelected = !isLiveServerEpg,
                                    onClick = { showEpgDialog = true },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Surface2, thickness = 1.dp)

                    // Auto EPG sync toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Автоматическое обновление EPG", color = if (isFocused) Color.White else TextPrim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Загружать в фоне при запуске и отсутствии программы", color = TextSec, fontSize = 12.sp)
                        }
                        Switch(
                            checked = autoEpgSync,
                            onCheckedChange = { checked ->
                                autoEpgSync = checked
                                prefs.edit().putBoolean("auto_epg_sync", checked).apply()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Accent,
                                uncheckedThumbColor = TextSec,
                                uncheckedTrackColor = Surface2,
                            ),
                        )
                    }

                    HorizontalDivider(color = Surface2, thickness = 1.dp)

                    // Refresh EPG now button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Обновить телепрограмму сейчас", color = if (isFocused) Color.White else TextPrim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        FocusableSettingButton(
                            text = "🔄 Обновить",
                            isSelected = true,
                            onClick = {
                                viewModel.refreshEpg(customEpgUrl.ifBlank { Playlist.LIVE_SERVER_EPG })
                                Toast.makeText(context, "Запущена фоновая загрузка EPG...", Toast.LENGTH_SHORT).show()
                            },
                        )
                    }

                    // EPG Sync Progress Bar
                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.epgSyncProgress != null,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
                    ) {
                        val progress = uiState.epgSyncProgress ?: 0f
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x2E00E5FF))
                                .border(1.dp, Color(0x4000E5FF), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = uiState.epgSyncStatus ?: "Загрузка телепрограммы…",
                                    color = Accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    color = Accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                color = Accent,
                                trackColor = Color(0x3300E5FF),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            // ── Section header: Родительский контроль ─────────────────────
            item {
                Text(
                    text = "РОДИТЕЛЬСКИЙ КОНТРОЛЬ",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }

            item {
                SettingRowContainer(modifier = Modifier.clickable { showPinChangeDialog = true }) { isFocused ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFFE5A00D), modifier = Modifier.size(20.dp))
                        Column {
                            Text("PIN-код для защиты (18+)", color = if (isFocused) Color.White else TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Текущий PIN: $currentPin", color = TextSec, fontSize = 12.sp)
                        }
                    }
                    TextButton(onClick = { showPinChangeDialog = true }) {
                        Text("Изменить", color = Accent)
                    }
                }
            }

            // ── Section header: Резервное копирование ──────────────────────
            item {
                Text(
                    text = "РЕЗЕРВНОЕ КОПИРОВАНИЕ (BACKUP & RESTORE)",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }

            item {
                SettingCardContainer { isFocused ->
                    Text("Резервная копия настроек и плейлистов", color = if (isFocused) Color.White else TextPrim, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FocusableSettingButton(
                            text = "💾 Экспорт в JSON",
                            isSelected = false,
                            onClick = { viewModel.exportBackup() },
                            modifier = Modifier.weight(1f),
                        )
                        FocusableSettingButton(
                            text = "📂 Импорт из JSON",
                            isSelected = false,
                            onClick = { viewModel.importBackup() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Section header: Оценка программы и отзывы ─────────────────
            item {
                Text(
                    text = "⭐ ОЦЕНКА ПРОГРАММЫ, ОТЗЫВЫ И ВОПРОСЫ РАЗРАБОТЧИКУ",
                    color = Color(0xFFFBBF24),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }

            item {
                AppFeedbackSettingsCard(
                    prefs = prefs,
                    webConfigUrl = webConfigUrl,
                )
            }

            // ── Section header: Обновление программы ──────────────────────
            item {
                Text(
                    text = "ОБНОВЛЕНИЕ ПРОГРАММЫ",
                    color = Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                )
            }

            item {
                AppUpdateSettingsCard(
                    updateState = updateState,
                    onCheckUpdates = {
                        AppUpdateManager.checkForUpdates(context, force = true) { hasUpdate ->
                            val msg = if (hasUpdate) {
                                "Найдена новая версия v${AppUpdateManager.state.value.remoteInfo?.versionName}!"
                            } else {
                                "У вас установлена последняя версия (${updateState.localVersionName})"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDownloadAndInstall = {
                        AppUpdateManager.downloadAndInstall(context)
                    },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }

        // ── Remote control guide bar ──────────────────────────────────────────
        RemoteControlHintBar(
            hints = listOf(
                RemoteHint("▲▼", "Навигация"),
                RemoteHint("OK", "Выбрать / Открыть"),
                RemoteHint("Назад", "Список каналов"),
            ),
        )
    }

    // ── Add playlist dialog ────────────────────────────────────────────────────
    if (showAddDialog) {
        PlaylistDialog(
            title = "Добавить плейлист",
            initial = null,
            onConfirm = { playlist, onSuccess, onError ->
                viewModel.validateAndSavePlaylist(
                    playlist = playlist,
                    isNew = true,
                    onSuccess = {
                        onSuccess()
                        showAddDialog = false
                    },
                    onError = onError,
                )
            },
            onDismiss = { showAddDialog = false },
        )
    }

    // ── Edit playlist dialog ───────────────────────────────────────────────────
    editingPlaylist?.let { pl ->
        PlaylistDialog(
            title = "Редактировать плейлист",
            initial = pl,
            onConfirm = { updated, onSuccess, onError ->
                viewModel.validateAndSavePlaylist(
                    playlist = updated,
                    isNew = false,
                    onSuccess = {
                        onSuccess()
                        editingPlaylist = null
                    },
                    onError = onError,
                )
            },
            onDismiss = { editingPlaylist = null },
        )
    }

    // ── Delete confirm dialog ──────────────────────────────────────────────────
    deleteTarget?.let { pl ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Surface1,
            title = {
                Text("Удалить плейлист?", color = TextPrim, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "«${pl.name}» и все его каналы будут удалены.",
                    color = TextSec,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(pl.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Отмена", color = Accent)
                }
            },
        )
    }

    // ── Backup / Web Config message dialog ───────────────────────────────────
    uiState.backupMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissBackupMessage() },
            containerColor = Surface1,
            title = {
                Text("Успешно выполнено", color = TextPrim, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = msg,
                    color = TextSec,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissBackupMessage() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("OK", color = Color.Black) }
            },
        )
    }

    // ── Error message dialog ─────────────────────────────────────────────────
    uiState.errorMessage?.let { errMsg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            containerColor = Surface1,
            title = {
                Text("Ошибка", color = DangerRed, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = errMsg,
                    color = TextPrim,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissError() },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("OK", color = Color.Black) }
            },
        )
    }

    // ── Parental PIN change dialog ──────────────────────────────────────────
    if (showPinChangeDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinChangeDialog = false },
            containerColor = Surface1,
            title = {
                Text("Сменить PIN-код родительского контроля", color = TextPrim, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Введите новый 4-значный цифровой код:", color = TextSec, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) newPin = it },
                        singleLine = true,
                        placeholder = { Text("0000", color = TextSec) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Surface2,
                            focusedTextColor = TextPrim,
                            unfocusedTextColor = TextPrim,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length == 4) {
                            prefs.edit().putString("parental_pin", newPin).apply()
                            currentPin = newPin
                            showPinChangeDialog = false
                            Toast.makeText(context, "PIN-код успешно изменен на $newPin", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = newPin.length == 4,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showPinChangeDialog = false }) {
                    Text("Отмена", color = TextSec)
                }
            },
        )
    }

    // ── Manual EPG URL Dialog ────────────────────────────────────────────────
    if (showEpgDialog) {
        EpgUrlDialog(
            currentUrl = if (customEpgUrl.equals(Playlist.LIVE_SERVER_EPG, ignoreCase = true)) "" else customEpgUrl,
            onSave = { newUrl ->
                val trimmed = newUrl.trim()
                val cleanUrl = if (trimmed.isEmpty() || trimmed.equals(Playlist.LIVE_SERVER_EPG, ignoreCase = true)) {
                    Playlist.LIVE_SERVER_EPG
                } else {
                    Playlist.normalizeUrl(trimmed)
                }
                customEpgUrl = cleanUrl
                prefs.edit().putString("custom_epg_url", cleanUrl).apply()
                showEpgDialog = false
                viewModel.refreshEpg(cleanUrl)
                val toastText = if (cleanUrl == Playlist.LIVE_SERVER_EPG) {
                    "Выбран Live EPG (готовая программа с сервера). Запущено обновление."
                } else {
                    "Адрес EPG сохранен. Запущено обновление."
                }
                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showEpgDialog = false },
        )
    }

    // ── Web Code Pairing Dialog (minogatvbox.duckdns.org) ────────────────────
    if (pairingState.isVisible) {
        WebCodePairingDialog(
            state = pairingState,
            onRegenerateCode = {
                viewModel.startWebPairingSession(
                    pageUrl = webConfigUrl,
                    onConfigApplied = { cfg ->
                        cfg.epgUrl?.let { customEpgUrl = it }
                        if (!cfg.vodUrl.isNullOrBlank() || !cfg.vodKey.isNullOrBlank()) {
                            vodPortalUrl = cfg.vodUrl.orEmpty()
                            vodPortalLogin = cfg.vodLogin.orEmpty()
                            vodPortalPassword = cfg.vodPassword.orEmpty()
                            vodPortalKey = cfg.vodKey.orEmpty()
                        }
                    },
                )
            },
            onDismiss = { viewModel.stopWebPairingSession() },
        )
    }

    // ── VOD Portal Config Dialog (4 Fields: URL, Login, Password, Key) ───────
    if (showVodConfigDialog) {
        VodPortalConfigDialog(
            initialUrl = vodPortalUrl,
            initialLogin = vodPortalLogin,
            initialPassword = vodPortalPassword,
            initialKey = vodPortalKey,
            onSave = { url, login, pass, key ->
                vodPortalUrl = url.trim()
                vodPortalLogin = login.trim()
                vodPortalPassword = pass.trim()
                vodPortalKey = key.trim()
                prefs.edit()
                    .putString("vod_portal_url", vodPortalUrl)
                    .putString("vod_portal_login", vodPortalLogin)
                    .putString("vod_portal_password", vodPortalPassword)
                    .putString("vod_portal_key", vodPortalKey)
                    .apply()
                showVodConfigDialog = false
                Toast.makeText(context, "Параметры портала Медиатеки сохранены", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showVodConfigDialog = false },
        )
    }
}

@Composable
private fun AppUpdateSettingsCard(
    updateState: AppUpdateState,
    onCheckUpdates: () -> Unit,
    onDownloadAndInstall: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT || configuration.screenWidthDp < 600
    val hasUpdate = updateState.isUpdateAvailable
    val remote = updateState.remoteInfo

    SettingCardContainer(
        modifier = if (hasUpdate) {
            Modifier.border(
                width = 1.5.dp,
                color = Color(0xFF10B981),
                shape = RoundedCornerShape(12.dp),
            )
        } else {
            Modifier
        },
    ) { isFocused ->
        val infoBlock: @Composable () -> Unit = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (hasUpdate) {
                        "Доступна новая версия v${remote?.versionName}"
                    } else {
                        "Версия программы: ${updateState.localVersionName}"
                    },
                    color = when {
                        hasUpdate -> Color(0xFF10B981)
                        isFocused -> Color.White
                        else -> TextPrim
                    },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (hasUpdate) Color(0x3310B981) else Surface2)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (hasUpdate && remote != null) {
                            "Сборка ${remote.versionCode}"
                        } else {
                            "Сборка ${updateState.localVersionCode}"
                        },
                        color = if (hasUpdate) Color(0xFF10B981) else Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            val statusText = when {
                updateState.isChecking -> "Проверка наличия обновлений на сервере minogatvbox.duckdns.org…"
                updateState.isDownloading -> {
                    val dlMb = AppUpdateManager.formatSizeMb(updateState.downloadedBytes)
                    val totalMb = AppUpdateManager.formatSizeMb(updateState.totalBytes)
                    "Скачивание обновления: $dlMb из $totalMb (${(updateState.downloadProgress * 100).toInt()}%)"
                }
                hasUpdate && remote != null -> {
                    val sizeStr = AppUpdateManager.formatSizeMb(remote.sizeBytes)
                    val sizePart = if (sizeStr.isNotBlank()) " ($sizeStr)" else ""
                    "Текущая версия: ${updateState.localVersionName} → Новая: ${remote.versionName}$sizePart"
                }
                else -> "Установлена актуальная версия программы (сервер обновлений: minogatvbox.duckdns.org)"
            }

            Text(
                text = statusText,
                color = if (hasUpdate) Color(0xFFE0FFF4) else TextSec,
                fontSize = 12.sp,
            )

            updateState.errorMessage?.let { err ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = err,
                    color = DangerRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        val actionButton: @Composable (Modifier) -> Unit = { btnMod ->
            if (hasUpdate) {
                FocusableSettingButton(
                    text = if (updateState.isDownloading) {
                        "Загрузка ${(updateState.downloadProgress * 100).toInt()}%…"
                    } else if (updateState.downloadedApkFile?.exists() == true) {
                        "⬇ Установить v${remote?.versionName}"
                    } else {
                        "⬇ Скачать и установить"
                    },
                    isSelected = true,
                    onClick = {
                        if (!updateState.isDownloading) {
                            onDownloadAndInstall()
                        }
                    },
                    modifier = btnMod,
                )
            } else {
                FocusableSettingButton(
                    text = if (updateState.isChecking) "Проверка…" else "🔄 Проверить обновления",
                    isSelected = false,
                    onClick = {
                        if (!updateState.isChecking && !updateState.isDownloading) {
                            onCheckUpdates()
                        }
                    },
                    modifier = btnMod,
                )
            }
        }

        if (isPortrait) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    infoBlock()
                }
                actionButton(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    infoBlock()
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actionButton(Modifier)
                }
            }
        }

        AnimatedVisibility(
            visible = updateState.isDownloading,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                LinearProgressIndicator(
                    progress = { updateState.downloadProgress },
                    color = Color(0xFF10B981),
                    trackColor = Color(0x3310B981),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

@Composable
private fun WebCodePairingDialog(
    state: WebPairingDialogState,
    onRegenerateCode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT || configuration.screenWidthDp < 600
    val closeFocusRequester = remember { FocusRequester() }
    val isVerifiedOrReady = state.status == WebPairingStatus.VERIFIED || state.status == WebPairingStatus.READY
    val accentColor = if (isVerifiedOrReady) Color(0xFF10B981) else Color(0xFF00E5FF)

    LaunchedEffect(Unit) {
        closeFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = { if (!state.isInstalling) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !state.isInstalling,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC05070B))
                .padding(horizontal = if (isPortrait) 12.dp else 48.dp, vertical = if (isPortrait) 16.dp else 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(20.dp))
                    .background(Surface1)
                    .border(1.5.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(if (isPortrait) 18.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isPortrait) 14.dp else 18.dp),
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tv,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(if (isPortrait) 22.dp else 26.dp),
                    )
                    Text(
                        text = "Загрузка плейлиста и EPG с Веб-страницы",
                        color = TextPrim,
                        fontSize = if (isPortrait) 16.sp else 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Step instructions
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "1. Откройте в браузере телефона или ПК страницу:",
                        color = TextSec,
                        fontSize = if (isPortrait) 13.sp else 14.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "minogatvbox.duckdns.org",
                        color = Color(0xFF00E5FF),
                        fontSize = if (isPortrait) 18.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "2. Введите этот 4-значный код и установите Название, Плейлист и EPG:",
                        color = TextSec,
                        fontSize = if (isPortrait) 13.sp else 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                // 4-Digit Code Display Boxes
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (isPortrait) 8.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val digits = state.code.padEnd(4, '0').take(4)
                    digits.forEach { ch ->
                        Box(
                            modifier = Modifier
                                .size(width = if (isPortrait) 58.dp else 74.dp, height = if (isPortrait) 68.dp else 84.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF080B12))
                                .border(2.dp, accentColor, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = ch.toString(),
                                color = accentColor,
                                fontSize = if (isPortrait) 28.sp else 38.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // Live Status Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = if (isPortrait) 12.dp else 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        color = accentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = state.statusText,
                        color = TextPrim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onRegenerateCode,
                        enabled = !state.isInstalling,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = TextPrim, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Новый код", color = TextPrim, fontSize = 13.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        enabled = !state.isInstalling,
                        colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.focusRequester(closeFocusRequester),
                    ) {
                        Text("Отмена", color = TextPrim, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpgUrlDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT || configuration.screenWidthDp < 600
    var urlText by remember { mutableStateOf(currentUrl) }
    val focusRequester = remember { FocusRequester() }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xBB000000))
                .padding(horizontal = if (isPortrait) 12.dp else 48.dp, vertical = if (isPortrait) 16.dp else 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .border(1.dp, Color(0xFF2A314A), RoundedCornerShape(16.dp))
                    .padding(if (isPortrait) 16.dp else 24.dp),
                verticalArrangement = Arrangement.spacedBy(if (isPortrait) 12.dp else 16.dp),
            ) {
                // Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tv,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = "Источник телепрограммы (EPG)",
                        color = TextPrim,
                        fontSize = if (isPortrait) 16.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = "Выберите «Live EPG» для мгновенной загрузки уже готовой программы с нашего сервера (как в Media Station X) или укажите прямую ссылку на XMLTV файл (.xml / .xml.gz).",
                    color = TextSec,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )

                // Quick Helper Actions (Default Presets / Paste / Clear)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onSave(Playlist.LIVE_SERVER_EPG)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (isPortrait) "⚡ Live EPG (Готовая с сервера)" else "⚡ Live EPG — Готовая программа с сервера (как в Media Station X)",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                urlText = "http://epg.one/epg2.xml.gz"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("EPG.ONE (XMLTV)", color = Accent, fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                urlText = "http://cdn.epg.one/epg2.xml.gz"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("EPG.ONE CDN", color = TextPrim, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                urlText = "https://raw.githubusercontent.com/it999/it999.github.io/master/epg2.xml.gz"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("IT999 GitHub", color = TextPrim, fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                urlText = "https://epg.it999.ru/edem.xml.gz"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("IT999 Edem", color = TextPrim, fontSize = 12.sp, maxLines = 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    urlText = clip.trim()
                                    Toast.makeText(context, "Вставлено из буфера", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Буфер обмена пуст", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Вставить из буфера", color = TextPrim, fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { urlText = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Очистить", color = DangerRed, fontSize = 12.sp)
                        }
                    }
                }

                // Input field
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Адрес EPG URL") },
                    placeholder = { Text("http://epg.one/epg2.xml.gz") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Default,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Surface2,
                        focusedTextColor = TextPrim,
                        unfocusedTextColor = TextPrim,
                        focusedContainerColor = Surface2.copy(alpha = 0.5f),
                        unfocusedContainerColor = Surface2.copy(alpha = 0.2f),
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )

                // Dialog Buttons (Save / Cancel)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text("Отмена", color = TextSec, fontSize = 14.sp)
                    }

                    Button(
                        onClick = { onSave(urlText.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ─── Components ───────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            color = Accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.weight(1f),
        )
        FocusableSettingButton(
            text = actionLabel,
            isSelected = false,
            onClick = onAction,
        )
    }
}

@Composable
private fun EmptyPlaylistHint(
    onAdd: () -> Unit,
    onLoadFromWeb: () -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT || configuration.screenWidthDp < 600
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(if (isPortrait) 16.dp else 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Нет подключенных плейлистов", color = TextPrim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Загрузите плейлист и EPG с веб-страницы minogatvbox.duckdns.org в один клик или добавьте ссылку вручную:",
            color = TextSec,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        if (isPortrait) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FocusableSettingButton(
                    text = "🌐 С minogatvbox.duckdns.org",
                    isSelected = true,
                    onClick = onLoadFromWeb,
                    modifier = Modifier.fillMaxWidth(),
                )
                FocusableSettingButton(
                    text = "+ Свой URL",
                    isSelected = false,
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusableSettingButton(
                    text = "🌐 С minogatvbox.duckdns.org",
                    isSelected = true,
                    onClick = onLoadFromWeb,
                )
                FocusableSettingButton(
                    text = "+ Свой URL",
                    isSelected = false,
                    onClick = onAdd,
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    isRefreshing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRefresh: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1.0f, animationSpec = tween(150), label = "playlist_scale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused || it.hasFocus }
            .clip(RoundedCornerShape(12.dp))
            .background(if (isFocused) FocusBg else Surface1)
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) FocusGold else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFocused) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FocusGold)
            )
            Spacer(Modifier.width(8.dp))
        }

        // Type badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isFocused) Color(0xFF234273) else Surface2)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = playlist.type.name,
                color = if (isFocused) Color.White else Accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                color = if (isFocused) Color.White else TextPrim,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "🔒 Ссылка на плейлист скрыта",
                color = if (isFocused) Color(0xFFB0C4DE) else TextSec,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Accent,
                strokeWidth = 2.dp,
            )
        } else {
            FocusableIconButton(
                onClick = onRefresh,
                icon = Icons.Filled.Refresh,
                contentDescription = "Обновить",
                tint = TextSec,
            )
        }
        FocusableIconButton(
            onClick = onEdit,
            icon = Icons.Filled.Edit,
            contentDescription = "Редактировать",
            tint = TextSec,
        )
        FocusableIconButton(
            onClick = onDelete,
            icon = Icons.Filled.Delete,
            contentDescription = "Удалить",
            tint = DangerRed,
        )
    }
}

// ─── Add / Edit Playlist dialog ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDialog(
    title: String,
    initial: Playlist?,
    onConfirm: (playlist: Playlist, onSuccess: () -> Unit, onError: (String) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name         by remember { mutableStateOf(initial?.name ?: "") }
    var url          by remember { mutableStateOf(initial?.url ?: "") }
    var type         by remember { mutableStateOf(initial?.type ?: PlaylistType.M3U) }
    var epgUrl       by remember { mutableStateOf(initial?.epgUrl ?: "") }
    var userAgent    by remember { mutableStateOf(initial?.userAgent ?: Playlist.DEFAULT_USER_AGENT) }
    var xtreamUser   by remember { mutableStateOf(initial?.xtreamUsername ?: "") }
    var xtreamPass   by remember { mutableStateOf(initial?.xtreamPassword ?: "") }
    var macAddress   by remember { mutableStateOf(initial?.macAddress ?: "") }
    var typeExpanded by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var isChecking   by remember { mutableStateOf(false) }
    var checkError   by remember { mutableStateOf<String?>(null) }

    val nameError = name.isBlank()
    val urlError  = url.isBlank()

    AlertDialog(
        onDismissRequest = { if (!isChecking) onDismiss() },
        containerColor = Surface1,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Text(title, color = TextPrim, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Validation Error Banner ──────────────────────────────
                checkError?.let { err ->
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DangerRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, DangerRed.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = err,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }
                }

                // ── Name ──────────────────────────────────────────────────
                item {
                    MinogaTextField(
                        value = name,
                        onValueChange = { name = it; checkError = null },
                        label = "Название *",
                        isError = nameError,
                        supportingText = if (nameError) "Обязательное поле" else null,
                    )
                }

                // ── Type picker ───────────────────────────────────────────
                item {
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { if (!isChecking) typeExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = type.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Тип", color = TextSec) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            colors = minogaTextFieldColors(),
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false },
                            modifier = Modifier.background(Surface2),
                        ) {
                            PlaylistType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(t.name, color = TextPrim) },
                                    onClick = { type = t; typeExpanded = false; checkError = null },
                                )
                            }
                        }
                    }
                }

                // ── URL / Portal ───────────────────────────────────────────
                item {
                    MinogaTextField(
                        value = url,
                        onValueChange = { url = it; checkError = null },
                        label = when (type) {
                            PlaylistType.M3U     -> "M3U URL *"
                            PlaylistType.XMLTV   -> "XMLTV URL *"
                            PlaylistType.STALKER -> "Адрес портала *"
                            PlaylistType.XTREAM  -> "Адрес сервера *"
                        },
                        isError = urlError,
                        supportingText = if (urlError) "Обязательное поле" else null,
                        keyboardType = KeyboardType.Uri,
                        visualTransformation = if (initial != null) PasswordVisualTransformation() else VisualTransformation.None,
                    )
                }

                // ── Type-specific credentials ─────────────────────────────
                when (type) {
                    PlaylistType.XTREAM -> {
                        item {
                            MinogaTextField(
                                value = xtreamUser,
                                onValueChange = { xtreamUser = it; checkError = null },
                                label = "Логин",
                            )
                        }
                        item {
                            MinogaTextField(
                                value = xtreamPass,
                                onValueChange = { xtreamPass = it; checkError = null },
                                label = "Пароль",
                                visualTransformation = PasswordVisualTransformation(),
                            )
                        }
                    }
                    PlaylistType.STALKER -> {
                        item {
                            MinogaTextField(
                                value = macAddress,
                                onValueChange = { macAddress = it; checkError = null },
                                label = "MAC-адрес",
                                placeholder = "00:1A:79:xx:xx:xx",
                            )
                        }
                    }
                    PlaylistType.M3U, PlaylistType.XMLTV -> { /* no extra credentials */ }
                }

                // ── Advanced toggle ───────────────────────────────────────
                item {
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (showAdvanced) "▲ Скрыть расширенные" else "▼ Расширенные настройки",
                            color = Accent,
                            fontSize = 13.sp,
                        )
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = showAdvanced,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MinogaTextField(
                                value = epgUrl,
                                onValueChange = { epgUrl = it },
                                label = "EPG URL (XMLTV)",
                                placeholder = Playlist.DEFAULT_EPG_URL,
                                keyboardType = KeyboardType.Uri,
                            )
                            MinogaTextField(
                                value = userAgent,
                                onValueChange = { userAgent = it },
                                label = "User-Agent",
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    checkError = null
                    isChecking = true
                    val finalEpg = Playlist.normalizeUrl(epgUrl.trim()).takeIf { it.isNotBlank() }
                    val playlist = (initial ?: Playlist(profileId = 1L, name = name, url = url)).copy(
                        name           = name.trim(),
                        url            = url.trim(),
                        type           = type,
                        epgUrl         = finalEpg,
                        userAgent      = userAgent.trim().ifEmpty { Playlist.DEFAULT_USER_AGENT },
                        xtreamUsername = xtreamUser.trim().takeIf { it.isNotEmpty() },
                        xtreamPassword = xtreamPass.trim().takeIf { it.isNotEmpty() },
                        macAddress     = macAddress.trim().takeIf { it.isNotEmpty() },
                    )
                    onConfirm(
                        playlist,
                        { isChecking = false },
                        { err ->
                            isChecking = false
                            checkError = err
                        },
                    )
                },
                enabled = !nameError && !urlError && !isChecking,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
            ) {
                if (isChecking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Text("Проверка...", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                } else {
                    Text("Сохранить", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isChecking,
            ) {
                Text("Отмена", color = TextSec)
            }
        },
    )
}

// ─── Shared text field helper ─────────────────────────────────────────────────

@Composable
private fun MinogaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: String? = null,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = if (isError) DangerRed else TextSec, fontSize = 12.sp) },
        placeholder = placeholder?.let { { Text(it, color = TextSec, fontSize = 12.sp) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it, color = DangerRed, fontSize = 11.sp) } },
        colors = minogaTextFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun minogaTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Accent,
    unfocusedBorderColor = Color(0xFF333655),
    focusedTextColor     = TextPrim,
    unfocusedTextColor   = TextPrim,
    cursorColor          = Accent,
    focusedContainerColor   = Surface2,
    unfocusedContainerColor = Surface2,
)

private val PORTAL_STRING_REGEX = Regex("^(?:[a-z]+:/+)?portal::(?:\\[([^\\]]*)\\])?\\s*(.*)$", RegexOption.IGNORE_CASE)
private val HEX_SUBDOMAIN_REGEX = Regex("^https?://([0-9a-f]{8,20})\\.([a-z0-9.-]+\\.[a-z]{2,})(?:/.*)?$", RegexOption.IGNORE_CASE)

private fun cleanVodScheme(raw: String): String {
    var s = raw.replace("\\/", "/").trim()
    if (s.isEmpty()) return ""
    while (s.matches(Regex("^[a-z]{3,7}:/+[a-z]{3,7}:/+.*", RegexOption.IGNORE_CASE))) {
        s = s.replaceFirst(Regex("^[a-z]{3,7}:/+", RegexOption.IGNORE_CASE), "")
    }
    s = when {
        s.matches(Regex("^h+t+p+s:/+.*", RegexOption.IGNORE_CASE)) ->
            s.replaceFirst(Regex("^h+t+p+s:/+", RegexOption.IGNORE_CASE), "https://")
        s.matches(Regex("^h+t+p+:/+.*", RegexOption.IGNORE_CASE)) ->
            s.replaceFirst(Regex("^h+t+p+:/+", RegexOption.IGNORE_CASE), "http://")
        !s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true) -> {
            val trimmed = s.trimStart('/')
            if (trimmed.contains("/api/v1", ignoreCase = true)) "http://$trimmed" else "https://$trimmed"
        }
        else -> s
    }
    return s
}

private fun normalizePortalUrlAndKey(rawUrl: String, rawKey: String): Pair<String, String> {
    var u = rawUrl.replace("\\/", "/").trim()
    var k = rawKey.replace("\\/", "/").trim()
    val mUrl = PORTAL_STRING_REGEX.find(u)
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
        val mKey = PORTAL_STRING_REGEX.find(k)
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
        u = cleanVodScheme(u)
        val subMatch = HEX_SUBDOMAIN_REGEX.find(u)
        if (subMatch != null && k.isEmpty()) {
            val extractedKey = subMatch.groupValues.getOrNull(1).orEmpty().trim()
            if (extractedKey.isNotEmpty()) k = extractedKey
        }
    }
    return u to k
}

@Composable
fun VodPortalConfigDialog(
    initialUrl: String,
    initialLogin: String,
    initialPassword: String,
    initialKey: String,
    onSave: (url: String, login: String, password: String, key: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT || configuration.screenWidthDp < 600
    var url by remember { mutableStateOf(initialUrl) }
    var login by remember { mutableStateOf(initialLogin) }
    var password by remember { mutableStateOf(initialPassword) }
    var key by remember { mutableStateOf(initialKey) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Surface1,
        titleContentColor = TextPrim,
        textContentColor = TextSec,
        title = {
            Text(
                text = "🎬 Подключение портала Медиатеки",
                fontWeight = FontWeight.Bold,
                fontSize = if (isPortrait) 16.sp else 18.sp,
                color = Color(0xFF00E5FF),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Введите параметры доступа от вашего провайдера (или отправьте их с телефона на minogatvbox.duckdns.org по 4-значный коду):",
                    color = TextSec,
                    fontSize = 12.sp,
                )
                MinogaTextField(
                    value = url,
                    onValueChange = {
                        if (it.contains("portal::", ignoreCase = true)) {
                            val (nu, nk) = normalizePortalUrlAndKey(it, key)
                            url = nu
                            key = nk
                        } else {
                            url = it
                        }
                        errorMsg = null
                    },
                    label = "1. Адрес портала (URL)",
                    placeholder = "https://example-portal.tv или portal::[key:...]http://...",
                    keyboardType = KeyboardType.Uri,
                )
                if (isPortrait) {
                    MinogaTextField(
                        value = login,
                        onValueChange = { login = it },
                        label = "2. Логин",
                        placeholder = "Логин абонента",
                    )
                    MinogaTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "3. Пароль",
                        placeholder = "Пароль",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MinogaTextField(
                            value = login,
                            onValueChange = { login = it },
                            label = "2. Логин",
                            placeholder = "Логин абонента",
                            modifier = Modifier.weight(1f),
                        )
                        MinogaTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = "3. Пароль",
                            placeholder = "Пароль",
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                MinogaTextField(
                    value = key,
                    onValueChange = {
                        if (it.contains("portal::", ignoreCase = true)) {
                            val (nu, nk) = normalizePortalUrlAndKey(url, it)
                            url = nu
                            key = nk
                        } else {
                            key = it
                        }
                        errorMsg = null
                    },
                    label = "4. Ключ доступа (Key / Токен портала)",
                    placeholder = "Ключ доступа к медиатеке",
                )
                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = DangerRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (cleanUrl, cleanKey) = normalizePortalUrlAndKey(url, key)
                    if (cleanUrl.isBlank() && cleanKey.isBlank()) {
                        errorMsg = "Укажите адрес портала (URL) или ключ доступа (Key)"
                    } else {
                        onSave(cleanUrl, login.trim(), password.trim(), cleanKey)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            ) {
                Text("✓ Сохранить и подключить", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = TextSec)
            }
        },
    )
}

// ─── Feedback & Rating Card + Personal Developer Replies ──────────────────────

data class PersonalFeedbackItem(
    val id: String,
    val author: String,
    val rating: Int,
    val text: String,
    val createdAt: String,
    val developerReply: String,
    val repliedAt: String,
)

fun getOrCreateFeedbackClientId(prefs: android.content.SharedPreferences): String {
    val existing = prefs.getString("feedback_client_id", null)
    if (!existing.isNullOrBlank()) return existing
    val generated = "agy_" + UUID.randomUUID().toString().replace("-", "").take(16)
    prefs.edit().putString("feedback_client_id", generated).apply()
    return generated
}

private fun resolveBackendOrigin(webConfigUrl: String): String {
    val trimmed = webConfigUrl.trim().ifBlank { "https://minogatvbox.duckdns.org" }
    return try {
        val u = URL(trimmed)
        val portPart = if (u.port != -1 && u.port != 80 && u.port != 443) ":${u.port}" else ""
        "${u.protocol}://${u.host}$portPart"
    } catch (_: Exception) {
        "https://minogatvbox.duckdns.org"
    }
}

suspend fun fetchPersonalFeedbackItems(
    webConfigUrl: String,
    clientId: String,
): List<PersonalFeedbackItem> = withContext(Dispatchers.IO) {
    if (clientId.isBlank()) return@withContext emptyList()
    try {
        val base = resolveBackendOrigin(webConfigUrl)
        val url = URL("$base/api/feedback/public?clientId=${URLEncoder.encode(clientId, "UTF-8")}")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
        }
        val code = conn.responseCode
        if (code !in 200..299) return@withContext emptyList()
        val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = JSONObject(body)
        if (!json.optBoolean("ok", false)) return@withContext emptyList()
        val arr = json.optJSONArray("myItems") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                add(
                    PersonalFeedbackItem(
                        id = obj.optString("id", ""),
                        author = obj.optString("author", "Зритель"),
                        rating = obj.optInt("rating", 5).coerceIn(1, 5),
                        text = obj.optString("text", ""),
                        createdAt = obj.optString("createdAt", ""),
                        developerReply = obj.optString("developerReply", ""),
                        repliedAt = obj.optString("repliedAt", ""),
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private suspend fun submitFeedbackRequest(
    webConfigUrl: String,
    clientId: String,
    author: String,
    rating: Int,
    text: String,
): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    try {
        val base = resolveBackendOrigin(webConfigUrl)
        val url = URL("$base/api/feedback/submit")
        val payload = JSONObject().apply {
            put("clientId", clientId)
            put("author", author.trim().ifBlank { "Пользователь Android ТВ" })
            put("rating", rating.coerceIn(1, 5))
            put("text", text.trim())
            put("device", "Minoga Android App")
        }
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 9000
            readTimeout = 9000
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        val json = runCatching { JSONObject(body) }.getOrNull()
        if (code in 200..299 && json?.optBoolean("ok", false) == true) {
            true to "✅ Спасибо! Ваша оценка и отзыв отправлены разработчику."
        } else {
            val err = json?.optString("error").orEmpty().ifBlank { "Ошибка сервера ($code)" }
            false to "❌ Не удалось отправить: $err"
        }
    } catch (e: Exception) {
        false to "❌ Ошибка сети при отправке: ${e.localizedMessage ?: "проверьте интернет"}"
    }
}

@Composable
private fun AppFeedbackSettingsCard(
    prefs: android.content.SharedPreferences,
    webConfigUrl: String,
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT || configuration.screenWidthDp < 600
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clientId = remember { getOrCreateFeedbackClientId(prefs) }
    var selectedRating by remember { mutableStateOf(prefs.getInt("feedback_draft_rating", 5).coerceIn(1, 5)) }
    var authorName by remember { mutableStateOf(prefs.getString("feedback_author_name", "") ?: "") }
    var feedbackText by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var myItems by remember { mutableStateOf<List<PersonalFeedbackItem>>(emptyList()) }

    var showNameInputDialog by remember { mutableStateOf(false) }
    var showTextInputDialog by remember { mutableStateOf(false) }

    fun reloadMyFeedback() {
        scope.launch {
            val list = fetchPersonalFeedbackItems(webConfigUrl, clientId)
            myItems = list
            val repliedCount = list.count { it.developerReply.isNotBlank() }
            prefs.edit().putInt("last_seen_dev_reply_count", repliedCount).apply()
        }
    }

    LaunchedEffect(clientId) {
        reloadMyFeedback()
    }

    val goldColor = Color(0xFFFBBF24)

    SettingCardContainer(
        modifier = Modifier.border(
            width = 1.dp,
            color = Color(0x55FBBF24),
            shape = RoundedCornerShape(12.dp),
        ),
    ) { isFocused ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Поставьте оценку приложению (от 1 до 5 звёзд) и напишите вопрос или идею разработчику:",
                    color = if (isFocused) Color.White else TextPrim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Выбор звезды только отмечает вашу оценку — отправка происходит при нажатии кнопки «Отправить вопрос / отзыв»",
                    color = TextSec,
                    fontSize = 12.sp,
                )
            }

            // Row of 5 star buttons (selecting ONLY highlights, does not auto-submit!)
            val starLabels = listOf(
                5 to if (isPortrait) "★★★★★ 5" else "★★★★★ 5 (Отлично)",
                4 to if (isPortrait) "★★★★ 4" else "★★★★ 4 (Хорошо)",
                3 to if (isPortrait) "★★★ 3" else "★★★ 3 (Нормально)",
                2 to "★★ 2",
                1 to "★ 1",
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                starLabels.chunked(if (isPortrait) 3 else 5).forEach { rowStars ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowStars.forEach { (stars, label) ->
                            val isSelected = selectedRating == stars
                            var starFocused by remember { mutableStateOf(false) }
                            val scale by animateFloatAsState(if (starFocused) 1.05f else 1.0f, tween(120), label = "star_btn")
                            Button(
                                onClick = {
                                    selectedRating = stars
                                    prefs.edit().putInt("feedback_draft_rating", stars).apply()
                                    statusIsError = false
                                    statusText = "Выбрана оценка: $stars ★ — нажмите «Отправить вопрос / отзыв» ниже для отправки"
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        isSelected && starFocused -> goldColor
                                        isSelected -> Color(0x44FBBF24)
                                        starFocused -> Color(0xFF2C4373)
                                        else -> Surface2
                                    },
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(if (!isPortrait && stars >= 3) 1.35f else 1f)
                                    .scale(scale)
                                    .onFocusChanged { starFocused = it.isFocused }
                                    .border(
                                        width = if (starFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp,
                                        color = when {
                                            starFocused -> Color.White
                                            isSelected -> goldColor
                                            else -> Color(0x26FFFFFF)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                            ) {
                                Text(
                                    text = if (isSelected) "✓ $label" else label,
                                    color = when {
                                        isSelected && starFocused -> Color.Black
                                        isSelected -> Color(0xFFFFFBEB)
                                        starFocused -> Color.White
                                        else -> TextPrim
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // Large, high-contrast D-pad focusable input fields for Name and Feedback/Question
            if (isPortrait) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FeedbackFocusableFieldBox(
                        label = "Ваше имя:",
                        value = authorName,
                        placeholder = "Например: Александр (нажмите для ввода)",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showNameInputDialog = true },
                    )
                    FeedbackFocusableFieldBox(
                        label = "Ваш вопрос, идея или отзыв разработчику:",
                        value = feedbackText,
                        placeholder = "Напишите вопрос или отзыв (нажмите для ввода)…",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showTextInputDialog = true },
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FeedbackFocusableFieldBox(
                        label = "Ваше имя:",
                        value = authorName,
                        placeholder = "Например: Александр (нажмите OK для ввода)",
                        modifier = Modifier.weight(0.38f),
                        onClick = { showNameInputDialog = true },
                    )
                    FeedbackFocusableFieldBox(
                        label = "Ваш вопрос, идея или отзыв разработчику:",
                        value = feedbackText,
                        placeholder = "Напишите вопрос или отзыв (нажмите OK для ввода)…",
                        modifier = Modifier.weight(0.62f),
                        onClick = { showTextInputDialog = true },
                    )
                }
            }

            // Submit button row
            val onSubmitClick: () -> Unit = {
                if (!isSubmitting) {
                    isSubmitting = true
                    statusText = "Отправка вашего отзыва на сервер…"
                    statusIsError = false
                    prefs.edit().putString("feedback_author_name", authorName.trim()).apply()
                    scope.launch {
                        val (ok, msg) = submitFeedbackRequest(
                            webConfigUrl = webConfigUrl,
                            clientId = clientId,
                            author = authorName,
                            rating = selectedRating,
                            text = feedbackText,
                        )
                        isSubmitting = false
                        statusIsError = !ok
                        statusText = msg
                        if (ok) {
                            feedbackText = ""
                            reloadMyFeedback()
                        }
                    }
                }
            }

            if (isPortrait) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusableSettingButton(
                        text = if (isSubmitting) "⏳ Отправка…" else "💬 Отправить вопрос / отзыв",
                        isSelected = true,
                        onClick = onSubmitClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (statusText != null) {
                        Text(
                            text = statusText!!,
                            color = if (statusIsError) Color(0xFFFCA5A5) else Color(0xFF4ADE80),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FocusableSettingButton(
                        text = if (isSubmitting) "⏳ Отправка…" else "💬 Отправить вопрос / отзыв",
                        isSelected = true,
                        onClick = onSubmitClick,
                    )

                    if (statusText != null) {
                        Text(
                            text = statusText!!,
                            color = if (statusIsError) Color(0xFFFCA5A5) else Color(0xFF4ADE80),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f).padding(start = 12.dp),
                        )
                    }
                }
            }

            // Personal block: User's submitted questions & Developer Replies
            if (myItems.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A1220))
                        .border(1.dp, Color(0x4500E5FF), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🔔 Ваши обращения и ответы разработчика (${myItems.size})",
                            color = Color(0xFF00E5FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        FocusableSettingButton(
                            text = "↻ Обновить ответы",
                            isSelected = false,
                            onClick = { reloadMyFeedback() },
                        )
                    }

                    myItems.take(10).forEach { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF111A2E))
                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${"★".repeat(item.rating.coerceIn(1, 5))} • ${item.author}",
                                    color = goldColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = item.createdAt,
                                    color = TextSec,
                                    fontSize = 11.sp,
                                )
                            }
                            if (item.text.isNotBlank()) {
                                Text(
                                    text = item.text,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                )
                            }
                            if (item.developerReply.isNotBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x2400E5FF))
                                        .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                ) {
                                    Text(
                                        text = "👨‍💻 Ответ разработчика" +
                                            if (item.repliedAt.isNotBlank()) " (${item.repliedAt}):" else ":",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = item.developerReply,
                                        color = Color(0xFFE0F7FA),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            } else {
                                Text(
                                    text = "⏳ Ожидает ответа разработчика",
                                    color = TextSec,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNameInputDialog) {
        FeedbackTvInputDialog(
            title = "Введите ваше имя",
            initialValue = authorName,
            placeholder = "Например: Александр",
            onConfirm = { newValue ->
                authorName = newValue.trim()
                prefs.edit().putString("feedback_author_name", authorName).apply()
                showNameInputDialog = false
            },
            onDismiss = { showNameInputDialog = false },
        )
    }

    if (showTextInputDialog) {
        FeedbackTvInputDialog(
            title = "Ваш вопрос, идея или отзыв разработчику",
            initialValue = feedbackText,
            placeholder = "Напишите ваш вопрос или отзыв…",
            onConfirm = { newValue ->
                feedbackText = newValue.trim()
                showTextInputDialog = false
            },
            onDismiss = { showTextInputDialog = false },
        )
    }
}

@Composable
private fun FeedbackFocusableFieldBox(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.02f else 1.0f, tween(120), label = "fb_field_scale")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = if (isFocused) Color(0xFF00E5FF) else Color(0xFFCBD5E1),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isFocused) Color(0xFF132238) else Color(0xFF0B1220))
                .border(
                    width = if (isFocused) 2.5.dp else 1.5.dp,
                    color = if (isFocused) Color(0xFF00E5FF) else Color(0x5594A3B8),
                    shape = RoundedCornerShape(10.dp),
                )
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Text(
                text = value.ifBlank { placeholder },
                color = if (value.isBlank()) Color(0xFF94A3B8) else Color.White,
                fontSize = 16.sp,
                fontWeight = if (value.isBlank()) FontWeight.Medium else FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeedbackTvInputDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initialValue) }
    var kbLang by remember { mutableStateOf("ru") }
    val ruRows = remember {
        listOf(
            listOf("А", "Б", "В", "Г", "Д", "Е", "Ж", "З", "И", "Й"),
            listOf("К", "Л", "М", "Н", "О", "П", "Р", "С", "Т", "У"),
            listOf("Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э"),
            listOf("Ю", "Я", "0", "1", "2", "3", "4", "5", "6", "7"),
            listOf("8", "9", ".", ",", "?", "!", "-", "+", "⌫", "✕"),
        )
    }
    val enRows = remember {
        listOf(
            listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J"),
            listOf("K", "L", "M", "N", "O", "P", "Q", "R", "S", "T"),
            listOf("U", "V", "W", "X", "Y", "Z", "0", "1", "2", "3"),
            listOf("4", "5", "6", "7", "8", "9", ".", ",", "?", "!"),
            listOf("@", "-", "_", "/", ":", ";", "(", ")", "⌫", "✕"),
        )
    }
    val activeRows = if (kbLang == "ru") ruRows else enRows

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT || configuration.screenWidthDp < 600

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = if (isPortrait) 10.dp else 32.dp)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .border(1.5.dp, Color(0xFF00E5FF), RoundedCornerShape(16.dp))
                .padding(if (isPortrait) 12.dp else 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    color = Color(0xFF00E5FF),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                FocusableSettingButton(
                    text = "✕ Закрыть",
                    isSelected = false,
                    onClick = onDismiss,
                )
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text(placeholder, color = Color(0xFF94A3B8), fontSize = 16.sp)
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0x6600E5FF),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF00E5FF),
                    focusedContainerColor = Color(0xFF07090E),
                    unfocusedContainerColor = Color(0xFF07090E),
                ),
                shape = RoundedCornerShape(10.dp),
            )

            // On-screen TV Keyboard (АБВГД / ABCDEF) for effortless D-pad entry
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                activeRows.forEach { rowKeys ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        rowKeys.forEach { keyLabel ->
                            FocusableSettingButton(
                                text = keyLabel,
                                isSelected = false,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (keyLabel) {
                                        "⌫" -> if (draft.isNotEmpty()) draft = draft.dropLast(1)
                                        "✕" -> draft = ""
                                        else -> {
                                            val ch = if (draft.isEmpty() || draft.endsWith(". ") || draft.endsWith(" ")) {
                                                keyLabel
                                            } else {
                                                keyLabel.lowercase()
                                            }
                                            draft += ch
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FocusableSettingButton(
                        text = if (kbLang == "ru") "🌐 RU → EN" else "🌐 EN → RU",
                        isSelected = false,
                        modifier = Modifier.weight(1.8f),
                        onClick = { kbLang = if (kbLang == "ru") "en" else "ru" },
                    )
                    FocusableSettingButton(
                        text = "␣ ПРОБЕЛ",
                        isSelected = false,
                        modifier = Modifier.weight(3.5f),
                        onClick = { draft += " " },
                    )
                    FocusableSettingButton(
                        text = "⌫ Стереть",
                        isSelected = false,
                        modifier = Modifier.weight(1.5f),
                        onClick = { if (draft.isNotEmpty()) draft = draft.dropLast(1) },
                    )
                    FocusableSettingButton(
                        text = "✓ Готово",
                        isSelected = true,
                        modifier = Modifier.weight(2.2f),
                        onClick = { onConfirm(draft) },
                    )
                }
            }
        }
    }
}
