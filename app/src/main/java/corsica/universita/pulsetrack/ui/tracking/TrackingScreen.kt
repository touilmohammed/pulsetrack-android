package corsica.universita.pulsetrack.ui.tracking

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import corsica.universita.pulsetrack.AppContainer
import corsica.universita.pulsetrack.R
import corsica.universita.pulsetrack.tracking.FinishedRun
import corsica.universita.pulsetrack.tracking.RunTracker
import corsica.universita.pulsetrack.tracking.TrackingService
import corsica.universita.pulsetrack.ui.formatDistance
import corsica.universita.pulsetrack.ui.permissions.PermissionState
import corsica.universita.pulsetrack.ui.permissions.rememberPermissionFlow
import kotlinx.coroutines.delay

/**
 * Le suivi de course : l'état vient de RunTracker (écrit par le service), l'écran ne fait qu'afficher et
 * démarrer ou arrêter le service. Une rotation ou un écran éteint ne changent rien à la mesure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(onSave: (FinishedRun) -> Unit) {
    val context = LocalContext.current
    val state by RunTracker.state.collectAsStateWithLifecycle()
    val permission = rememberPermissionFlow(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        requireAll = false,
    )
    // Approximative (± 2 km) : une position, mais pas de quoi mesurer une distance de course.
    val precise = permission.state == PermissionState.Granted &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    var startWhenGranted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(permission.state) {
        when (permission.state) {
            PermissionState.Granted -> if (startWhenGranted) {
                startWhenGranted = false
                if (precise) TrackingService.start(context) else AppContainer.analytics.runTracked("approximate")
            }
            PermissionState.DeniedOnce, PermissionState.DeniedForever -> {
                if (startWhenGranted) AppContainer.analytics.runTracked("denied")
                startWhenGranted = false
            }
            PermissionState.NotAsked -> Unit
        }
    }

    // Le chrono de l'écran ; la mesure, elle, ne dépend pas de cet affichage.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.running) {
        while (state.running) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tracking_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val finished = state.finished
            val elapsedMs = when {
                state.running -> now - state.startedAtMillis
                finished != null -> finished.durationMin * 60_000L
                else -> 0L
            }
            val km = finished?.distanceKm ?: (state.distanceM / 1000)

            Text(formatElapsed(elapsedMs), fontSize = 56.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric(stringResource(R.string.tracking_distance), "${formatDistance(km)} km")
                Metric(stringResource(R.string.tracking_pace), pace(elapsedMs, km))
            }
            if (state.running) {
                Text(
                    stringResource(
                        R.string.tracking_gps,
                        state.fixes,
                        state.lastAccuracyM?.let { "± ${it.toInt()} m" } ?: "—",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.tracking_running_hint), style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { TrackingService.stop(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.tracking_stop)) }
                return@Column
            }

            if (finished != null) {
                Button(onClick = { onSave(finished) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tracking_save))
                }
                OutlinedButton(onClick = RunTracker::reset, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tracking_discard))
                }
                return@Column
            }

            Text(stringResource(R.string.tracking_intro), style = MaterialTheme.typography.bodyMedium)
            when {
                precise -> Button(onClick = { TrackingService.start(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.tracking_start))
                }
                permission.state == PermissionState.Granted -> {
                    // Accordée, mais approximative seulement.
                    Text(stringResource(R.string.tracking_needs_precise), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = permission::openSettings) { Text(stringResource(R.string.open_settings)) }
                }
                permission.state == PermissionState.DeniedForever -> {
                    Text(stringResource(R.string.tracking_denied_forever), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = permission::openSettings) { Text(stringResource(R.string.open_settings)) }
                }
                else -> Button(onClick = {
                    startWhenGranted = true
                    permission.request()
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tracking_start)) }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall)
    }
}

private fun formatElapsed(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

/** Allure en minutes par kilomètre, l'unité des coureurs. */
private fun pace(ms: Long, km: Double): String {
    if (km < 0.05) return "—"
    val secondsPerKm = (ms / 1000.0 / km).toLong()
    return "%d:%02d /km".format(secondsPerKm / 60, secondsPerKm % 60)
}
