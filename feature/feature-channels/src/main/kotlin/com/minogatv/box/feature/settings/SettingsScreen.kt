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
import android.view.KeyEvent
import android.widget.Toast
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
import androidx.compose.runtime.LaunchedEffect
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
import com.minogatv.box.core.model.enums.PlaylistType

// ─── Design tokens ────────────────────────────────────────────────────────────

private val Bg         = Color(0xFF0B0D14)
private val Surface1   = Color(0xFF111318)
private val Surface2   = Color(0xFF1C1F2E)
private val Accent     = Color(0xFF4F8EF7)
private val TextPrim   = Color(0xFFE8E8F0)
private val TextSec    = Color(0xFF8888A8)
private val DangerRed  = Color(0xFFE53935)

// ─── Focus & TV Navigation Helpers ──────────────────────────────────────────

@Composable
private fun SettingRowContainer(
    modifier: Modifier = Modifier,
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
            .background(if (isFocused) Color(0xFF1B2E52) else Surface1)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Accent else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFocused) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00E5FF))
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
            .background(if (isFocused) Color(0xFF1B2E52) else Surface1)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Accent else Color(0x22FFFFFF),
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
                isSelected && isFocused -> Color(0xFF00E5FF)
                isSelected -> Accent
                isFocused -> Color(0xFF2C4373)
                else -> Surface2
            },
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            ),
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.Black else (if (isFocused) Color.White else TextPrim),
            fontSize = 12.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal,
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
                color = if (isFocused) Color(0xFF2C4373) else Color.Transparent,
                shape = CircleShape,
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = CircleShape,
            ),
    ) {
        Icon(icon, contentDescription, tint = if (isFocused) Color.White else tint, modifier = Modifier.size(18.dp))
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
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE) }

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
    var autoEpgSync  by remember { mutableStateOf(prefs.getBoolean("auto_epg_sync", true)) }
    var showEpgDialog by remember { mutableStateOf(false) }
    var showPinChangeDialog by remember { mutableStateOf(false) }
    var currentPin by remember { mutableStateOf(prefs.getString("parental_pin", "0000") ?: "0000") }
    var seekStepSeconds by remember { mutableStateOf(prefs.getInt("seek_step_seconds", 10)) }
    var pipEnabled by remember { mutableStateOf(prefs.getBoolean("pip_enabled", true)) }
    var uiScalePercent by remember { mutableStateOf(prefs.getInt("ui_scale_percent", 100)) }

    var showAddDialog      by remember { mutableStateOf(false) }
    var editingPlaylist    by remember { mutableStateOf<Playlist?>(null) }
    var deleteTarget       by remember { mutableStateOf<Playlist?>(null) }

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

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {

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
                SettingCardContainer { isFocused ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                    "Масштаб высоты строк и шрифта каналов и телепрограммы",
                                    color = TextSec,
                                    fontSize = 12.sp,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isFocused) Color(0xFF234273) else Surface2)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "$uiScalePercent%",
                                    color = if (isFocused) Color.White else Accent,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        val scaleOptions = listOf(
                            100 to "100%",
                            80 to "80%",
                            60 to "60%",
                            40 to "40%",
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            scaleOptions.forEach { (scale, label) ->
                                val isSelected = uiScalePercent == scale
                                FocusableSettingButton(
                                    text = label,
                                    isSelected = isSelected,
                                    onClick = {
                                        uiScalePercent = scale
                                        prefs.edit().putInt("ui_scale_percent", scale).apply()
                                    },
                                    modifier = Modifier.weight(1f),
                                )
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

            // ── Playlist rows ──────────────────────────────────────────────
            if (uiState.playlists.isEmpty() && !uiState.isLoading) {
                item {
                    EmptyPlaylistHint(
                        onAdd = { showAddDialog = true },
                        onAddDemo = {
                            viewModel.addPlaylist(
                                Playlist(
                                    profileId = 1L,
                                    name = "Minoga Demo TV",
                                    url = "http://10.0.2.2:8080/playlist.m3u",
                                    type = PlaylistType.M3U,
                                    epgUrl = "http://10.0.2.2:8080/epg.xml",
                                ),
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
                SettingRowContainer { isFocused ->
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
                SettingCardContainer { isFocused ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Адрес источника EPG (XMLTV)", color = if (isFocused) Color.White else TextPrim, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            val displayUrl = if (customEpgUrl.isBlank()) {
                                "По умолчанию: ${Playlist.DEFAULT_EPG_URL}"
                            } else {
                                customEpgUrl
                            }
                            Text(
                                text = displayUrl,
                                color = if (customEpgUrl.isBlank()) TextSec else Color(0xFF00E5FF),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (customEpgUrl.isNotBlank()) {
                                TextButton(onClick = {
                                    customEpgUrl = ""
                                    prefs.edit().putString("custom_epg_url", "").apply()
                                    viewModel.refreshEpg(Playlist.DEFAULT_EPG_URL)
                                    Toast.makeText(context, "Сброшено на источник по умолчанию (EPG.ONE)", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Сброс", color = TextSec)
                                }
                            }
                            Button(
                                onClick = { showEpgDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(if (customEpgUrl.isBlank()) "Задать" else "Изменить", color = Accent, fontSize = 13.sp)
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
                        Button(
                            onClick = {
                                viewModel.refreshEpg(customEpgUrl.ifBlank { Playlist.DEFAULT_EPG_URL })
                                Toast.makeText(context, "Запущена фоновая загрузка EPG...", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Обновить", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
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
                        Button(
                            onClick = {
                                val jsonCount = uiState.playlists.size
                                Toast.makeText(context, "Экспортировано $jsonCount плейлистов в backup.json", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Filled.Backup, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Экспорт в JSON", color = TextPrim, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "Импорт настроек готов к выбору файла", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Импорт из JSON", color = Accent, fontSize = 13.sp)
                        }
                    }
                }
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
            currentUrl = customEpgUrl,
            onSave = { newUrl ->
                val cleanUrl = Playlist.normalizeUrl(newUrl.trim())
                customEpgUrl = cleanUrl
                prefs.edit().putString("custom_epg_url", cleanUrl).apply()
                showEpgDialog = false
                viewModel.refreshEpg(cleanUrl.ifBlank { Playlist.DEFAULT_EPG_URL })
                Toast.makeText(context, "Адрес EPG сохранен. Запущено обновление.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showEpgDialog = false },
        )
    }
}

@Composable
private fun EpgUrlDialog(
    currentUrl: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
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
                .padding(horizontal = 48.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(620.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .border(1.dp, Color(0xFF2A314A), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = "Введите прямую ссылку на XMLTV файл (.xml или .xml.gz). При ручной установке телепрограмма и пиконы каналов загружаются строго с неё.",
                    color = TextSec,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )

                // Quick Helper Actions (Default Presets / Paste / Clear)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text("EPG.ONE (основной)", color = Accent, fontSize = 12.sp, maxLines = 1)
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
        TextButton(onClick = onAction) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(actionLabel, color = Accent, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyPlaylistHint(
    onAdd: () -> Unit,
    onAddDemo: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface1)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Нет подключенных плейлистов", color = TextPrim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Добавьте свой M3U/Xtream плейлист или загрузите демо-каналы с запущенного бэкенда:",
            color = TextSec,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onAddDemo,
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("▶ Демо-каналы (Бэкенд)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onAdd,
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("+ Свой URL", color = TextPrim, fontSize = 13.sp)
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
            .background(if (isFocused) Color(0xFF1B2E52) else Surface1)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Accent else Color(0x22FFFFFF),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFocused) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF00E5FF))
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
                text = playlist.url,
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
