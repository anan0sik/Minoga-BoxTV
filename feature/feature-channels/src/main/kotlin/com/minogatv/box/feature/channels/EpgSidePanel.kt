package com.minogatv.box.feature.channels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minogatv.box.core.model.EpgProgram
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ─── Design tokens ────────────────────────────────────────────────────────────

private val PanelBackground     = ChannelDesignTokens.SidePanelGlass
private val AccentColor         = ChannelDesignTokens.NeonCyan
private val AccentBlue          = ChannelDesignTokens.ElectricBlue
private val TextPrimary         = ChannelDesignTokens.TextPrimary
private val TextSecondary       = ChannelDesignTokens.TextSecondary
private val LiveBadgeColor      = ChannelDesignTokens.LiveRed
private val CatchupBadgeColor   = ChannelDesignTokens.CatchupGreen
private val SelectedItemBg      = Color(0xFF1D3B68)
private val DividerColor        = Color(0x1F00E5FF)

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

/**
 * Slide-in EPG + Catch-up side panel (1/3 of screen width).
 *
 * Displayed when the user presses D-Pad RIGHT on a channel.
 *
 * @param isVisible           Whether the panel should be shown.
 * @param items               Combined list of [SidePanelItem.Programme] and [SidePanelItem.CatchupSlot].
 * @param focusedIndex        Currently focused item index (for D-Pad highlight).
 * @param channelName         Shown in the panel header.
 * @param onItemSelected      Called when the user presses OK on an item.
 * @param modifier            Optional [Modifier].
 */
