package corsica.universita.pulsetrack.ui.journal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import corsica.universita.pulsetrack.R
import corsica.universita.pulsetrack.data.Session
import corsica.universita.pulsetrack.data.SessionType
import corsica.universita.pulsetrack.ui.sessionMeta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onNewSession: () -> Unit,
    onOpenSession: (String) -> Unit,
    viewModel: JournalViewModel = viewModel(factory = JournalViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // LazyColumn garde son premier élément visible : une séance insérée au-dessus resterait hors écran.
    // On ne remonte que si la tête de liste a changé, pas à chaque retour sur le journal.
    val newestId = state.sessions.firstOrNull()?.id
    var lastNewestId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(newestId) {
        if (newestId == null) return@LaunchedEffect
        if (lastNewestId != null && lastNewestId != newestId) listState.scrollToItem(0)
        lastNewestId = newestId
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.journal_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewSession) {
                Text(stringResource(R.string.new_session))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TypeFilter(
                selected = state.filter,
                onSelect = viewModel::setFilter,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (state.sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.journal_empty),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionCard(session, onClick = { onOpenSession(session.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeFilter(
    selected: SessionType?,
    onSelect: (SessionType?) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.filter_all)) },
        )
        SessionType.entries.forEach { type ->
            FilterChip(
                selected = selected == type,
                onClick = { onSelect(type) },
                label = { Text(type.label) },
            )
        }
    }
}

@Composable
private fun SessionCard(session: Session, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = session.type.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (session.isExample) {
                    SuggestionChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.badge_example)) })
                }
            }
            Text(
                text = sessionMeta(session),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (session.note.isNotEmpty()) Text(session.note)
        }
    }
}
