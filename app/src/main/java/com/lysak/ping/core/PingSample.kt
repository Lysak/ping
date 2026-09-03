// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.core

enum class Method { ICMP, ICMP_EXEC, TCP }

enum class LossReason { TIMEOUT, UNREACHABLE, UNRESOLVED, NETWORK_DOWN, ERROR }

sealed interface PingSample {
    val seq: Int

    data class Reply(
        override val seq: Int,
        val rttMs: Double,
        val ttl: Int?,
        val via: Method,
        val bytes: Int,
    ) : PingSample

    data class Lost(
        override val seq: Int,
        val reason: LossReason,
    ) : PingSample
}
