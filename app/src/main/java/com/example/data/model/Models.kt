package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Hostel definition matching src/zones.js from CampusGuard backend:
 * 0: ALL, 1: Visvesvaraya, 2: Abdul Kalam, 3: Ramanujan, 4: Kalpana Chawla
 */
data class Hostel(
    val id: Int,
    val name: String,
    val shortName: String
) {
    companion object {
        val ALL_HOSTELS = listOf(
            Hostel(1, "Visvesvaraya", "Visvesvaraya"),
            Hostel(2, "Abdul Kalam", "Abdul Kalam"),
            Hostel(3, "Ramanujan", "Ramanujan"),
            Hostel(4, "Kalpana Chawla", "Kalpana Chawla")
        )

        fun find(id: Int): Hostel? = ALL_HOSTELS.find { it.id == id }

        fun getDisplayName(id: Int): String = when (id) {
            0 -> "ALL (Campus-Wide)"
            1 -> "Visvesvaraya"
            2 -> "Abdul Kalam"
            3 -> "Ramanujan"
            4 -> "Kalpana Chawla"
            else -> "Hostel $id"
        }
    }
}

/**
 * Immutable CampusGuard alert matching the backend signed envelope.
 */
data class CampusAlert(
    val v: Int = 1,
    val id: String, // e.g. "7ac912ef:42"
    val serverId: String,
    val seq: Long,
    val timestamp: String,
    val ttl: Int, // seconds (1..3600)
    val type: String, // fire, earthquake, medical, security, drill, all-clear
    val severity: Int, // 1 advisory, 2 urgent, 3 critical
    val zone: Int? = null,
    val zones: List<Int> = emptyList(),
    val assemblyPoint: String? = null, // football, cricket
    val message: String,
    val signature: String? = null,
    val receivedVia: String? = null,
    val receivedAtMillis: Long = System.currentTimeMillis()
) {
    val effectiveZones: List<Int>
        get() = if (zones.isNotEmpty()) zones else listOfNotNull(zone)

    fun isApplicable(studentZone: Int): Boolean {
        val targetList = effectiveZones
        return targetList.contains(0) || targetList.contains(studentZone)
    }

    val expiresAtMillis: Long
        get() {
            val parsed = parseIsoTimestamp(timestamp)
            return if (parsed > 0) parsed + (ttl * 1000L) else System.currentTimeMillis() + (ttl * 1000L)
        }

    fun isExpired(serverTimeOffsetMillis: Long = 0L): Boolean {
        if (type == "all-clear") return false
        val nowOnServer = System.currentTimeMillis() + serverTimeOffsetMillis
        return nowOnServer >= expiresAtMillis
    }

    fun isCleared(clearMarkers: Map<Int, Long>, studentZone: Int): Boolean {
        if (type == "all-clear") return false
        val markerAll = clearMarkers[0] ?: 0L
        val markerZone = clearMarkers[studentZone] ?: 0L
        val maxClear = maxOf(markerAll, markerZone)
        return seq <= maxClear
    }

    val zonesLabel: String
        get() = effectiveZones.joinToString(", ") { Hostel.getDisplayName(it) }

    val assemblyPointDisplayName: String
        get() = when (assemblyPoint?.lowercase()) {
            "cricket" -> "Cricket ground"
            "football" -> "Football ground"
            else -> "Football ground (Default)"
        }

    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("v", v)
        json.put("id", id)
        json.put("serverId", serverId)
        json.put("seq", seq)
        json.put("timestamp", timestamp)
        json.put("ttl", ttl)
        json.put("type", type)
        json.put("severity", severity)
        if (zone != null) json.put("zone", zone) else json.put("zone", JSONObject.NULL)
        val zonesArr = JSONArray()
        effectiveZones.forEach { zonesArr.put(it) }
        json.put("zones", zonesArr)
        if (assemblyPoint != null) json.put("assemblyPoint", assemblyPoint)
        json.put("message", message)
        if (signature != null) json.put("signature", signature)
        if (receivedVia != null) json.put("receivedVia", receivedVia)
        json.put("receivedAtMillis", receivedAtMillis)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject, fallbackVia: String? = null): CampusAlert {
            val zonesList = mutableListOf<Int>()
            if (json.has("zones")) {
                val arr = json.optJSONArray("zones")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        zonesList.add(arr.optInt(i))
                    }
                }
            }

            val zoneVal = if (json.has("zone") && !json.isNull("zone")) json.optInt("zone") else null

            return CampusAlert(
                v = json.optInt("v", 1),
                id = json.optString("id"),
                serverId = json.optString("serverId"),
                seq = json.optLong("seq"),
                timestamp = json.optString("timestamp"),
                ttl = json.optInt("ttl", 300),
                type = json.optString("type", "drill"),
                severity = json.optInt("severity", 1),
                zone = zoneVal,
                zones = zonesList,
                assemblyPoint = if (json.has("assemblyPoint") && !json.isNull("assemblyPoint")) json.optString("assemblyPoint") else null,
                message = json.optString("message"),
                signature = json.optString("signature").takeIf { it.isNotEmpty() },
                receivedVia = json.optString("receivedVia").ifEmpty { fallbackVia },
                receivedAtMillis = json.optLong("receivedAtMillis", System.currentTimeMillis())
            )
        }

        fun parseIsoTimestamp(iso: String?): Long {
            if (iso.isNullOrBlank()) return 0L
            return try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                format.timeZone = TimeZone.getTimeZone("UTC")
                format.parse(iso)?.time ?: 0L
            } catch (e: Exception) {
                try {
                    val formatNoMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    formatNoMillis.timeZone = TimeZone.getTimeZone("UTC")
                    formatNoMillis.parse(iso)?.time ?: 0L
                } catch (e2: Exception) {
                    0L
                }
            }
        }
    }
}

data class ClientRegistration(
    val id: String,
    val token: String,
    val zone: Int,
    val name: String,
    val createdAt: String
)

data class ServerConfig(
    val serverId: String,
    val serverTime: String,
    val medicalPhone: String?,
    val securityPhone: String?
)

data class SyncResult(
    val serverId: String,
    val serverTime: String,
    val cursor: Long,
    val alerts: List<CampusAlert>,
    val clearMarkers: Map<Int, Long>,
    val reset: Boolean,
    val zone: Int,
    val responses: Map<String, String> // alertId -> status ("safe" / "needs-help")
)

enum class ConnectionState {
    OFFLINE,
    CONNECTING,
    CONNECTED_WS,
    CONNECTED_POLLING,
    SERVER_ERROR
}
