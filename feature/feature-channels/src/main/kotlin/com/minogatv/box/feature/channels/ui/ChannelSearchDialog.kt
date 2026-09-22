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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ChannelDesignTokens.MidnightBgGradientStart.copy(alpha = 0.95f))
                .padding(horizontal = 48.dp, vertical = 24.dp),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Search Field and Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
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

                    Spacer(Modifier.width(16.dp))

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E2638)),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть", tint = Color.White)
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
                        items(
                            items = filtered,
                            key = { it.channel.id },
                        ) { item ->
                            SearchResultItem(
                                item = item,
                                onClick = { onSelectChannel(item.channel.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    item: ChannelDisplayItem,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusCard(isFocused = isFocused, cornerRadius = 14.dp)
            .clickable(onClick = onClick),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Channel Logo
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2436)),
                contentAlignment = Alignment.Center,
            ) {
                if (!item.channel.logoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = item.channel.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp),
                    )
                } else {
                    Icon(Icons.Filled.Tv, contentDescription = null, tint = Color(0xFF2979FF), modifier = Modifier.size(22.dp))
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
