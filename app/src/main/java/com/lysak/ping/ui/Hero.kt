// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lysak.ping.R
import com.lysak.ping.core.StatsSnapshot
import com.lysak.ping.presentation.PingUiState
import com.lysak.ping.ui.theme.LocalPingColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale

private const val PING_BUTTON_HEIGHT_DP = 94
private const val SPARKLINE_HEIGHT_DP = 40
private const val BAR_WIDTH_FRACTION = 0.6f
private const val MIN_BAR_FRACTION = 0.02f
private const val LOST_BAR_HEIGHT_FRACTION = 0.9f
private const val CHIP_CORNER_PERCENT = 50
private const val HALF = 2f

// Expressive button: the corner radius eases between idle (pill) and running
// (blocky), and squishes rounder while pressed. A single Animatable plays the two
// beats strictly in sequence — press-in fully, THEN settle — so they never fight
// or reverse mid-flight (that was the jitter / "ривками" feel).
private const val PING_IDLE_CORNER_DP = 30
private const val PING_RUNNING_CORNER_DP = 14
private const val PING_PRESSED_CORNER_DP = 48
private const val PING_CORNER_BEAT_MS = 380

// Small Expressive loader shown in the latency slot: immediately after a tap
// until the first reply, and again whenever a later probe stays outstanding this
// long (the "sometimes there's a lag" case).
private const val LATENCY_LOADER_DELAY_MS = 250L
private const val LATENCY_LOADER_SIZE_DP = 32

// Fixed height for the latency row so swapping the loader for the number never
// shifts the layout.
private const val LATENCY_ROW_HEIGHT_DP = 64

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Hero(
    state: PingUiState,
    onToggle: () -> Unit,
    onTargetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPingColors.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TargetChip(state.prefs.selected.label, state.prefs.selected.value, onTargetClick)

        val animated by animateFloatAsState(
            targetValue = state.lastRttMs?.toFloat() ?: 0f,
            animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
            label = "rtt",
        )
        val latency = state.lastRttMs?.let { String.format(Locale.US, "%.1f ms", animated) } ?: "—"

        var lingeringWait by remember { mutableStateOf(false) }
        LaunchedEffect(state.awaitingReply) {
            lingeringWait =
                if (state.awaitingReply) {
                    delay(LATENCY_LOADER_DELAY_MS)
                    true
                } else {
                    false
                }
        }
        val showLoader =
            state.running &&
                ((state.awaitingReply && state.lastRttMs == null) || lingeringWait)
        Box(
            modifier = Modifier.height(LATENCY_ROW_HEIGHT_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (showLoader) {
                LoadingIndicator(
                    color = colors.accent,
                    modifier = Modifier.size(LATENCY_LOADER_SIZE_DP.dp),
                )
            } else {
                Text(
                    text = latency,
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textPrimary,
                )
            }
        }

        StatsRow(state.stats)

        // Sparkline reads as the button's readout strip, so keep them visually paired.
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Sparkline(
                window = state.window,
                color = colors.accent,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(SPARKLINE_HEIGHT_DP.dp),
            )

            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                WavePulse(
                    pulseKey = state.stats.received,
                    color = colors.accent,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(PING_BUTTON_HEIGHT_DP.dp),
                )
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val restingCorner =
                    if (state.running) PING_RUNNING_CORNER_DP.dp else PING_IDLE_CORNER_DP.dp
                val restingCornerNow by rememberUpdatedState(restingCorner)
                val cornerAnim = remember { Animatable(PING_IDLE_CORNER_DP.dp, Dp.VectorConverter) }
                var inPressCycle by remember { mutableStateOf(false) }
                val beat = tween<Dp>(PING_CORNER_BEAT_MS, easing = FastOutSlowInEasing)

                LaunchedEffect(interaction) {
                    snapshotFlow { pressed }.collectLatest { down ->
                        if (down) {
                            inPressCycle = true
                            cornerAnim.animateTo(PING_PRESSED_CORNER_DP.dp, beat)
                        } else if (inPressCycle) {
                            // Beat 1: guarantee the press-in finished even on a fast tap.
                            cornerAnim.animateTo(PING_PRESSED_CORNER_DP.dp, beat)
                            // Beat 2: settle to whatever running state the tap left us in.
                            cornerAnim.animateTo(restingCornerNow, beat)
                            inPressCycle = false
                        }
                    }
                }
                // Non-press state changes (e.g. host switch while running) just ease.
                LaunchedEffect(restingCorner) {
                    if (!inPressCycle) cornerAnim.animateTo(restingCorner, beat)
                }
                val corner = cornerAnim.value
                Button(
                    onClick = onToggle,
                    interactionSource = interaction,
                    shape = RoundedCornerShape(corner),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = colors.background,
                        ),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(PING_BUTTON_HEIGHT_DP.dp),
                ) {
                    Text(
                        text =
                            stringResource(
                                if (state.running) R.string.ping_stop else R.string.ping_start,
                            ),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetChip(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val colors = LocalPingColors.current
    val cd = stringResource(R.string.target_cd)
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(CHIP_CORNER_PERCENT))
                .clickable(onClick = onClick)
                .background(colors.cell)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { contentDescription = cd },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = colors.textPrimary)
        Text(value, color = colors.textSecondary)
    }
}

@Composable
private fun StatsRow(stats: StatsSnapshot) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Stat(stringResource(R.string.stat_min), stats.minMs)
        Stat(stringResource(R.string.stat_avg), stats.avgMs)
        Stat(stringResource(R.string.stat_max), stats.maxMs)
        Stat(stringResource(R.string.stat_jitter), stats.stddevMs)
        Stat(
            stringResource(R.string.stat_loss),
            stats.lossPct.takeIf { stats.transmitted > 0 },
            "%",
        )
    }
}

@Composable
private fun Stat(
    label: String,
    value: Double?,
    suffix: String = "",
) {
    val colors = LocalPingColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value?.let { String.format(Locale.US, "%.1f%s", it, suffix) } ?: "—",
            color = colors.textPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            color = colors.textSecondary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Sparkline(
    window: List<Double?>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val gap = LocalPingColors.current.divider
    Canvas(modifier) {
        if (window.isEmpty()) return@Canvas
        val maxV = window.filterNotNull().maxOrNull() ?: return@Canvas
        val slot = size.width / window.size
        window.forEachIndexed { i, v ->
            val x = i * slot + slot / HALF
            val top =
                if (v == null) {
                    size.height * LOST_BAR_HEIGHT_FRACTION
                } else {
                    size.height - (v / maxV).toFloat().coerceIn(MIN_BAR_FRACTION, 1f) * size.height
                }
            drawLine(
                color = if (v == null) gap else color,
                start = Offset(x, size.height),
                end = Offset(x, top),
                strokeWidth = slot * BAR_WIDTH_FRACTION,
            )
        }
    }
}
