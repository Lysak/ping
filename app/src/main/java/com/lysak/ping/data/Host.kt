// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.data

data class Host(
    val label: String,
    val value: String,
    val deletable: Boolean,
)

object DefaultHosts {
    val list =
        listOf(
            Host("Google", "8.8.8.8", deletable = false),
            Host("Cloudflare", "1.1.1.1", deletable = false),
        )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class PingPrefs(
    val hosts: List<Host>,
    val selected: Host,
    val theme: ThemeMode,
    val dynamicColor: Boolean,
)
