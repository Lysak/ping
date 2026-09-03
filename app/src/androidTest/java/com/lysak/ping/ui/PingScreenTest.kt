// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lysak.ping.core.Method
import com.lysak.ping.core.PingSample
import com.lysak.ping.data.HostsRepository
import com.lysak.ping.net.PingProbe
import com.lysak.ping.presentation.PingViewModel
import com.lysak.ping.ui.theme.PingTheme
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PingScreenTest {
    @get:Rule val compose = createComposeRule()

    private fun screen() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store =
            PreferenceDataStoreFactory.create {
                ctx.filesDir.resolve("test_${System.nanoTime()}.preferences_pb")
            }
        val probe =
            PingProbe { _, seq, _ ->
                PingSample.Reply(seq, rttMs = 12.3, ttl = 55, via = Method.ICMP, bytes = 56)
            }
        // Long interval: the ping loop must not churn during a UI assertion.
        val model =
            PingViewModel.forTest(
                HostsRepository.forTest(store),
                probe,
                intervalMs = 60_000L,
                dispatcher = Dispatchers.Default,
            )
        compose.setContent { PingTheme { PingScreen(vm = model) } }
    }

    @Test
    fun startButtonTogglesLabel() {
        screen()
        compose.onNodeWithText("Ping").performClick()
        compose.onNodeWithText("Stop").assertExists()
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("Ping").assertExists()
    }

    @Test
    fun targetChipOpensHostSheet() {
        screen()
        compose.onNodeWithContentDescription("Change target host").performClick()
        compose.onNodeWithText("Add host").assertExists()
    }

    @Test
    fun invalidHostKeepsAddDisabled() {
        screen()
        compose.onNodeWithContentDescription("Change target host").performClick()
        compose.onNodeWithText("Hostname or IP").performTextInput("not a host")
        compose.onNodeWithText("Enter a valid hostname or IP address").assertExists()
        compose.onNodeWithText("Add host").assertIsNotEnabled()
    }
}
