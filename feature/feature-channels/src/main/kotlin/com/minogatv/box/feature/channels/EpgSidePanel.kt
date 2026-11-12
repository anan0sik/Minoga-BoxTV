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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
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
private val ArchiveAmber        = Color(0xFFF59E0B)
private val ArchiveAmberLight   = Color(0xFFFBBF24)
private val ArchiveRowBg        = Color(0x14F59E0B)

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
    onItemFocused: (Int) -> Unit = {},
    onHeaderActionClick: () -> Unit = {},
    isFocusInSidePanel: Boolean = true,
    isProgramDetailsOpen: Boolean = false,
    isLoadingEpg: Boolean = false,
    scaleFactor: Float = 1.0f,
    detailsScrollState: ScrollState = rememberScrollState(),
) {
    val listState = rememberLazyListState()

    // Display rows = programme entries + non-focusable day dividers (+ optional footer).
    // sidePanelItems indexes stay unchanged; focus index is mapped to the list row for scrolling.
    val panelRows = remember(items) { buildPanelRows(items, System.currentTimeMillis()) }

    // Auto-scroll to keep the focused item visible (show its day divider when it starts a new day)
    LaunchedEffect(focusedIndex, panelRows) {
        if (items.isNotEmpty()) {
            val idx = focusedIndex.coerceIn(0, items.lastIndex)
            var row = panelRows.focusToRow.getOrElse(idx) { idx }
            if (row > 0 && panelRows.rows.getOrNull(row - 1) is PanelRow.Divider) row -= 1
            listState.animateScrollToItem(row)
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
                    scaleFactor = scaleFactor,
                    onHeaderActionClick = onHeaderActionClick,
                )

                HorizontalDivider(color = DividerColor, thickness = 0.8.dp)

                // ── EPG + Catch-up list & Details ─────────────────────────────
                val isPortrait = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
                if (items.isEmpty()) {
                    EmptyPanelContent(isLoading = isLoadingEpg)
                } else if (isProgramDetailsOpen && isPortrait) {
                    // In portrait mode on a phone, show ProgramDetailsCard full-width instead of 35%/65% split
                    ProgramDetailsCard(
                        item = items.getOrNull(focusedIndex),
                        channelName = channelName,
                        scrollState = detailsScrollState,
                        onBackClick = onHeaderActionClick,
                        onPlayClick = { items.getOrNull(focusedIndex)?.let(onItemSelected) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
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
                            panelRowItems(
                                panelRows = panelRows,
                                entries = items,
                                focusedIndex = focusedIndex,
                                isFocusInSidePanel = isFocusInSidePanel,
                                scaleFactor = scaleFactor,
                                onItemFocused = onItemFocused,
                                onItemSelected = onItemSelected,
                            )
                        }

                        // Right 65%: Program details card
                        ProgramDetailsCard(
                            item = items.getOrNull(focusedIndex),
                            channelName = channelName,
                            scrollState = detailsScrollState,
                            onBackClick = onHeaderActionClick,
                            onPlayClick = { items.getOrNull(focusedIndex)?.let(onItemSelected) },
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
                        panelRowItems(
                            panelRows = panelRows,
                            entries = items,
                            focusedIndex = focusedIndex,
                            isFocusInSidePanel = isFocusInSidePanel,
                            scaleFactor = scaleFactor,
                            onItemFocused = onItemFocused,
                            onItemSelected = onItemSelected,
                        )
                    }
                }
            }
        }
    }
}

// ─── Display rows (entries + day dividers + footer) ──────────────────────────

private sealed class PanelRow {
    data class Entry(val index: Int, val key: String, val datePrefix: String) : PanelRow()
    data class Divider(val label: String, val key: String) : PanelRow()
    data object Footer : PanelRow()
}

private class PanelRows(val rows: List<PanelRow>, val focusToRow: IntArray)

private fun SidePanelItem.programOf(): EpgProgram = when (this) {
    is SidePanelItem.Programme -> program
    is SidePanelItem.CatchupSlot -> program
}

private fun buildPanelRows(items: List<SidePanelItem>, nowMs: Long): PanelRows {
    if (items.isEmpty()) return PanelRows(emptyList(), IntArray(0))
    val rows = ArrayList<PanelRow>(items.size + 12)
    val focusToRow = IntArray(items.size)
    var prevDay = Int.MIN_VALUE
    items.forEachIndexed { index, item ->
        val p = item.programOf()
        val day = EpgDateFormat.dayKey(p.startMs)
        if (prevDay != Int.MIN_VALUE && day != prevDay) {
            rows.add(PanelRow.Divider(EpgDateFormat.dayDividerLabel(p.startMs, nowMs), "d_$day"))
        }
        prevDay = day
        focusToRow[index] = rows.size
        rows.add(PanelRow.Entry(index, "p_${index}_${p.startMs}", EpgDateFormat.dayPrefix(p.startMs, nowMs)))
    }
    // Source publishes nothing further: tell the user instead of looking broken
    val lastEnd = items.maxOf { it.programOf().endMs }
    if (lastEnd - nowMs < 3L * 3600_000L) {
        rows.add(PanelRow.Footer)
    }
    return PanelRows(rows, focusToRow)
}

