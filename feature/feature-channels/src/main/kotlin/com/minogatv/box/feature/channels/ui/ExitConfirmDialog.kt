package com.minogatv.box.feature.channels.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minogatv.box.feature.channels.ChannelDesignTokens

/**
 * # ExitConfirmDialog
 *
 * In-window TV-optimized modal overlay asking for user confirmation before exiting the app.
 *
 * Rendered inside the Activity window (rather than a separate Dialog window) so hardware
 * remote controls on Xiaomi Mi TV Stick and Android TV boxes never lose window focus.
 *
 * - Defaults to "Выйти" (index 1) so pressing Back -> OK (or Back -> Back) exits immediately.
 * - D-Pad Left / Right toggles between "Отмена" (0) and "Выйти" (1).
 */
@Composable
fun ExitConfirmDialog(
    selectedButtonIndex: Int = 1,
    onSelectButtonIndex: (Int) -> Unit = {},
    onConfirmExit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val backdropInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xD9050811))
            .clickable(
                interactionSource = backdropInteraction,
                indication = null,
                onClick = onDismiss,
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val native = keyEvent.nativeKeyEvent
                if (native.action == KeyEvent.ACTION_DOWN) {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onSelectButtonIndex(0)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onSelectButtonIndex(1)
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE -> {
                            onConfirmExit()
                            return@onPreviewKeyEvent true
                        }
                    }
                } else if (native.action == KeyEvent.ACTION_UP) {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (selectedButtonIndex == 1) {
                                onConfirmExit()
                            } else {
                                onDismiss()
                            }
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_ESCAPE -> {
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 300.dp, max = 440.dp)
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFA0F172A))
                .border(
                    width = 1.8.dp,
                    color = ChannelDesignTokens.NeonCyan.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(20.dp),
                )
                .clickable(
                    interactionSource = cardInteraction,
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE53935).copy(alpha = 0.15f))
                    .border(1.5.dp, Color(0xFFE53935).copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Выход",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Выход из приложения",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Вы действительно хотите выйти из Minoga TV?",
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 19.sp,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = "OK или Back — Выйти  •  ◄ Отмена",
                color = Color(0xFF94A3B8),
                fontSize = 11.5.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Кнопка «Отмена» (Index 0)
                val isCancelFocused = selectedButtonIndex == 0
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onSelectButtonIndex(0)
                            onDismiss()
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isCancelFocused) Color(0x3300E5FF) else Color(0x1A8193B2),
                    border = BorderStroke(
                        width = if (isCancelFocused) 2.dp else 1.dp,
                        color = if (isCancelFocused) ChannelDesignTokens.NeonCyan else Color(0x338193B2),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Отмена",
                            color = if (isCancelFocused) Color.White else Color(0xFFCBD5E1),
                            fontSize = 14.sp,
                            fontWeight = if (isCancelFocused) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }

                // Кнопка «Выйти» (Index 1 — выбор по умолчанию)
                val isExitFocused = selectedButtonIndex == 1
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onSelectButtonIndex(1)
                            onConfirmExit()
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isExitFocused) Color(0x55FF5252) else Color(0x1AFF5252),
                    border = BorderStroke(
                        width = if (isExitFocused) 2.2.dp else 1.dp,
                        color = if (isExitFocused) Color(0xFFFF5252) else Color(0x44FF5252),
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Выйти",
                            color = if (isExitFocused) Color.White else Color(0xFFE57373),
                            fontSize = 14.sp,
                            fontWeight = if (isExitFocused) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
