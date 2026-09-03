// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.core

import kotlin.math.sqrt

private const val PERCENT = 100.0

data class StatsSnapshot(
    val transmitted: Int,
    val received: Int,
    val lossPct: Double,
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?,
    val stddevMs: Double?,
)

/** Online BSD-`ping`-style statistics. Welford mean/variance, population stddev. */
class PingStats {
    private var n = 0
    private var mean = 0.0
    private var m2 = 0.0
    private var tx = 0
    private var min = Double.MAX_VALUE
    private var max = -Double.MAX_VALUE

    fun recordReply(rttMs: Double) {
        tx++
        n++
        val delta = rttMs - mean
        mean += delta / n
        m2 += delta * (rttMs - mean)
        if (rttMs < min) min = rttMs
        if (rttMs > max) max = rttMs
    }

    fun recordLoss() {
        tx++
    }

    fun reset() {
        n = 0
        mean = 0.0
        m2 = 0.0
        tx = 0
        min = Double.MAX_VALUE
        max = -Double.MAX_VALUE
    }

    val transmitted: Int get() = tx
    val received: Int get() = n
    val lossPct: Double get() = if (tx == 0) 0.0 else (tx - n) * PERCENT / tx
    val minMs: Double? get() = if (n > 0) min else null
    val avgMs: Double? get() = if (n > 0) mean else null
    val maxMs: Double? get() = if (n > 0) max else null
    val stddevMs: Double? get() = if (n >= 2) sqrt(m2 / n) else null

    fun snapshot() = StatsSnapshot(transmitted, received, lossPct, minMs, avgMs, maxMs, stddevMs)
}
