// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.core

private const val MAX_HOSTNAME_LEN = 253

object HostValidation {
    private val LABEL = Regex("^(?!-)[A-Za-z0-9-]{1,63}(?<!-)$")
    private val IPV4 = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")
    private val NUMERIC = Regex("^\\d+$")

    fun isValid(host: String): Boolean {
        if (host.isBlank() || host != host.trim() || host.any { it.isWhitespace() }) return false
        if (host.length > MAX_HOSTNAME_LEN) return false
        if (IPV4.matches(host)) return true
        if (host.contains(':')) {
            // Bracketed literal parse stays offline (no DNS) for a real IPv6 literal.
            val literalChars = host.all { it == ':' || it in "0123456789abcdefABCDEF" }
            return literalChars &&
                runCatching { java.net.InetAddress.getByName("[$host]") }.isSuccess
        }
        val labels = host.split('.')
        if (labels.isEmpty() || !labels.all { LABEL.matches(it) }) return false
        // Reject dotted-numeric strings that are not a valid IPv4 (e.g. "1.2.3", "999.1.1.1").
        return labels.any { !NUMERIC.matches(it) }
    }
}