private fun androidx.compose.foundation.lazy.LazyListScope.panelRowItems(
    panelRows: PanelRows,
    entries: List<SidePanelItem>,
    focusedIndex: Int,
    isFocusInSidePanel: Boolean,
    scaleFactor: Float,
    onItemFocused: (Int) -> Unit,
    onItemSelected: (SidePanelItem) -> Unit,
) {
    items(
        count = panelRows.rows.size,
        key = { i ->
            when (val r = panelRows.rows[i]) {
                is PanelRow.Entry -> r.key
                is PanelRow.Divider -> r.key
                PanelRow.Footer -> "footer"
            }
        },
        contentType = { i ->
            when (panelRows.rows[i]) {
                is PanelRow.Entry -> 0
                is PanelRow.Divider -> 1
                PanelRow.Footer -> 2
            }
        },
    ) { i ->
        when (val row = panelRows.rows[i]) {
            is PanelRow.Divider -> DayDividerRow(label = row.label, scaleFactor = scaleFactor)
            PanelRow.Footer -> EpgEndFooter(scaleFactor = scaleFactor)
            is PanelRow.Entry -> {
                val index = row.index
                val item = entries.getOrNull(index) ?: return@items
                val isFocused = isFocusInSidePanel && index == focusedIndex
                val handleRowClick = {
                    if (!isFocused) {
                        onItemFocused(index)
                    } else {
                        onItemSelected(item)
                    }
                }
                when (item) {
                    is SidePanelItem.Programme ->
                        ProgrammeRow(
                            item = item,
                            isFocused = isFocused,
                            scaleFactor = scaleFactor,
                            datePrefix = row.datePrefix,
                            onClick = handleRowClick,
                        )

                    is SidePanelItem.CatchupSlot ->
                        CatchupSlotRow(
                            item = item,
                            isFocused = isFocused,
                            scaleFactor = scaleFactor,
                            datePrefix = row.datePrefix,
                            onClick = handleRowClick,
                        )
                }
            }
        }
    }
}

@Composable
private fun DayDividerRow(label: String, scaleFactor: Float) {
    val isCompact = scaleFactor <= 0.65f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = if (isCompact) 3.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(AccentColor.copy(alpha = 0.25f)))
        Text(
            text = label,
            color = AccentColor.copy(alpha = 0.85f),
            fontSize = if (isCompact) 8.5.sp else 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(AccentColor.copy(alpha = 0.25f)))
    }
}

@Composable
private fun EpgEndFooter(scaleFactor: Float) {
    val isCompact = scaleFactor <= 0.65f
    Text(
        text = "Программа передач дальше не опубликована источником EPG",
        color = TextSecondary.copy(alpha = 0.7f),
        fontSize = if (isCompact) 8.5.sp else 9.5.sp,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun PanelHeader(
    channelName: String,
    isFocusInSidePanel: Boolean,
    isProgramDetailsOpen: Boolean = false,
    scaleFactor: Float = 1.0f,
    onHeaderActionClick: () -> Unit = {},
) {
    val isCompact = scaleFactor <= 0.65f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (isCompact) 8.dp else 12.dp,
                vertical = if (isCompact) 4.dp else 6.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LiveTv,
                    contentDescription = null,
                    tint = AccentColor,
                    modifier = Modifier.size(if (isCompact) 13.dp else 16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (isCompact && channelName.isNotBlank()) {
                        "ПРОГРАММА • $channelName"
                    } else {
                        "ТЕЛЕПРОГРАММА И АРХИВ"
                    },
                    color = if (isCompact && channelName.isNotBlank()) TextPrimary else AccentColor,
                    fontSize = if (isCompact) 10.5.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Remote hint pill (also clickable by touch)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isFocusInSidePanel) Color(0x3300E5FF) else Color(0x1F8193B2),
                border = BorderStroke(1.dp, if (isFocusInSidePanel) AccentColor else Color(0x338193B2)),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onHeaderActionClick() },
            ) {
                Text(
                    text = when {
                        isProgramDetailsOpen -> "◀ В список"
                        isFocusInSidePanel -> "▶ Инфо / ◀ Каналы"
                        else -> "▶ В программу"
                    },
                    color = if (isFocusInSidePanel) Color.White else TextSecondary,
                    fontSize = if (isCompact) 8.5.sp else 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(
                        horizontal = if (isCompact) 5.dp else 6.dp,
                        vertical = if (isCompact) 1.5.dp else 2.dp,
                    ),
                )
            }
        }

        if (!isCompact) {
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
}

