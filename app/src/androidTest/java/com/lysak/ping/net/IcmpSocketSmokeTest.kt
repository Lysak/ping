// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.net

import android.system.Os
import android.system.OsConstants.AF_INET
import android.system.OsConstants.IPPROTO_ICMP
import android.system.OsConstants.SOCK_DGRAM
import com.lysak.ping.core.PingSample
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileDescriptor

class IcmpSocketSmokeTest {
    @Test
    fun canOpenUnprivilegedIcmpDatagramSocket() {
        var fd: FileDescriptor? = null
        try {
            fd = Os.socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP)
            assertTrue("socket fd should be valid", fd.valid())
        } finally {
            fd?.let { runCatching { Os.close(it) } }
        }
    }

    @Test
    fun probeGoogleReturnsReply() =
        runTest {
            val s = Pinger().probe("8.8.8.8", seq = 0, timeoutMs = 3000)
            assertTrue("expected a reply, got $s", s is PingSample.Reply)
            val reply = s as PingSample.Reply
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                reply.via == com.lysak.ping.core.Method.ICMP
            ) {
                val ttl = reply.ttl
                assertTrue("expected a sane received TTL, got $ttl", ttl != null && ttl in 1..255)
            }
        }
}
