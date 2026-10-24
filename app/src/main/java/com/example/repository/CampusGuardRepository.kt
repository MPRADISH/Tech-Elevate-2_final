package com.example.repository

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.CampusGuardPreferences
import com.example.data.model.CampusAlert
import com.example.data.model.ClientRegistration
import com.example.data.model.ConnectionState
import com.example.data.model.ServerConfig
import com.example.network.CampusGuardApiClient
import com.example.network.CampusGuardWebSocketManager
import com.example.network.WebSocketAlertListener
import com.example.service.AlarmController
import com.example.service.CampusGuardMonitorService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CampusGuardRepository private constructor(private val appContext: Context) : WebSocketAlertListener {
    private val tag = "CampusGuardRepo"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val preferences = CampusGuardPreferences(appContext)
    val apiClient = CampusGuardApiClient()
    val alarmController = AlarmController(appContext)

    private val wsManager = CampusGuardWebSocketManager(preferences, this)

    // Reactive StateFlows for UI
    private val _connectionState = MutableStateFlow(ConnectionState.OFFLINE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _isRegistered = MutableStateFlow(preferences.isRegistered)
    val isRegistered: StateFlow<Boolean> = _isRegistered.asStateFlow()

    private val _isMonitoringEnabled = MutableStateFlow(preferences.isMonitoringEnabled)
    val isMonitoringEnabled: StateFlow<Boolean> = _isMonitoringEnabled.asStateFlow()

    private val _hostelZone = MutableStateFlow(preferences.hostelZone)
    val hostelZone: StateFlow<Int> = _hostelZone.asStateFlow()

    private val _studentName = MutableStateFlow(preferences.studentName)
    val studentName: StateFlow<String> = _studentName.asStateFlow()

    private val _serverUrl = MutableStateFlow(preferences.serverUrl)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _clientId = MutableStateFlow(preferences.clientId)
    val clientId: StateFlow<String?> = _clientId.asStateFlow()

    private val _serverId = MutableStateFlow(preferences.serverId)
    val serverId: StateFlow<String?> = _serverId.asStateFlow()

    private val _cursor = MutableStateFlow(preferences.cursor)
    val cursor: StateFlow<Long> = _cursor.asStateFlow()

    private val _receiptsCount = MutableStateFlow(preferences.receiptsCount)
    val receiptsCount: StateFlow<Int> = _receiptsCount.asStateFlow()

    private val _clearMarkers = MutableStateFlow(preferences.getClearMarkers())
    val clearMarkers: StateFlow<Map<Int, Long>> = _clearMarkers.asStateFlow()

    private val _activeAlert = MutableStateFlow<CampusAlert?>(null)
    val activeAlert: StateFlow<CampusAlert?> = _activeAlert.asStateFlow()

    private val _alertsHistory = MutableStateFlow<List<CampusAlert>>(emptyList())
    val alertsHistory: StateFlow<List<CampusAlert>> = _alertsHistory.asStateFlow()

    private val _myResponses = MutableStateFlow<Map<String, String>>(emptyMap())
    val myResponses: StateFlow<Map<String, String>> = _myResponses.asStateFlow()

    private val _serverConfig = MutableStateFlow<ServerConfig?>(null)
    val serverConfig: StateFlow<ServerConfig?> = _serverConfig.asStateFlow()

    private val _serverTimeOffset = MutableStateFlow(0L)
    val serverTimeOffset: StateFlow<Long> = _serverTimeOffset.asStateFlow()

    private val _isAlarmSounding = MutableStateFlow(false)
    val isAlarmSounding: StateFlow<Boolean> = _isAlarmSounding.asStateFlow()

    init {
        refreshFlowsFromPreferences()
        evaluateActiveAlert()

        scope.launch {
            wsManager.connectionState.collect { state ->
                _connectionState.value = state
                if (state == ConnectionState.CONNECTED_WS) {
                    _connectionError.value = null
                }
            }
        }
    }

    private fun refreshFlowsFromPreferences() {
        _isRegistered.value = preferences.isRegistered
        _isMonitoringEnabled.value = preferences.isMonitoringEnabled
        _hostelZone.value = preferences.hostelZone
        _studentName.value = preferences.studentName
        _serverUrl.value = preferences.serverUrl
        _clientId.value = preferences.clientId
        _serverId.value = preferences.serverId
        _cursor.value = preferences.cursor
        _receiptsCount.value = preferences.receiptsCount
        _clearMarkers.value = preferences.getClearMarkers()
        _alertsHistory.value = preferences.getSavedAlerts().sortedByDescending { it.seq }
        _myResponses.value = preferences.getMyResponses()
    }

    fun startMonitoring() {
        preferences.isMonitoringEnabled = true
        _isMonitoringEnabled.value = true
        try {
            val serviceIntent = Intent(appContext, CampusGuardMonitorService::class.java).apply {
                action = CampusGuardMonitorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(serviceIntent)
            } else {
                appContext.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not start foreground service: ${e.message}")
        }
        wsManager.start()
    }

    fun stopMonitoring() {
        preferences.isMonitoringEnabled = false
        _isMonitoringEnabled.value = false
        wsManager.stop()
        silenceAlarm()
        try {
            val serviceIntent = Intent(appContext, CampusGuardMonitorService::class.java).apply {
                action = CampusGuardMonitorService.ACTION_STOP
            }
            appContext.startService(serviceIntent)
        } catch (e: Exception) {
            Log.w(tag, "Could not stop service: ${e.message}")
        }
    }

    fun saveServerUrlOnly(url: String) {
        val cleaned = url.trim().removeSuffix("/")
        preferences.serverUrl = cleaned
        _serverUrl.value = preferences.serverUrl
    }

    suspend fun testServerConnection(url: String): Result<ServerConfig> {
        saveServerUrlOnly(url)
        val result = apiClient.testConnection(preferences.serverUrl)
        result.onSuccess { config ->
            _serverConfig.value = config
            _connectionError.value = null
            if (config.serverTime.isNotEmpty()) {
                val serverEpoch = CampusAlert.parseIsoTimestamp(config.serverTime)
                if (serverEpoch > 0) {
                    _serverTimeOffset.value = serverEpoch - System.currentTimeMillis()
                }
            }
        }.onFailure { err ->
            _connectionError.value = "Cannot reach ${preferences.serverUrl}: ${err.localizedMessage ?: "Unreachable"}"
            _connectionState.value = ConnectionState.SERVER_ERROR
        }
        return result
    }

    suspend fun register(name: String, zone: Int, customServerUrl: String? = null): Result<ClientRegistration> {
        if (!customServerUrl.isNullOrBlank()) {
            saveServerUrlOnly(customServerUrl)
        }
        val url = preferences.serverUrl
        val result = apiClient.register(url, name, zone)
        result.onSuccess { reg ->
            preferences.saveRegistration(reg)
            _connectionError.value = null
            refreshFlowsFromPreferences()
            evaluateActiveAlert()
            wsManager.stop()
            if (preferences.isMonitoringEnabled) {
                startMonitoring()
            }
            syncNow()
        }.onFailure { err ->
            _connectionError.value = "Registration failed at $url: ${err.localizedMessage ?: "Server unreachable"}"
            _connectionState.value = ConnectionState.SERVER_ERROR
        }
        return result
    }

    suspend fun changeHostel(newZone: Int): Result<Int> {
        val clientId = preferences.clientId ?: return Result.failure(IllegalStateException("Not registered"))
        val token = preferences.clientToken ?: return Result.failure(IllegalStateException("Not registered"))
        val url = preferences.serverUrl

        val result = apiClient.updateZone(url, clientId, token, newZone)
        result.onSuccess { updatedZone ->
            preferences.hostelZone = updatedZone
            preferences.cursor = 0L
            _connectionError.value = null
            refreshFlowsFromPreferences()
            evaluateActiveAlert()
            wsManager.stop()
            if (preferences.isMonitoringEnabled) {
                startMonitoring()
            }
            syncNow()
        }.onFailure { err ->
            _connectionError.value = "Hostel update failed: ${err.localizedMessage ?: "Server unreachable"}"
        }
        return result
    }

    suspend fun syncNow() = withContext(Dispatchers.IO) {
        val clientId = preferences.clientId ?: return@withContext
        val token = preferences.clientToken ?: return@withContext
        val url = preferences.serverUrl
        val zone = preferences.hostelZone
        val cursorVal = preferences.cursor
        val serverIdVal = preferences.serverId

        val result = apiClient.sync(url, clientId, token, zone, cursorVal, serverIdVal)
        result.onSuccess { syncRes ->
            _connectionError.value = null
            if (_connectionState.value != ConnectionState.CONNECTED_WS) {
                _connectionState.value = ConnectionState.CONNECTED_POLLING
            }

            if (syncRes.serverTime.isNotEmpty()) {
                val serverEpoch = CampusAlert.parseIsoTimestamp(syncRes.serverTime)
                if (serverEpoch > 0) {
                    _serverTimeOffset.value = serverEpoch - System.currentTimeMillis()
                }
            }

            if (syncRes.reset || (preferences.serverId != null && preferences.serverId != syncRes.serverId)) {
                preferences.serverId = syncRes.serverId
                preferences.cursor = syncRes.cursor
                preferences.saveAlerts(emptyList())
            } else {
                if (syncRes.serverId.isNotEmpty()) {
                    preferences.serverId = syncRes.serverId
                }
                if (syncRes.cursor > preferences.cursor) {
                    preferences.cursor = syncRes.cursor
                }
            }

            preferences.setClearMarkers(syncRes.clearMarkers)

            syncRes.alerts.forEach { alert ->
                val isNew = preferences.upsertAlert(alert)
                val receiptRes = apiClient.sendReceipt(url, clientId, token, alert.id, "polling")
                if (receiptRes.isSuccess && isNew) {
                    preferences.incrementReceiptsCount()
                }
            }

            syncRes.responses.forEach { (alertId, status) ->
                preferences.setResponse(alertId, status)
            }

            refreshFlowsFromPreferences()
            evaluateActiveAlert()
        }.onFailure { err ->
            _connectionError.value = "Sync failed (${url}): ${err.localizedMessage ?: "Unreachable"}"
            if (_connectionState.value != ConnectionState.CONNECTED_WS) {
                _connectionState.value = ConnectionState.SERVER_ERROR
            }
        }
    }

    suspend fun respondToAlert(alertId: String, status: String, message: String?): Result<Boolean> {
        val clientId = preferences.clientId ?: return Result.failure(IllegalStateException("Not registered"))
        val token = preferences.clientToken ?: return Result.failure(IllegalStateException("Not registered"))
        val url = preferences.serverUrl

        preferences.setResponse(alertId, status)
        _myResponses.value = preferences.getMyResponses()

        CampusGuardMonitorService.onResponseRecorded(appContext, alertId, status)

        val result = apiClient.sendResponse(url, clientId, token, alertId, status, message, "app")
        result.onSuccess {
            _connectionError.value = null
            if (status == "safe") {
                wsManager.sendSafeAck(alertId)
            }
        }.onFailure { err ->
            _connectionError.value = "Saved locally; server sync failed: ${err.localizedMessage}"
        }
        return result
    }

    fun silenceAlarm() {
        alarmController.stopAlarm()
        _isAlarmSounding.value = false
        CampusGuardMonitorService.silenceEmergencyAlarm(appContext)
    }

    fun triggerTestAlarm() {
        alarmController.startAlarm(severity = 2, allowSound = true, allowVibration = true)
        _isAlarmSounding.value = true
        CampusGuardMonitorService.showTestNotification(appContext)

        scope.launch {
            delay(4000L)
            silenceAlarm()
        }
    }

    // --- WebSocketAlertListener callbacks ---

    override fun onAlertReceived(alert: CampusAlert, isReplay: Boolean) {
        preferences.upsertAlert(alert)
        if (alert.seq > preferences.cursor) {
            preferences.cursor = alert.seq
        }

        if (alert.type == "all-clear") {
            alert.effectiveZones.forEach { z ->
                preferences.updateClearMarker(z, alert.seq)
            }
            val studentZone = preferences.hostelZone
            if (alert.effectiveZones.contains(0) || alert.effectiveZones.contains(studentZone)) {
                silenceAlarm()
                CampusGuardMonitorService.showAllClearNotification(appContext, alert)
            }
        }

        refreshFlowsFromPreferences()
        evaluateActiveAlert()

        val studentZone = preferences.hostelZone
        val clearMarkersMap = preferences.getClearMarkers()
        if (alert.type != "all-clear" &&
            alert.isApplicable(studentZone) &&
            !alert.isCleared(clearMarkersMap, studentZone) &&
            !alert.isExpired(_serverTimeOffset.value)
        ) {
            val responseAlreadyGiven = preferences.getMyResponses()[alert.id] != null
            if (!isReplay || !responseAlreadyGiven) {
                CampusGuardMonitorService.showEmergencyAlertNotification(appContext, alert)
                if (preferences.audioAlarmEnabled || preferences.vibrationAlarmEnabled) {
                    alarmController.startAlarm(
                        severity = alert.severity,
                        allowSound = preferences.audioAlarmEnabled,
                        allowVibration = preferences.vibrationAlarmEnabled
                    )
                    _isAlarmSounding.value = true
                }
            }
        }
    }

    override fun onClearMarkerUpdated(zone: Int, seq: Long) {
        preferences.updateClearMarker(zone, seq)
        _clearMarkers.value = preferences.getClearMarkers()
        evaluateActiveAlert()
    }

    override fun onServerReset(newServerId: String, cursor: Long) {
        preferences.clearCredentials()
        preferences.serverId = newServerId
        preferences.cursor = cursor
        refreshFlowsFromPreferences()
        _activeAlert.value = null
        silenceAlarm()
    }

    override fun onServerTimeUpdated(serverTime: String) {
        val serverEpoch = CampusAlert.parseIsoTimestamp(serverTime)
        if (serverEpoch > 0) {
            _serverTimeOffset.value = serverEpoch - System.currentTimeMillis()
        }
    }

    override fun onConnectionError(error: String) {
        _connectionError.value = error
    }

    override fun onReceiptRecorded() {
        preferences.incrementReceiptsCount()
        _receiptsCount.value = preferences.receiptsCount
    }

    override fun onAckRecorded(alertId: String) {
        preferences.setResponse(alertId, "safe")
        _myResponses.value = preferences.getMyResponses()
    }

    private fun evaluateActiveAlert() {
        val studentZone = preferences.hostelZone
        val clearMarkersMap = preferences.getClearMarkers()
        val allAlerts = preferences.getSavedAlerts()

        val active = allAlerts.filter { alert ->
            alert.type != "all-clear" &&
                alert.isApplicable(studentZone) &&
                !alert.isCleared(clearMarkersMap, studentZone) &&
                !alert.isExpired(_serverTimeOffset.value)
        }.maxByOrNull { it.seq }

        _activeAlert.value = active
    }

    companion object {
        @Volatile
        private var instance: CampusGuardRepository? = null

        fun getInstance(context: Context): CampusGuardRepository {
            return instance ?: synchronized(this) {
                instance ?: CampusGuardRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
