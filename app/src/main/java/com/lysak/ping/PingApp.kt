// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping

import android.app.Application
import com.lysak.ping.data.HostsRepository
import com.lysak.ping.net.Pinger

class PingApp : Application() {
    val repo by lazy { HostsRepository(this) }
    val pinger by lazy { Pinger() }
}
