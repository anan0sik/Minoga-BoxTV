package com.minogatv.box.feature.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * First-launch User Agreement and Disclaimer screen.
 *
 * Requires the user to accept the agreement (`accepted_terms = true`),
 * otherwise the application closes immediately.
 */
@Composable
fun DisclaimerScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val isCompact = isPortrait || configuration.screenHeightDp < 500 || configuration.screenWidthDp < 700

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val screenKeyDispatcher = LocalScreenKeyDispatcher.current

    var isChecked by remember { mutableStateOf(false) }
    // 0 = "Я согласен" (default focused), 1 = "Не согласен (Выйти)"
    var focusedButtonIndex by remember { mutableIntStateOf(0) }

    // Pressing system Back before accepting closes the app
    BackHandler {
        onDecline()
    }

    val handleKeyEvent: (KeyEvent) -> Boolean = { event ->
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    scope.launch {
                        scrollState.animateScrollTo((scrollState.value - 160).coerceAtLeast(0))
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    scope.launch {
                        scrollState.animateScrollTo((scrollState.value + 160).coerceAtMost(scrollState.maxValue))
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    focusedButtonIndex = 0
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    focusedButtonIndex = 1
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (focusedButtonIndex == 0) {
                        isChecked = true
                        onAccept()
                    } else {
                        onDecline()
                    }
                    true
                }
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE -> {
                    onDecline()
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }

    DisposableEffect(screenKeyDispatcher) {
        screenKeyDispatcher?.activeHandler = handleKeyEvent
        onDispose {
            if (screenKeyDispatcher?.activeHandler === handleKeyEvent) {
                screenKeyDispatcher.activeHandler = null
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF07090E), Color(0xFF0D1322), Color(0xFF07090E)),
                ),
            )
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                handleKeyEvent(keyEvent.nativeKeyEvent)
            }
            .padding(
                horizontal = if (isCompact) 12.dp else 40.dp,
                vertical = if (isCompact) 10.dp else 24.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(if (isCompact) 14.dp else 20.dp),
            color = Color(0xF20D1322),
            border = BorderStroke(1.5.dp, Color(0x4D00E5FF)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = if (isCompact) 14.dp else 28.dp,
                        vertical = if (isCompact) 12.dp else 20.dp,
                    ),
            ) {
                // ── Header ───────────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 34.dp else 42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x2600E5FF))
                            .border(1.dp, Color(0x6600E5FF), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Gavel,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(if (isCompact) 18.dp else 22.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ПОЛЬЗОВАТЕЛЬСКОЕ СОГЛАШЕНИЕ И ОТКАЗ ОТ ОТВЕТСТВЕННОСТИ (DISCLAIMER)",
                            color = Color(0xFF00E5FF),
                            fontSize = if (isCompact) 12.5.sp else 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = if (isCompact) 16.sp else 20.sp,
                        )
                    }
                }

                Spacer(Modifier.height(if (isCompact) 8.dp else 12.dp))
                HorizontalDivider(color = Color(0x2600E5FF), thickness = 1.dp)
                Spacer(Modifier.height(if (isCompact) 8.dp else 12.dp))

                // ── Scrollable Agreement Text ────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x8007090E))
                        .border(1.dp, Color(0x1F00E5FF), RoundedCornerShape(10.dp))
                        .verticalScroll(scrollState)
                        .padding(horizontal = if (isCompact) 12.dp else 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DisclaimerSection(
                        title = "1. Статус приложения",
                        body = "Приложение «Minoga TV Box» (далее — «Программа») представляет собой программный проигрыватель (медиаплеер), предназначенный для воспроизведения потокового аудио- и видеоконтента по протоколам, поддерживаемым сетью Интернет. Программа работает по принципу интернет-браузера и лишь визуализирует данные по ссылкам, указанным пользователем.",
                        isCompact = isCompact,
                    )

                    DisclaimerSection(
                        title = "2. Отсутствие медиаконтента и авторские права",
                        body = "• Программа не включает в себя какие-либо телеканалы, фильмы, трансляции, плейлисты или ссылки на них.\n" +
                            "• Разработчик Программы не является вещателем, провайдером IPTV, хостинг-провайдером или распространителем контента, а также не связан ни с одним поставщиком сторонних плейлистов.\n" +
                            "• Разработчик не контролирует, не модерирует и не несет ответственности за содержание, качество, легальность и доступность медиапотоков, которые пользователь открывает с помощью Программы.\n" +
                            "• Мы не поддерживаем и не поощряем потоковую передачу материалов, защищенных авторским правом, без разрешения правообладателя.",
                        isCompact = isCompact,
                    )

                    DisclaimerSection(
                        title = "3. Обязательства и ответственность пользователя",
                        body = "• Пользователь самостоятельно находит, формирует и загружает в Программу плейлисты и ссылки на медиапотоки.\n" +
                            "• Пользователь обязуется использовать в Программе только те материалы и потоки, на просмотр которых у него есть законное право (включая легальные подписки от официальных провайдеров или контент из общественного достояния).\n" +
                            "• Вся полнота юридической ответственности за возможное нарушение авторских или смежных прав при использовании сторонних плейлистов возлагается исключительно на пользователя.",
                        isCompact = isCompact,
                    )

                    DisclaimerSection(
                        title = "4. Ограничение ответственности («КАК ЕСТЬ»)",
                        body = "Программа предоставляется на условиях «КАК ЕСТЬ» (AS IS), без каких-либо прямых или косвенных гарантий. Разработчик не несет ответственности за любые прямые или косвенные убытки, перебои в работе, потерю данных или претензии третьих лиц, возникшие в результате использования или невозможности использования Программы.",
                        isCompact = isCompact,
                    )
                }

                Spacer(Modifier.height(if (isCompact) 8.dp else 12.dp))

                // ── Checkbox Row ─────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1A00E5FF))
                        .clickable { isChecked = !isChecked }
                        .padding(horizontal = 8.dp, vertical = if (isCompact) 2.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { isChecked = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF00E5FF),
                            uncheckedColor = Color(0xFF94A3B8),
                            checkmarkColor = Color(0xFF07090E),
                        ),
                    )
                    Text(
                        text = "Я прочитал(а) и принимаю условия Пользовательского соглашения",
                        color = Color.White,
                        fontSize = if (isCompact) 11.5.sp else 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(if (isCompact) 8.dp else 12.dp))

                // ── Action Buttons ("Я согласен" / "Выйти") ──────────────────
                val isAcceptFocused = focusedButtonIndex == 0
                val isDeclineFocused = focusedButtonIndex == 1

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Decline / Exit button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(if (isCompact) 40.dp else 46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onDecline() },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDeclineFocused) Color(0x33EF4444) else Color(0x1F8193B2),
                        border = BorderStroke(
                            width = if (isDeclineFocused) 2.dp else 1.dp,
                            color = if (isDeclineFocused) Color(0xFFEF4444) else Color(0x338193B2),
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Не согласен (Выйти)",
                                color = if (isDeclineFocused) Color(0xFFFF8A80) else Color(0xFFCBD5E1),
                                fontSize = if (isCompact) 12.sp else 14.sp,
                                fontWeight = if (isDeclineFocused) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }

                    // Accept button
                    Surface(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(if (isCompact) 40.dp else 46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                isChecked = true
                                onAccept()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAcceptFocused) Color(0xFF00E5FF) else Color(0x3300E5FF),
                        border = BorderStroke(
                            width = if (isAcceptFocused) 2.dp else 1.dp,
                            color = Color(0xFF00E5FF),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (isAcceptFocused) Color(0xFF07090E) else Color.White,
                                modifier = Modifier.size(if (isCompact) 16.dp else 18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Я согласен",
                                color = if (isAcceptFocused) Color(0xFF07090E) else Color.White,
                                fontSize = if (isCompact) 13.sp else 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclaimerSection(
    title: String,
    body: String,
    isCompact: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = Color(0xFFE5A00D),
            fontSize = if (isCompact) 12.5.sp else 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = Color(0xFFD6E2F0),
            fontSize = if (isCompact) 11.5.sp else 13.sp,
            lineHeight = if (isCompact) 16.sp else 19.sp,
        )
    }
}
