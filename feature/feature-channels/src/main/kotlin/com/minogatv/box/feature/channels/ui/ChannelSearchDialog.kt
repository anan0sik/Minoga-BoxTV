package com.minogatv.box.feature.channels.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.LinearProgressIndicator
import coil.compose.AsyncImage
import com.minogatv.box.feature.channels.ChannelDesignTokens
import com.minogatv.box.feature.channels.ChannelDisplayItem
import com.minogatv.box.feature.channels.tvFocusCard

@Composable
fun ChannelSearchDialog(
    allChannels: List<ChannelDisplayItem>,
    onSelectChannel: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    epgSyncProgress: Float? = null,
    epgSyncStatus: String? = null,
) {
    var query by remember { mutableStateOf("") }
    var focusedResultIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    val filtered = remember(query, allChannels) {
        if (query.isBlank()) {
            allChannels.take(20)
        } else {
            val q = query.trim().lowercase()
            allChannels.filter { item ->
                item.channel.name.lowercase().contains(q) ||
                    (item.currentProgram?.title?.lowercase()?.contains(q) == true) ||
                    item.channel.groupTitle.lowercase().contains(q)
            }.take(30)
        }
    }

    val config = LocalConfiguration.current
    val isCompact = config.screenWidthDp < 600 || config.screenHeightDp < 500
    val isPortrait = config.screenHeightDp > config.screenWidthDp
    var showTvKeyboard by remember { mutableStateOf(!isPortrait) }
    var kbLang by remember { mutableStateOf("ru") }
    val firstKeyFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }

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
            listOf("4", "5", "6", "7", "8", "9", "-", "+", "⌫", "✕"),
        )
    }
    val activeRows = if (kbLang == "ru") ruRows else enRows

    LaunchedEffect(showTvKeyboard) {
        if (showTvKeyboard) {
            runCatching { firstKeyFocusRequester.requestFocus() }
        } else {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ChannelDesignTokens.MidnightBgGradientStart.copy(alpha = 0.95f))
                .padding(
                    horizontal = if (isCompact) 12.dp else 40.dp,
                    vertical = if (isCompact) 10.dp else 18.dp,
                ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Search Field, Keyboard Toggle, and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            focusedResultIndex = 0
                        },
                        placeholder = { Text("Поиск каналов и передач...", color = ChannelDesignTokens.TextSecondary) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = ChannelDesignTokens.NeonCyan)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Очистить", tint = ChannelDesignTokens.TextSecondary)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChannelDesignTokens.NeonCyan,
                            unfocusedBorderColor = Color(0x3300E5FF),
                            focusedContainerColor = ChannelDesignTokens.CardGlassFocused,
                            unfocusedContainerColor = ChannelDesignTokens.CardGlassUnfocused,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    )

                    TvSearchKeyChip(
                        label = if (showTvKeyboard) "⌨ Скрыть клав." else "⌨ Клавиатура ТВ",
                        isPrimary = showTvKeyboard,
                        onClick = { showTvKeyboard = !showTvKeyboard },
                    )

                    TvSearchKeyChip(
                        label = "✕ Закрыть",
                        isPrimary = false,
                        onClick = onDismiss,
                    )
                }

                // Classic АБВГД / ABCDEF TV Keyboard for D-Pad Remotes
                AnimatedVisibility(
                    visible = showTvKeyboard,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D1322))
                            .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        activeRows.forEachIndexed { rIdx, rowKeys ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                rowKeys.forEachIndexed { cIdx, keyLabel ->
                                    TvSearchKeyChip(
                                        label = keyLabel,
                                        isPrimary = false,
                                        modifier = Modifier
                                            .weight(1f)
                                            .let { mod ->
                                                if (rIdx == 0 && cIdx == 0) mod.focusRequester(firstKeyFocusRequester) else mod
                                            },
                                        onClick = {
                                            when (keyLabel) {
                                                "⌫" -> if (query.isNotEmpty()) query = query.dropLast(1)
                                                "✕" -> query = ""
                                                else -> query += keyLabel.lowercase()
                                            }
                                            focusedResultIndex = 0
                                        },
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            TvSearchKeyChip(
                                label = if (kbLang == "ru") "🌐 RU → EN" else "🌐 EN → RU",
                                isPrimary = true,
                                modifier = Modifier.weight(2f),
                                onClick = { kbLang = if (kbLang == "ru") "en" else "ru" },
                            )
                            TvSearchKeyChip(
                                label = "␣ ПРОБЕЛ",
                                isPrimary = false,
                                modifier = Modifier.weight(3.8f),
                                onClick = { query += " " },
                            )
                            TvSearchKeyChip(
                                label = "⌫",
                                isPrimary = false,
                                modifier = Modifier.weight(1f),
                                onClick = { if (query.isNotEmpty()) query = query.dropLast(1) },
                            )
                            TvSearchKeyChip(
                                label = "✕",
                                isPrimary = false,
                                modifier = Modifier.weight(1f),
                                onClick = { query = "" },
                            )
                            TvSearchKeyChip(
                                label = "↵ ENTER (НАЙТИ)",
                                isPrimary = true,
                                modifier = Modifier.weight(2.6f),
                                onClick = {
                                    if (filtered.isNotEmpty()) {
                                        runCatching { firstResultFocusRequester.requestFocus() }
                                    }
                                },
                            )
                        }
                    }
                }

                // EPG Sync Progress Bar right below search buttons row
                AnimatedVisibility(
                    visible = epgSyncProgress != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    if (epgSyncProgress != null) {
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
                                    text = epgSyncStatus ?: "Загрузка телепрограммы…",
                                    color = ChannelDesignTokens.NeonCyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${(epgSyncProgress * 100).toInt()}%",
                                    color = ChannelDesignTokens.AccentGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { epgSyncProgress },
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

                Spacer(Modifier.height(18.dp))

                // Search Results
                if (query.isBlank()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Введите название канала или передачи для поиска",
                            color = Color(0xFF78909C),
                            fontSize = 15.sp,
                        )
                    }
                } else if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "По запросу «$query» ничего не найдено",
                            color = Color(0xFF78909C),
                            fontSize = 15.sp,
                        )
                    }
                } else {
                    Text(
                        text = "Найдено каналов: ${filtered.size}",
                        color = Color(0xFF2979FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )

                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(
                            items = filtered,
                            key = { _, it -> it.channel.id },
                        ) { idx, item ->
                            SearchResultItem(
                                item = item,
                                onClick = { onSelectChannel(item.channel.id) },
                                modifier = if (idx == 0) Modifier.focusRequester(firstResultFocusRequester) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSearchKeyChip(
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> ChannelDesignTokens.NeonCyan.copy(alpha = 0.3f)
                    isPrimary -> ChannelDesignTokens.NeonCyan.copy(alpha = 0.16f)
                    else -> Color(0xFF161E31)
                }
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = when {
                    isFocused -> ChannelDesignTokens.NeonCyan
                    isPrimary -> ChannelDesignTokens.NeonCyan.copy(alpha = 0.65f)
                    else -> Color(0x26FFFFFF)
                },
                shape = RoundedCornerShape(8.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                isFocused -> Color.White
                isPrimary -> ChannelDesignTokens.NeonCyan
                else -> Color(0xFFF1F5F9)
            },
            fontSize = 12.sp,
            fontWeight = if (isFocused || isPrimary) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchResultItem(
    item: ChannelDisplayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .tvFocusCard(isFocused = isFocused, cornerRadius = 14.dp)
            .clickable(onClick = onClick)
            .focusable(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Channel Logo
            val validSearchLogo = remember(item.channel.logoUrl) {
                val u = item.channel.logoUrl?.trim().orEmpty()
                if (u.isEmpty() ||
                    u.contains("vattik/picons-rtrs", ignoreCase = true) ||
                    u.contains("it999.github.io", ignoreCase = true)
                ) null else u
            }
            var logoLoadError by remember(validSearchLogo) { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2436)),
                contentAlignment = Alignment.Center,
            ) {
                if (validSearchLogo != null && !logoLoadError) {
                    AsyncImage(
                        model = validSearchLogo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        onError = { logoLoadError = true },
                        modifier = Modifier.size(36.dp),
                    )
                } else {
                    Icon(Icons.Filled.Tv, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.channel.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.currentProgram?.let { prog ->
                    Text(
                        text = "Сейчас: ${prog.title}",
                        color = Color(0xFF90A4AE),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Category badge
            if (item.channel.groupTitle.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x334F8EF7))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = item.channel.groupTitle,
                        color = Color(0xFF90CAF9),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
