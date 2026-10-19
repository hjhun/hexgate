package dev.hexgate

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/** User-visible protection for a user-initiated, long-running SSH connection. */
class ConnectionService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "SSH connection", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == DISCONNECT) {
            (application as HexgateApplication).disconnectSession()
            stopSelf()
            return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val disconnect = PendingIntent.getService(this, 1,
            Intent(this, ConnectionService::class.java).setAction(DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_connection)
            .setContentTitle("Hexgate SSH")
            .setContentText("Connection active in background")
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .addAction(0, "Disconnect", disconnect).build()
        startForeground(71, notification)
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Hexgate:SSH")
                .apply { setReferenceCounted(false); acquire() }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
        (application as HexgateApplication).releaseIdleSession()
    }
    companion object {
        private const val CHANNEL = "ssh_connection"
        private const val DISCONNECT = "dev.hexgate.DISCONNECT"
    }
}
