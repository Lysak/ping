// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

// Expressive theme + motion scheme is an AGENTS.md non-negotiable; the API is
// still behind this opt-in in material3 1.5.0-alpha.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) PingColors.Dark else PingColors.Light

    val scheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme ->
                darkColorScheme(
                    primary = tokens.accent,
                    background = tokens.background,
                    surface = tokens.foreground,
                    onBackground = tokens.textPrimary,
                    onSurface = tokens.textPrimary,
                )
            else ->
                lightColorScheme(
                    primary = tokens.accent,
                    background = tokens.background,
                    surface = tokens.foreground,
                    onBackground = tokens.textPrimary,
                    onSurface = tokens.textPrimary,
                )
        }

    CompositionLocalProvider(LocalPingColors provides tokens) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = PingTypography,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
