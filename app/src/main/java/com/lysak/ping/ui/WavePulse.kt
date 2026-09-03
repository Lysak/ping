// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.ui

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import kotlin.math.min

private const val PULSE_DURATION_MS = 900f
private const val NANOS_PER_MS = 1_000_000f
private const val DEFAULT_ANIM_SCALE = 1f
private const val RING_MAX_ALPHA = 0.6f
private const val STATIC_RING_ALPHA = 0.4f
private const val STATIC_RING_RADIUS_FRACTION = 0.7f
private const val HALF = 2f
private const val RING_STROKE_DP = 2f

/**
 * Emits one expanding, fading ring from the centre every time [pulseKey] changes.
 * When system animations are disabled it draws a single static ring instead.
 */
@Composable
fun WavePulse(
    pulseKey: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reducedMotion =
        remember {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                DEFAULT_ANIM_SCALE,
            ) == 0f
        }

    val rings = remember { mutableStateListOf<Long>() }
    var now by remember { mutableLongStateOf(0L) }

    LaunchedEffect(pulseKey) {
        if (pulseKey == 0 || reducedMotion) return@LaunchedEffect
        rings += withFrameNanos { it }
        while (rings.isNotEmpty()) {
            now = withFrameNanos { it }
            rings.removeAll { (now - it) / NANOS_PER_MS >= PULSE_DURATION_MS }
        }
    }

    Canvas(modifier) {
        val centre = Offset(size.width / HALF, size.height / HALF)
        val maxRadius = min(size.width, size.height) / HALF
        val stroke = Stroke(width = RING_STROKE_DP * density)
        if (reducedMotion) {
            drawCircle(
                color = color.copy(alpha = STATIC_RING_ALPHA),
                radius = maxRadius * STATIC_RING_RADIUS_FRACTION,
                center = centre,
                style = stroke,
            )
            return@Canvas
        }
        rings.forEach { start ->
            val progress = ((now - start) / NANOS_PER_MS / PULSE_DURATION_MS).coerceIn(0f, 1f)
            drawCircle(
                color = color.copy(alpha = (1f - progress) * RING_MAX_ALPHA),
                radius = maxRadius * progress,
                center = centre,
                style = stroke,
            )
        }
    }
}
