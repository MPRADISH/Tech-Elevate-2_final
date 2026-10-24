package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.repository.CampusGuardRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_MARK_SAFE = "com.example.campusguard.ACTION_MARK_SAFE"
        const val ACTION_NEED_HELP = "com.example.campusguard.ACTION_NEED_HELP"
        const val ACTION_SILENCE = "com.example.campusguard.ACTION_SILENCE"
        const val EXTRA_ALERT_ID = "extra_alert_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val alertId = intent.getStringExtra(EXTRA_ALERT_ID)
        Log.d("NotificationAction", "Action received: $action for alert $alertId")

        val repository = CampusGuardRepository.getInstance(context)

        when (action) {
            ACTION_SILENCE -> {
                repository.silenceAlarm()
            }
            ACTION_MARK_SAFE -> {
                repository.silenceAlarm()
                if (!alertId.isNullOrEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.respondToAlert(alertId, "safe", null)
                    }
                }
            }
            ACTION_NEED_HELP -> {
                repository.silenceAlarm()
                if (!alertId.isNullOrEmpty()) {
                    CoroutineScope(Dispatchers.IO).launch {
                        repository.respondToAlert(alertId, "needs-help", "Requested help via quick notification action")
                    }
                }
            }
        }
    }
}
