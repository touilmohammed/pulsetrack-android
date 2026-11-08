package corsica.universita.pulsetrack.ui.permissions

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Une permission n'est pas un booléen. L'OS ne répond qu'à deux questions (accordée ? refusée une fois ?),
 * qui ne distinguent pas « jamais demandée » de « refusée définitivement » : un drapeau « déjà demandée »,
 * gardé par l'app, les sépare.
 */
enum class PermissionState { NotAsked, Granted, DeniedOnce, DeniedForever }

class PermissionFlow(
    private val activity: Activity,
    private val permissions: List<String>,
    /** Faux pour la position : approximative seule suffit, ce n'est pas un refus. */
    private val requireAll: Boolean,
    private val askedBefore: () -> Boolean,
    private val markAsked: () -> Unit,
) {
    var state by mutableStateOf(compute(afterResult = false))
        private set

    private var launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>? = null

    internal fun attach(launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>) {
        this.launcher = launcher
    }

    /** Ouvre le dialogue de l'OS. Après un refus définitif, l'OS ne l'ouvre plus et répond aussitôt « refusé ». */
    fun request() {
        markAsked()
        launcher?.launch(permissions.toTypedArray())
    }

    internal fun onResult() {
        state = compute(afterResult = true)
    }

    /** À relire au retour au premier plan : l'utilisateur a pu tout changer dans les réglages. */
    fun refresh() {
        state = compute(afterResult = false)
    }

    /** Le seul endroit où l'utilisateur peut encore accorder une permission refusée définitivement. */
    fun openSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
        )
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    private fun compute(afterResult: Boolean): PermissionState {
        val granted = if (requireAll) permissions.all(::granted) else permissions.any(::granted)
        val rationale = permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        return when {
            granted -> PermissionState.Granted
            rationale -> PermissionState.DeniedOnce // refusée une fois : expliquer, puis redemander
            askedBefore() || afterResult -> PermissionState.DeniedForever // les réglages seulement
            else -> PermissionState.NotAsked
        }
    }
}

@Composable
fun rememberPermissionFlow(vararg permissions: String, requireAll: Boolean = true): PermissionFlow {
    val activity = requireNotNull(LocalActivity.current)
    // rememberSaveable : survit à la rotation que le dialogue de l'OS peut provoquer.
    var asked by rememberSaveable { mutableStateOf(false) }
    val flow = remember {
        PermissionFlow(activity, permissions.toList(), requireAll, askedBefore = { asked }, markAsked = { asked = true })
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        flow.onResult()
    }
    flow.attach(launcher)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { flow.refresh() }
    return flow
}
