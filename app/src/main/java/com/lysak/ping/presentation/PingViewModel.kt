// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lysak.ping.PingApp
import com.lysak.ping.core.LossReason
import com.lysak.ping.core.Method
import com.lysak.ping.core.PingSample
import com.lysak.ping.core.PingStats
import com.lysak.ping.core.StatsSnapshot
import com.lysak.ping.data.DefaultHosts
import com.lysak.ping.data.HostsRepository
import com.lysak.ping.data.PingPrefs
import com.lysak.ping.data.ThemeMode
import com.lysak.ping.net.PingProbe
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

private const val WINDOW = 60
private const val TRANSCRIPT_CAP = 500
private const val TIMEOUT_MS = 2000
private const val ICMP_HEADER_BYTES = 8

/** ICMP echo data size the Pinger sends; mirrors BSD `ping`'s "56 data bytes" header. */
private const val ECHO_DATA_BYTES = 56

data class PingUiState(
    val prefs: PingPrefs,
    val running: Boolean = false,
    /** True while the loop is blocked waiting for the current probe's reply. */
    val awaitingReply: Boolean = false,
    val lastRttMs: Double? = null,
    val lastVia: Method? = null,
    val window: List<Double?> = emptyList(),
    val stats: StatsSnapshot = StatsSnapshot(0, 0, 0.0, null, null, null, null),
    val transcript: List<String> = emptyList(),
)

// Single-screen state holder: user intents + lifecycle + loop internals legitimately
// exceed detekt's 11-function class limit.
@Suppress("TooManyFunctions")
class PingViewModel internal constructor(
    private val repo: HostsRepository,
    private val probe: PingProbe,
    private val intervalMs: Long = 1000L,
    loopDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + loopDispatcher)
    private val stats = PingStats()

    private val seed =
        PingPrefs(
            hosts = DefaultHosts.list,
            selected = DefaultHosts.list.first(),
            theme = ThemeMode.SYSTEM,
            dynamicColor = false,
        )
    private val _state = MutableStateFlow(PingUiState(prefs = seed))
    val state: StateFlow<PingUiState> = _state.asStateFlow()

    private var loop: Job? = null

    /** Host the running loop is probing; drives the stats block and switch-while-running. */
    private var activeHost: String? = null

    init {
        scope.launch {
            repo.prefs.collect { p ->
                val prevSelected = _state.value.prefs.selected.value
                _state.value = _state.value.copy(prefs = p)
                if (_state.value.running && p.selected.value != prevSelected) {
                    stop()
                    start()
                }
            }
        }
    }

    fun toggle() {
        if (_state.value.running) stop() else start()
    }

    /** Stop the loop if it is running (used when the screen leaves the foreground). */
    fun pauseIfRunning() {
        if (_state.value.running) stop()
    }

    fun selectHost(value: String) = scope.launch { repo.select(value) }.let {}

    suspend fun addHost(
        label: String,
        value: String,
    ): Boolean = repo.addHost(label, value)

    fun deleteHost(value: String) = scope.launch { repo.deleteHost(value) }.let {}

    fun setTheme(mode: ThemeMode) = scope.launch { repo.setTheme(mode) }.let {}

    fun setDynamicColor(enabled: Boolean) = scope.launch { repo.setDynamicColor(enabled) }.let {}

    fun clearTranscript() {
        _state.value = _state.value.copy(transcript = emptyList())
    }

    private fun start() {
        val host = _state.value.prefs.selected.value
        activeHost = host
        stats.reset()
        _state.value =
            _state.value.copy(
                running = true,
                awaitingReply = true,
                window = emptyList(),
                lastRttMs = null,
                lastVia = null,
                stats = stats.snapshot(),
                transcript = _state.value.transcript + "PING $host: $ECHO_DATA_BYTES data bytes",
            )
        loop =
            scope.launch {
                var seq = 0
                while (isActive) {
                    _state.value = _state.value.copy(awaitingReply = true)
                    fold(host, probe.probe(host, seq, TIMEOUT_MS))
                    seq++
                    // coerce to >=1: delay(0) never suspends, so it would spin without
                    // yielding to cancellation.
                    delay(intervalMs.coerceAtLeast(1L))
                }
            }
    }

    private fun stop() {
        loop?.cancel()
        loop = null
        val host = activeHost ?: _state.value.prefs.selected.value
        activeHost = null
        _state.value =
            _state.value.copy(
                running = false,
                awaitingReply = false,
                transcript =
                    capped(
                        _state.value.transcript + formatStatsBlock(host, stats.snapshot()).lines(),
                    ),
            )
    }

    private fun fold(
        host: String,
        sample: PingSample,
    ) {
        when (sample) {
            is PingSample.Reply -> stats.recordReply(sample.rttMs)
            is PingSample.Lost -> stats.recordLoss()
        }
        val rttOrNull = (sample as? PingSample.Reply)?.rttMs
        val line =
            when (sample) {
                is PingSample.Reply -> formatReplyLine(host, sample)
                is PingSample.Lost -> formatLostLine(host, sample)
            }
        _state.value =
            _state.value.copy(
                awaitingReply = false,
                lastRttMs = rttOrNull ?: _state.value.lastRttMs,
                lastVia = (sample as? PingSample.Reply)?.via ?: _state.value.lastVia,
                window = (_state.value.window + rttOrNull).takeLast(WINDOW),
                stats = stats.snapshot(),
                transcript = capped(_state.value.transcript + line),
            )
    }

    private fun capped(lines: List<String>): List<String> =
        if (lines.size <= TRANSCRIPT_CAP) lines else lines.takeLast(TRANSCRIPT_CAP)

    override fun onCleared() {
        scope.coroutineContext[Job]?.cancel()
    }

    companion object {
        fun forTest(
            repo: HostsRepository,
            probe: PingProbe,
            intervalMs: Long,
            dispatcher: CoroutineDispatcher,
        ) = PingViewModel(repo, probe, intervalMs, dispatcher)

        val Factory =
            viewModelFactory {
                initializer {
                    val app = this[APPLICATION_KEY] as PingApp
                    PingViewModel(app.repo, app.pinger)
                }
            }
    }
}

