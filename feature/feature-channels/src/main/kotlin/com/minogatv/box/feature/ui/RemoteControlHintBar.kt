package com.minogatv.box.feature.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Single D-Pad / Remote control button hint.
 *
 * @param keyLabel  e.g. "▲▼", "OK", "◀", "▶", "Назад"
 * @param action    e.g. "Канал", "Смотреть", "Инфо", "Категории"
 * @param keyColor  Custom badge color for special buttons (e.g. red button for favorites)
 */
data class RemoteHint(
    val keyLabel: String,
    val action: String,
    val keyColor: Color = Color(0xFFE5A00D), // Gold/Accent
)

/**
 * Bottom Remote Control Guide bar displayed on TV box screens.
 * Helps users navigate using the Xiaomi Mi Box 4K remote control.
 */
@Composable
fun RemoteControlHintBar(
    hints: List<RemoteHint>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Color(0xEA080B13)) // Deep glassmorphism
            .border(width = 1.dp, color = Color(0x1F00E5FF), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            hints.forEachIndexed { index, hint ->
                RemoteHintPill(hint = hint)
                if (index < hints.lastIndex) {
                    Text(
                        text = "•",
                        color = Color(0xFF2A364F),
                        modifier = Modifier.padding(horizontal = 8.dp),
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteHintPill(hint: RemoteHint) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Remote button cap
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF131825))
                .border(1.dp, hint.keyColor.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = hint.keyLabel,
                color = hint.keyColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }

        // Action explanation
        Text(
            text = hint.action,
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
