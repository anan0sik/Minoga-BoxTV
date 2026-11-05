package com.minogatv.box.feature.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Direct activity-level [KeyEvent] dispatcher registry.
 *
 * On touch devices (phones/tablets), touching the screen switches Jetpack Compose into
 * `InputMode.Touch`, which clears Compose focus and prevents `Modifier.onPreviewKeyEvent`
 * from receiving synthetic or hardware D-pad key events. Registering a screen key handler
 * here ensures D-pad and gesture keys are always delivered regardless of `InputMode`.
 */
class ScreenKeyDispatcher {
    var activeHandler: ((KeyEvent) -> Boolean)? = null

    fun dispatch(event: KeyEvent): Boolean = activeHandler?.invoke(event) == true
}

val LocalScreenKeyDispatcher = staticCompositionLocalOf<ScreenKeyDispatcher?> { null }

/**
 * Single D-Pad / Remote control button hint (also directly tappable on touch screens).
 *
 * @param keyLabel  e.g. "▲▼", "OK", "◀", "▶", "Назад"
 * @param action    e.g. "Канал", "Смотреть", "Инфо", "Категории"
 * @param keyColor  Custom badge color for special buttons (e.g. red button for favorites)
 * @param onClick   Optional click action when tapped with a finger on touch devices
 */
data class RemoteHint(
    val keyLabel: String,
    val action: String,
    val keyColor: Color = Color(0xFFE5A00D), // Gold/Accent
    val onClick: (() -> Unit)? = null,
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
    val config = LocalConfiguration.current
    val isCompact = config.screenHeightDp < 500 || config.screenWidthDp < 500
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isCompact) 32.dp else 42.dp)
            .background(Color(0xEA080B13)) // Deep glassmorphism
            .border(width = 1.dp, color = Color(0x1F00E5FF), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .padding(horizontal = if (isCompact) 6.dp else 16.dp, vertical = if (isCompact) 2.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            hints.forEachIndexed { index, hint ->
                RemoteHintPill(hint = hint, isCompact = isCompact)
                if (index < hints.lastIndex) {
                    Text(
                        text = "•",
                        color = Color(0xFF2A364F),
                        modifier = Modifier.padding(horizontal = if (isCompact) 4.dp else 8.dp),
                        fontSize = if (isCompact) 10.sp else 12.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteHintPill(
    hint: RemoteHint,
    isCompact: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .then(
                if (hint.onClick != null) {
                    Modifier.clickable { hint.onClick.invoke() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = if (isCompact) 3.dp else 4.dp, vertical = if (isCompact) 1.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp),
    ) {
        // Remote button cap
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF131825))
                .border(1.dp, hint.keyColor.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                .padding(horizontal = if (isCompact) 5.dp else 6.dp, vertical = if (isCompact) 1.dp else 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = hint.keyLabel,
                color = hint.keyColor,
                fontSize = if (isCompact) 9.5.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }

        // Action explanation
        Text(
            text = hint.action,
            color = Color(0xFFE2E8F0),
            fontSize = if (isCompact) 10.5.sp else 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
