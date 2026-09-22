package com.minogatv.box.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import com.minogatv.box.core.model.enums.CatchupType
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.minogatv.box.feature.channels.ChannelDesignTokens
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.minogatv.box.feature.ui.RemoteControlHintBar
import com.minogatv.box.feature.ui.RemoteHint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    channelId: Long = 0L,
    catchupStartMs: Long? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    LaunchedEffect(channelId, catchupStartMs) {
        if (channelId > 0L) {
            viewModel.setChannelId(channelId, catchupStartMs)
        }
    }

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val currentUiState by rememberUpdatedState(uiState)
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val prefs = remember { context.getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE) }
    val seekStepSeconds = remember(uiState.isCatchupMode) { prefs.getInt("seek_step_seconds", 10) }
    val seekStepMs = seekStepSeconds * 1000L

    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var pendingSeekTargetTimeMs by remember { mutableStateOf<Long?>(null) }
    var seekRepeatCount by remember { mutableIntStateOf(0) }
    var seekDebounceJob by remember { mutableStateOf<Job?>(null) }
    var lastLoadedStreamUrl by remember { mutableStateOf<String?>(null) }

    var playerPositionMs by remember { mutableLongStateOf(0L) }
    var playerDurationMs by remember { mutableLongStateOf(0L) }

    BackHandler {
        if (uiState.isCatchupMode) {
            pendingSeekTargetTimeMs = null
            seekRepeatCount = 0
            seekDebounceJob?.cancel()
            seekFeedbackText = null
            viewModel.switchToLive()
        } else {
            onBack()
        }
    }

    // ─── Auto-Reconnect Policy ────────────────────────────────────────────────
    val loadErrorPolicy = remember {
        MinogaLoadErrorPolicy { attempt ->
            viewModel.onReconnectAttempt(attempt)
        }
    }

    // ─── Initialize ExoPlayer for FHD & 4K IPTV Streams ──────────────────────
    val exoPlayer = remember {
        // 1. Hardware-accelerated decoders with fallback for FHD & 4K streams
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true) // Crucial for 4K / 1080p60: fallback if primary hardware codec fails
        }

        // 2. TrackSelector optimized for TV box displays without crashing on tunneling
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTunnelingEnabled(false) // Tunneling breaks on Amlogic TV boxes
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setAllowMultipleAdaptiveSelections(true),
            )
        }

        // 3. High-throughput buffer control for heavy FHD and 4K streams
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 4. HTTP Data Source with cross-protocol redirect support and IPTV-accepted User-Agent
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                uiState.playlist?.userAgent?.takeIf { it.isNotBlank() }
                    ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            )
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)

        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorPolicy)

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    // ─── Setup Player Listener ────────────────────────────────────────────────
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> viewModel.onPlayerStateChanged(isPlaying = false, isBuffering = true)
                    Player.STATE_READY -> {
                        viewModel.onPlayerStateChanged(isPlaying = true, isBuffering = false)
                        viewModel.retry() // Resets reconnect attempt counter on successful playback
                    }
                    Player.STATE_ENDED -> viewModel.onPlayerStateChanged(isPlaying = false, isBuffering = false)
                    Player.STATE_IDLE -> Unit
                }
            }

            override fun onRenderedFirstFrame() {
                viewModel.onPlayerStateChanged(isPlaying = true, isBuffering = false)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    viewModel.onPlayerStateChanged(isPlaying = true, isBuffering = false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("PlayerScreen", "Player error: code=${error.errorCodeName} (${error.errorCode})", error)
                val attemptCount = currentUiState.reconnectAttempt + 1
                if (attemptCount <= MinogaLoadErrorPolicy.MAX_RETRIES) {
                    viewModel.onReconnectAttempt(attemptCount)
                    // Exponential backoff delay is handled by posting a delayed retry
                    val delayMs = (1000L * (1L shl (attemptCount - 1))).coerceAtMost(16_000L)
                    exoPlayer.playWhenReady = true
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val state = currentUiState
                        val isCatchup = state.isCatchupMode || state.catchupStartMs != null
                        val streamUrl = if (isCatchup) state.catchupUrl else state.channel?.streamUrl
                        if (!streamUrl.isNullOrEmpty()) {
                            lastLoadedStreamUrl = streamUrl
                            exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
                            exoPlayer.prepare()
                            exoPlayer.play()
                        } else {
                            viewModel.onPlayerError(IptvMediaSourceHelper.parsePlaybackError(error))
                        }
                    }, delayMs)
                } else {
                    viewModel.onPlayerError(IptvMediaSourceHelper.parsePlaybackError(error))
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val hasVideo = tracks.groups.any { group ->
                    val trackType = group.type
                    trackType == androidx.media3.common.C.TRACK_TYPE_VIDEO && group.length > 0
                }
                viewModel.onTracksDetected(hasVideo)
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // ─── Track Player Position & Duration for Archive Playback ───────────────
    LaunchedEffect(exoPlayer, uiState.isPlaying, uiState.isCatchupMode) {
        while (true) {
            playerPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            playerDurationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(1000L)
        }
    }

    // ─── Absolute Timeline Seek Handler for Archive Mode ─────────────────────
    val nowMs = System.currentTimeMillis()
    val archiveDays = if ((uiState.channel?.catchupDays ?: 0) > 0) uiState.channel!!.catchupDays else 7
    val minArchiveMs = nowMs - (archiveDays * 86_400_000L)
    val maxArchiveMs = nowMs - 15_000L

    val currentStreamTimeMs = if (uiState.isCatchupMode) {
        (uiState.catchupStreamStartMs ?: uiState.catchupProgramStartMs ?: nowMs) + playerPositionMs
    } else {
        nowMs
    }

    val onSeek: (Boolean, Boolean) -> Unit = { forward, isRepeat ->
        val baseTargetTime = pendingSeekTargetTimeMs ?: currentStreamTimeMs

        if (isRepeat) {
            seekRepeatCount++
        } else {
            seekRepeatCount = 0
        }

        // Accelerate when key is held down continuously
        val speedMultiplier = when {
            seekRepeatCount > 30 -> 6
            seekRepeatCount > 18 -> 4
            seekRepeatCount > 8  -> 2
            else -> 1
        }
        val step = seekStepMs * speedMultiplier
        val delta = if (forward) step else -step
        val newTarget = (baseTargetTime + delta).coerceIn(minArchiveMs, maxArchiveMs)
        pendingSeekTargetTimeMs = newTarget

        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFmt = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())
        val isToday = android.text.format.DateUtils.isToday(newTarget)
        val targetTimeStr = if (isToday) timeFmt.format(Date(newTarget)) else dateFmt.format(Date(newTarget))

        val totalDeltaMs = newTarget - currentStreamTimeMs
        val totalDeltaSec = kotlin.math.abs(totalDeltaMs) / 1000L
        val sign = if (totalDeltaMs >= 0) "+" else "-"
        val deltaStr = if (totalDeltaSec >= 60) {
            val mins = totalDeltaSec / 60
            val secs = totalDeltaSec % 60
            if (secs > 0) "$sign$mins мин $secs сек" else "$sign$mins мин"
        } else {
            "$sign$totalDeltaSec сек"
        }

        seekFeedbackText = "$deltaStr  ($targetTimeStr)"
        viewModel.showOsd()

        seekDebounceJob?.cancel()
        seekDebounceJob = coroutineScope.launch {
            delay(450L) // 450ms debounce for rapid or continuous remote control key holding
            val finalTarget = newTarget
            pendingSeekTargetTimeMs = null
            seekRepeatCount = 0
            viewModel.seekCatchupTo(finalTarget)
            delay(1500L)
            seekFeedbackText = null
        }
    }

    // ─── Update Stream when Channel or Catch-up URL Changes ───────────────────
    LaunchedEffect(uiState.channel?.streamUrl, uiState.catchupUrl, uiState.isCatchupMode, uiState.catchupStartMs) {
        val isCatchup = uiState.isCatchupMode || uiState.catchupStartMs != null || catchupStartMs != null
        val streamUrl = if (isCatchup) {
            uiState.catchupUrl
        } else {
            uiState.channel?.streamUrl
        }
        if (!streamUrl.isNullOrEmpty() && streamUrl != lastLoadedStreamUrl) {
            lastLoadedStreamUrl = streamUrl
            val mediaItem = MediaItem.fromUri(streamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // ─── Listen for Sleep Timer auto-exit ────────────────────────────────────
    LaunchedEffect(viewModel) {
        viewModel.exitPlayerEvent.collect {
            onBack()
        }
    }

    // ─── Listen for stream switch events (Catch-up / Live) ──────────────────
    LaunchedEffect(viewModel) {
        viewModel.streamSwitchEvent.collect { newUrl ->
            if (newUrl.isNotEmpty() && newUrl != lastLoadedStreamUrl) {
                lastLoadedStreamUrl = newUrl
                val mediaItem = MediaItem.fromUri(newUrl)
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    // ─── Auto-focus for D-Pad Remote ──────────────────────────────────────────
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    // Any key press resets the screensaver timer
                    viewModel.onUserInteraction()

                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!uiState.isCatchupMode) {
                                if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                                    viewModel.previousChannel()
                                } else {
                                    viewModel.nextChannel()
                                }
                                true
                            } else {
                                viewModel.showOsd()
                                true
                            }
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                            val hasCatchup = uiState.isCatchupMode || uiState.isArchiveSupported || (uiState.channel?.hasArchive == true)

                            if (hasCatchup) {
                                val isRepeat = event.nativeKeyEvent.repeatCount > 0
                                onSeek(true, isRepeat)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                            val hasCatchup = uiState.isCatchupMode || uiState.isArchiveSupported || (uiState.channel?.hasArchive == true)

                            if (hasCatchup) {
                                val isRepeat = event.nativeKeyEvent.repeatCount > 0
                                onSeek(false, isRepeat)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            viewModel.toggleOsd()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_MEDIA_PLAY,
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                // When pausing live TV with catch-up support, switch to timeshift
                                if (!uiState.isCatchupMode && (uiState.isArchiveSupported || uiState.channel?.hasArchive == true)) {
                                    viewModel.switchToCatchup()
                                }
                            } else {
                                exoPlayer.play()
                            }
                            viewModel.showOsd()
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            // If in catch-up mode, go back to live first
                            if (uiState.isCatchupMode) {
                                pendingSeekTargetTimeMs = null
                                seekRepeatCount = 0
                                seekDebounceJob?.cancel()
                                seekFeedbackText = null
                                viewModel.switchToLive()
                                true
                            } else {
                                onBack()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            },
    ) {
        // ─── Surface View Video Player ────────────────────────────────────────
        val resizeMode = when (uiState.aspectRatio) {
            VideoAspectRatio.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            VideoAspectRatio.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            VideoAspectRatio.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            VideoAspectRatio.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    isFocusable = false
                    isFocusableInTouchMode = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { playerView ->
                playerView.resizeMode = resizeMode
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ─── Buffering Indicator (with reconnect counter) ─────────────────────
        if (uiState.isBuffering && !uiState.isPlaying && uiState.errorMessage == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFFE5A00D),
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(56.dp),
                    )
                    if (uiState.reconnectAttempt > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Переподключение ${uiState.reconnectAttempt}/${MinogaLoadErrorPolicy.MAX_RETRIES}...",
                            color = Color(0xFFE5A00D),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // ─── Audio-only (Radio) Mode Overlay ──────────────────────────────────
        if (uiState.isAudioOnly && uiState.isPlaying && uiState.errorMessage == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0D1117)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Channel logo or radio icon
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1E2533)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!uiState.channel?.logoUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = uiState.channel!!.logoUrl,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                contentScale = ContentScale.Fit,
                            )
                        } else {
                            Text(
                                text = "📻",
                                fontSize = 48.sp,
                            )
                        }
                    }
                    Text(
                        text = uiState.channel?.name ?: "Радио",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Аудиопоток",
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp,
                    )
                }
            }
        }

        // ─── Error Overlay ────────────────────────────────────────────────────
        if (uiState.errorMessage != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xCC0D1117)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(32.dp),
                ) {
                    Text(
                        text = "Ошибка воспроизведения",
                        color = Color(0xFFFF5252),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        color = Color(0xFF8B949E),
                        fontSize = 14.sp,
                    )
                    Button(
                        onClick = {
                            viewModel.retry()
                            val isCatchup = uiState.isCatchupMode || uiState.catchupStartMs != null
                            val streamUrl = if (isCatchup) uiState.catchupUrl else uiState.channel?.streamUrl
                            if (!streamUrl.isNullOrEmpty()) {
                                lastLoadedStreamUrl = streamUrl
                                exoPlayer.setMediaItem(IptvMediaSourceHelper.buildMediaItem(streamUrl))
                                exoPlayer.prepare()
                                exoPlayer.play()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D)),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("Повторить", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ─── On-Screen Display (OSD) Overlay ──────────────────────────────────
        AnimatedVisibility(
            visible = uiState.isOsdVisible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC000000),
                                Color.Transparent,
                                Color.Transparent,
                                Color(0xF0000000),
                            ),
                        ),
                    ),
            ) {
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = Color.White,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${uiState.channelIndex + 1}. ${uiState.channel?.name.orEmpty()}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        uiState.channel?.groupTitle?.takeIf { it.isNotEmpty() }?.let { grp ->
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFF222938))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text(grp, color = Color(0xFFE5A00D), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Top Right: Aspect Ratio, Sleep Timer, PiP & Clock
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Aspect Ratio button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xCC131826))
                                .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
                                .clickable { viewModel.cycleAspectRatio() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = com.minogatv.box.feature.channels.ChannelDesignTokens.NeonCyan, modifier = Modifier.size(16.dp))
                            Text(uiState.aspectRatio.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        // Sleep Timer button
                        val isSleepActive = uiState.sleepTimerMinutes > 0
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSleepActive) Color(0x66FFD54F) else Color(0xCC131826))
                                .border(1.dp, if (isSleepActive) com.minogatv.box.feature.channels.ChannelDesignTokens.AccentGold else Color(0x3300E5FF), RoundedCornerShape(8.dp))
                                .clickable { viewModel.cycleSleepTimer() }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Filled.Timer, contentDescription = null, tint = if (isSleepActive) com.minogatv.box.feature.channels.ChannelDesignTokens.AccentGold else Color.White, modifier = Modifier.size(16.dp))
                            Text(
                                if (isSleepActive) "${uiState.sleepTimerMinutes} мин" else "Сон",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        // Picture in Picture button
                        val pipEnabled = remember { prefs.getBoolean("pip_enabled", true) }
                        if (pipEnabled) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xCC131826))
                                    .border(1.dp, Color(0x3300E5FF), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val activity = context as? Activity
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            activity?.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Icon(Icons.Filled.PictureInPicture, contentDescription = "PiP", tint = com.minogatv.box.feature.channels.ChannelDesignTokens.NeonCyan, modifier = Modifier.size(16.dp))
                                Text("PiP", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Current clock
                        val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                        Text(
                            text = timeFmt.format(Date()),
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // Bottom Panel: Program details + Remote Hint Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                ) {
                    // Channel Info & EPG
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Channel Logo
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF1E2533)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (!uiState.channel?.logoUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = uiState.channel!!.logoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(46.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFFE5A00D),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        // Program titles & Progress
                        Column(modifier = Modifier.weight(1f)) {
                            val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

                            val currProg = uiState.currentProgram
                            if (currProg != null) {
                                val startStr = timeFormat.format(Date(currProg.startMs))
                                val endStr   = timeFormat.format(Date(currProg.endMs))

                                val activeTimestamp = pendingSeekTargetTimeMs ?: currentStreamTimeMs
                                val progDurationMs = (currProg.endMs - currProg.startMs).coerceAtLeast(1L)
                                val progPosMs = (activeTimestamp - currProg.startMs).coerceIn(0L, progDurationMs)

                                val posSec = progPosMs / 1000L
                                val durSec = progDurationMs / 1000L
                                val timePosStr = String.format(Locale.getDefault(), "%02d:%02d / %02d:%02d", posSec / 60L, posSec % 60L, durSec / 60L, durSec % 60L)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (uiState.isCatchupMode) {
                                        Text(
                                            text = "⏪ $startStr - $endStr  ${currProg.title}  ($timePosStr)",
                                            color = ChannelDesignTokens.NeonCyan,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    } else {
                                        Text(
                                            text = "$startStr - $endStr  ${currProg.title}",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }

                                Spacer(Modifier.height(6.dp))

                                val progressFraction = if (uiState.isCatchupMode) {
                                    (progPosMs.toFloat() / progDurationMs.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    uiState.epgProgress
                                }

                                LinearProgressIndicator(
                                    progress = { progressFraction },
                                    color = ChannelDesignTokens.NeonCyan,
                                    trackColor = Color(0x332A374E),
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(4.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                val activeTimestamp = pendingSeekTargetTimeMs ?: currentStreamTimeMs
                                val timeFmt = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()) }
                                val dateStr = timeFmt.format(Date(activeTimestamp))
                                Text(
                                    text = if (uiState.isCatchupMode) "⏪ Архив: $dateStr" else (uiState.channel?.name ?: "Прямой эфир"),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }

                            // Next program preview
                            val nxtProg = uiState.nextProgram
                            if (nxtProg != null) {
                                Spacer(Modifier.height(4.dp))
                                val nxtStart = timeFormat.format(Date(nxtProg.startMs))
                                Text(
                                    text = "Далее: $nxtStart  ${nxtProg.title}",
                                    color = Color(0xFF8B949E),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // ── Bottom Remote Control Guide Bar ──────────────────────────────
                    RemoteControlHintBar(
                        hints = if (uiState.isCatchupMode) {
                            listOf(
                                RemoteHint(keyLabel = "◀ ▶", action = "Перемотка (±${seekStepSeconds}с)"),
                                RemoteHint(keyLabel = "OK", action = "Инфо / OSD"),
                                RemoteHint(keyLabel = "⏯", action = "Пауза"),
                                RemoteHint(keyLabel = "Назад", action = "Прямой эфир"),
                            )
                        } else {
                            listOfNotNull(
                                RemoteHint(keyLabel = "▲▼", action = "Канал"),
                                RemoteHint(keyLabel = "OK", action = "Инфо / OSD"),
                                if (uiState.isArchiveSupported || uiState.channel?.hasArchive == true) RemoteHint(keyLabel = "⏯", action = "Архив") else null,
                                RemoteHint(keyLabel = "Назад", action = "Список каналов"),
                            )
                        },
                    )
                }
            }
        }

        // ─── Catch-up Mode Badge ──────────────────────────────────────────────
        if (uiState.isCatchupMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xCCE53935))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "⏪ АРХИВ",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // ─── Seek Feedback HUD Overlay ────────────────────────────────────────
        AnimatedVisibility(
            visible = seekFeedbackText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xEE161B26))
                    .border(2.dp, ChannelDesignTokens.NeonCyan, RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = seekFeedbackText.orEmpty(),
                    color = ChannelDesignTokens.NeonCyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // ─── OLED Screensaver Overlay ──────────────────────────────────────────
        ScreensaverOverlay(isActive = uiState.isScreensaverActive)
    }
}
