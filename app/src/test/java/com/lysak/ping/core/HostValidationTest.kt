// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class HostValidationTest {
    private fun ok(h: String) = assertThat(HostValidation.isValid(h)).isTrue()

    private fun bad(h: String) = assertThat(HostValidation.isValid(h)).isFalse()

    @Test fun acceptsIpv4() = ok("8.8.8.8")

    @Test fun acceptsIpv6() = ok("2001:4860:4860::8888")

    @Test fun acceptsHostname() {
        ok("google.com")
        ok("one.one.one.one")
        ok("a-b.example.co.uk")
    }

    @Test fun rejectsEmptyAndSpaces() {
        bad("")
        bad("  ")
        bad("goo gle.com")
    }

    @Test fun rejectsSchemeAndPath() {
        bad("https://google.com")
        bad("google.com/ping")
    }

    @Test fun rejectsBadIpv4() {
        bad("999.1.1.1")
        bad("1.2.3")
    }

    @Test fun rejectsLeadingTrailingHyphen() {
        bad("-a.com")
        bad("a-.com")
    }
}
