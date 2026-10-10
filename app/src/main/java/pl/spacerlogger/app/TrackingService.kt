package pl.spacerlogger.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import java.util.Locale

/** Usługa pierwszoplanowa – trasa jest rejestrowana także przy wygaszonym ekranie. */
class TrackingService : Service() {

    private lateinit var lm: LocationManager

    private val locListener = object : LocationListenerCompat {
        override fun onLocationChanged(location: Location) = Tracker.onLocation(location)
    }
    private val tick: () -> Unit = { if (Tracker.state != WalkState.IDLE) updateNotification() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val ch = NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        Tracker.addListener(tick)
    }

    override fun onDestroy() {
        stopUpdates()
        Tracker.removeListener(tick)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                ServiceCompat.startForeground(
                    this, NOTIF_ID, buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
                Tracker.start()
                startUpdates()
            }
            ACTION_PAUSE -> { Tracker.pause(); stopUpdates() }
            ACTION_RESUME -> { Tracker.resume(); startUpdates() }
            ACTION_STOP -> {
                stopUpdates()
                Tracker.reset()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> if (Tracker.state == WalkState.IDLE) stopSelf()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, locListener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Tracker.reset()
            stopSelf()
        }
    }

    private fun stopUpdates() {
        lm.removeUpdates(locListener)
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val paused = Tracker.state == WalkState.PAUSED
        val text = String.format(
            Locale.getDefault(), "%.2f km · %s · %.0f kcal",
            Tracker.distanceM / 1000.0, formatTime(Tracker.elapsedMs()), Tracker.calories
        )
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(getString(if (paused) R.string.notif_paused else R.string.notif_running))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val ACTION_START = "pl.spacerlogger.app.START"
        const val ACTION_PAUSE = "pl.spacerlogger.app.PAUSE"
        const val ACTION_RESUME = "pl.spacerlogger.app.RESUME"
        const val ACTION_STOP = "pl.spacerlogger.app.STOP"
        private const val CHANNEL = "walk"
        private const val NOTIF_ID = 1

        fun send(ctx: Context, action: String) {
            val i = Intent(ctx, TrackingService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
        }
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}
