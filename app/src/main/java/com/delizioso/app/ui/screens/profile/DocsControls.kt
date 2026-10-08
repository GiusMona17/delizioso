package com.delizioso.app.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delizioso.app.R
import com.delizioso.app.ui.components.ClayButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The documents for the assistant: a switch, the status and "send now". Off until the owner turns it on. */
@Composable
fun DocsControls(viewModel: DocsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val status = docsStatus(state.docs.enabled, state.configured, state.docs.lastPushedAt, state.docs.lastError)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.docs_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.docs_switch), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = state.docs.enabled,
                onCheckedChange = viewModel::setEnabled,
                enabled = !busy && state.configured,
            )
        }
        Text(
            statusText(status),
            style = MaterialTheme.typography.labelMedium,
            color = if (status is DocsStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ClayButton(
            text = stringResource(R.string.docs_send_now),
            enabled = !busy && state.configured && state.docs.enabled,
            onClick = viewModel::sendNow,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun statusText(status: DocsStatus): String = when (status) {
    DocsStatus.Off -> stringResource(R.string.docs_status_off)
    DocsStatus.NeedsServer -> stringResource(R.string.docs_status_needs_server)
    DocsStatus.Never -> stringResource(R.string.docs_status_never)
    is DocsStatus.Failed -> stringResource(R.string.docs_status_error, status.message)
    is DocsStatus.Sent -> stringResource(
        R.string.docs_status_last,
        Instant.ofEpochMilli(status.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
    )
}
