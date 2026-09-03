// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.net

import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.system.StructMsghdr
import android.system.StructPollfd
import android.system.StructTimeval
import androidx.annotation.RequiresApi
import com.lysak.ping.core.LossReason
import com.lysak.ping.core.Method
import com.lysak.ping.core.PingSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.nio.ByteBuffer

private val TIME_RE = Regex("""time[=<]([0-9]+(?:\.[0-9]+)?)\s*ms""")
private val TTL_RE = Regex("""ttl=([0-9]+)""", RegexOption.IGNORE_CASE)

/** BSD/Linux `ping` default ICMP data size; makes console lines read `64 bytes from …`. */
private const val PAYLOAD_BYTES = 56

// Ancillary-data constants missing from android.system.OsConstants (Linux values).
private const val IP_RECVTTL = 12
private const val IPV6_HOPLIMIT = 52

/** Android added Os.recvmsg (and thus received-TTL ancillary data) in API 33. */
private const val SDK_RECVMSG = Build.VERSION_CODES.TIRAMISU

internal fun parseExecPingLine(line: String): Pair<Double, Int?>? {
    val t =
        TIME_RE
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.toDoubleOrNull() ?: return null
    val ttl =
        TTL_RE
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    return t to ttl
}

/** One probe attempt. `Pinger` is the real implementation; tests supply a fake. */
fun interface PingProbe {
    suspend fun probe(
        host: String,
        seq: Int,
        timeoutMs: Int,
    ): PingSample
}

class Pinger : PingProbe {
    override suspend fun probe(
        host: String,
        seq: Int,
        timeoutMs: Int,
    ): PingSample =
        withContext(Dispatchers.IO) {
            val addr =
                try {
                    InetAddress.getByName(host)
                } catch (_: UnknownHostException) {
                    return@withContext PingSample.Lost(seq, LossReason.UNRESOLVED)
                }
            datagramProbe(addr, seq, timeoutMs)
                ?: execProbe(host, seq, timeoutMs)
                ?: tcpProbe(addr, seq, timeoutMs)
        }

    private fun datagramProbe(
        addr: InetAddress,
        seq: Int,
        timeoutMs: Int,
    ): PingSample? {
        val v6 = addr is Inet6Address
        val fd: FileDescriptor =
            try {
                Os.socket(
                    if (v6) OsConstants.AF_INET6 else OsConstants.AF_INET,
                    OsConstants.SOCK_DGRAM,
                    if (v6) OsConstants.IPPROTO_ICMPV6 else OsConstants.IPPROTO_ICMP,
                )
            } catch (_: Throwable) {
                return null
            }
        try {
            Os.connect(fd, addr, 0)
            boundReceiveTime(fd, timeoutMs)
            enableReceivedTtl(fd, v6)
            val localPort = (Os.getsockname(fd) as InetSocketAddress).port
            val id = localPort and 0xFFFF
            val payload = ByteArray(PAYLOAD_BYTES) { it.toByte() }
            val packet =
                if (v6) {
                    IcmpPacket.echoRequestV6(id, seq, payload)
                } else {
                    IcmpPacket.echoRequest(id, seq, payload)
                }

            val start = System.nanoTime()
            Os.write(fd, ByteBuffer.wrap(packet))

            val pfd =
                StructPollfd().apply {
                    this.fd = fd
                    events = OsConstants.POLLIN.toShort()
                }
            val ready = Os.poll(arrayOf(pfd), timeoutMs)
            if (ready == 0) return PingSample.Lost(seq, LossReason.TIMEOUT)

            // Direct buffer: Os.recvmsg's scatter/gather path requires it.
            val buf = ByteBuffer.allocateDirect(1500)
            val (data, ttl) = receiveReply(fd, buf, v6)
            val rttMs = (System.nanoTime() - start) / 1_000_000.0

            return if (IcmpPacket.parseEchoReply(data, id, seq)) {
                PingSample.Reply(seq, rttMs, ttl = ttl, via = Method.ICMP, bytes = payload.size)
            } else {
                // ponytail: single read; drain-until-match if stray replies ever cause false loss
                PingSample.Lost(seq, LossReason.ERROR)
            }
        } catch (_: Throwable) {
            return null
        } finally {
            runCatching { Os.close(fd) }
        }
    }

