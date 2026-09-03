// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lysak.ping.core.HostValidation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pingDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ping_prefs",
)

private const val MAX_CUSTOM_HOSTS = 20
private const val MAX_LABEL_LEN = 24

class HostsRepository private constructor(
    private val store: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.applicationContext.pingDataStore)

    private object Keys {
        val CUSTOM_HOSTS = stringPreferencesKey("custom_hosts")
        val SELECTED = stringPreferencesKey("selected")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }

    val prefs: Flow<PingPrefs> =
        store.data.map { p ->
            val custom = decodeHosts(p[Keys.CUSTOM_HOSTS].orEmpty())
            val hosts = DefaultHosts.list + custom
            val selectedValue = p[Keys.SELECTED]
            val selected = hosts.firstOrNull { it.value == selectedValue } ?: hosts.first()
            val theme =
                runCatching {
                    ThemeMode.valueOf(
                        p[Keys.THEME] ?: "",
                    )
                }.getOrDefault(ThemeMode.SYSTEM)
            PingPrefs(hosts, selected, theme, p[Keys.DYNAMIC_COLOR] ?: false)
        }

    suspend fun addHost(
        label: String,
        value: String,
    ): Boolean {
        val host = value.trim()
        if (!HostValidation.isValid(host)) return false
        var added = false
        store.edit { p ->
            val custom = decodeHosts(p[Keys.CUSTOM_HOSTS].orEmpty()).toMutableList()
            val taken = DefaultHosts.list.map { it.value } + custom.map { it.value }
            if (host in taken || custom.size >= MAX_CUSTOM_HOSTS) return@edit
            custom += Host(sanitizeLabel(label, host), host, deletable = true)
            p[Keys.CUSTOM_HOSTS] = encodeHosts(custom)
            added = true
        }
        return added
    }

    suspend fun deleteHost(value: String) {
        store.edit { p ->
            val custom = decodeHosts(p[Keys.CUSTOM_HOSTS].orEmpty()).filterNot { it.value == value }
            p[Keys.CUSTOM_HOSTS] = encodeHosts(custom)
            if (p[Keys.SELECTED] == value) p.remove(Keys.SELECTED)
        }
    }

    suspend fun select(value: String) {
        store.edit { it[Keys.SELECTED] = value }
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[Keys.THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        store.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    private fun sanitizeLabel(
        raw: String,
        fallback: String,
    ): String {
        val clean =
            raw
                .replace('|', ' ')
                .replace('\n', ' ')
                .trim()
                .take(MAX_LABEL_LEN)
        return clean.ifBlank { fallback }
    }

    private fun encodeHosts(hosts: List<Host>): String =
        hosts.joinToString("\n") { "${it.label}|${it.value}" }

    private fun decodeHosts(encoded: String): List<Host> =
        encoded
            .lineSequence()
            .mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val sep = line.indexOf('|')
                if (sep < 0) return@mapNotNull null
                val label = line.substring(0, sep)
                val value = line.substring(sep + 1).trim()
                if (value.isEmpty()) {
                    null
                } else {
                    Host(
                        label.ifBlank { value },
                        value,
                        deletable = true,
                    )
                }
            }.toList()

    companion object {
        internal fun forTest(store: DataStore<Preferences>) = HostsRepository(store)
    }
}
