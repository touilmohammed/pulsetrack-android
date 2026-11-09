package corsica.universita.pulsetrack.ui.settings

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import corsica.universita.pulsetrack.R
import corsica.universita.pulsetrack.ui.permissions.PermissionFlow
import corsica.universita.pulsetrack.ui.permissions.PermissionState
import corsica.universita.pulsetrack.ui.permissions.rememberPermissionFlow

/** Lundi d'abord, comme dans la PWA ; la valeur suit Date.getDay() (0 = dimanche). */
private val DAYS = listOf(1 to "Lun", 2 to "Mar", 3 to "Mer", 4 to "Jeu", 5 to "Ven", 6 to "Sam", 0 to "Dim")
private val HOURS = 6..22

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val hour by viewModel.hour.collectAsStateWithLifecycle()
    val enabled by viewModel.enabled.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val analyticsConsent by viewModel.analyticsConsent.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewModel.analyticsAvailable) {
                AnalyticsSetting(analyticsConsent == true, viewModel::setAnalyticsConsent)
                HorizontalDivider()
            }
            Text(stringResource(R.string.reminders_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.reminders_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!viewModel.available) {
                Text(stringResource(R.string.reminders_unavailable), color = MaterialTheme.colorScheme.error)
                return@Column
            }

            Text(stringResource(R.string.reminders_days), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DAYS.forEach { (value, label) ->
                    FilterChip(selected = value in days, onClick = { viewModel.toggleDay(value) }, label = { Text(label) })
                }
            }
            HourPicker(hour, viewModel::setHour)

            EnableButton(enabled = enabled, busy = busy, onEnable = viewModel::enable)
            if (enabled) {
                OutlinedButton(onClick = viewModel::sendTest, enabled = !busy) { Text(stringResource(R.string.reminders_test)) }
                OutlinedButton(onClick = viewModel::disable, enabled = !busy) { Text(stringResource(R.string.reminders_disable)) }
            }
            StatusText(status, enabled, days, hour)
        }
    }
}

@Composable
private fun AnalyticsSetting(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.consent_setting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun HourPicker(hour: Int, onChange: (Int) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.reminders_hour), style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(onClick = { open = true }) { Text(if (hour in HOURS) "$hour h" else "—") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            HOURS.forEach { h ->
                DropdownMenuItem(text = { Text("$h h") }, onClick = {
                    onChange(h)
                    open = false
                })
            }
        }
    }
}

/**
 * Le bouton fait ce que l'état de la permission permet (Android 13+) : il ne demande jamais à l'aveugle.
 * Avant Android 13, notifier n'est pas une permission : le bouton active directement.
 */
@Composable
private fun EnableButton(enabled: Boolean, busy: Boolean, onEnable: () -> Unit) {
    val label = stringResource(if (enabled) R.string.reminders_update else R.string.reminders_enable)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Button(onClick = onEnable, enabled = !busy) { Text(label) }
        return
    }
    val permission = rememberPermissionFlow(Manifest.permission.POST_NOTIFICATIONS)
    NotificationPermissionButton(permission, label, busy, onEnable)
}

@Composable
private fun NotificationPermissionButton(permission: PermissionFlow, label: String, busy: Boolean, onEnable: () -> Unit) {
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var enableWhenGranted by rememberSaveable { mutableStateOf(false) }

    // L'utilisateur vient d'accepter : on enchaîne sur l'activation qu'il demandait. Un refus l'annule.
    LaunchedEffect(permission.state) {
        when (permission.state) {
            PermissionState.Granted -> if (enableWhenGranted) {
                enableWhenGranted = false
                onEnable()
            }
            PermissionState.DeniedOnce, PermissionState.DeniedForever -> enableWhenGranted = false
            PermissionState.NotAsked -> Unit
        }
    }

    when (permission.state) {
        PermissionState.Granted -> Button(onClick = onEnable, enabled = !busy) { Text(label) }
        PermissionState.NotAsked -> Button(onClick = {
            enableWhenGranted = true
            permission.request()
        }) { Text(label) }
        PermissionState.DeniedOnce -> Button(onClick = { showRationale = true }) { Text(label) }
        PermissionState.DeniedForever -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.notifications_denied_forever),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = permission::openSettings) { Text(stringResource(R.string.open_settings)) }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.notifications_rationale_title)) },
            text = { Text(stringResource(R.string.notifications_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    enableWhenGranted = true
                    permission.request()
                }) { Text(stringResource(R.string.action_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text(stringResource(R.string.action_not_now)) }
            },
        )
    }
}

@Composable
private fun StatusText(status: ReminderStatus, enabled: Boolean, days: IntArray, hour: Int) {
    val dayNames = DAYS.filter { it.first in days }.joinToString(" ") { it.second.lowercase() + "." }
    val text = when (status) {
        ReminderStatus.None -> if (enabled) stringResource(R.string.reminders_status_on, dayNames, hour) else null
        ReminderStatus.Enabled -> stringResource(R.string.reminders_status_on, dayNames, hour)
        ReminderStatus.Disabled -> stringResource(R.string.reminders_status_off)
        ReminderStatus.TestSent -> stringResource(R.string.reminders_status_test)
        ReminderStatus.Offline -> stringResource(R.string.reminders_status_offline)
        ReminderStatus.NoDays -> stringResource(R.string.reminders_status_no_days)
        is ReminderStatus.Failed -> status.message
    } ?: return
    val isError = status is ReminderStatus.Failed || status == ReminderStatus.Offline || status == ReminderStatus.NoDays
    Text(text, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
}