    /**
     * Hard bound on `recvmsg`, so a consumed-but-unreturned datagram can't hang the loop.
     * `SO_RCVTIMEO` needs API 29; below that only the API-33+ `recvmsg` path exists anyway,
     * so the pre-29 devices keep the plain `poll` + blocking `read` they always had.
     */
    private fun boundReceiveTime(
        fd: FileDescriptor,
        timeoutMs: Int,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            Os.setsockoptTimeval(
                fd,
                OsConstants.SOL_SOCKET,
                OsConstants.SO_RCVTIMEO,
                StructTimeval.fromMillis(timeoutMs.toLong()),
            )
        }
    }

    /** Best-effort: ask the kernel to attach the received TTL / hop limit as ancillary data. */
    private fun enableReceivedTtl(
        fd: FileDescriptor,
        v6: Boolean,
    ) {
        if (Build.VERSION.SDK_INT < SDK_RECVMSG) return
        runCatching {
            if (v6) {
                Os.setsockoptInt(fd, OsConstants.IPPROTO_IPV6, OsConstants.IPV6_RECVHOPLIMIT, 1)
            } else {
                Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, IP_RECVTTL, 1)
            }
        }
    }

    /** Reads one datagram; on API 33+ also returns the received TTL if the kernel supplied it. */
    private fun receiveReply(
        fd: FileDescriptor,
        buf: ByteBuffer,
        v6: Boolean,
    ): Pair<ByteArray, Int?> {
        if (Build.VERSION.SDK_INT >= SDK_RECVMSG) {
            // TTL is cosmetic: never let ancillary-data quirks sink the primary probe.
            runCatching { return receiveReplyWithTtl(fd, buf, v6) }
            buf.clear()
        }
        val n = Os.read(fd, buf)
        return buf.toBytes(n) to null
    }

    @RequiresApi(SDK_RECVMSG)
    private fun receiveReplyWithTtl(
        fd: FileDescriptor,
        buf: ByteBuffer,
        v6: Boolean,
    ): Pair<ByteArray, Int?> {
        val msg = StructMsghdr(null, arrayOf(buf), null, 0)
        val n = Os.recvmsg(fd, msg, 0)
        // recvmsg does not advance the iov buffer's position; read the n bytes from the start.
        val data = ByteArray(n)
        buf.rewind()
        buf.limit(n)
        buf.get(data)
        val ttl =
            msg.msg_control?.firstNotNullOfOrNull { c ->
                val hit =
                    if (v6) {
                        c.cmsg_level == OsConstants.IPPROTO_IPV6 && c.cmsg_type == IPV6_HOPLIMIT
                    } else {
                        c.cmsg_level == OsConstants.IPPROTO_IP && c.cmsg_type == OsConstants.IP_TTL
                    }
                // Linux delivers it as a host-endian int; Android is always little-endian.
                c.cmsg_data.takeIf { hit && it.isNotEmpty() }?.let { it[0].toInt() and 0xFF }
            }
        return data to ttl
    }

    private fun ByteBuffer.toBytes(n: Int): ByteArray =
        ByteArray(n).also {
            flip()
            get(it)
        }

    private fun execProbe(
        host: String,
        seq: Int,
        timeoutMs: Int,
    ): PingSample? {
        return try {
            val secs = (timeoutMs / 1000).coerceAtLeast(1)
            val p =
                ProcessBuilder("ping", "-n", "-c", "1", "-W", secs.toString(), host)
                    .redirectErrorStream(true)
                    .start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            val hit =
                out.lineSequence().firstNotNullOfOrNull { parseExecPingLine(it) }
                    ?: return if (out.contains("100% packet loss")) {
                        PingSample.Lost(seq, LossReason.TIMEOUT)
                    } else {
                        null
                    }
            PingSample.Reply(seq, hit.first, hit.second, Method.ICMP_EXEC, bytes = 56)
        } catch (_: Throwable) {
            null
        }
    }

    private fun tcpProbe(
        addr: InetAddress,
        seq: Int,
        timeoutMs: Int,
    ): PingSample =
        try {
            val start = System.nanoTime()
            Socket().use { it.connect(InetSocketAddress(addr, 443), timeoutMs) }
            PingSample.Reply(
                seq,
                (System.nanoTime() - start) / 1_000_000.0,
                null,
                Method.TCP,
                bytes = 0,
            )
        } catch (_: java.net.SocketTimeoutException) {
            PingSample.Lost(seq, LossReason.TIMEOUT)
        } catch (_: Throwable) {
            PingSample.Lost(seq, LossReason.UNREACHABLE)
        }
}
