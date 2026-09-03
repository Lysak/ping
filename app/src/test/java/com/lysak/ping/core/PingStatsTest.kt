// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PingStatsTest {
    @Test fun matchesBsdPingExample() {
        val s = PingStats()
        listOf(11.161, 6.945, 9.770, 9.078).forEach { s.recordReply(it) }
        val snap = s.snapshot()
        assertThat(snap.transmitted).isEqualTo(4)
        assertThat(snap.received).isEqualTo(4)
        assertThat(snap.lossPct).isWithin(1e-9).of(0.0)
        assertThat(snap.minMs!!).isWithin(1e-3).of(6.945)
        assertThat(snap.avgMs!!).isWithin(1e-3).of(9.239)
        assertThat(snap.maxMs!!).isWithin(1e-3).of(11.161)
        assertThat(snap.stddevMs!!).isWithin(1e-3).of(1.522)
    }

    @Test fun countsLossAndReportsPercent() {
        val s = PingStats()
        s.recordReply(10.0)
        s.recordLoss()
        s.recordReply(20.0)
        s.recordLoss()
        val snap = s.snapshot()
        assertThat(snap.transmitted).isEqualTo(4)
        assertThat(snap.received).isEqualTo(2)
        assertThat(snap.lossPct).isWithin(1e-9).of(50.0)
    }

    @Test fun statsNullBeforeEnoughSamples() {
        val s = PingStats()
        assertThat(s.snapshot().minMs).isNull()
        s.recordReply(5.0)
        assertThat(s.snapshot().stddevMs).isNull()
        assertThat(s.snapshot().avgMs!!).isWithin(1e-9).of(5.0)
    }
}