@Composable
private fun ProgrammeRow(
    item: SidePanelItem.Programme,
    isFocused: Boolean,
    scaleFactor: Float = 1.0f,
    datePrefix: String = "",
    onClick: () -> Unit,
) {
    // Past programmes without archive stay grey and muted
    val isPast = !item.isLive && item.program.endMs <= System.currentTimeMillis()
    val bg = if (isFocused) SelectedItemBg else if (isPast) Color(0x330F131D) else Color(0x55131825)
    val borderColor = if (isFocused) AccentColor else Color(0x1A00E5FF)
    val isCompact = scaleFactor <= 0.65f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((6 * scaleFactor).coerceAtLeast(4f).dp))
            .background(bg)
            .border(
                width = if (isFocused) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape((6 * scaleFactor).coerceAtLeast(4f).dp),
            )
            .clickable { onClick() }
            .focusable()
            .padding(
                horizontal = if (isCompact) 6.dp else 8.dp,
                vertical = if (isCompact) 2.dp else 3.dp,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isFocused) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(if (isCompact) 16.dp else 22.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(AccentColor),
                )
                Spacer(Modifier.width(5.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                // Line 1: Time + Live badge + Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    if (datePrefix.isNotEmpty()) {
                        Text(
                            text = datePrefix,
                            color = TextSecondary.copy(alpha = 0.75f),
                            fontSize = if (isCompact) 8.5.sp else 9.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Text(
                        text = EpgDateFormat.timeRange(item.program.startMs, item.program.endMs),
                        color = if (item.isLive || isFocused) AccentColor else TextSecondary,
                        fontSize = if (isCompact) 9.5.sp else 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (item.isLive) {
                        LiveBadge()
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = item.program.title,
                        color = when {
                            isFocused -> Color.White
                            isPast -> TextSecondary
                            else -> TextPrimary
                        },
                        fontSize = if (isCompact) 10.5.sp else 11.5.sp,
                        fontWeight = if (item.isLive || isFocused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Line 2: Abbreviated description (shown always on TV scale, or on focused/live row in compact phone mode)
                val sub = getProgramSubtitle(item.program)
                if (sub.isNotEmpty() && (!isCompact || isFocused || item.isLive)) {
                    Text(
                        text = sub,
                        color = if (isFocused) Color(0xFFB0C4DE) else TextSecondary.copy(alpha = 0.8f),
                        fontSize = if (isCompact) 8.5.sp else 9.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Live progress bar
                if (item.isLive && item.progress > 0f) {
                    Spacer(Modifier.height(1.5.dp))
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
    datePrefix: String = "",
    onClick: () -> Unit,
) {
    // Archive rows (actually playable catch-up): warm amber tint + 3dp amber left bar.
    // Focused state keeps the cyan focus border AND the amber bar so it's still clearly "archive".
    val bg = if (isFocused) SelectedItemBg else ArchiveRowBg
    val borderColor = if (isFocused) AccentColor else ArchiveAmber.copy(alpha = 0.22f)
    val isCompact = scaleFactor <= 0.65f
    val shape = RoundedCornerShape((6 * scaleFactor).coerceAtLeast(4f).dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(
                width = if (isFocused) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = shape,
            )
            .clickable { onClick() }
            .focusable(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Amber archive bar (full row height)
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(ArchiveAmber),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (isCompact) 5.dp else 7.dp,
                        end = if (isCompact) 6.dp else 8.dp,
                        top = if (isCompact) 2.dp else 3.dp,
                        bottom = if (isCompact) 2.dp else 3.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Line 1: Date + Time + Archive badge + Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (datePrefix.isNotEmpty()) {
                            Text(
                                text = datePrefix,
                                color = ArchiveAmber.copy(alpha = 0.65f),
                                fontSize = if (isCompact) 8.5.sp else 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Text(
                            text = EpgDateFormat.timeRange(item.program.startMs, item.program.endMs),
                            color = if (isFocused) ArchiveAmberLight else ArchiveAmber,
                            fontSize = if (isCompact) 9.5.sp else 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(ArchiveAmber.copy(alpha = 0.2f))
                                .border(0.6.dp, ArchiveAmber.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 0.5.dp),
                        ) {
                            Text(
                                text = if (isCompact) "▶ АРХ" else "▶ АРХИВ",
                                color = ArchiveAmberLight,
                                fontSize = if (isCompact) 7.5.sp else 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = item.program.title,
                            color = if (isFocused) Color.White else TextPrimary,
                            fontSize = if (isCompact) 10.5.sp else 11.5.sp,
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // Line 2: Abbreviated description (always on TV scale, or on focused row in compact phone mode)
                    val sub = getProgramSubtitle(item.program)
                    if (sub.isNotEmpty() && (!isCompact || isFocused)) {
                        Text(
                            text = sub,
                            color = if (isFocused) Color(0xFFB0C4DE) else TextSecondary.copy(alpha = 0.8f),
                            fontSize = if (isCompact) 8.5.sp else 9.5.sp,
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
                        tint = ArchiveAmberLight,
                        modifier = Modifier.size(if (isCompact) 14.dp else 16.dp),
                    )
                }
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
    onBackClick: () -> Unit = {},
    onPlayClick: () -> Unit = {},
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
                text = EpgDateFormat.programLabel(program.startMs, program.endMs, separator = " – "),
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

        // Remote & Touch Navigation Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x2600E5FF))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "◀ К списку передач",
                color = AccentColor,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onBackClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            Text(
                text = "▶ Смотреть",
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onPlayClick() }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
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
