package com.minogatv.box.feature.channels

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design system tokens and styling primitives for Minoga TV.
 * Implements the Midnight Obsidian + Glassmorphism + Electric Neon aesthetic.
 */
object ChannelDesignTokens {

    // ── Backgrounds ─────────────────────────────────────────────────────────────
    val MidnightBg              = Color(0xFF080A10)
    val MidnightBgGradientStart = Color(0xFF0D121F)
    val MidnightBgGradientEnd   = Color(0xFF06080D)

    // ── Surfaces & Glass ────────────────────────────────────────────────────────
    val CardGlassUnfocused      = Color(0xD9121624)
    val CardGlassFocused        = Color(0xFF1B3258)
    val TopBarGlass             = Color(0xE60A0D16)
    val SidePanelGlass          = Color(0xF50C101A)

    // ── Neon Accents ────────────────────────────────────────────────────────────
    val NeonCyan                = Color(0xFF00E5FF)
    val ElectricBlue            = Color(0xFF2979FF)
    val AccentGold              = Color(0xFFFFD54F)
    val AccentGoldGlow          = Color(0xFFFFA000)

    // ── Text Hierarchy ──────────────────────────────────────────────────────────
    val TextPrimary             = Color(0xFFF1F5F9)
    val TextSecondary           = Color(0xFF8E9EB5)
    val TextMuted               = Color(0xFF56657A)

    // ── Status & Badges ─────────────────────────────────────────────────────────
    val LiveRed                 = Color(0xFFFF1744)
    val CatchupGreen            = Color(0xFF00E676)
    val FavRed                  = Color(0xFFFF5252)
    val ArchiveBadgeBg          = Color(0x33455A64)
    val ArchiveBadgeText        = Color(0xFFCFD8DC)

    // ── Gradients & Brushes ─────────────────────────────────────────────────────
    val BackgroundGradient = Brush.verticalGradient(
        colors = listOf(MidnightBgGradientStart, MidnightBg, MidnightBgGradientEnd),
    )

    val NeonFocusBrush = Brush.linearGradient(
        colors = listOf(NeonCyan, ElectricBlue),
    )

    val FocusedRowBrush = Brush.horizontalGradient(
        colors = listOf(Color(0xFF203E78), Color(0xFF162544)),
    )

    val SubtleBorderBrush = Brush.linearGradient(
        colors = listOf(Color(0x3300E5FF), Color(0x1A2979FF)),
    )

    val FocusedCardGradient = Brush.verticalGradient(
        colors = listOf(Color(0x332979FF), CardGlassFocused),
    )
}

/**
 * Extension modifier that applies the TV neon glow border, smooth scale, and glass background
 * when an item is focused with the TV remote.
 */
fun Modifier.tvFocusCard(
    isFocused: Boolean,
    cornerRadius: Dp = 16.dp,
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.035f else 1.0f,
        animationSpec = tween(40),
        label = "tv_focus_scale",
    )
    val shape = RoundedCornerShape(cornerRadius)

    this
        .scale(scale)
        .clip(shape)
        .then(
            if (isFocused) Modifier.background(ChannelDesignTokens.FocusedRowBrush)
            else Modifier.background(ChannelDesignTokens.CardGlassUnfocused)
        )
        .border(
            width = if (isFocused) 2.5.dp else 1.dp,
            brush = if (isFocused) ChannelDesignTokens.NeonFocusBrush
                    else ChannelDesignTokens.SubtleBorderBrush,
            shape = shape,
        )
}
