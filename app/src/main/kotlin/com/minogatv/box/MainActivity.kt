package com.minogatv.box

import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.minogatv.box.feature.ui.DisclaimerScreen
import com.minogatv.box.feature.ui.LocalScreenKeyDispatcher
import com.minogatv.box.feature.ui.ScreenKeyDispatcher
import com.minogatv.box.input.TouchRemoteController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.lifecycleScope
import com.minogatv.box.core.data.backup.BackupManager
import com.minogatv.box.feature.channels.ChannelListScreen
import com.minogatv.box.feature.player.PlayerScreen
import com.minogatv.box.feature.settings.SettingsScreen
import com.minogatv.box.ui.theme.MinogaTVBoxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * # MainActivity
 *
 * Single-activity host for the entire Minoga TV Box app.
 * Supports Picture-in-Picture (PiP) and auto-play last channel on startup.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var backupManager: BackupManager

    private var isPlayerActive by mutableStateOf(false)

    /** Direct key dispatcher so D-pad & gesture keys work even in Compose InputMode.Touch. */
    private val screenKeyDispatcher = ScreenKeyDispatcher()

    /** Touch-screen gestures → virtual D-pad remote keys (double tap = OK, swipes = arrows). */
    private val touchRemote by lazy { TouchRemoteController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MinogaTVBox)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("accepted_terms", false)) {
            lifecycleScope.launch {
                backupManager.checkAndAutoRestoreOnFirstLaunch()
            }
        }

        val autoPlayLast = prefs.getBoolean("auto_play_last_channel", false)
        val lastChannelId = prefs.getLong("last_watched_channel_id", 0L)

        val startDest = if (autoPlayLast && lastChannelId > 0L) {
            "player/$lastChannelId"
        } else {
            Routes.CHANNEL_LIST
        }

        setContent {
            var acceptedTerms by remember {
                mutableStateOf(prefs.getBoolean("accepted_terms", false))
            }

            CompositionLocalProvider(LocalScreenKeyDispatcher provides screenKeyDispatcher) {
                MinogaTVBoxTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        if (!acceptedTerms) {
                            DisclaimerScreen(
                                onAccept = {
                                    prefs.edit().putBoolean("accepted_terms", true).apply()
                                    acceptedTerms = true
                                    lifecycleScope.launch {
                                        backupManager.checkAndAutoRestoreOnFirstLaunch()
                                    }
                                },
                                onDecline = {
                                    finishAffinity()
                                },
                            )
                        } else {
                            MinogaNavHost(
                                startDestination = startDest,
                                onPlayerStateChanged = { active ->
                                    isPlayerActive = active
                                    if (active) {
                                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                    } else {
                                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) touchRemote.ensureKeyFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (screenKeyDispatcher.dispatch(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val prefs = getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        if (!isInPictureInPictureModeCompat() && prefs.getBoolean(TouchRemoteController.PREF_KEY, true)) {
            val cancelNativeClick = touchRemote.onTouchEvent(ev, isPlayerActive = isPlayerActive)
            if (cancelNativeClick && ev.actionMasked == MotionEvent.ACTION_UP) {
                val cancelEv = MotionEvent.obtain(ev)
                cancelEv.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancelEv)
                cancelEv.recycle()
                return true
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isInPictureInPictureModeCompat(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val prefs = getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        val pipEnabled = prefs.getBoolean("pip_enabled", true)
        if (pipEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isPlayerActive) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().build())
            } catch (_: Exception) {}
        }
    }
}

// ─── Navigation host ──────────────────────────────────────────────────────────

private object Routes {
    const val CHANNEL_LIST = "channel_list"
    const val SETTINGS     = "settings"
    const val PLAYER       = "player/{channelId}?catchupStartMs={catchupStartMs}"
}

@Composable
private fun MinogaNavHost(
    startDestination: String = Routes.CHANNEL_LIST,
    onPlayerStateChanged: (Boolean) -> Unit = {},
) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val route = currentEntry?.destination?.route.orEmpty()
    onPlayerStateChanged(route.startsWith("player/"))

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {

        // ── Главный экран: список каналов ─────────────────────────────────
        composable(Routes.CHANNEL_LIST) {
            ChannelListScreen(
                onNavigateToPlayer = { channelId, catchupStartMs ->
                    val dest = if (catchupStartMs != null) {
                        "player/$channelId?catchupStartMs=$catchupStartMs"
                    } else {
                        "player/$channelId"
                    }
                    navController.navigate(dest)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }

        // ── Настройки ─────────────────────────────────────────────────────
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        // ── Плеер (ExoPlayer Media3) ─────────────────────────────────────
        composable(
            route = "player/{channelId}?catchupStartMs={catchupStartMs}",
            arguments = listOf(
                androidx.navigation.navArgument("channelId") { type = androidx.navigation.NavType.LongType },
                androidx.navigation.navArgument("catchupStartMs") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getLong("channelId") ?: 0L
            val catchupStartMs = backStackEntry.arguments?.getString("catchupStartMs")?.toLongOrNull()
            PlayerScreen(
                channelId = channelId,
                catchupStartMs = catchupStartMs,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
