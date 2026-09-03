// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * PING colour tokens. Values copied from the metiq design system
 * (github.com/metiq-xyz/android-app, GPL-3.0), reduced to what one screen needs.
 */
data class PingColors(
    val background: Color,
    val foreground: Color,
    val cell: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val accent: Color,
    val consoleText: Color,
) {
    companion object {
        val Dark =
            PingColors(
                background = Color(0xFF111010),
                foreground = Color(0xFF222121),
                cell = Color(0xFF2E2C2D),
                textPrimary = Color.White,
                textSecondary = Color.White.copy(alpha = 0.50f),
                divider = Color.White.copy(alpha = 0.08f),
                accent = Color(0xFFDBF1B3),
                consoleText = Color.White.copy(alpha = 0.82f),
            )
        val Light =
            PingColors(
                background = Color(0xFFE5E7EB),
                foreground = Color(0xFFF5F7FA),
                cell = Color(0xFFECEEF3),
                textPrimary = Color(0xFF111827),
                textSecondary = Color.Black.copy(alpha = 0.50f),
                divider = Color.Black.copy(alpha = 0.08f),
                accent = Color(0xFFADC08B),
                consoleText = Color(0xFF111827).copy(alpha = 0.82f),
            )
    }
}

val LocalPingColors = staticCompositionLocalOf { PingColors.Dark }
