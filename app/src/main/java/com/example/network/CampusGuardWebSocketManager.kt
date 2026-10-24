package com.example.network

import android.util.Log
import com.example.data.CampusGuardPreferences
import com.example.data.model.CampusAlert
import com.example.data.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface WebSocketAlertListener {
    fun onAlertReceived(alert: CampusAlert, isReplay: Boolean)
    fun onClearMarkerUpdated(zone: Int, seq: Long)
    fun onServerReset(newServerId: String, cursor: Long)
    fun onServerTimeUpdated(serverTime: String)
    fun onConnectionError(error: String)
    fun onReceiptRecorded()
    fun onAckRecorded(alertId: String)
}

class CampusGuardWebSocketManager(
    private val preferences: CampusGuardPreferences,
    private val alertListener: WebSocketAlertListener
) {
    private val tag = "CampusGuardWS"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null

    @Volatile
    private var webSocket: WebSocket? = null
    @Volatile
    private var isManualStop = false

    private val _connectionState = MutableStateFlow(ConnectionState.OFFLINE)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun start() {
        isManualStop = false
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch {
            var retryDelay = 1500L
            while (isActive && !isManualStop) {
                if (!preferences.isRegistered) {
                    _connectionState.value = ConnectionState.OFFLINE
                    delay(2000L)
                    continue
                }

                _connectionState.value = ConnectionState.CONNECTING
                val started = connectWebSocket()
                if (!started) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 1.5).toLong().coerceAtMost(10000L)
                    continue
                }

                // Wait while socket is active
                while (isActive && webSocket != null && !isManualStop) {
                    delay(1000L)
                }

                if (!isManualStop) {
                    if (_connectionState.value != ConnectionState.SERVER_ERROR) {
                        _connectionState.value = ConnectionState.OFFLINE
                    }
                    delay(retryDelay)
                    retryDelay = (retryDelay * 1.5).toLong().coerceAtMost(10000L)
                }
            }
        }
    }

    fun stop() {
        isManualStop = true
        heartbeatJob?.cancel()
        connectionJob?.cancel()
        connectionJob = null
        try {
            webSocket?.close(1000, "Stopped monitoring")
        } catch (ignored: Exception) {
        }
        webSocket = null
        _connectionState.value = ConnectionState.OFFLINE
    }

    private fun connectWebSocket(): Boolean {
        val baseUrl = preferences.serverUrl.trim().removeSuffix("/")
        if (baseUrl.isBlank()) {
            _connectionState.value = ConnectionState.SERVER_ERROR
            alertListener.onConnectionError("Server URL is empty")
            return false
        }

        val wsUrl = when {
            baseUrl.startsWith("https://", ignoreCase = true) ->
                baseUrl.replaceFirst("https://", "wss://", ignoreCase = true) + "/ws"
            baseUrl.startsWith("http://", ignoreCase = true) ->
                baseUrl.replaceFirst("http://", "ws://", ignoreCase = true) + "/ws"
            else -> "ws://$baseUrl/ws"
        }

        val request = try {
            Request.Builder().url(wsUrl).build()
        } catch (e: Exception) {
            Log.e(tag, "Invalid WebSocket URL: $wsUrl", e)
            _connectionState.value = ConnectionState.SERVER_ERROR
            alertListener.onConnectionError("Invalid server URL: ${e.message ?: wsUrl}")
            return false
        }

        return try {
            Log.d(tag, "Connecting to WebSocket: $wsUrl")
            webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    Log.d(tag, "WebSocket opened, sending hello...")
                    sendHello(ws)
                    startHeartbeats(ws)
                }

                override fun onMessage(ws: WebSocket, text: String) {
                    handleMessage(text)
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "WebSocket closing ($code): $reason")
                    try {
                        ws.close(code, reason)
                    } catch (ignored: Exception) {
                    }
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    Log.d(tag, "WebSocket closed ($code): $reason")
                    heartbeatJob?.cancel()
                    if (webSocket == ws) webSocket = null
                    if (code == 1008) {
                        _connectionState.value = ConnectionState.SERVER_ERROR
                        alertListener.onConnectionError("Server rejected credentials ($reason). Re-register phone.")
                    } else {
                        _connectionState.value = ConnectionState.OFFLINE
                    }
                }

                override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                    Log.w(tag, "WebSocket failure: ${t.message}")
                    heartbeatJob?.cancel()
                    if (webSocket == ws) webSocket = null
                    _connectionState.value = ConnectionState.SERVER_ERROR
                    alertListener.onConnectionError("Cannot reach server at $baseUrl (${t.localizedMessage ?: "Connection refused/timed out"})")
                }
            })
            true
        } catch (e: Exception) {
            Log.e(tag, "Failed to initiate WebSocket", e)
            webSocket = null
            _connectionState.value = ConnectionState.SERVER_ERROR
            alertListener.onConnectionError("WebSocket error: ${e.localizedMessage ?: "Unknown error"}")
            false
        }
    }

    private fun sendHello(ws: WebSocket) {
        try {
            val hello = JSONObject().apply {
                put("kind", "hello")
                put("clientId", preferences.clientId ?: "")
                put("token", preferences.clientToken ?: "")
                put("zone", preferences.hostelZone)
                put("lastSeq", preferences.cursor)
                if (!preferences.serverId.isNullOrEmpty()) {
                    put("serverId", preferences.serverId)
                }
            }
            ws.send(hello.toString())
        } catch (e: Exception) {
            Log.e(tag, "Failed to send hello", e)
        }
    }

    private fun startHeartbeats(ws: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && webSocket == ws) {
                delay(12000L)
                try {
                    ws.send(JSONObject().apply { put("kind", "ping") }.toString())
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    fun sendReceipt(alertId: String) {
        try {
            val msg = JSONObject().apply {
                put("kind", "receipt")
                put("alertId", alertId)
                put("via", "ws")
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.w(tag, "Failed to send receipt over WS: ${e.message}")
        }
    }

    fun sendSafeAck(alertId: String) {
        try {
            val msg = JSONObject().apply {
                put("kind", "ack")
                put("alertId", alertId)
                put("status", "safe")
            }
            webSocket?.send(msg.toString())
        } catch (e: Exception) {
            Log.w(tag, "Failed to send ack over WS: ${e.message}")
        }
    }

    private fun handleMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            when (json.optString("kind")) {
                "ready" -> {
                    _connectionState.value = ConnectionState.CONNECTED_WS
                    val serverId = json.optString("serverId")
                    val serverTime = json.optString("serverTime")
                    val cursor = json.optLong("cursor", 0L)
                    val reset = json.optBoolean("reset", false)

                    if (serverTime.isNotEmpty()) alertListener.onServerTimeUpdated(serverTime)

                    val markersObj = json.optJSONObject("clearMarkers")
                    if (markersObj != null) {
                        val keys = markersObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            k.toIntOrNull()?.let { zoneId ->
                                alertListener.onClearMarkerUpdated(zoneId, markersObj.optLong(k))
                            }
                        }
                    }

                    if (reset || (preferences.serverId != null && preferences.serverId != serverId)) {
                        alertListener.onServerReset(serverId, cursor)
                    } else if (serverId.isNotEmpty()) {
                        preferences.serverId = serverId
                    }
                }

                "alert" -> {
                    val alertObj = json.optJSONObject("alert")
                    if (alertObj != null) {
                        val isReplay = json.optBoolean("replay", false)
                        val alert = CampusAlert.fromJson(alertObj, "ws")
                        alertListener.onAlertReceived(alert, isReplay)
                        sendReceipt(alert.id)
                    }
                }

                "replay-complete" -> {
                    val cursor = json.optLong("cursor", preferences.cursor)
                    if (cursor > preferences.cursor) {
                        preferences.cursor = cursor
                    }
                    val markersObj = json.optJSONObject("clearMarkers")
                    if (markersObj != null) {
                        val keys = markersObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            k.toIntOrNull()?.let { zoneId ->
                                alertListener.onClearMarkerUpdated(zoneId, markersObj.optLong(k))
                            }
                        }
                    }
                }

                "heartbeat" -> {
                    val time = json.optString("serverTime")
                    if (time.isNotEmpty()) alertListener.onServerTimeUpdated(time)
                }

                "receipt-recorded" -> {
                    alertListener.onReceiptRecorded()
                }

                "ack-recorded" -> {
                    val alertId = json.optString("alertId")
                    if (alertId.isNotEmpty()) {
                        alertListener.onAckRecorded(alertId)
                    }
                }

                "error" -> {
                    val err = json.optString("error", "Unknown server error")
                    Log.w(tag, "Server error: $err")
                    alertListener.onConnectionError(err)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error parsing WS message", e)
        }
    }
}