private fun f2(v: Double) = String.format(Locale.US, "%.2f", v)

internal fun formatReplyLine(
    host: String,
    s: PingSample.Reply,
): String {
    val ttl = s.ttl?.let { " ttl=$it" }.orEmpty()
    val tag =
        when (s.via) {
            Method.TCP -> "  (tcp)"
            Method.ICMP_EXEC -> "  (exec)"
            Method.ICMP -> ""
        }
    val bytes = s.bytes + ICMP_HEADER_BYTES
    return "$bytes bytes from $host: icmp_seq=${s.seq}$ttl time=${f2(s.rttMs)} ms$tag"
}

internal fun formatLostLine(
    host: String,
    s: PingSample.Lost,
): String =
    when (s.reason) {
        LossReason.TIMEOUT -> "Request timeout for icmp_seq ${s.seq}"
        LossReason.UNRESOLVED -> "ping: cannot resolve $host: Unknown host"
        LossReason.NETWORK_DOWN -> "ping: sendto: Network is unreachable"
        LossReason.UNREACHABLE, LossReason.ERROR ->
            "From $host: Destination unreachable (icmp_seq ${s.seq})"
    }

internal fun formatStatsBlock(
    host: String,
    snap: StatsSnapshot,
): String {
    val head = "--- $host ping statistics ---"
    val counts =
        "${snap.transmitted} packets transmitted, ${snap.received} received, " +
            "${String.format(Locale.US, "%.1f", snap.lossPct)}% packet loss"
    if (snap.received == 0 || snap.minMs == null) return "$head\n$counts"
    val rt =
        "round-trip min/avg/max/stddev = " +
            "${f2(
                snap.minMs,
            )}/${f2(snap.avgMs!!)}/${f2(snap.maxMs!!)}/${f2(snap.stddevMs ?: 0.0)} ms"
    return "$head\n$counts\n$rt"
}
