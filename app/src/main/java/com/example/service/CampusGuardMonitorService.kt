package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.CampusGuardPreferences
import com.example.data.model.CampusAlert
import com.example.data.model.Hostel
import com.example.repository.CampusGuardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CampusGuardMonitorService : Service() {
    private val tag = "CampusGuardService"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var periodicSyncJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var preferences: CampusGuardPreferences
    private lateinit var repository: CampusGuardRepository

    override fun onCreate() {
        super.onCreate()
        preferences = CampusGuardPreferences(this)
        repository = CampusGuardRepository.getInstance(this)
        createNotificationChannels()
        Log.d(tag, "CampusGuardMonitorService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(tag, "Service onStartCommand action=$action")

        when (action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundMonitoring()
            }
        }
        return START_STICKY
    }

    private fun startForegroundMonitoring() {
        val notification = buildOngoingMonitoringNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID_MONITORING,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID_MONITORING, notification)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to enter foreground state: ${e.message}")
        }

        startPeriodicSync()
    }

    private fun stopForegroundService() {
        periodicSyncJob?.cancel()
        periodicSyncJob = null
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (ignored: Exception) {
        }
        stopSelf()
    }

    private fun startPeriodicSync() {
        periodicSyncJob?.cancel()
        periodicSyncJob = scope.launch {
            while (isActive) {
                delay(15000L)
                if (preferences.isRegistered) {
                    try {
                        repository.syncNow()
                        updateOngoingNotification()
                    } catch (e: Exception) {
                        Log.w(tag, "Background periodic sync check: ${e.message}")
                    }
                }
            }
        }
    }

    private fun buildOngoingMonitoringNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hostelName = Hostel.getDisplayName(preferences.hostelZone)
        val statusText = if (preferences.isRegistered) {
            "Listening for alerts in $hostelName • Local Network Active"
        } else {
            "Setup required • Open app to register student"
        }

        return NotificationCompat.Builder(this, CHANNEL_MONITORING)
            .setContentTitle("CampusGuard • Protection Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateOngoingNotification() {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID_MONITORING, buildOngoingMonitoringNotification())
        } catch (ignored: Exception) {
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING,
                "CampusGuard Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows that CampusGuard is monitoring for emergency alerts over campus Wi-Fi"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(monitoringChannel)

            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "Emergency Campus Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent evacuation drills, fire alarms, and security alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 700)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(emergencyChannel)

            val allClearChannel = NotificationChannel(
                CHANNEL_ALL_CLEAR,
                "Campus All-Clear Notices",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "All-clear notices and safety acknowledgement status"
            }
            notificationManager.createNotificationChannel(allClearChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundService()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.campusguard.service.START"
        const val ACTION_STOP = "com.example.campusguard.service.STOP"

        const val CHANNEL_MONITORING = "campusguard_monitoring_channel"
        const val CHANNEL_EMERGENCY = "campusguard_emergency_channel"
        const val CHANNEL_ALL_CLEAR = "campusguard_all_clear_channel"

        const val NOTIFICATION_ID_MONITORING = 1001
        const val NOTIFICATION_ID_EMERGENCY = 1002
        const val NOTIFICATION_ID_ALL_CLEAR = 1003

        private fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (notificationManager.getNotificationChannel(CHANNEL_EMERGENCY) == null) {
                    val emergencyChannel = NotificationChannel(
                        CHANNEL_EMERGENCY,
                        "Emergency Campus Alerts",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Urgent evacuation drills, fire alarms, and security alerts"
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 700)
                        setBypassDnd(true)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    }
                    notificationManager.createNotificationChannel(emergencyChannel)
                }
                if (notificationManager.getNotificationChannel(CHANNEL_ALL_CLEAR) == null) {
                    val allClearChannel = NotificationChannel(
                        CHANNEL_ALL_CLEAR,
                        "Campus All-Clear Notices",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationManager.createNotificationChannel(allClearChannel)
                }
            }
        }

        fun showEmergencyAlertNotification(context: Context, alert: CampusAlert) {
            try {
                ensureChannels(context)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val safeIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_MARK_SAFE
                    putExtra(NotificationActionReceiver.EXTRA_ALERT_ID, alert.id)
                }
                val safePendingIntent = PendingIntent.getBroadcast(
                    context,
                    101,
                    safeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val helpIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_NEED_HELP
                    putExtra(NotificationActionReceiver.EXTRA_ALERT_ID, alert.id)
                }
                val helpPendingIntent = PendingIntent.getBroadcast(
                    context,
                    102,
                    helpIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val silenceIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                    action = NotificationActionReceiver.ACTION_SILENCE
                    putExtra(NotificationActionReceiver.EXTRA_ALERT_ID, alert.id)
                }
                val silencePendingIntent = PendingIntent.getBroadcast(
                    context,
                    103,
                    silenceIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val severityTitle = when (alert.severity) {
                    3 -> "CRITICAL EMERGENCY"
                    2 -> "URGENT ALERT"
                    else -> "CAMPUS ADVISORY"
                }

                val title = "$severityTitle: ${alert.type.uppercase()}"
                val content = "${alert.message}\nHostels: ${alert.zonesLabel}\nAssembly: ${alert.assemblyPointDisplayName}"

                val builder = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
                    .setContentTitle(title)
                    .setContentText(alert.message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentIntent(openPendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .addAction(android.R.drawable.checkbox_on_background, "✓ I'm Safe", safePendingIntent)
                    .addAction(android.R.drawable.ic_dialog_alert, "⊕ Need Help", helpPendingIntent)
                    .addAction(android.R.drawable.ic_lock_silent_mode, "Silence", silencePendingIntent)

                notificationManager.notify(NOTIFICATION_ID_EMERGENCY, builder.build())
            } catch (e: Exception) {
                Log.w("CampusGuardService", "Could not post emergency notification: ${e.message}")
            }
        }

        fun showAllClearNotification(context: Context, alert: CampusAlert) {
            try {
                ensureChannels(context)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(NOTIFICATION_ID_EMERGENCY)

                val openAppIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openAppIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, CHANNEL_ALL_CLEAR)
                    .setContentTitle("✓ ALL-CLEAR: ${alert.zonesLabel}")
                    .setContentText(alert.message.ifEmpty { "The emergency drill or incident has been cleared. Follow campus directions." })
                    .setSmallIcon(android.R.drawable.checkbox_on_background)
                    .setContentIntent(openPendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)

                notificationManager.notify(NOTIFICATION_ID_ALL_CLEAR, builder.build())
            } catch (e: Exception) {
                Log.w("CampusGuardService", "Could not post all-clear notification: ${e.message}")
            }
        }

        fun onResponseRecorded(context: Context, alertId: String, status: String) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (status == "safe") {
                    notificationManager.cancel(NOTIFICATION_ID_EMERGENCY)
                }
            } catch (ignored: Exception) {
            }
        }

        fun silenceEmergencyAlarm(context: Context) {
            // Silence handled in AlarmController
        }

        fun showTestNotification(context: Context) {
            try {
                ensureChannels(context)
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val builder = NotificationCompat.Builder(context, CHANNEL_EMERGENCY)
                    .setContentTitle("TEST: CampusGuard Siren & Notification")
                    .setContentText("Emergency notification and audible siren test succeeded. Native notifications work in the background!")
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)

                notificationManager.notify(9999, builder.build())
            } catch (e: Exception) {
                Log.w("CampusGuardService", "Could not post test notification: ${e.message}")
            }
        }
    }
}
