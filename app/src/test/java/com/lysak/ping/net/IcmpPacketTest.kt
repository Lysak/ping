// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.net

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IcmpPacketTest {
    @Test fun echoRequestHasTypeEightCodeZero() {
        val p = IcmpPacket.echoRequest(identifier = 0x1234, sequence = 7, payload = ByteArray(16))
        assertThat(p[0].toInt() and 0xFF).isEqualTo(8)
        assertThat(p[1].toInt() and 0xFF).isEqualTo(0)
        assertThat(p.size).isEqualTo(8 + 16)
    }

    @Test fun echoRequestEncodesIdAndSequenceBigEndian() {
        val p =
            IcmpPacket.echoRequest(
                identifier = 0xABCD,
                sequence = 0x0102,
                payload = ByteArray(0),
            )
        assertThat(p[4].toInt() and 0xFF).isEqualTo(0xAB)
        assertThat(p[5].toInt() and 0xFF).isEqualTo(0xCD)
        assertThat(p[6].toInt() and 0xFF).isEqualTo(0x01)
        assertThat(p[7].toInt() and 0xFF).isEqualTo(0x02)
    }

    @Test fun checksumOfValidPacketIsZeroWhenVerified() {
        val p = IcmpPacket.echoRequest(identifier = 1, sequence = 1, payload = "abcd".toByteArray())
        assertThat(IcmpPacket.checksum(p)).isEqualTo(0)
    }

    @Test fun parseEchoReplyMatchesTypeZeroWithSameIdAndSeq() {
        val reply =
            ByteArray(8).also {
                it[0] = 0
                it[4] = 0x12
                it[5] = 0x34
                it[6] = 0x00
                it[7] = 0x07
            }
        assertThat(IcmpPacket.parseEchoReply(reply, expectedId = 0x1234, expectedSeq = 7)).isTrue()
        assertThat(IcmpPacket.parseEchoReply(reply, expectedId = 0x1234, expectedSeq = 8)).isFalse()
        assertThat(IcmpPacket.parseEchoReply(reply, expectedId = 0x9999, expectedSeq = 7)).isFalse()
    }
}
