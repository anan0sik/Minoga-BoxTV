package com.minogatv.box.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * # ScreensaverOverlay
 *
 * OLED burn-in protection overlay. When activated (after 3 minutes of inactivity
 * with a paused stream), displays a black screen with a slowly floating
 * "Minoga TV" logo that continuously moves to prevent pixel burn-in.
 *
 * The logo follows a smooth Lissajous-curve path across the screen, ensuring
 * no pixel remains static for more than a few seconds.
 *
 * @param isActive Whether the screensaver should be displayed.
 * @param modifier Optional modifier for the overlay container.
 */
@Composable
fun ScreensaverOverlay(
    isActive: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = isActive,
        enter = fadeIn(animationSpec = tween(durationMillis = 1_500)),
        exit = fadeOut(animationSpec = tween(durationMillis = 500)),
    ) {
        val config = LocalConfiguration.current
        val screenWidthDp = config.screenWidthDp.toFloat()
        val screenHeightDp = config.screenHeightDp.toFloat()

        // Logo bounding area (keep 120dp margin from edges)
        val logoWidth = 180f
        val logoHeight = 40f
        val marginX = 60f
        val marginY = 40f
        val rangeX = (screenWidthDp - logoWidth - marginX * 2).coerceAtLeast(100f)
        val rangeY = (screenHeightDp - logoHeight - marginY * 2).coerceAtLeast(60f)

        val infiniteTransition = rememberInfiniteTransition(label = "screensaver")

        // Horizontal oscillation — slow (~25 seconds per cycle)
        val xPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 25_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "x_phase",
        )

        // Vertical oscillation — slightly different period (~19 seconds) for Lissajous pattern
        val yPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 2f * Math.PI.toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 19_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "y_phase",
        )

        val offsetX = marginX + ((sin(xPhase) + 1f) / 2f) * rangeX
        val offsetY = marginY + ((cos(yPhase) + 1f) / 2f) * rangeY

        // Subtle color cycling for the logo
        val colorPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 12_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "color_phase",
        )

        val logoColor = Color(
            red = 0.2f + 0.6f * colorPhase,
            green = 0.5f + 0.3f * (1f - colorPhase),
            blue = 0.8f - 0.3f * colorPhase,
            alpha = 0.7f,
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // Floating logo
            Box(
                modifier = Modifier
                    .offset(x = offsetX.dp, y = offsetY.dp)
                    .size(width = logoWidth.dp, height = logoHeight.dp),
            ) {
                Text(
                    text = "🐟 Minoga TV",
                    color = logoColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
        }
    }
}