@Composable
fun EpgSidePanel(
    isVisible: Boolean,
    items: List<SidePanelItem>,
    focusedIndex: Int,
    channelName: String,
    onItemSelected: (SidePanelItem) -> Unit,
    modifier: Modifier = Modifier,
    isFocusInSidePanel: Boolean = true,
    isProgramDetailsOpen: Boolean = false,
    isLoadingEpg: Boolean = false,
    scaleFactor: Float = 1.0f,
    detailsScrollState: ScrollState = rememberScrollState(),
) {
    val listState = rememberLazyListState()

    // Auto-scroll to keep the focused item visible
    LaunchedEffect(focusedIndex) {
        if (items.isNotEmpty()) {
            listState.animateScrollToItem(focusedIndex.coerceIn(0, items.lastIndex))
        }
    }

    if (isVisible) {
        Surface(
            modifier = modifier,
            color = PanelBackground,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                width = if (isFocusInSidePanel) 1.5.dp else 1.dp,
                color = if (isFocusInSidePanel) AccentColor else Color(0x2200E5FF),
            ),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Panel header ─────────────────────────────────────────────
                PanelHeader(
                    channelName = channelName,
                    isFocusInSidePanel = isFocusInSidePanel,
                    isProgramDetailsOpen = isProgramDetailsOpen,
                )

                HorizontalDivider(color = DividerColor, thickness = 0.8.dp)

                // ── EPG + Catch-up list & Details ─────────────────────────────
                if (items.isEmpty()) {
                    EmptyPanelContent(isLoading = isLoadingEpg)
                } else if (isProgramDetailsOpen) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // Left 35%: EPG items
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(0.35f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            itemsIndexed(items) { index, item ->
                                val isFocused = isFocusInSidePanel && index == focusedIndex
                                when (item) {
                                    is SidePanelItem.Programme ->
                                        ProgrammeRow(
                                            item = item,
                                            isFocused = isFocused,
                                            scaleFactor = scaleFactor,
                                            onClick = { onItemSelected(item) },
                                        )

                                    is SidePanelItem.CatchupSlot ->
                                        CatchupSlotRow(
                                            item = item,
                                            isFocused = isFocused,
                                            scaleFactor = scaleFactor,
                                            onClick = { onItemSelected(item) },
                                        )
                                }
                            }
                        }

                        // Right 65%: Program details card
                        ProgramDetailsCard(
                            item = items.getOrNull(focusedIndex),
                            channelName = channelName,
                            scrollState = detailsScrollState,
                            modifier = Modifier
                                .weight(0.65f)
                                .fillMaxHeight(),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(items) { index, item ->
                            val isFocused = isFocusInSidePanel && index == focusedIndex
                            when (item) {
                                is SidePanelItem.Programme ->
                                    ProgrammeRow(
                                        item = item,
                                        isFocused = isFocused,
                                        scaleFactor = scaleFactor,
                                        onClick = { onItemSelected(item) },
                                    )

                                is SidePanelItem.CatchupSlot ->
                                    CatchupSlotRow(
                                        item = item,
                                        isFocused = isFocused,
                                        scaleFactor = scaleFactor,
                                        onClick = { onItemSelected(item) },
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun PanelHeader(
    channelName: String,
    isFocusInSidePanel: Boolean,
    isProgramDetailsOpen: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LiveTv,
                    contentDescription = null,
                    tint = AccentColor,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "ТЕЛЕПРОГРАММА И АРХИВ",
                    color = AccentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Remote hint pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isFocusInSidePanel) Color(0x3300E5FF) else Color(0x1F8193B2),
                border = BorderStroke(1.dp, if (isFocusInSidePanel) AccentColor else Color(0x338193B2)),
            ) {
                Text(
                    text = when {
                        isProgramDetailsOpen -> "◀ / Back В список"
                        isFocusInSidePanel -> "▶ Инфо / ◀ Каналы"
                        else -> "▶ В программу"
                    },
                    color = if (isFocusInSidePanel) Color.White else TextSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = channelName.ifBlank { "Выберите канал" },
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgrammeRow(
    item: SidePanelItem.Programme,
    isFocused: Boolean,
    scaleFactor: Float = 1.0f,
    onClick: () -> Unit,
) {
    val bg = if (isFocused) SelectedItemBg else Color(0x55131825)
    val borderColor = if (isFocused) AccentColor else Color(0x1A00E5FF)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((6 * scaleFactor).coerceAtLeast(4f).dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape((6 * scaleFactor).coerceAtLeast(4f).dp),
            )
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(AccentColor),
                )
                Spacer(Modifier.width(6.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: Time + Live badge + Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = timeFmt.format(Date(item.program.startMs)),
                        color = if (item.isLive || isFocused) AccentColor else TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = "–",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = timeFmt.format(Date(item.program.endMs)),
                        color = TextSecondary,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (item.isLive) {
                        LiveBadge()
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = item.program.title,
                        color = if (isFocused) Color.White else TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = if (item.isLive || isFocused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Line 2: Abbreviated description (сокращенно информация о телепередаче)
                val sub = getProgramSubtitle(item.program)
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        color = if (isFocused) Color(0xFFB0C4DE) else TextSecondary.copy(alpha = 0.8f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Live progress bar
                if (item.isLive && item.progress > 0f) {
                    Spacer(Modifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp)),
                        color = AccentColor,
                        trackColor = AccentColor.copy(alpha = 0.25f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CatchupSlotRow(
    item: SidePanelItem.CatchupSlot,
    isFocused: Boolean,
    scaleFactor: Float = 1.0f,
    onClick: () -> Unit,
) {
    val bg = if (isFocused) SelectedItemBg else Color(0x44131825)
    val borderColor = if (isFocused) AccentColor else Color(0x1A00E5FF)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((6 * scaleFactor).coerceAtLeast(4f).dp))
            .background(bg)
            .border(
                width = if (isFocused) 2.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape((6 * scaleFactor).coerceAtLeast(4f).dp),
            )
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(AccentColor),
                )
                Spacer(Modifier.width(6.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: Time + Archive badge + Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "${timeFmt.format(Date(item.program.startMs))} – ${timeFmt.format(Date(item.program.endMs))}",
                        color = if (isFocused) AccentColor else TextSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(ChannelDesignTokens.ArchiveBadgeBg)
                            .padding(horizontal = 3.dp, vertical = 0.5.dp),
                    ) {
                        Text(
                            text = "АРХИВ",
                            color = ChannelDesignTokens.ArchiveBadgeText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = item.program.title,
                        color = if (isFocused) Color.White else TextPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Line 2: Abbreviated description (сокращенно информация о телепередаче)
                val sub = getProgramSubtitle(item.program)
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        color = if (isFocused) Color(0xFFB0C4DE) else TextSecondary.copy(alpha = 0.8f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isFocused) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = AccentColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgramDetailsCard(
    item: SidePanelItem?,
    channelName: String,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    if (item == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Выберите передачу для просмотра информации",
                color = TextSecondary,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }

    val program = when (item) {
        is SidePanelItem.Programme -> item.program
        is SidePanelItem.CatchupSlot -> item.program
    }

    val isLive = item is SidePanelItem.Programme && item.isLive
    val isPast = item is SidePanelItem.CatchupSlot || (item is SidePanelItem.Programme && item.program.endMs <= System.currentTimeMillis())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xE60D1322), RoundedCornerShape(10.dp))
            .border(1.dp, AccentColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Top row: Channel Name & Badges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = channelName,
                color = AccentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isLive) {
                    LiveBadge()
                } else if (isPast) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(ChannelDesignTokens.ArchiveBadgeBg)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = "АРХИВ",
                            color = ChannelDesignTokens.ArchiveBadgeText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        // Program Title
        Text(
            text = program.title,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            lineHeight = 15.sp,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(2.dp))

        // Time & Category
        val durationMins = ((program.endMs - program.startMs) / 60_000L).coerceAtLeast(1)
        val durationText = if (durationMins >= 60) {
            "${durationMins / 60}ч ${durationMins % 60}м"
        } else {
            "$durationMins мин"
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${timeFmt.format(Date(program.startMs))} – ${timeFmt.format(Date(program.endMs))}",
                color = AccentBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "($durationText)",
                color = TextSecondary,
                fontSize = 9.5.sp,
            )
            if (!program.category.isNullOrBlank()) {
                Text(
                    text = "• ${program.category}",
                    color = ChannelDesignTokens.AccentGold,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(3.dp))
        HorizontalDivider(color = Color(0x2600E5FF), thickness = 0.8.dp)
        Spacer(Modifier.height(3.dp))

        // Full Description (Scrollable)
        val isCatchupAvailable = item is SidePanelItem.CatchupSlot && item.isAvailable
        val desc = program.description?.trim().orEmpty()
        val displayText = if (desc.isNotBlank()) {
            desc
        } else {
            buildDetailedProgramInfo(
                program = program,
                channelName = channelName,
                isLive = isLive,
                isPast = isPast,
                isCatchupAvailable = isCatchupAvailable,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            Text(
                text = displayText,
                color = if (desc.isNotBlank()) Color(0xFFD6E2F0) else Color(0xFFB8C7D9),
                fontSize = 11.sp,
                lineHeight = 14.5.sp,
            )
        }

        Spacer(Modifier.height(3.dp))

        // Remote Navigation Hints
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x2600E5FF))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "▲▼ Прокрутка • ◀ Назад",
                color = AccentColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "OK Воспроизвести",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LiveBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(LiveBadgeColor)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = "LIVE",
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun EmptyPanelContent(isLoading: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = AccentColor,
                    strokeWidth = 3.dp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Поиск телепрограммы в сети...",
                    color = AccentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Ищем программу передач в открытых источниках EPG",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.LiveTv,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Телепрограмма недоступна",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Для этого канала нет данных EPG в источнике",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private fun getProgramSubtitle(program: EpgProgram): String {
    val desc = program.description?.trim().orEmpty()
    if (desc.isNotBlank()) return desc

    val cat = program.category?.trim().orEmpty()
    val title = program.title.trim()

    val format = detectFormatFromTitle(title)
    return when {
        format.isNotEmpty() && cat.isNotEmpty() -> "$format • $cat"
        format.isNotEmpty() -> format
        cat.isNotEmpty() -> cat
        else -> ""
    }
}

private fun detectFormatFromTitle(title: String): String {
    val upper = title.uppercase()
    return when {
        upper.startsWith("Х/Ф") || upper.contains("Х/Ф ") || upper.contains("ХУДОЖЕСТВЕННЫЙ ФИЛЬМ") -> "Художественный фильм"
        upper.startsWith("Т/С") || upper.contains("Т/С ") || upper.contains("ТЕЛЕСЕРИАЛ") || upper.contains("СЕРИАЛ") -> "Телесериал"
        upper.startsWith("Д/Ф") || upper.contains("Д/Ф ") || upper.contains("ДОКУМЕНТАЛЬНЫЙ ФИЛЬМ") -> "Документальный фильм"
        upper.startsWith("М/Ф") || upper.contains("М/Ф ") || upper.contains("МУЛЬТФИЛЬМ") -> "Мультфильм"
        upper.startsWith("М/С") || upper.contains("М/С ") || upper.contains("МУЛЬТСЕРИАЛ") -> "Мультипликационный сериал"
        upper.startsWith("Д/С") || upper.contains("Д/С ") -> "Документальный сериал"
        upper.contains("НОВОСТИ") || upper.contains("ВЕСТИ") || upper.contains("СОБЫТИЯ") -> "Информационная программа"
        upper.contains("ШОУ") || upper.contains("КОНКУРС") -> "Развлекательное шоу"
        else -> ""
    }
}

private fun buildDetailedProgramInfo(
    program: EpgProgram,
    channelName: String,
    isLive: Boolean,
    isPast: Boolean,
    isCatchupAvailable: Boolean,
): String {
    val sb = StringBuilder()
    val title = program.title.trim()
    val format = detectFormatFromTitle(title)
    val cat = program.category?.trim().orEmpty()

    when {
        isLive -> sb.append("🔴 Сейчас в прямом эфире\n")
        isCatchupAvailable -> sb.append("📼 Доступна в архиве телепередач (повтор)\n")
        isPast -> sb.append("🕒 Прошедшая передача\n")
        else -> sb.append("📅 Скоро в эфире\n")
    }

    sb.append("Канал: $channelName\n")

    val typeDesc = when {
        format.isNotEmpty() && cat.isNotEmpty() -> "$format ($cat)"
        format.isNotEmpty() -> format
        cat.isNotEmpty() -> cat
        else -> null
    }
    if (typeDesc != null) {
        sb.append("Формат: $typeDesc\n")
    }

    val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(title)
    if (yearMatch != null) {
        sb.append("Год: ${yearMatch.value}\n")
    }

    val durationMins = ((program.endMs - program.startMs) / 60_000L).coerceAtLeast(1)
    val durationStr = if (durationMins >= 60) "${durationMins / 60} ч ${durationMins % 60} мин" else "$durationMins мин"
    sb.append("Длительность: $durationStr\n\n")

    sb.append("Подробный синопсис не указан провайдером телепрограммы (EPG) для данной передачи.")

    return sb.toString()
}
