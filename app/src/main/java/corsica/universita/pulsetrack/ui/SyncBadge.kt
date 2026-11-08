package corsica.universita.pulsetrack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import corsica.universita.pulsetrack.R
import corsica.universita.pulsetrack.data.Session
import corsica.universita.pulsetrack.data.SyncState

/** Le badge d'une séance, comme dans le journal de la PWA : où en est son envoi. */
@Composable
fun SyncBadge(session: Session, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (label, content, container) = when {
        session.isExample -> Triple(R.string.badge_example, colors.onSurfaceVariant, Color.Transparent)
        session.syncState == SyncState.SYNCED -> Triple(R.string.badge_synced, colors.primary, Color.Transparent)
        session.syncState == SyncState.PENDING -> Triple(R.string.badge_pending, Color(0xFF78350F), Color(0xFFFEF3C7))
        session.syncState == SyncState.CONFLICT -> Triple(R.string.badge_conflict, colors.error, Color.Transparent)
        else -> Triple(R.string.badge_error, colors.error, Color.Transparent)
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, content.copy(alpha = 0.6f)),
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
