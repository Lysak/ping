// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.net

object IcmpPacket {
    fun echoRequest(
        identifier: Int,
        sequence: Int,
        payload: ByteArray,
    ): ByteArray =
        build(
            type = 8,
            identifier = identifier,
            sequence = sequence,
            payload = payload,
            fillChecksum = true,
        )

    /** ICMPv6 echo request. Kernel fills the checksum for IPPROTO_ICMPV6 datagram sockets. */
    fun echoRequestV6(
        identifier: Int,
        sequence: Int,
        payload: ByteArray,
    ): ByteArray =
        build(
            type = 128,
            identifier = identifier,
            sequence = sequence,
            payload = payload,
            fillChecksum = false,
        )

    private fun build(
        type: Int,
        identifier: Int,
        sequence: Int,
        payload: ByteArray,
        fillChecksum: Boolean,
    ): ByteArray {
        val pkt = ByteArray(8 + payload.size)
        pkt[0] = type.toByte()
        pkt[1] = 0
        pkt[2] = 0
        pkt[3] = 0
        pkt[4] = (identifier ushr 8).toByte()
        pkt[5] = identifier.toByte()
        pkt[6] = (sequence ushr 8).toByte()
        pkt[7] = sequence.toByte()
        payload.copyInto(pkt, destinationOffset = 8)
        if (fillChecksum) {
            val cs = checksum(pkt)
            pkt[2] = (cs ushr 8).toByte()
            pkt[3] = cs.toByte()
        }
        return pkt
    }

    /**
     * RFC 1071 16-bit one's-complement sum.
     * Returns 0 when the packet's checksum field is already valid.
     */
    fun checksum(data: ByteArray): Int {
        var sum = 0L
        var i = 0
        while (i + 1 < data.size) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < data.size) sum += (data[i].toInt() and 0xFF) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    fun parseEchoReply(
        datagram: ByteArray,
        expectedId: Int,
        expectedSeq: Int,
    ): Boolean {
        if (datagram.size < 8) return false
        val type = datagram[0].toInt() and 0xFF
        val code = datagram[1].toInt() and 0xFF
        if ((type != 0 && type != 129) || code != 0) return false
        val id = ((datagram[4].toInt() and 0xFF) shl 8) or (datagram[5].toInt() and 0xFF)
        val seq = ((datagram[6].toInt() and 0xFF) shl 8) or (datagram[7].toInt() and 0xFF)
        return id == (expectedId and 0xFFFF) && seq == (expectedSeq and 0xFFFF)
    }
}
