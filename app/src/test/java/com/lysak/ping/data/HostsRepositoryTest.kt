// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class HostsRepositoryTest {
    @TempDir lateinit var tmp: File

    private fun repo(): HostsRepository {
        val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create { File(tmp, "prefs.preferences_pb") }
        return HostsRepository.forTest(store)
    }

    @Test fun defaultsPresentAndSelectedIsGoogle() =
        runTest {
            val p = repo().prefs.first()
            assertThat(p.hosts.map { it.value }).containsExactly("8.8.8.8", "1.1.1.1").inOrder()
            assertThat(p.selected.value).isEqualTo("8.8.8.8")
        }

    @Test fun addCustomHostPersistsAndAppearsAfterDefaults() =
        runTest {
            val r = repo()
            assertThat(r.addHost("  My Router ", "192.168.1.1")).isTrue()
            val p = r.prefs.first()
            assertThat(p.hosts.map { it.value })
                .containsExactly("8.8.8.8", "1.1.1.1", "192.168.1.1")
                .inOrder()
            assertThat(p.hosts.last().label).isEqualTo("My Router")
            assertThat(p.hosts.last().deletable).isTrue()
        }

    @Test fun rejectsInvalidAndDuplicate() =
        runTest {
            val r = repo()
            assertThat(r.addHost("bad", "not a host")).isFalse()
            assertThat(r.addHost("dup", "8.8.8.8")).isFalse()
        }

    @Test fun deleteRemovesCustomButNotDefault() =
        runTest {
            val r = repo()
            r.addHost("X", "9.9.9.9")
            r.select("9.9.9.9")
            r.deleteHost("9.9.9.9")
            val p = r.prefs.first()
            assertThat(p.hosts.map { it.value }).containsExactly("8.8.8.8", "1.1.1.1").inOrder()
            assertThat(p.selected.value).isEqualTo("8.8.8.8")
            r.deleteHost("8.8.8.8")
            assertThat(r.prefs.first().hosts).hasSize(2)
        }
}
