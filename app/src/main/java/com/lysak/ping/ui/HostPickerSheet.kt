// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lysak.ping.R
import com.lysak.ping.core.HostValidation
import com.lysak.ping.data.PingPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostPickerSheet(
    prefs: PingPrefs,
    onSelect: (String) -> Unit,
    onAdd: (label: String, value: String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.hosts_title),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            prefs.hosts.forEach { host ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = host.value == prefs.selected.value,
                                onClick = { onSelect(host.value) },
                            ).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = host.value == prefs.selected.value,
                        onClick = { onSelect(host.value) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(host.label)
                        Text(
                            text = host.value,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (host.deletable) {
                        IconButton(onClick = { onDelete(host.value) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.hosts_delete_cd),
                            )
                        }
                    }
                }
            }

            AddHostRow(onAdd)
        }
    }
}

@Composable
private fun AddHostRow(
    onAdd: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var label by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    val trimmed = value.trim()
    val showError = trimmed.isNotEmpty() && !HostValidation.isValid(trimmed)
    val canAdd = trimmed.isNotEmpty() && !showError

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text(stringResource(R.string.hosts_label_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(stringResource(R.string.hosts_host_hint)) },
            singleLine = true,
            isError = showError,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri,
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (showError) {
            Text(
                text = stringResource(R.string.hosts_invalid),
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = {
                onAdd(label.trim(), trimmed)
                label = ""
                value = ""
            },
            enabled = canAdd,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.hosts_add))
        }
    }
}
