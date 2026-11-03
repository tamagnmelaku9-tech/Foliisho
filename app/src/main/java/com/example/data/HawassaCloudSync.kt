package com.example.data

import com.example.model.FuelStatus
import com.example.model.VehicleCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real-Time Cloud Synchronization Engine for Hawassa Transit Driver App
 * Connects directly to Firebase Realtime Database and Vercel Web Admin (https://foliisho.vercel.app/)
 * Features live SSE WebSocket-like streaming with automated 4-second fallback polling for resilient mobile connectivity.
 */
class HawassaCloudSync(
    private val repository: QueueRepository,
    private val scope: CoroutineScope,
    private val configuredUrl: String = com.example.BuildConfig.HAWASSA_API_BASE_URL
) {

    private val _isCloudConnected = MutableStateFlow(false)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    private val _connectionStatusText = MutableStateFlow("Connecting to Live Cloud...")
    val connectionStatusText: StateFlow<String> = _connectionStatusText.asStateFlow()

    private var streamJob: Job? = null
    private var pollJob: Job? = null

    // Cloud Database URLs (Primary: Firebase RTDB, Fallbacks: Configured URL & Vercel deployment)
    private val primaryCloudDbUrl: String = resolvePrimaryUrl()

    private fun resolvePrimaryUrl(): String {
        val clean = configuredUrl.trim().trimEnd('/')
        return when {
            clean.contains("firebaseio.com") || clean.contains("firebasedatabase.app") -> {
                if (clean.endsWith(".json")) clean else "$clean/stations.json"
            }
            clean.startsWith("http") -> clean
            else -> "https://hawassa-transit-default-rtdb.firebaseio.com/stations.json"
        }
    }

    fun startRealtimeSync() {
        stopSync()

        // 1. Start continuous live Server-Sent Events (SSE) streaming listener
        streamJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (repository.isOnline.value) {
                    runLiveStreamListener()
                }
                delay(5000) // Reconnect delay if stream disconnects
            }
        }

        // 2. Continuous 2-second polling loop reading the exact shared state
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                if (repository.isOnline.value) {
                    syncStationsSnapshot()
                }
                delay(2000) // Poll every 2 seconds for immediate synchronization
            }
        }
    }

    fun stopSync() {
        streamJob?.cancel()
        streamJob = null
        pollJob?.cancel()
        pollJob = null
    }

    /**
     * Direct live SSE streaming listener (WebSocket-like zero-latency push)
     */
    private suspend fun runLiveStreamListener() {
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            var reader: BufferedReader? = null
            try {
                val streamTarget = when {
                    primaryCloudDbUrl.endsWith(".json") -> primaryCloudDbUrl
                    primaryCloudDbUrl.endsWith("/api") -> "$primaryCloudDbUrl/events"
                    primaryCloudDbUrl.contains("/api/") -> primaryCloudDbUrl.substringBeforeLast("/api/") + "/api/events"
                    else -> "${primaryCloudDbUrl.trimEnd('/')}/api/events"
                }
                val url = URL(streamTarget)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Accept", "text/event-stream")
                    setRequestProperty("Cache-Control", "no-cache")
                    connectTimeout = 8000
                    readTimeout = 0 // Infinite read timeout for continuous event stream
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    withContext(Dispatchers.Main) {
                        _isCloudConnected.value = true
                        _connectionStatusText.value = "Live Cloud Stream: Connected"
                    }

                    reader = BufferedReader(InputStreamReader(conn.inputStream))
                    var line: String? = null
                    while (isActive && reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: break
                        if (currentLine.startsWith("data: ")) {
                            val dataStr = currentLine.removePrefix("data: ").trim()
                            if (dataStr.isNotEmpty() && dataStr != "null") {
                                handleStreamData(dataStr)
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _isCloudConnected.value = false
                        _connectionStatusText.value = "Cloud Stream: Polling Mode"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isCloudConnected.value = false
                    _connectionStatusText.value = "Cloud Stream: Reconnecting..."
                }
            } finally {
                try { reader?.close() } catch (e: Exception) {}
                try { conn?.disconnect() } catch (e: Exception) {}
            }
        }
    }

    /**
     * Parse stream data payload from Firebase RTDB or SSE server
     */
    private suspend fun handleStreamData(dataStr: String) {
        withContext(Dispatchers.Main) {
            try {
                if (dataStr.startsWith("{")) {
                    val json = JSONObject(dataStr)
                    // 1. Firebase RTDB stream format: {"path":"/...", "data":...}
                    if (json.has("path") && json.has("data")) {
                        val path = json.optString("path", "")
                        val dataObj = json.opt("data")
                        handleFirebasePathUpdate(path, dataObj)
                        return@withContext
                    }

                    // 2. Custom SSE event format: {"type":"QUEUE_ADVANCED", ...}
                    if (json.has("type")) {
                        handleCustomEventType(json)
                        return@withContext
                    }

                    // 3. Full station dump object
                    parseAndApplyStationsObject(json)
                } else if (dataStr.startsWith("[")) {
                    val arr = JSONArray(dataStr)
                    parseAndApplyStationsArray(arr)
                }
            } catch (e: Exception) {
                // Ignore transient parse error
            }
        }
    }

    private fun handleFirebasePathUpdate(path: String, data: Any?) {
        val cleanPath = path.trimStart('/')
        val parts = cleanPath.split('/')
        if (parts.isEmpty() || parts[0].isEmpty()) {
            if (data is JSONObject) {
                parseAndApplyStationsObject(data)
            }
            return
        }

        val stationId = parts[0]
        if (parts.size == 1 && data is JSONObject) {
            parseSingleStationObject(stationId, data)
        } else if (parts.size >= 2) {
            val field = parts[1]
            when (field) {
                "activePermittedCategory" -> {
                    val catStr = data?.toString() ?: ""
                    val cat = runCatching { VehicleCategory.valueOf(catStr) }.getOrNull()
                    if (cat != null) {
                        repository.setAdminActiveCategory(stationId, cat)
                    }
                }
                "lanes" -> {
                    if (parts.size >= 3) {
                        val catStr = parts[2]
                        val cat = runCatching { VehicleCategory.valueOf(catStr) }.getOrNull()
                        if (cat != null && data is JSONObject) {
                            val serving = data.optInt("currentlyServing", -1)
                            val assigned = data.optInt("lastAssigned", -1)
                            if (serving >= 0 || assigned >= 0) {
                                repository.syncStationStateFromCloud(
                                    stationId = stationId,
                                    permittedCategory = null,
                                    benzeneStatus = null,
                                    dieselStatus = null,
                                    laneServingMap = if (serving >= 0) mapOf(cat to serving) else emptyMap(),
                                    laneAssignedMap = if (assigned >= 0) mapOf(cat to assigned) else emptyMap()
                                )
                            }
                        }
                    }
                }
                "benzeneStatus" -> {
                    val st = runCatching { FuelStatus.valueOf(data?.toString() ?: "") }.getOrNull()
                    if (st != null) repository.updateFuelStock(stationId, true, st)
                }
                "dieselStatus" -> {
                    val st = runCatching { FuelStatus.valueOf(data?.toString() ?: "") }.getOrNull()
                    if (st != null) repository.updateFuelStock(stationId, false, st)
                }
            }
        }
    }

    private fun handleCustomEventType(json: JSONObject) {
        val type = json.optString("type", "")
        val stationId = json.optString("stationId", "")
        when (type) {
            "PERMITTED_CATEGORY_CHANGED" -> {
                val catStr = json.optString("activePermittedCategory", "")
                val cat = runCatching { VehicleCategory.valueOf(catStr) }.getOrNull()
                if (cat != null && stationId.isNotEmpty()) {
                    repository.setAdminActiveCategory(stationId, cat)
                }
            }
            "QUEUE_ADVANCED" -> {
                val catStr = json.optString("category", "")
                val serving = json.optInt("currentlyServing", 0)
                val cat = runCatching { VehicleCategory.valueOf(catStr) }.getOrNull()
                if (cat != null && stationId.isNotEmpty() && serving > 0) {
                    repository.syncStationStateFromCloud(
                        stationId = stationId,
                        permittedCategory = null,
                        benzeneStatus = null,
                        dieselStatus = null,
                        laneServingMap = mapOf(cat to serving),
                        laneAssignedMap = emptyMap()
                    )
                }
            }
            "FUEL_STATUS_UPDATED" -> {
                val isBenzene = json.optBoolean("isBenzene", true)
                val statusStr = json.optString("status", "")
                val status = runCatching { FuelStatus.valueOf(statusStr) }.getOrNull()
                if (status != null && stationId.isNotEmpty()) {
                    repository.updateFuelStock(stationId, isBenzene, status)
                }
            }
        }
    }

    /**
     * Periodic fallback HTTP poll to keep stations fresh
     */
    private suspend fun syncStationsSnapshot() {
        withContext(Dispatchers.IO) {
            val pollTarget = when {
                primaryCloudDbUrl.endsWith(".json") -> primaryCloudDbUrl
                primaryCloudDbUrl.endsWith("/api") -> "$primaryCloudDbUrl/stations"
                primaryCloudDbUrl.contains("/api/") -> primaryCloudDbUrl.substringBeforeLast("/api/") + "/api/stations"
                else -> "${primaryCloudDbUrl.trimEnd('/')}/api/stations"
            }
            val targets = listOf(
                pollTarget,
                "https://foliisho.vercel.app/api/stations",
                "https://ais-dev-44vroeovmuty6c3srnm5ne-215424788643.europe-west2.run.app/api/stations",
                "http://10.0.2.2:3000/api/stations",
                "http://localhost:3000/api/stations"
            ).distinct()

            for (target in targets) {
                var conn: HttpURLConnection? = null
                var reader: BufferedReader? = null
                try {
                    val url = URL(target)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 3500
                        readTimeout = 3500
                        setRequestProperty("Accept", "application/json")
                    }

                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        reader = BufferedReader(InputStreamReader(conn.inputStream))
                        val response = reader.readText().trim()
                        withContext(Dispatchers.Main) {
                            if (response.startsWith("{")) {
                                val root = JSONObject(response)
                                if (root.has("data") && root.opt("data") is JSONArray) {
                                    parseAndApplyStationsArray(root.getJSONArray("data"))
                                } else {
                                    parseAndApplyStationsObject(root)
                                }
                            } else if (response.startsWith("[")) {
                                parseAndApplyStationsArray(JSONArray(response))
                            }
                            _isCloudConnected.value = true
                        }
                        break // Succeeded, exit targets loop
                    }
                } catch (e: Exception) {
                    // Try next target
                } finally {
                    try { reader?.close() } catch (e: Exception) {}
                    try { conn?.disconnect() } catch (e: Exception) {}
                }
            }
        }
    }

    private fun parseAndApplyStationsObject(obj: JSONObject) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val stationId = keys.next()
            val stationObj = obj.optJSONObject(stationId)
            if (stationObj != null) {
                parseSingleStationObject(stationId, stationObj)
            }
        }
    }

    private fun parseAndApplyStationsArray(dataArray: JSONArray) {
        for (i in 0 until dataArray.length()) {
            val stationObj = dataArray.optJSONObject(i) ?: continue
            val stationId = stationObj.optString("id", "")
            if (stationId.isNotEmpty()) {
                parseSingleStationObject(stationId, stationObj)
            }
        }
    }

    private fun parseSingleStationObject(stationId: String, stationObj: JSONObject) {
        val permittedCatStr = stationObj.optString("activePermittedCategory", "")
        val permittedCat = runCatching { VehicleCategory.valueOf(permittedCatStr) }.getOrNull()

        val benzeneStatusStr = stationObj.optString("benzeneStatus", "")
        val benzeneStatus = runCatching { FuelStatus.valueOf(benzeneStatusStr) }.getOrNull()

        val dieselStatusStr = stationObj.optString("dieselStatus", "")
        val dieselStatus = runCatching { FuelStatus.valueOf(dieselStatusStr) }.getOrNull()

        val lanesObj = stationObj.optJSONObject("lanes")
        val servingMap = mutableMapOf<VehicleCategory, Int>()
        val assignedMap = mutableMapOf<VehicleCategory, Int>()

        if (lanesObj != null) {
            for (cat in VehicleCategory.entries) {
                if (cat == VehicleCategory.ALL) continue
                val laneData = lanesObj.optJSONObject(cat.name)
                if (laneData != null) {
                    servingMap[cat] = laneData.optInt("currentlyServing", 0)
                    assignedMap[cat] = laneData.optInt("lastAssigned", 0)
                }
            }
        }

        repository.syncStationStateFromCloud(
            stationId = stationId,
            permittedCategory = permittedCat,
            benzeneStatus = benzeneStatus,
            dieselStatus = dieselStatus,
            laneServingMap = servingMap,
            laneAssignedMap = assignedMap
        )
    }
}
