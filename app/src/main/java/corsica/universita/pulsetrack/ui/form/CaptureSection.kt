package corsica.universita.pulsetrack.ui.form

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import corsica.universita.pulsetrack.AppContainer
import corsica.universita.pulsetrack.R
import corsica.universita.pulsetrack.data.SessionLocation
import corsica.universita.pulsetrack.ui.locationText
import corsica.universita.pulsetrack.ui.permissions.PermissionState
import corsica.universita.pulsetrack.ui.permissions.rememberPermissionFlow

/** « Lieu et photo », facultatif. Aucune permission n'est demandée avant un clic sur ces boutons. */
@Composable
fun CaptureSection(
    location: SessionLocation?,
    locating: Boolean,
    photoPath: String?,
    message: String?,
    onLocate: () -> Unit,
    onRemoveLocation: () -> Unit,
    newCaptureUri: () -> Uri,
    onPhotoTaken: (Boolean) -> Unit,
    onPhotoPicked: (Uri?) -> Unit,
    onRemovePhoto: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.capture_title), style = MaterialTheme.typography.titleSmall)
        LocationRow(location, locating, onLocate, onRemoveLocation)
        PhotoRow(photoPath, newCaptureUri, onPhotoTaken, onPhotoPicked, onRemovePhoto)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun LocationRow(
    location: SessionLocation?,
    locating: Boolean,
    onLocate: () -> Unit,
    onRemove: () -> Unit,
) {
    // Précise et approximative ensemble : l'utilisateur choisit ce qu'il donne.
    val permission = rememberPermissionFlow(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        requireAll = false,
    )
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var locateWhenGranted by rememberSaveable { mutableStateOf(false) }

    // L'utilisateur vient d'accepter : on enchaîne sur la position qu'il demandait.
    // Un refus annule la demande : s'il accorde plus tard dans les réglages, c'est à lui de toucher le bouton.
    LaunchedEffect(permission.state) {
        when (permission.state) {
            PermissionState.Granted -> if (locateWhenGranted) {
                locateWhenGranted = false
                onLocate()
            }
            PermissionState.DeniedOnce, PermissionState.DeniedForever -> locateWhenGranted = false
            PermissionState.NotAsked -> Unit
        }
    }

    if (location != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(locationText(location), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRemove) { Text(stringResource(R.string.action_remove)) }
        }
        return
    }

    // Un bouton par état : il ne demande jamais à l'aveugle.
    when (permission.state) {
        PermissionState.Granted -> OutlinedButton(onClick = onLocate, enabled = !locating) {
            Text(stringResource(if (locating) R.string.locating else R.string.add_location))
        }
        PermissionState.NotAsked -> OutlinedButton(onClick = {
            locateWhenGranted = true
            permission.request()
        }) { Text(stringResource(R.string.add_location)) }
        PermissionState.DeniedOnce -> OutlinedButton(onClick = { showRationale = true }) {
            Text(stringResource(R.string.add_location))
        }
        PermissionState.DeniedForever -> Column {
            Text(
                stringResource(R.string.location_denied_forever),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = permission::openSettings) { Text(stringResource(R.string.open_settings)) }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.location_rationale_title)) },
            text = { Text(stringResource(R.string.location_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    locateWhenGranted = true
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
private fun PhotoRow(
    photoPath: String?,
    newCaptureUri: () -> Uri,
    onPhotoTaken: (Boolean) -> Unit,
    onPhotoPicked: (Uri?) -> Unit,
    onRemove: () -> Unit,
) {
    // L'appareil photo et le sélecteur du système : aucune permission à demander.
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), onPhotoTaken)
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), onPhotoPicked)

    if (photoPath != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoThumbnail(photoPath)
            TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_photo)) }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { takePicture.launch(newCaptureUri()) }) { Text(stringResource(R.string.take_photo)) }
        OutlinedButton(onClick = {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) { Text(stringResource(R.string.pick_photo)) }
    }
}

@Composable
private fun PhotoThumbnail(path: String) {
    val bitmap by produceState<ImageBitmap?>(null, path) { value = AppContainer.photos.load(path)?.asImageBitmap() }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = stringResource(R.string.photo_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
        )
    }
}
