// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.net

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PingerParseTest {
    @Test fun parsesAndroidToyboxLine() {
        val (t, ttl) = parseExecPingLine("from 142.251.13.139: icmp_seq=4 ttl=111 time=77.5 ms")!!
        assertThat(t).isWithin(1e-9).of(77.5)
        assertThat(ttl).isEqualTo(111)
    }

    @Test fun parsesBsdLine() {
        val (t, ttl) = parseExecPingLine("64 bytes from 1.0.0.1: icmp_seq=1 ttl=56 time=6.945 ms")!!
        assertThat(t).isWithin(1e-9).of(6.945)
        assertThat(ttl).isEqualTo(56)
    }

    @Test fun parsesLineWithoutTtl() {
        val (t, ttl) = parseExecPingLine("64 bytes from 1.1.1.1: icmp_seq=0 time=11 ms")!!
        assertThat(t).isWithin(1e-9).of(11.0)
        assertThat(ttl).isNull()
    }

    @Test fun returnsNullForStatsAndGarbage() {
        assertThat(parseExecPingLine("--- google.com ping statistics ---")).isNull()
        assertThat(parseExecPingLine("7 packets transmitted, 7 packets received, 0% loss")).isNull()
        assertThat(parseExecPingLine("")).isNull()
    }
}
