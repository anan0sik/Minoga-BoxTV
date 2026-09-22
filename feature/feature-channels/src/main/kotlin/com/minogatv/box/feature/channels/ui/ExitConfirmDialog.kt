package com.minogatv.box.feature.channels.ui

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.DialogProperties
import com.minogatv.box.feature.channels.ChannelDesignTokens

/**
 * # ExitConfirmDialog
 *
 * TV-optimized modal dialog asking for user confirmation before exiting the app.
 *
 * - D-Pad Left / Right toggles between "Отмена" (Cancel) and "Выйти" (Exit).
 * - D-Pad Center / Enter activates the selected button.
 * - Back button or Escape cancels and returns to the app.
 * - Defaults to "Отмена" to prevent accidental exits on double-tap of the Back button.
 */
@Composable
fun ExitConfirmDialog(
    onConfirmExit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 0 = Отмена (Cancel, default safe choice), 1 = Выйти (Exit)
    var selectedButtonIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            selectedButtonIndex = 0
                            return@onPreviewKeyEvent true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            selectedButtonIndex = 1
                            return@onPreviewKeyEvent true
                        }
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
                            onDismiss()
                            return@onPreviewKeyEvent true
                        }
                    }
                }
                false
            },
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
            containerColor = Color(0xF20F172A),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .width(440.dp)
                .border(
                    width = 1.5.dp,
                    color = ChannelDesignTokens.NeonCyan.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                ),
            icon = {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935).copy(alpha = 0.15f))
                        .border(1.5.dp, Color(0xFFE53935).copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Выход",
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(28.dp),
                    )
                }
            },
            title = {
                Text(
                    text = "Выход из приложения",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Вы действительно хотите выйти из Minoga TV?",
                        color = Color(0xFFCBD5E1),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "◄► Выбор кнопки  •  OK Подтвердить  •  Back Отмена",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Кнопка «Отмена» (Index 0) — безопасный выбор по умолчанию
                    val isCancelFocused = selectedButtonIndex == 0
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDismiss() },
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

                    // Кнопка «Выйти» (Index 1) — выход из приложения
                    val isExitFocused = selectedButtonIndex == 1
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onConfirmExit() },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isExitFocused) Color(0x44FF5252) else Color(0x1AFF5252),
                        border = BorderStroke(
                            width = if (isExitFocused) 2.dp else 1.dp,
                            color = if (isExitFocused) Color(0xFFFF5252) else Color(0x44FF5252),
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Выйти",
                                color = if (isExitFocused) Color(0xFFFF8A80) else Color(0xFFE57373),
                                fontSize = 14.sp,
                                fontWeight = if (isExitFocused) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            },
            dismissButton = null,
        )
    }
}
