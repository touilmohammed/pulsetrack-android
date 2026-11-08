package corsica.universita.pulsetrack.ui.detail

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import corsica.universita.pulsetrack.AppContainer
import corsica.universita.pulsetrack.R
import corsica.universita.pulsetrack.data.Session
import corsica.universita.pulsetrack.ui.SyncBadge
import corsica.universita.pulsetrack.ui.locationText
import corsica.universita.pulsetrack.ui.mapUrl
import corsica.universita.pulsetrack.ui.sessionMeta
import corsica.universita.pulsetrack.ui.shareText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    viewModel: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.factory(sessionId)),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = { TopAppBar(title = { Text(session?.type?.label.orEmpty()) }) },
    ) { padding ->
        val current = session ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = sessionMeta(current),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SyncBadge(current)
            if (current.note.isNotEmpty()) Text(current.note)
            current.photoPath?.let { DetailPhoto(it) }
            current.location?.let { location ->
                Text(stringResource(R.string.location_label, locationText(location)))
                TextButton(onClick = { openUrl(context, mapUrl(location)) }) { Text(stringResource(R.string.see_on_map)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
                OutlinedButton(onClick = { share(context, current) }) { Text(stringResource(R.string.action_share)) }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onClose)
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DetailPhoto(path: String) {
    val bitmap by produceState<ImageBitmap?>(null, path) { value = AppContainer.photos.load(path)?.asImageBitmap() }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = stringResource(R.string.photo_description),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)),
        )
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // Aucun navigateur : rien à ouvrir, la position reste affichée.
    }
}

/**
 * Le partage du système : texte, et photo si la séance en a une. L'app choisie reçoit une adresse
 * content:// avec un droit de lecture temporaire, jamais l'accès au dossier des photos.
 */
private fun share(context: Context, session: Session) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, shareText(session))
        val photo = session.photoPath
        if (photo != null) {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, AppContainer.photos.shareUri(photo))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_title)))
}
