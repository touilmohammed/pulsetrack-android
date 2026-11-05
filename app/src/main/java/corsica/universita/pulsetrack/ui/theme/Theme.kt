package corsica.universita.pulsetrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Mêmes couleurs que styles.css de la PWA.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCCEBE6),
    onPrimaryContainer = Color(0xFF0B3D38),
    secondaryContainer = Color(0xFFCCEBE6),
    onSecondaryContainer = Color(0xFF0B3D38),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EEEC),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF14201E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14201E),
    onSurfaceVariant = Color(0xFF52615E),
    outline = Color(0xFFD5DEDB),
    error = Color(0xFFB91C1C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF2DD4BF),
    onPrimary = Color(0xFF04201C),
    primaryContainer = Color(0xFF134E48),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondaryContainer = Color(0xFF134E48),
    onSecondaryContainer = Color(0xFFCCFBF1),
    surfaceContainer = Color(0xFF15201E),
    surfaceContainerHighest = Color(0xFF15201E),
    surfaceVariant = Color(0xFF1E2C29),
    background = Color(0xFF0B1211),
    onBackground = Color(0xFFE6EEEC),
    surface = Color(0xFF15201E),
    onSurface = Color(0xFFE6EEEC),
    onSurfaceVariant = Color(0xFF9BB0AB),
    outline = Color(0xFF2A3A37),
    error = Color(0xFFF87171),
)

@Composable
fun PulseTrackTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
