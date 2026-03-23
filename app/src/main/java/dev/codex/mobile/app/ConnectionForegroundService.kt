package dev.codex.mobile.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.codex.mobile.MainActivity
import dev.codex.mobile.R
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.HostProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

private data class ConnectionServiceSnapshot(
    val activeHost: HostProfile?,
    val connection: ConnectionState,
)

class ConnectionForegroundService : Service() {
    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository: CodexRepository by lazy { CodexAppGraph.repository }
    private var stateObservationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    repository.clearActiveHost()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }

            else -> {
                promoteToForeground(
                    notification = buildNotification(
                        hostName = getString(R.string.connection_service_generic_host),
                        connection = ConnectionState(phase = ConnectionPhase.Connecting),
                    ),
                )
                startObservingStateIfNeeded()
                serviceScope.launch {
                    repository.ensureActiveHostConnection()
                }
                return START_STICKY
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        stateObservationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startObservingStateIfNeeded() {
        if (stateObservationJob != null) return
        stateObservationJob = serviceScope.launch {
            combine(
                repository.observeHosts(),
                repository.observeConnection(),
            ) { hosts, connection ->
                ConnectionServiceSnapshot(
                    activeHost = hosts.firstOrNull { host -> host.isActive },
                    connection = connection,
                )
            }.collect { snapshot ->
                val activeHost: HostProfile = snapshot.activeHost ?: run {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return@collect
                }

                promoteToForeground(
                    notification = buildNotification(
                        hostName = activeHost.name,
                        connection = snapshot.connection,
                    ),
                )
            }
        }
    }

    private fun promoteToForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    private fun buildNotification(
        hostName: String,
        connection: ConnectionState,
    ): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.icon)
            .setContentTitle(notificationTitle(hostName = hostName, phase = connection.phase))
            .setContentText(notificationBody(hostName = hostName, connection = connection))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(openAppPendingIntent())
            .addAction(
                0,
                getString(R.string.connection_service_action_open_app),
                openAppPendingIntent(),
            )
            .addAction(
                0,
                getString(R.string.connection_service_action_disconnect),
                disconnectPendingIntent(),
            )
            .build()
    }

    private fun notificationTitle(
        hostName: String,
        phase: ConnectionPhase,
    ): String = when (phase) {
        ConnectionPhase.Connected -> getString(R.string.connection_service_title_connected, hostName)
        ConnectionPhase.Connecting -> getString(R.string.connection_service_title_connecting, hostName)
        ConnectionPhase.Reconnecting,
        ConnectionPhase.Disconnected,
        ConnectionPhase.Error,
        ConnectionPhase.Idle,
        -> getString(R.string.connection_service_title_reconnecting, hostName)
    }

    private fun notificationBody(
        hostName: String,
        connection: ConnectionState,
    ): String = when (connection.phase) {
        ConnectionPhase.Connected -> getString(R.string.connection_service_body_connected, hostName)
        ConnectionPhase.Connecting -> getString(R.string.connection_service_body_connecting, hostName)
        ConnectionPhase.Reconnecting -> connection.message
            ?: getString(R.string.connection_service_body_reconnecting, hostName)
        ConnectionPhase.Disconnected,
        ConnectionPhase.Error,
        ConnectionPhase.Idle,
        -> connection.message ?: getString(R.string.connection_service_body_reconnecting, hostName)
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun disconnectPendingIntent(): PendingIntent {
        val intent = Intent(this, ConnectionForegroundService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        return PendingIntent.getService(
            this,
            DISCONNECT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureNotificationChannel() {
        val notificationManager: NotificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.connection_service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.connection_service_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START = "dev.codex.mobile.action.connection.START"
        private const val ACTION_DISCONNECT = "dev.codex.mobile.action.connection.DISCONNECT"
        private const val NOTIFICATION_CHANNEL_ID = "background_connection"
        private const val NOTIFICATION_ID = 4_500
        private const val OPEN_APP_REQUEST_CODE = 100
        private const val DISCONNECT_REQUEST_CODE = 101

        fun start(context: Context) {
            val intent = Intent(context, ConnectionForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ConnectionForegroundService::class.java))
        }
    }
}
