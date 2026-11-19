package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.CongestionLevel
import com.example.model.FuelStatus
import com.example.model.QueueTicket
import com.example.model.StationInfo
import com.example.model.StationLaneState
import com.example.model.TicketStatus
import com.example.model.VehicleCategory
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestoreQueueSync(
    private val context: Context,
    private val repository: QueueRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "FirestoreQueueSync"
        private const val PREFS_NAME = "hawassa_firestore_prefs"
        private const val KEY_ACTIVE_TICKET_ID = "hawassa_active_ticket_id"
        private val STATION_ORDER = listOf(
            "benzene_madya",
            "total_menahariya",
            "noc_piassa",
            "ola_datto",
            "tabor_oil"
        )
    }

    private val db: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id).ifBlank { "(default)" }
        if (dbId == "(default)") {
            FirebaseFirestore.getInstance()
        } else {
            FirebaseFirestore.getInstance(dbId)
        }
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _isCloudConnected = MutableStateFlow(false)
    val isCloudConnected: StateFlow<Boolean> = _isCloudConnected.asStateFlow()

    private val _connectionStatusText = MutableStateFlow("Connecting to Cloud Firestore (foliisho)...")
    val connectionStatusText: StateFlow<String> = _connectionStatusText.asStateFlow()

    private var stationsListener: ListenerRegistration? = null
    private var ticketListener: ListenerRegistration? = null
    private var lastBroadcastSeenMillis: Long = System.currentTimeMillis()
    private var onBroadcastAlertCallback: ((stationId: String, category: String) -> Unit)? = null

    fun startRealtimeSync(
        onRemoteBroadcastAlert: ((stationId: String, category: String) -> Unit)? = null
    ) {
        onBroadcastAlertCallback = onRemoteBroadcastAlert
        stopSync()

        // 1. Ensure initial stations exist in Firestore if collection is missing any
        scope.launch(Dispatchers.IO) {
            seedMissingStationsIfNeeded()
        }

        // 2. Real-time snapshot listener on /stations
        stationsListener = db.collection("stations")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Stations snapshot listener error", error)
                    _isCloudConnected.value = false
                    _connectionStatusText.value = "Firestore Error: ${error.localizedMessage ?: "Disconnected"}"
                    repository.setOnlineState(false)
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val parsedStations = mutableListOf<StationInfo>()
                for (doc in snapshot.documents) {
                    parseStationDoc(doc)?.let { parsedStations.add(it) }
                    checkRemoteBroadcastAlert(doc)
                }

                if (parsedStations.isNotEmpty()) {
                    parsedStations.sortBy { st ->
                        val idx = STATION_ORDER.indexOf(st.id)
                        if (idx == -1) 99 else idx
                    }
                    repository.updateStationsFromCloud(parsedStations)
                }

                _isCloudConnected.value = true
                _connectionStatusText.value = "Firestore Live: foliisho"
            }

        // 3. Restore and track any previously active ticket saved on device
        val savedTicketId = prefs.getString(KEY_ACTIVE_TICKET_ID, null)
        if (!savedTicketId.isNullOrBlank()) {
            subscribeToTicket(savedTicketId)
        }
    }

    fun stopSync() {
        stationsListener?.remove()
        stationsListener = null
        ticketListener?.remove()
        ticketListener = null
    }

    suspend fun joinQueue(
        stationId: String,
        driverName: String,
        driverPhone: String,
        licensePlate: String,
        explicitCategory: VehicleCategory? = null
    ): Result<QueueTicket> {
        val sanitizedPlate = licensePlate.trim().uppercase()
        val sanitizedPhone = driverPhone.trim()
        val sanitizedName = driverName.trim().ifBlank { "Driver" }

        if (sanitizedPlate.length < 4) {
            return Result.failure(IllegalArgumentException("Please enter a valid Ethiopian license plate (e.g. AA-2-84920)"))
        }
        if (sanitizedPhone.length < 9) {
            return Result.failure(IllegalArgumentException("Please enter a valid Ethiopian phone number (e.g. 0916123456)"))
        }

        return try {
            // 1. Check duplicate active plate in Firestore (single-field query + in-memory filter avoids composite index)
            val existingSnap = db.collection("tickets")
                .whereEqualTo("licensePlate", sanitizedPlate)
                .get()
                .awaitResult()

            for (doc in existingSnap.documents) {
                val statusStr = doc.getString("status") ?: ""
                if (statusStr == "WAITING" || statusStr == "CALLED" || statusStr == "SERVING") {
                    val existingNumber = (doc.getLong("ticketNumber") ?: 0L).toInt()
                    val existingStation = doc.getString("stationNameEn") ?: doc.getString("stationId") ?: ""
                    return Result.failure(
                        IllegalStateException("Vehicle $sanitizedPlate already has active ticket #$existingNumber at $existingStation.")
                    )
                }
            }

            // 2. Atomic Firestore transaction to increment station lane counter & create ticket document
            val stationRef = db.collection("stations").document(stationId)
            val ticketId = "TKT-${System.currentTimeMillis()}-${(100..999).random()}"
            val ticketRef = db.collection("tickets").document(ticketId)

            val createdTicket = db.runTransaction { transaction ->
                val stDoc = transaction.get(stationRef)
                val fallbackStation = QueueRepository.initialHawassaStations().find { it.id == stationId }
                    ?: QueueRepository.initialHawassaStations().first()

                val baseStation = if (stDoc.exists()) {
                    parseStationDoc(stDoc) ?: fallbackStation
                } else {
                    fallbackStation
                }

                val targetCategory = explicitCategory ?: baseStation.activeCalledCategory
                val lane = baseStation.getLane(targetCategory)
                if (!lane.isActive) {
                    throw IllegalStateException("The ${targetCategory.nameEn} lane at ${baseStation.nameEn} is currently paused by the coordinator.")
                }

                val newNumber = lane.lastAssignedNumber + 1
                val updatedLanesMap = toFirestoreLanesMap(
                    baseStation.lanes.toMutableMap().apply {
                        put(targetCategory, lane.copy(lastAssignedNumber = newNumber))
                    }
                )

                val stationPayload = hashMapOf<String, Any>(
                    "id" to baseStation.id,
                    "nameEn" to baseStation.nameEn,
                    "nameAm" to baseStation.nameAm,
                    "neighborhoodEn" to baseStation.neighborhoodEn,
                    "neighborhoodAm" to baseStation.neighborhoodAm,
                    "distanceKm" to baseStation.distanceKm,
                    "activePermittedCategory" to baseStation.activeCalledCategory.name,
                    "benzeneStatus" to baseStation.benzeneStatus.name,
                    "dieselStatus" to baseStation.dieselStatus.name,
                    "congestionLevel" to baseStation.congestionLevel.name,
                    "isOpen" to baseStation.isOpen,
                    "lanes" to updatedLanesMap,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(stationRef, stationPayload, SetOptions.merge())

                val ticketPayload = hashMapOf<String, Any?>(
                    "ticketId" to ticketId,
                    "stationId" to baseStation.id,
                    "stationNameEn" to baseStation.nameEn,
                    "stationNameAm" to baseStation.nameAm,
                    "vehicleCategory" to targetCategory.name,
                    "driverName" to sanitizedName,
                    "phoneNumber" to sanitizedPhone,
                    "licensePlate" to sanitizedPlate,
                    "ticketNumber" to newNumber,
                    "status" to TicketStatus.WAITING.name,
                    "calledPumpNumber" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(ticketRef, ticketPayload)

                QueueTicket(
                    id = ticketId,
                    stationId = baseStation.id,
                    stationNameEn = baseStation.nameEn,
                    stationNameAm = baseStation.nameAm,
                    vehicleCategory = targetCategory,
                    licensePlate = sanitizedPlate,
                    driverPhone = sanitizedPhone,
                    driverName = sanitizedName,
                    ticketNumber = newNumber,
                    status = TicketStatus.WAITING,
                    registeredAtMillis = System.currentTimeMillis(),
                    calledPumpNumber = null
                )
            }.awaitResult()

            prefs.edit().putString(KEY_ACTIVE_TICKET_ID, createdTicket.id).apply()
            repository.setActiveTicketFromCloud(createdTicket)
            subscribeToTicket(createdTicket.id)
            Result.success(createdTicket)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "joinQueue Firestore transaction failed", e)
            Result.failure(e)
        }
    }

    suspend fun leaveQueue(ticketId: String?) {
        prefs.edit().remove(KEY_ACTIVE_TICKET_ID).apply()
        ticketListener?.remove()
        ticketListener = null
        repository.setActiveTicketFromCloud(null)

        if (ticketId.isNullOrBlank()) return
        try {
            db.collection("tickets").document(ticketId)
                .update(
                    mapOf(
                        "status" to TicketStatus.CANCELLED.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .awaitResult()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "leaveQueue Firestore update warning", e)
        }
    }

    fun subscribeToTicket(ticketId: String) {
        ticketListener?.remove()
        ticketListener = db.collection("tickets").document(ticketId)
            .addSnapshotListener { doc, error ->
                if (error != null) {
                    Log.e(TAG, "Ticket listener error", error)
                    return@addSnapshotListener
                }
                if (doc == null || !doc.exists()) {
                    prefs.edit().remove(KEY_ACTIVE_TICKET_ID).apply()
                    repository.setActiveTicketFromCloud(null)
                    return@addSnapshotListener
                }

                val ticket = parseTicketDoc(doc)
                if (ticket != null) {
                    repository.setActiveTicketFromCloud(ticket)
                    if (ticket.status == TicketStatus.CANCELLED || ticket.status == TicketStatus.COMPLETED) {
                        prefs.edit().remove(KEY_ACTIVE_TICKET_ID).apply()
                    }
                }
            }
    }

    private suspend fun seedMissingStationsIfNeeded() {
        try {
            val snap = db.collection("stations").get().awaitResult()
            val existingIds = snap.documents.map { it.id }.toSet()
            val missingStations = QueueRepository.initialHawassaStations().filter { !existingIds.contains(it.id) }
            if (missingStations.isEmpty()) return

            val batch = db.batch()
            for (st in missingStations) {
                val ref = db.collection("stations").document(st.id)
                val payload = hashMapOf<String, Any>(
                    "id" to st.id,
                    "nameEn" to st.nameEn,
                    "nameAm" to st.nameAm,
                    "neighborhoodEn" to st.neighborhoodEn,
                    "neighborhoodAm" to st.neighborhoodAm,
                    "distanceKm" to st.distanceKm,
                    "activePermittedCategory" to st.activeCalledCategory.name,
                    "benzeneStatus" to st.benzeneStatus.name,
                    "dieselStatus" to st.dieselStatus.name,
                    "congestionLevel" to st.congestionLevel.name,
                    "isOpen" to st.isOpen,
                    "lanes" to toFirestoreLanesMap(st.lanes),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(ref, payload, SetOptions.merge())
            }
            batch.commit().awaitResult()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Initial station seed check warning: ${e.message}")
        }
    }

    private fun checkRemoteBroadcastAlert(doc: DocumentSnapshot) {
        val alertObj = doc.get("lastBroadcastAlert") as? Map<*, *> ?: return
        val ts = (alertObj["timestamp"] as? Number)?.toLong() ?: return
        val category = (alertObj["category"] as? String) ?: "ALL"
        if (ts > lastBroadcastSeenMillis) {
            lastBroadcastSeenMillis = ts
            onBroadcastAlertCallback?.invoke(doc.id, category)
        }
    }

    private fun parseStationDoc(doc: DocumentSnapshot): StationInfo? {
        val id = doc.getString("id") ?: doc.id
        val fallback = QueueRepository.initialHawassaStations().find { it.id == id }
        val nameEn = doc.getString("nameEn") ?: fallback?.nameEn ?: return null
        val nameAm = doc.getString("nameAm") ?: fallback?.nameAm ?: nameEn
        val neighborhoodEn = doc.getString("neighborhoodEn") ?: fallback?.neighborhoodEn ?: ""
        val neighborhoodAm = doc.getString("neighborhoodAm") ?: fallback?.neighborhoodAm ?: ""
        val distanceKm = doc.getDouble("distanceKm") ?: fallback?.distanceKm ?: 1.0

        val permittedStr = doc.getString("activePermittedCategory") ?: ""
        val permittedCat = runCatching { VehicleCategory.valueOf(permittedStr) }.getOrNull()
            ?: fallback?.activeCalledCategory
            ?: VehicleCategory.BAJAJ

        val benzeneStatus = runCatching {
            FuelStatus.valueOf(doc.getString("benzeneStatus") ?: "")
        }.getOrNull() ?: fallback?.benzeneStatus ?: FuelStatus.AVAILABLE

        val dieselStatus = runCatching {
            FuelStatus.valueOf(doc.getString("dieselStatus") ?: "")
        }.getOrNull() ?: fallback?.dieselStatus ?: FuelStatus.AVAILABLE

        val congestionLevel = runCatching {
            CongestionLevel.valueOf(doc.getString("congestionLevel") ?: "")
        }.getOrNull() ?: fallback?.congestionLevel ?: CongestionLevel.MODERATE

        val isOpen = doc.getBoolean("isOpen") ?: true

        val parsedLanes = mutableMapOf<VehicleCategory, StationLaneState>()
        val rawLanes = doc.get("lanes") as? Map<*, *>
        for (cat in VehicleCategory.entries) {
            if (cat == VehicleCategory.ALL) continue
            val defaultLane = fallback?.lanes?.get(cat) ?: StationLaneState(
                vehicleCategory = cat,
                avgDurationSeconds = cat.defaultDispenseSeconds
            )
            val laneMap = rawLanes?.get(cat.name) as? Map<*, *>
            if (laneMap != null) {
                val serving = (laneMap["currentlyServing"] as? Number)?.toInt() ?: defaultLane.currentlyServingNumber
                val assigned = (laneMap["lastAssigned"] as? Number)?.toInt() ?: defaultLane.lastAssignedNumber
                val avgSec = (laneMap["avgDurationSeconds"] as? Number)?.toInt() ?: defaultLane.avgDurationSeconds
                val isActive = (laneMap["isActive"] as? Boolean) ?: true
                parsedLanes[cat] = StationLaneState(
                    vehicleCategory = cat,
                    isActive = isActive,
                    isPrioritized = (cat == permittedCat),
                    lastAssignedNumber = maxOf(assigned, serving),
                    currentlyServingNumber = serving,
                    avgDurationSeconds = avgSec
                )
            } else {
                parsedLanes[cat] = defaultLane.copy(isPrioritized = (cat == permittedCat))
            }
        }

        return StationInfo(
            id = id,
            nameEn = nameEn,
            nameAm = nameAm,
            neighborhoodEn = neighborhoodEn,
            neighborhoodAm = neighborhoodAm,
            distanceKm = distanceKm,
            benzeneStatus = benzeneStatus,
            dieselStatus = dieselStatus,
            congestionLevel = congestionLevel,
            isOpen = isOpen,
            lanes = parsedLanes,
            prioritizedCategory = permittedCat,
            activeCalledCategory = permittedCat,
            adminAnnouncementAm = "አሁን የተፈቀደው ተሽከርካሪ: ${permittedCat.nameAm}",
            adminAnnouncementEn = "Admin Called Category: ${permittedCat.nameEn}"
        )
    }

    private fun parseTicketDoc(doc: DocumentSnapshot): QueueTicket? {
        val ticketId = doc.getString("ticketId") ?: doc.id
        val stationId = doc.getString("stationId") ?: return null
        val stationNameEn = doc.getString("stationNameEn") ?: stationId
        val stationNameAm = doc.getString("stationNameAm") ?: stationNameEn
        val cat = runCatching {
            VehicleCategory.valueOf(doc.getString("vehicleCategory") ?: "BAJAJ")
        }.getOrDefault(VehicleCategory.BAJAJ)
        val licensePlate = doc.getString("licensePlate") ?: ""
        val driverPhone = doc.getString("phoneNumber") ?: ""
        val driverName = doc.getString("driverName") ?: "Driver"
        val ticketNumber = (doc.getLong("ticketNumber") ?: doc.getDouble("ticketNumber")?.toLong() ?: 0L).toInt()
        val status = runCatching {
            TicketStatus.valueOf(doc.getString("status") ?: "WAITING")
        }.getOrDefault(TicketStatus.WAITING)
        val calledPump = (doc.getLong("calledPumpNumber") ?: doc.getDouble("calledPumpNumber")?.toLong())?.toInt()
        val createdMillis = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()

        return QueueTicket(
            id = ticketId,
            stationId = stationId,
            stationNameEn = stationNameEn,
            stationNameAm = stationNameAm,
            vehicleCategory = cat,
            licensePlate = licensePlate,
            driverPhone = driverPhone,
            driverName = driverName,
            ticketNumber = ticketNumber,
            status = status,
            registeredAtMillis = createdMillis,
            calledPumpNumber = calledPump
        )
    }

    private fun toFirestoreLanesMap(lanes: Map<VehicleCategory, StationLaneState>): Map<String, Map<String, Any>> {
        val out = HashMap<String, Map<String, Any>>()
        for ((cat, lane) in lanes) {
            if (cat == VehicleCategory.ALL) continue
            out[cat.name] = hashMapOf(
                "currentlyServing" to lane.currentlyServingNumber,
                "lastAssigned" to lane.lastAssignedNumber,
                "avgDurationSeconds" to lane.avgDurationSeconds,
                "isActive" to lane.isActive
            )
        }
        return out
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { exc ->
            if (cont.isActive) cont.resumeWithException(exc)
        }
    }
}
