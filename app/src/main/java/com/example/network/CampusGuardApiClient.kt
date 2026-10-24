package com.example.network

import com.example.data.model.CampusAlert
import com.example.data.model.ClientRegistration
import com.example.data.model.ServerConfig
import com.example.data.model.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class CampusGuardApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun testConnection(serverUrl: String): Result<ServerConfig> = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.removeSuffix("/")}/api/config"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Server returned HTTP ${response.code}"))
                }
                val bodyStr = response.body?.string() ?: throw IOException("Empty response from server")
                val json = JSONObject(bodyStr)
                val campusObj = json.optJSONObject("campus")
                val config = ServerConfig(
                    serverId = json.optString("serverId"),
                    serverTime = json.optString("serverTime"),
                    medicalPhone = campusObj?.optString("medicalPhone"),
                    securityPhone = campusObj?.optString("securityPhone")
                )
                Result.success(config)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(
        serverUrl: String,
        name: String,
        zone: Int
    ): Result<ClientRegistration> = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.removeSuffix("/")}/api/clients"
            val bodyJson = JSONObject().apply {
                put("name", name)
                put("zone", zone)
            }
            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val err = parseError(bodyStr, "Registration failed (${response.code})")
                    return@withContext Result.failure(IOException(err))
                }
                val json = JSONObject(bodyStr)
                val reg = ClientRegistration(
                    id = json.getString("id"),
                    token = json.getString("token"),
                    zone = json.getInt("zone"),
                    name = json.optString("name", name),
                    createdAt = json.optString("createdAt")
                )
                Result.success(reg)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateZone(
        serverUrl: String,
        clientId: String,
        token: String,
        newZone: Int
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.removeSuffix("/")}/api/clients/me"
            val bodyJson = JSONObject().apply {
                put("zone", newZone)
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $clientId.$token")
                .patch(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val err = parseError(bodyStr, "Failed to update hostel (${response.code})")
                    return@withContext Result.failure(IOException(err))
                }
                val json = JSONObject(bodyStr)
                Result.success(json.optInt("zone", newZone))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sync(
        serverUrl: String,
        clientId: String,
        token: String,
        zone: Int,
        afterSeq: Long,
        serverId: String?
    ): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            val serverIdParam = if (!serverId.isNullOrEmpty()) "&serverId=${serverId}" else ""
            val url = "${serverUrl.removeSuffix("/")}/api/sync?zone=$zone&after=$afterSeq&transport=polling$serverIdParam"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $clientId.$token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val err = parseError(bodyStr, "Sync failed (${response.code})")
                    return@withContext Result.failure(IOException(err))
                }
                val json = JSONObject(bodyStr)
                val alertsList = mutableListOf<CampusAlert>()
                val alertsArr = json.optJSONArray("alerts") ?: JSONArray()
                for (i in 0 until alertsArr.length()) {
                    alertsList.add(CampusAlert.fromJson(alertsArr.getJSONObject(i), "polling"))
                }

                val markersMap = mutableMapOf<Int, Long>()
                val markersObj = json.optJSONObject("clearMarkers")
                if (markersObj != null) {
                    val keys = markersObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        markersMap[k.toInt()] = markersObj.getLong(k)
                    }
                }

                val responsesMap = mutableMapOf<String, String>()
                val responsesObj = json.optJSONObject("responses")
                if (responsesObj != null) {
                    val keys = responsesObj.keys()
                    while (keys.hasNext()) {
                        val alertId = keys.next()
                        val item = responsesObj.optJSONObject(alertId)
                        val status = item?.optString("status") ?: "safe"
                        responsesMap[alertId] = status
                    }
                }

                val result = SyncResult(
                    serverId = json.getString("serverId"),
                    serverTime = json.getString("serverTime"),
                    cursor = json.getLong("cursor"),
                    alerts = alertsList,
                    clearMarkers = markersMap,
                    reset = json.optBoolean("reset", false),
                    zone = json.optInt("zone", zone),
                    responses = responsesMap
                )
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendReceipt(
        serverUrl: String,
        clientId: String,
        token: String,
        alertId: String,
        via: String = "app"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.removeSuffix("/")}/api/receipts"
            val bodyJson = JSONObject().apply {
                put("alertId", alertId)
                put("via", via)
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $clientId.$token")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val err = parseError(bodyStr, "Receipt failed (${response.code})")
                    return@withContext Result.failure(IOException(err))
                }
                val json = JSONObject(bodyStr)
                Result.success(json.optBoolean("recorded", true))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendResponse(
        serverUrl: String,
        clientId: String,
        token: String,
        alertId: String,
        status: String, // "safe" or "needs-help"
        message: String? = null,
        via: String = "app"
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.removeSuffix("/")}/api/responses"
            val bodyJson = JSONObject().apply {
                put("alertId", alertId)
                put("status", status)
                if (!message.isNullOrBlank()) {
                    put("message", message.trim())
                }
                put("via", via)
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $clientId.$token")
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    val err = parseError(bodyStr, "Response failed (${response.code})")
                    return@withContext Result.failure(IOException(err))
                }
                val json = JSONObject(bodyStr)
                Result.success(json.optBoolean("recorded", true))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchMyResponses(
        serverUrl: String,
        clientId: String,
        token: String
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val url = "${serverUrl.removeSuffix("/")}/api/responses/me"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $clientId.$token")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("Failed to fetch responses (${response.code})"))
                }
                val json = JSONObject(bodyStr)
                val responsesObj = json.optJSONObject("responses") ?: JSONObject()
                val map = mutableMapOf<String, String>()
                val keys = responsesObj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val item = responsesObj.getJSONObject(id)
                    map[id] = item.optString("status", "safe")
                }
                Result.success(map)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseError(body: String, default: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("error", default)
        } catch (e: Exception) {
            default
        }
    }
}
