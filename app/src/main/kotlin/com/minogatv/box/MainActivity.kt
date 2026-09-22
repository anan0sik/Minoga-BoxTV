package com.minogatv.box

import android.app.PictureInPictureParams
import android.content.Context
import android.os.Build
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MinogaTVBox)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            backupManager.checkAndAutoRestoreOnFirstLaunch()
        }

        val prefs = getSharedPreferences("minoga_tv_prefs", Context.MODE_PRIVATE)
        val autoPlayLast = prefs.getBoolean("auto_play_last_channel", false)
        val lastChannelId = prefs.getLong("last_watched_channel_id", 0L)

        val startDest = if (autoPlayLast && lastChannelId > 0L) {
            "player/$lastChannelId"
        } else {
            Routes.CHANNEL_LIST
        }

        setContent {
            MinogaTVBoxTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MinogaNavHost(
                        startDestination = startDest,
                        onPlayerStateChanged = { active -> isPlayerActive = active },
                    )
                }
            }
        }
    }

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
