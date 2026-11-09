package corsica.universita.pulsetrack.ui.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import corsica.universita.pulsetrack.R
import corsica.universita.pulsetrack.data.NOTE_MAX
import corsica.universita.pulsetrack.data.SessionType
import corsica.universita.pulsetrack.ui.TrackedRun
import corsica.universita.pulsetrack.ui.formatDate
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionFormScreen(
    sessionId: String?,
    onDone: () -> Unit,
    run: TrackedRun? = null,
    sharedText: String? = null,
    viewModel: SessionFormViewModel = viewModel(factory = SessionFormViewModel.factory(sessionId, run, sharedText)),
) {
    val type by viewModel.type.collectAsStateWithLifecycle()
    val dateMillis by viewModel.dateMillis.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val distance by viewModel.distance.collectAsStateWithLifecycle()
    val note by viewModel.note.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val locating by viewModel.locating.collectAsStateWithLifecycle()
    val photoPath by viewModel.photoPath.collectAsStateWithLifecycle()
    val captureMessage by viewModel.captureMessage.collectAsStateWithLifecycle()

    // Un état plutôt qu'un rappel : si l'écran tourne pendant l'enregistrement, la fermeture a quand même lieu.
    LaunchedEffect(saved) { if (saved) onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.isEditing) R.string.edit_session else R.string.new_session)) },
                // Toujours visible : sur un petit écran, le clavier cache le bouton du bas.
                actions = { TextButton(onClick = viewModel::save) { Text(stringResource(R.string.action_save)) } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TypePicker(type, viewModel::setType)
            DateField(dateMillis, viewModel::setDate)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = viewModel::setDuration,
                    label = { Text(stringResource(R.string.field_duration)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = distance,
                    onValueChange = viewModel::setDistance,
                    label = { Text(stringResource(R.string.field_distance)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(R.string.field_note)) },
                supportingText = { Text("${note.length} / $NOTE_MAX") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            CaptureSection(
                location = location,
                locating = locating,
                photoPath = photoPath,
                message = captureMessage,
                onLocate = viewModel::locate,
                onRemoveLocation = viewModel::removeLocation,
                newCaptureUri = viewModel::newCaptureUri,
                onPhotoTaken = viewModel::onPhotoTaken,
                onPhotoPicked = viewModel::onPhotoPicked,
                onRemovePhoto = viewModel::removePhoto,
            )
            errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_session))
            }
        }
    }
}

@Composable
private fun TypePicker(selected: SessionType, onSelect: (SessionType) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SessionType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.label) },
            )
        }
    }
}

/** Deux dialogues à la suite, comme un champ date et heure : d'abord le jour, puis l'heure. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(millis: Long, onChange: (Long) -> Unit) {
    var step by rememberSaveable { mutableStateOf(DateStep.NONE) }
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(millis).atZone(zone)

    OutlinedButton(onClick = { step = DateStep.DAY }, modifier = Modifier.fillMaxWidth()) {
        Text(formatDate(millis))
    }

    when (step) {
        DateStep.NONE -> Unit
        DateStep.DAY -> {
            // DatePicker raisonne en minuit UTC : on lui donne le jour affiché, pas l'instant.
            val state = rememberDatePickerState(
                initialSelectedDateMillis = current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { step = DateStep.NONE },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { picked ->
                            val day = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                            onChange(current.with(day).toInstant().toEpochMilli())
                        }
                        step = DateStep.TIME
                    }) { Text(stringResource(R.string.action_next)) }
                },
                dismissButton = {
                    TextButton(onClick = { step = DateStep.NONE }) { Text(stringResource(R.string.action_cancel)) }
                },
            ) { DatePicker(state) }
        }
        DateStep.TIME -> {
            val state = rememberTimePickerState(current.hour, current.minute, is24Hour = true)
            TimePickerDialog(
                onDismissRequest = { step = DateStep.NONE },
                title = { Text(stringResource(R.string.field_time)) },
                confirmButton = {
                    TextButton(onClick = {
                        onChange(current.withHour(state.hour).withMinute(state.minute).toInstant().toEpochMilli())
                        step = DateStep.NONE
                    }) { Text(stringResource(R.string.action_ok)) }
                },
            ) { TimePicker(state) }
        }
    }
}

private enum class DateStep { NONE, DAY, TIME }
