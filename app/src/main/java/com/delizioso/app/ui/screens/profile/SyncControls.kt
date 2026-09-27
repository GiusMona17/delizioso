package com.delizioso.app.ui.screens.profile

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delizioso.app.R
import com.delizioso.app.data.sync.SyncOutcome
import com.delizioso.app.data.sync.SyncSettings
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayLabelledField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SyncControls(viewModel: SyncViewModel) {
    val settings by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    var url by rememberSaveable(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var token by rememberSaveable(settings.token) { mutableStateOf(settings.token) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.sync_desc), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ClayLabelledField(
            label = stringResource(R.string.sync_server),
            value = url,
            onValueChange = { url = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        ClayLabelledField(
            label = stringResource(R.string.sync_token),
            value = token,
            onValueChange = { token = it },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        Text(
            statusLine(settings),
            style = MaterialTheme.typography.labelMedium,
            color = if (settings.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (settings.conflict) {
            Text(stringResource(R.string.sync_conflict), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ClayButton(
                    text = stringResource(R.string.sync_keep_phone),
                    enabled = !busy,
                    onClick = viewModel::keepThisDevice,
                    modifier = Modifier.weight(1f),
                )
                ClayButton(
                    text = stringResource(R.string.sync_use_pi),
                    enabled = !busy,
                    onClick = viewModel::usePiVersion,
                    container = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ClayButton(
                text = stringResource(R.string.sync_save),
                enabled = !busy,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.save(url, token)
                },
                container = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            ClayButton(
                text = stringResource(R.string.sync_now),
                enabled = !busy && settings.isConfigured,
                onClick = viewModel::syncNow,
                modifier = Modifier.weight(1f),
            )
        }
        outcome?.let {
            Text(
                outcomeText(it),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().clickable(onClick = viewModel::dismissOutcome),
            )
        }
    }
}

@Composable
fun outcomeText(outcome: SyncOutcome): String = when (outcome) {
    SyncOutcome.NotConfigured -> stringResource(R.string.sync_outcome_not_configured)
    SyncOutcome.UpToDate -> stringResource(R.string.sync_outcome_up_to_date)
    SyncOutcome.NothingOnPi -> stringResource(R.string.sync_outcome_nothing_on_pi)
    SyncOutcome.Pushed -> stringResource(R.string.sync_outcome_pushed)
    SyncOutcome.Pulled -> stringResource(R.string.sync_outcome_pulled)
    SyncOutcome.Conflict -> stringResource(R.string.sync_outcome_conflict)
    SyncOutcome.UpdateRequired -> stringResource(R.string.sync_outcome_update_required)
    is SyncOutcome.Failed -> stringResource(R.string.sync_outcome_failed, outcome.message)
}

@Composable
private fun statusLine(s: SyncSettings): String = when {
    !s.isConfigured -> stringResource(R.string.sync_not_configured)
    s.lastError != null -> stringResource(R.string.sync_error, s.lastError)
    s.lastSyncAt != null -> stringResource(
        R.string.sync_last,
        Instant.ofEpochMilli(s.lastSyncAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
    )
    else -> stringResource(R.string.sync_never)
}
