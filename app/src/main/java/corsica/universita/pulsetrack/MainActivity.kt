package corsica.universita.pulsetrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import corsica.universita.pulsetrack.ui.PulseTrackNavigation
import corsica.universita.pulsetrack.ui.theme.PulseTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseTrackTheme {
                PulseTrackNavigation()
            }
        }
    }
}
