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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minogatv.box.feature.update.AppUpdateManager
import kotlin.math.roundToInt

/**
 * Full-screen non-dismissible mandatory update screen.
 *
 * Automatically starts downloading and launching the APK installer when an update
 * has been pending for >= 30 days or when `localVersionCode < minRequiredVersionCode`.
 */
@Composable
fun ForceUpdateScreen(
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
    val isCompact = isPortrait || configuration.screenHeightDp < 500 || configuration.screenWidthDp < 700

    val updateState by AppUpdateManager.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val screenKeyDispatcher = LocalScreenKeyDispatcher.current

    // 0 = "Установить обновление" (primary), 1 = "Выйти"
    var focusedButtonIndex by remember { mutableIntStateOf(0) }
    var autoDownloadTriggered by remember { mutableStateOf(false) }

    // Ensure version info is loaded and automatically start downloading the mandatory update
    LaunchedEffect(Unit) {
        AppUpdateManager.ensureLocalVersionLoaded(context)
        if (updateState.remoteInfo == null && !updateState.isChecking) {
            AppUpdateManager.checkForUpdates(context, force = true)
        }
    }

    LaunchedEffect(updateState.remoteInfo, updateState.isChecking) {
        val remote = updateState.remoteInfo
        if (remote != null && !autoDownloadTriggered && !updateState.isDownloading) {
            autoDownloadTriggered = true
            AppUpdateManager.downloadAndInstall(context)
        }
    }

    val triggerPrimaryAction: () -> Unit = {
        val existingFile = updateState.downloadedApkFile
        when {
            updateState.isDownloading -> Unit
            existingFile != null && existingFile.exists() -> {
                AppUpdateManager.installApk(context, existingFile)
            }
            updateState.remoteInfo != null -> {
                AppUpdateManager.downloadAndInstall(context)
            }
            else -> {
                AppUpdateManager.checkForUpdates(context, force = true) { hasUpdate ->
                    if (hasUpdate) {
                        AppUpdateManager.downloadAndInstall(context)
                    }
                }
            }
        }
    }

    // Pressing Back does NOT bypass the mandatory update screen
    BackHandler {
        // Focus Exit button or exit if already focused
        if (focusedButtonIndex == 1) {
            onExitApp()
        } else {
            focusedButtonIndex = 1
        }
    }

    val handleKeyEvent: (KeyEvent) -> Boolean = { event ->
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_UP -> {
                    focusedButtonIndex = 0
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    focusedButtonIndex = 1
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (focusedButtonIndex == 0) {
                        triggerPrimaryAction()
                    } else {
                        onExitApp()
                    }
                    true
                }
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE -> {
                    if (focusedButtonIndex == 1) {
                        onExitApp()
                    } else {
                        focusedButtonIndex = 1
                    }
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
                horizontal = if (isCompact) 14.dp else 48.dp,
                vertical = if (isCompact) 16.dp else 32.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (isCompact) 1f else 0.78f),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF101624),
            border = BorderStroke(1.5.dp, Color(0x6600E5FF)),
            shadowElevation = 24.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isCompact) 18.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 40.dp else 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x2200E5FF))
                            .border(1.dp, Color(0x8800E5FF), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(if (isCompact) 22.dp else 26.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ОБЯЗАТЕЛЬНОЕ ОБНОВЛЕНИЕ",
                            color = Color(0xFF00E5FF),
                            fontSize = if (isCompact) 15.sp else 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp,
                        )
                        val remoteVer = updateState.remoteInfo?.versionName
                        Text(
                            text = if (!remoteVer.isNullOrBlank()) {
                                "Версия ${updateState.localVersionName} ➔ v$remoteVer"
                            } else {
                                "Текущая версия: ${updateState.localVersionName}"
                            },
                            color = Color(0xFF94A3B8),
                            fontSize = if (isCompact) 12.sp else 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0x26FFFFFF))
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Вышла новая обязательная версия Minoga TV Box (срок работы старой версии более 30 дней истёк). " +
                        "Для продолжения просмотра телеканалов необходимо установить обновление.",
                    color = Color(0xFFE2E8F0),
                    fontSize = if (isCompact) 13.sp else 14.5.sp,
                    lineHeight = if (isCompact) 18.sp else 21.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Progress / Status Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF090D16))
                        .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                ) {
                    val existingFile = updateState.downloadedApkFile
                    when {
                        updateState.isDownloading -> {
                            val pct = (updateState.downloadProgress * 100f).roundToInt().coerceIn(0, 100)
                            val dlMb = AppUpdateManager.formatSizeMb(updateState.downloadedBytes)
                            val totalMb = AppUpdateManager.formatSizeMb(updateState.totalBytes)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "Автоматическая загрузка обновления... $pct%",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (dlMb.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (totalMb.isNotBlank()) "$dlMb из $totalMb" else dlMb,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { updateState.downloadProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = Color(0xFF00E5FF),
                                    trackColor = Color(0xFF1E293B),
                                )
                            }
                        }

                        existingFile != null && existingFile.exists() -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "✅ Обновление скачано и готово к установке",
                                    color = Color(0xFF10B981),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Нажмите «Установить обновление» ниже и подтвердите установку в системном окне Android.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }

                        updateState.isChecking -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF00E5FF),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Подключение к серверу обновлений...",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        else -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                val err = updateState.errorMessage
                                Text(
                                    text = err ?: "Нажмите кнопку ниже для загрузки и установки актуальной версии.",
                                    color = if (err != null) Color(0xFFF87171) else Color(0xFFE2E8F0),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                val isPrimaryFocused = focusedButtonIndex == 0
                val isExitFocused = focusedButtonIndex == 1
                val existingFile = updateState.downloadedApkFile
                val primaryLabel = when {
                    updateState.isDownloading -> "Скачивание... ${(updateState.downloadProgress * 100f).roundToInt()}%"
                    existingFile != null && existingFile.exists() -> "Установить обновление"
                    updateState.errorMessage != null -> "Повторить загрузку"
                    else -> "Скачать и установить"
                }

                if (isPortrait) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isPrimaryFocused) Color(0xFF00E5FF) else Color(0xFF152238),
                                )
                                .border(
                                    width = if (isPrimaryFocused) 2.dp else 1.dp,
                                    color = if (isPrimaryFocused) Color.White else Color(0x6600E5FF),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable(enabled = !updateState.isDownloading) {
                                    focusedButtonIndex = 0
                                    triggerPrimaryAction()
                                }
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = primaryLabel,
                                color = if (isPrimaryFocused) Color(0xFF05070B) else Color(0xFFF1F5F9),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isExitFocused) Color(0xFFEF4444) else Color(0xFF161B26),
                                )
                                .border(
                                    width = if (isExitFocused) 2.dp else 1.dp,
                                    color = if (isExitFocused) Color.White else Color(0xFF2D3748),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    focusedButtonIndex = 1
                                    onExitApp()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Выйти из программы",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.4f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isPrimaryFocused) Color(0xFF00E5FF) else Color(0xFF152238),
                                )
                                .border(
                                    width = if (isPrimaryFocused) 2.dp else 1.dp,
                                    color = if (isPrimaryFocused) Color.White else Color(0x6600E5FF),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable(enabled = !updateState.isDownloading) {
                                    focusedButtonIndex = 0
                                    triggerPrimaryAction()
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = primaryLabel,
                                color = if (isPrimaryFocused) Color(0xFF05070B) else Color(0xFFF1F5F9),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isExitFocused) Color(0xFFEF4444) else Color(0xFF161B26),
                                )
                                .border(
                                    width = if (isExitFocused) 2.dp else 1.dp,
                                    color = if (isExitFocused) Color.White else Color(0xFF2D3748),
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable {
                                    focusedButtonIndex = 1
                                    onExitApp()
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Выйти из программы",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}

