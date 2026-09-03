// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.presentation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.google.common.truth.Truth.assertThat
import com.lysak.ping.core.LossReason
import com.lysak.ping.core.Method
import com.lysak.ping.core.PingSample
import com.lysak.ping.core.StatsSnapshot
import com.lysak.ping.data.DefaultHosts
import com.lysak.ping.data.HostsRepository
import com.lysak.ping.data.PingPrefs
import com.lysak.ping.data.ThemeMode
import com.lysak.ping.net.PingProbe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PingViewModelTest {
    @TempDir lateinit var tmp: File

    private fun fakeRepo(): HostsRepository {
        val store: DataStore<Preferences> =
            PreferenceDataStoreFactory.create { File(tmp, "p.preferences_pb") }
        return HostsRepository.forTest(store)
    }

    private class FakePinger(
        val f: (Int) -> PingSample,
    ) : PingProbe {
        override suspend fun probe(
            host: String,
            seq: Int,
            timeoutMs: Int,
        ) = f(seq)
    }

    @Test fun switchingHostWhileRunningRestartsLoopOnNewHost() =
        runTest {
            val h1 = DefaultHosts.list[0]
            val h2 = DefaultHosts.list[1]
            val prefsFlow =
                MutableStateFlow(PingPrefs(DefaultHosts.list, h1, ThemeMode.SYSTEM, false))
            val repo = mockk<HostsRepository>()
            every { repo.prefs } returns prefsFlow
            coEvery { repo.select(any()) } answers {
                val v = firstArg<String>()
                prefsFlow.value =
                    prefsFlow.value.copy(selected = DefaultHosts.list.first { it.value == v })
            }
            val seen = mutableListOf<String>()
            val fake =
                object : PingProbe {
                    override suspend fun probe(
                        host: String,
                        seq: Int,
                        timeoutMs: Int,
                    ): PingSample {
                        seen += host
                        return PingSample.Reply(seq, 10.0, 55, Method.ICMP, 16)
                    }
                }
            val vm =
                PingViewModel.forTest(
                    repo,
                    fake,
                    intervalMs = 1000,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()
            vm.toggle()
            advanceTimeBy(2500)
            runCurrent()
            assertThat(seen.all { it == h1.value }).isTrue()
            vm.selectHost(h2.value)
            advanceTimeBy(2500)
            runCurrent()
            vm.toggle()
            runCurrent()
            assertThat(seen.last()).isEqualTo(h2.value)
            val st = vm.state.value
            assertThat(
                st.transcript.any { it.contains("--- ${h1.value} ping statistics ---") },
            ).isTrue()
            assertThat(st.transcript.any { it.contains("PING ${h2.value}") }).isTrue()
        }

    @Test fun awaitingReplyIsTrueWhileProbeSuspendsAndClearsAfterReplyAndStop() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val probe =
                object : PingProbe {
                    override suspend fun probe(
                        host: String,
                        seq: Int,
                        timeoutMs: Int,
                    ): PingSample {
                        if (seq == 0) gate.await()
                        return PingSample.Reply(seq, 10.0, 55, Method.ICMP, 16)
                    }
                }
            val vm =
                PingViewModel.forTest(
                    fakeRepo(),
                    probe,
                    intervalMs = 1000,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()
            vm.toggle()
            runCurrent()
            assertThat(vm.state.value.awaitingReply).isTrue()
            gate.complete(Unit)
            runCurrent()
            assertThat(vm.state.value.awaitingReply).isFalse()
            vm.toggle()
            runCurrent()
            assertThat(vm.state.value.awaitingReply).isFalse()
        }

    @Test fun formatReplyLineMatchesBsd() {
        val line = formatReplyLine("1.1.1.1", PingSample.Reply(1, 6.945, 56, Method.ICMP, 56))
        assertThat(line).isEqualTo("64 bytes from 1.1.1.1: icmp_seq=1 ttl=56 time=6.95 ms")
    }

    @Test fun formatStatsBlockMatchesBsd() {
        val snap = StatsSnapshot(4, 4, 0.0, 6.945, 9.239, 11.161, 1.522)
        val block = formatStatsBlock("1.1.1.1", snap)
        assertThat(block).contains("--- 1.1.1.1 ping statistics ---")
        assertThat(block).contains("4 packets transmitted, 4 received, 0.0% packet loss")
        assertThat(block).contains("round-trip min/avg/max/stddev = 6.95/9.24/11.16/1.52 ms")
    }

    @Test fun toggleRunsLoopAccumulatesStatsAndWindow() =
        runTest {
            val replies = ArrayDeque(listOf(10.0, 20.0, 30.0))
            val fake =
                FakePinger { seq ->
                    val rtt = replies.removeFirstOrNull()
                    if (rtt != null) {
                        PingSample.Reply(seq, rtt, 55, Method.ICMP, 16)
                    } else {
                        PingSample.Lost(seq, LossReason.TIMEOUT)
                    }
                }
            val vm =
                PingViewModel.forTest(
                    fakeRepo(),
                    fake,
                    intervalMs = 1000,
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            runCurrent()
            vm.toggle()
            advanceTimeBy(3500)
            runCurrent()
            vm.toggle()
            runCurrent()
            val st = vm.state.value
            assertThat(st.stats.transmitted).isAtLeast(3)
            assertThat(st.window.first()).isEqualTo(10.0)
            assertThat(st.transcript.any { it.contains("icmp_seq=0") }).isTrue()
            assertThat(st.running).isFalse()
        }
}
