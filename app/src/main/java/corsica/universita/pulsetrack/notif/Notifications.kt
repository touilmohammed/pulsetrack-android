package corsica.universita.pulsetrack.notif

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import corsica.universita.pulsetrack.MainActivity
import corsica.universita.pulsetrack.R

/** Les deux canaux, et les notifications qu'on y poste. */
object Notifications {

    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_SYNC = "sync"
    const val ACTION_NEW_SESSION = "corsica.universita.pulsetrack.NEW_SESSION"

    private const val ID_REMINDER = 1
    private const val ID_SYNC = 2

    /**
     * Dans Application.onCreate : un canal doit exister avant la première notification, même celle d'un
     * push arrivé app fermée. Sans canal, notify() ne fait rien, sans exception (« la notification muette »).
     * L'app propose une importance ; ensuite, elle appartient à l'utilisateur, dans les réglages.
     */
    fun createChannels(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_REMINDERS, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(context.getString(R.string.channel_reminders))
                .setDescription(context.getString(R.string.channel_reminders_description))
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_SYNC, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.channel_sync))
                .setDescription(context.getString(R.string.channel_sync_description))
                .build(),
        )
    }

    /** Notifications coupées dans les réglages, ou permission refusée (Android 13+) : rien à poster. */
    fun canPost(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    /** Le rappel d'entraînement : un clic ouvre directement « Nouvelle séance ». */
    fun showReminder(context: Context, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openNewSession(context))
            .setAutoCancel(true)
            .build()
        post(context, ID_REMINDER, notification)
    }

    /** Silencieuse (canal LOW) : l'utilisateur sait que ses séances hors ligne sont arrivées. */
    fun showSyncDone(context: Context, count: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_sync_title))
            .setContentText(context.resources.getQuantityString(R.plurals.notif_sync_text, count, count))
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .build()
        post(context, ID_SYNC, notification) // même id : la suivante remplace la précédente
    }

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission retirée entre la vérification et l'envoi : rien à afficher.
        }
    }

    /**
     * L'app peut être tuée avant le clic : elle confie à l'OS un Intent tout prêt, qu'il lancera à sa place.
     * FLAG_IMMUTABLE : personne ne peut modifier l'Intent confié. Un requestCode par action, sinon l'OS
     * réutiliserait le même PendingIntent pour les deux notifications.
     */
    private fun openNewSession(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_NEW_SESSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, ID_REMINDER, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, ID_SYNC, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
