// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lysak.ping.data.ThemeMode
import com.lysak.ping.ui.PingScreen
import com.lysak.ping.ui.theme.PingTheme

// Matches windowSplashScreenAnimationDuration in themes.xml — hold the splash
// just long enough to let the animated launcher mark finish drawing itself.
private const val SPLASH_ANIM_MS = 840L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        val started = android.os.SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            android.os.SystemClock.uptimeMillis() - started < SPLASH_ANIM_MS
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val app = application as PingApp
            val prefs by app.repo.prefs.collectAsStateWithLifecycle(initialValue = null)
            val p = prefs
            val dark =
                when (p?.theme) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    else -> isSystemInDarkTheme()
                }
            PingTheme(darkTheme = dark, dynamicColor = p?.dynamicColor == true) {
                if (p != null) PingScreen()
            }
        }
    }
}
