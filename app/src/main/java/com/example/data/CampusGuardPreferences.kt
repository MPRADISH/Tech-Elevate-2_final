package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.CampusAlert
import com.example.data.model.ClientRegistration
import org.json.JSONArray
import org.json.JSONObject

class CampusGuardPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("campusguard_prefs", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, "http://192.168.1.100:8080") ?: "http://192.168.1.100:8080"
        set(value) {
            val normalized = value.trim().removeSuffix("/")
            prefs.edit().putString(KEY_SERVER_URL, normalized).apply()
        }

    var studentName: String
        get() = prefs.getString(KEY_STUDENT_NAME, "Student") ?: "Student"
        set(value) = prefs.edit().putString(KEY_STUDENT_NAME, value.trim()).apply()

    var hostelZone: Int
        get() = prefs.getInt(KEY_HOSTEL_ZONE, 1)
        set(value) = prefs.edit().putInt(KEY_HOSTEL_ZONE, value).apply()

    var clientId: String?
        get() = prefs.getString(KEY_CLIENT_ID, null)
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value).apply()

    var clientToken: String?
        get() = prefs.getString(KEY_CLIENT_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_CLIENT_TOKEN, value).apply()

    val isRegistered: Boolean
        get() = !clientId.isNullOrEmpty() && !clientToken.isNullOrEmpty()

    val authBearerHeader: String?
        get() {
            val id = clientId
            val token = clientToken
            return if (!id.isNullOrEmpty() && !token.isNullOrEmpty()) "Bearer $id.$token" else null
        }

    var serverId: String?
        get() = prefs.getString(KEY_SERVER_ID, null)
        set(value) = prefs.edit().putString(KEY_SERVER_ID, value).apply()

    var cursor: Long
        get() = prefs.getLong(KEY_CURSOR, 0L)
        set(value) = prefs.edit().putLong(KEY_CURSOR, value).apply()

    var isMonitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    var audioAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_ENABLED, value).apply()

    var vibrationAlarmEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var receiptsCount: Int
        get() = prefs.getInt(KEY_RECEIPTS_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_RECEIPTS_COUNT, value).apply()

    fun incrementReceiptsCount() {
        receiptsCount += 1
    }

    fun saveRegistration(reg: ClientRegistration) {
        prefs.edit()
            .putString(KEY_CLIENT_ID, reg.id)
            .putString(KEY_CLIENT_TOKEN, reg.token)
            .putInt(KEY_HOSTEL_ZONE, reg.zone)
            .putString(KEY_STUDENT_NAME, reg.name)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_CLIENT_ID)
            .remove(KEY_CLIENT_TOKEN)
            .remove(KEY_CURSOR)
            .remove(KEY_SERVER_ID)
            .remove(KEY_CLEAR_MARKERS)
            .remove(KEY_SAVED_ALERTS)
            .remove(KEY_RESPONSES)
            .apply()
    }

    fun getClearMarkers(): Map<Int, Long> {
        val raw = prefs.getString(KEY_CLEAR_MARKERS, null) ?: return emptyMap()
        val result = mutableMapOf<Int, Long>()
        return try {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key.toInt()] = json.getLong(key)
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun updateClearMarker(zone: Int, seq: Long) {
        val current = getClearMarkers().toMutableMap()
        val existing = current[zone] ?: 0L
        if (seq > existing) {
            current[zone] = seq
            val json = JSONObject()
            current.forEach { (k, v) -> json.put(k.toString(), v) }
            prefs.edit().putString(KEY_CLEAR_MARKERS, json.toString()).apply()
        }
    }

    fun setClearMarkers(markers: Map<Int, Long>) {
        val json = JSONObject()
        markers.forEach { (k, v) -> json.put(k.toString(), v) }
        prefs.edit().putString(KEY_CLEAR_MARKERS, json.toString()).apply()
    }

    fun getSavedAlerts(): List<CampusAlert> {
        val raw = prefs.getString(KEY_SAVED_ALERTS, null) ?: return emptyList()
        val list = mutableListOf<CampusAlert>()
        return try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                list.add(CampusAlert.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveAlerts(alerts: List<CampusAlert>) {
        val trimmed = alerts.takeLast(100)
        val arr = JSONArray()
        trimmed.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_SAVED_ALERTS, arr.toString()).apply()
    }

    fun upsertAlert(alert: CampusAlert): Boolean {
        val current = getSavedAlerts().toMutableList()
        val existingIndex = current.indexOfFirst { it.id == alert.id }
        val isNew = existingIndex < 0
        if (isNew) {
            current.add(alert)
        } else {
            current[existingIndex] = alert
        }
        saveAlerts(current)
        return isNew
    }

    fun getMyResponses(): Map<String, String> {
        val raw = prefs.getString(KEY_RESPONSES, null) ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        return try {
            val json = JSONObject(raw)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = json.getString(k)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun setResponse(alertId: String, status: String) {
        val current = getMyResponses().toMutableMap()
        current[alertId] = status
        val json = JSONObject()
        current.forEach { (k, v) -> json.put(k, v) }
        prefs.edit().putString(KEY_RESPONSES, json.toString()).apply()
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_STUDENT_NAME = "student_name"
        private const val KEY_HOSTEL_ZONE = "hostel_zone"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_CLIENT_TOKEN = "client_token"
        private const val KEY_SERVER_ID = "server_id"
        private const val KEY_CURSOR = "cursor_seq"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_AUDIO_ENABLED = "audio_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_RECEIPTS_COUNT = "receipts_count"
        private const val KEY_CLEAR_MARKERS = "clear_markers"
        private const val KEY_SAVED_ALERTS = "saved_alerts"
        private const val KEY_RESPONSES = "my_responses"
    }
}
