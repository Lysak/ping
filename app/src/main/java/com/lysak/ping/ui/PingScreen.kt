// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lysak.ping.presentation.PingViewModel
import com.lysak.ping.ui.theme.LocalPingColors
import kotlinx.coroutines.launch

@Composable
fun PingScreen(
    modifier: Modifier = Modifier,
    vm: PingViewModel = viewModel(factory = PingViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var sheetOpen by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.pauseIfRunning() }

    val transcriptText = { state.transcript.joinToString("\n") }
    val colors = LocalPingColors.current

    Scaffold(containerColor = colors.background, modifier = modifier) { inner ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(colors.background)
                    .padding(inner)
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp),
        ) {
            Hero(
                state = state,
                onToggle = vm::toggle,
                onTargetClick = { sheetOpen = true },
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
            )
            // ponytail: console fills remaining height; small-screen collapse (<560dp)
            // deferred until it's actually a problem on a real device.
            Console(
                lines = state.transcript,
                onCopy = {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("PING transcript", transcriptText())),
                        )
                    }
                },
                onShare = {
                    val send =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, transcriptText())
                        }
                    context.startActivity(Intent.createChooser(send, null))
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 12.dp),
            )
        }
    }

    if (sheetOpen) {
        HostPickerSheet(
            prefs = state.prefs,
            onSelect = {
                vm.selectHost(it)
                sheetOpen = false
            },
            onAdd = { label, value -> scope.launch { vm.addHost(label, value) } },
            onDelete = vm::deleteHost,
            onDismiss = { sheetOpen = false },
        )
    }
}
