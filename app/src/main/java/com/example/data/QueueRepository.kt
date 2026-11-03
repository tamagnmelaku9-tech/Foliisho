package com.example.data

import com.example.model.CongestionLevel
import com.example.model.FuelStatus
import com.example.model.QueueTicket
import com.example.model.StationInfo
import com.example.model.StationLaneState
import com.example.model.TicketStatus
import com.example.model.VehicleCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QueueRepository {

    private val _stations = MutableStateFlow<List<StationInfo>>(initialHawassaStations())
    val stations: StateFlow<List<StationInfo>> = _stations.asStateFlow()

    private val _currentTicket = MutableStateFlow<QueueTicket?>(null)
    val currentTicket: StateFlow<QueueTicket?> = _currentTicket.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _lastSyncTime = MutableStateFlow(getCurrentFormattedTime())
    val lastSyncTime: StateFlow<String> = _lastSyncTime.asStateFlow()

    // Anti-duplicate active plate registry
    private val activePlates = mutableMapOf<String, String>() // plate -> ticketId

    fun toggleNetworkConnection() {
        _isOnline.value = !_isOnline.value
        if (_isOnline.value) {
            _lastSyncTime.value = getCurrentFormattedTime()
        }
    }

    fun joinQueue(
        stationId: String,
        vehicleCategory: VehicleCategory,
        driverName: String,
        driverPhone: String,
        licensePlate: String
    ): Result<QueueTicket> {
        val sanitizedPlate = licensePlate.trim().uppercase()
        val sanitizedPhone = driverPhone.trim()

        if (sanitizedPlate.length < 5) {
            return Result.failure(IllegalArgumentException("Please enter a valid Ethiopian license plate (e.g. AA-2-84920)"))
        }

        if (sanitizedPhone.length < 9) {
            return Result.failure(IllegalArgumentException("Please enter a valid Ethiopian phone number"))
        }

        if (activePlates.containsKey(sanitizedPlate)) {
            return Result.failure(IllegalStateException("Vehicle $sanitizedPlate is already queued in the system!"))
        }

        val stationList = _stations.value.toMutableList()
        val stationIndex = stationList.indexOfFirst { it.id == stationId }
        if (stationIndex == -1) {
            return Result.failure(IllegalArgumentException("Station not found"))
        }

        val station = stationList[stationIndex]
        val currentLane = station.getLane(vehicleCategory)
        val newAssignedNumber = currentLane.lastAssignedNumber + 1

        val updatedLane = currentLane.copy(lastAssignedNumber = newAssignedNumber)
        val updatedLanes = station.lanes.toMutableMap()
        updatedLanes[vehicleCategory] = updatedLane

        stationList[stationIndex] = station.copy(lanes = updatedLanes)
        _stations.value = stationList
        _lastSyncTime.value = getCurrentFormattedTime()

        val ticketId = "TKT-${System.currentTimeMillis() % 100000}"
        val ticket = QueueTicket(
            id = ticketId,
            stationId = station.id,
            stationNameEn = station.nameEn,
            stationNameAm = station.nameAm,
            vehicleCategory = vehicleCategory,
            licensePlate = sanitizedPlate,
            driverPhone = sanitizedPhone,
            driverName = driverName.ifBlank { "Driver" },
            ticketNumber = newAssignedNumber,
            status = TicketStatus.WAITING,
            registeredAtMillis = System.currentTimeMillis()
        )

        activePlates[sanitizedPlate] = ticketId
        _currentTicket.value = ticket

        return Result.success(ticket)
    }

    fun setAdminActiveCategory(stationId: String, category: VehicleCategory) {
        val stationList = _stations.value.toMutableList()
        val idx = stationList.indexOfFirst { it.id == stationId }
        if (idx == -1) return

        val station = stationList[idx]
        val announcementAm = "አሁን የተፈቀደው ተሽከርካሪ: ${category.nameAm}"
        val announcementEn = "Admin Called Category: ${category.nameEn}"
        stationList[idx] = station.copy(
            activeCalledCategory = category,
            prioritizedCategory = category,
            adminAnnouncementAm = announcementAm,
            adminAnnouncementEn = announcementEn
        )
        _stations.value = stationList
        _lastSyncTime.value = getCurrentFormattedTime()
    }

    fun joinQueue(
        stationId: String,
        driverName: String,
        driverPhone: String,
        licensePlate: String
    ): Result<QueueTicket> {
        val station = _stations.value.find { it.id == stationId }
            ?: return Result.failure(IllegalArgumentException("Station not found"))
        val targetCategory = station.activeCalledCategory
        return joinQueue(stationId, targetCategory, driverName, driverPhone, licensePlate)
    }

    fun leaveQueue() {
        val ticket = _currentTicket.value ?: return
        activePlates.remove(ticket.licensePlate)
        _currentTicket.value = null
    }

    fun advanceLaneQueue(stationId: String, vehicleCategory: VehicleCategory) {
        val stationList = _stations.value.toMutableList()
        val idx = stationList.indexOfFirst { it.id == stationId }
        if (idx == -1) return

        val station = stationList[idx]
        val lane = station.getLane(vehicleCategory)
        if (lane.currentlyServingNumber < lane.lastAssignedNumber) {
            val nextServing = lane.currentlyServingNumber + 1
            val updatedLane = lane.copy(currentlyServingNumber = nextServing)
            val updatedLanes = station.lanes.toMutableMap()
            updatedLanes[vehicleCategory] = updatedLane
            stationList[idx] = station.copy(lanes = updatedLanes)
            _stations.value = stationList
            _lastSyncTime.value = getCurrentFormattedTime()

            // Update user ticket status if active
            val active = _currentTicket.value
            if (active != null && active.stationId == stationId && active.vehicleCategory == vehicleCategory) {
                if (active.ticketNumber == nextServing) {
                    _currentTicket.value = active.copy(
                        status = TicketStatus.CALLED,
                        calledPumpNumber = (1..4).random()
                    )
                } else if (active.ticketNumber < nextServing) {
                    _currentTicket.value = active.copy(status = TicketStatus.COMPLETED)
                }
            }
        }
    }

    fun prioritizeLane(stationId: String, vehicleCategory: VehicleCategory) {
        val stationList = _stations.value.toMutableList()
        val idx = stationList.indexOfFirst { it.id == stationId }
        if (idx == -1) return

        val station = stationList[idx]
        val updatedLanes = station.lanes.toMutableMap()
        for ((cat, lane) in station.lanes) {
            updatedLanes[cat] = lane.copy(isPrioritized = (cat == vehicleCategory))
        }
        stationList[idx] = station.copy(
            prioritizedCategory = vehicleCategory,
            lanes = updatedLanes
        )
        _stations.value = stationList
    }

    fun toggleLaneActive(stationId: String, vehicleCategory: VehicleCategory) {
        val stationList = _stations.value.toMutableList()
        val idx = stationList.indexOfFirst { it.id == stationId }
        if (idx == -1) return

        val station = stationList[idx]
        val lane = station.getLane(vehicleCategory)
        val updatedLanes = station.lanes.toMutableMap()
        updatedLanes[vehicleCategory] = lane.copy(isActive = !lane.isActive)
        stationList[idx] = station.copy(lanes = updatedLanes)
        _stations.value = stationList
    }

    fun updateFuelStock(stationId: String, isBenzene: Boolean, newStatus: FuelStatus) {
        val stationList = _stations.value.toMutableList()
        val idx = stationList.indexOfFirst { it.id == stationId }
        if (idx == -1) return

        val station = stationList[idx]
        val updated = if (isBenzene) {
            station.copy(benzeneStatus = newStatus)
        } else {
            station.copy(dieselStatus = newStatus)
        }
        stationList[idx] = updated
        _stations.value = stationList
    }

    fun syncStationStateFromCloud(
        stationId: String,
        permittedCategory: VehicleCategory?,
        benzeneStatus: FuelStatus?,
        dieselStatus: FuelStatus?,
        laneServingMap: Map<VehicleCategory, Int>,
        laneAssignedMap: Map<VehicleCategory, Int>
    ) {
        val stationList = _stations.value.toMutableList()
        val idx = stationList.indexOfFirst { it.id == stationId }
        if (idx == -1) return

        val station = stationList[idx]
        val updatedLanes = station.lanes.toMutableMap()
        for ((cat, lane) in station.lanes) {
            val serving = laneServingMap[cat] ?: lane.currentlyServingNumber
            val assigned = laneAssignedMap[cat] ?: lane.lastAssignedNumber
            updatedLanes[cat] = lane.copy(
                currentlyServingNumber = serving,
                lastAssignedNumber = maxOf(assigned, serving)
            )
        }

        val updatedCategory = permittedCategory ?: station.activeCalledCategory
        val announcementAm = "አሁን የተፈቀደው ተሽከርካሪ: ${updatedCategory.nameAm}"
        val announcementEn = "Admin Called Category: ${updatedCategory.nameEn}"

        stationList[idx] = station.copy(
            activeCalledCategory = updatedCategory,
            prioritizedCategory = updatedCategory,
            adminAnnouncementAm = announcementAm,
            adminAnnouncementEn = announcementEn,
            benzeneStatus = benzeneStatus ?: station.benzeneStatus,
            dieselStatus = dieselStatus ?: station.dieselStatus,
            lanes = updatedLanes
        )
        _stations.value = stationList
        _lastSyncTime.value = getCurrentFormattedTime()

        // Update active driver ticket if affected
        val active = _currentTicket.value
        if (active != null && active.stationId == stationId) {
            val lane = updatedLanes[active.vehicleCategory]
            if (lane != null) {
                if (active.ticketNumber == lane.currentlyServingNumber) {
                    _currentTicket.value = active.copy(
                        status = TicketStatus.CALLED,
                        calledPumpNumber = 2
                    )
                } else if (active.ticketNumber < lane.currentlyServingNumber) {
                    _currentTicket.value = active.copy(status = TicketStatus.COMPLETED)
                }
            }
        }
    }

    fun markTenMinuteAlertTriggered() {
        val ticket = _currentTicket.value ?: return
        _currentTicket.value = ticket.copy(isTenMinAlertTriggered = true)
    }

    private fun getCurrentFormattedTime(): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    companion object {
        fun initialHawassaStations(): List<StationInfo> {
            return listOf(
                StationInfo(
                    id = "benzene_madya",
                    nameEn = "Benzene Madya Central",
                    nameAm = "ቤንዚን ማድያ ዋና ጣቢያ",
                    neighborhoodEn = "Piazza / Menahariya Road",
                    neighborhoodAm = "ፒያሳ ወደ መናኸሪያ መታጠፊያ",
                    distanceKm = 1.2,
                    benzeneStatus = FuelStatus.AVAILABLE,
                    dieselStatus = FuelStatus.AVAILABLE,
                    congestionLevel = CongestionLevel.MODERATE,
                    lanes = mapOf(
                        VehicleCategory.BAJAJ to StationLaneState(
                            vehicleCategory = VehicleCategory.BAJAJ,
                            lastAssignedNumber = 125,
                            currentlyServingNumber = 117,
                            avgDurationSeconds = 110,
                            isPrioritized = false
                        ),
                        VehicleCategory.TAXI_MINIBUS to StationLaneState(
                            vehicleCategory = VehicleCategory.TAXI_MINIBUS,
                            lastAssignedNumber = 68,
                            currentlyServingNumber = 62,
                            avgDurationSeconds = 240,
                            isPrioritized = true
                        ),
                        VehicleCategory.PUBLIC_BUS to StationLaneState(
                            vehicleCategory = VehicleCategory.PUBLIC_BUS,
                            lastAssignedNumber = 24,
                            currentlyServingNumber = 21,
                            avgDurationSeconds = 480,
                            isPrioritized = false
                        ),
                        VehicleCategory.CAR to StationLaneState(
                            vehicleCategory = VehicleCategory.CAR,
                            lastAssignedNumber = 45,
                            currentlyServingNumber = 39,
                            avgDurationSeconds = 180,
                            isPrioritized = false
                        ),
                        VehicleCategory.MOTORCYCLE to StationLaneState(
                            vehicleCategory = VehicleCategory.MOTORCYCLE,
                            lastAssignedNumber = 18,
                            currentlyServingNumber = 16,
                            avgDurationSeconds = 90,
                            isPrioritized = false
                        ),
                        VehicleCategory.TRUCK_HEAVY to StationLaneState(
                            vehicleCategory = VehicleCategory.TRUCK_HEAVY,
                            lastAssignedNumber = 12,
                            currentlyServingNumber = 10,
                            avgDurationSeconds = 800,
                            isPrioritized = false
                        )
                    )
                ),
                StationInfo(
                    id = "total_menahariya",
                    nameEn = "TotalEnergies Menahariya",
                    nameAm = "ቶታል ኢነርጂ መናኸሪያ",
                    neighborhoodEn = "Hawassa Main Bus Terminal",
                    neighborhoodAm = "ሀዋሳ አውቶቡስ መናኸሪያ ፊት ለፊት",
                    distanceKm = 2.4,
                    benzeneStatus = FuelStatus.AVAILABLE,
                    dieselStatus = FuelStatus.AVAILABLE,
                    congestionLevel = CongestionLevel.HEAVY,
                    prioritizedCategory = VehicleCategory.TAXI_MINIBUS,
                    lanes = mapOf(
                        VehicleCategory.BAJAJ to StationLaneState(
                            vehicleCategory = VehicleCategory.BAJAJ,
                            lastAssignedNumber = 160,
                            currentlyServingNumber = 138,
                            avgDurationSeconds = 110
                        ),
                        VehicleCategory.TAXI_MINIBUS to StationLaneState(
                            vehicleCategory = VehicleCategory.TAXI_MINIBUS,
                            lastAssignedNumber = 95,
                            currentlyServingNumber = 81,
                            avgDurationSeconds = 220,
                            isPrioritized = true
                        ),
                        VehicleCategory.PUBLIC_BUS to StationLaneState(
                            vehicleCategory = VehicleCategory.PUBLIC_BUS,
                            lastAssignedNumber = 35,
                            currentlyServingNumber = 27,
                            avgDurationSeconds = 500
                        ),
                        VehicleCategory.CAR to StationLaneState(
                            vehicleCategory = VehicleCategory.CAR,
                            lastAssignedNumber = 52,
                            currentlyServingNumber = 42,
                            avgDurationSeconds = 190
                        ),
                        VehicleCategory.MOTORCYCLE to StationLaneState(
                            vehicleCategory = VehicleCategory.MOTORCYCLE,
                            lastAssignedNumber = 22,
                            currentlyServingNumber = 19,
                            avgDurationSeconds = 90
                        ),
                        VehicleCategory.TRUCK_HEAVY to StationLaneState(
                            vehicleCategory = VehicleCategory.TRUCK_HEAVY,
                            lastAssignedNumber = 18,
                            currentlyServingNumber = 14,
                            avgDurationSeconds = 850
                        )
                    )
                ),
                StationInfo(
                    id = "noc_piassa",
                    nameEn = "NOC Piassa Central",
                    nameAm = "ኤን ኦ ሲ ፒያሳ መሀል ከተማ",
                    neighborhoodEn = "Piassa Commercial Center",
                    neighborhoodAm = "ፒያሳ የንግድ ማዕከል አጠገብ",
                    distanceKm = 0.8,
                    benzeneStatus = FuelStatus.AVAILABLE,
                    dieselStatus = FuelStatus.LOW_STOCK,
                    congestionLevel = CongestionLevel.LOW,
                    lanes = mapOf(
                        VehicleCategory.BAJAJ to StationLaneState(
                            vehicleCategory = VehicleCategory.BAJAJ,
                            lastAssignedNumber = 88,
                            currentlyServingNumber = 82,
                            avgDurationSeconds = 100,
                            isPrioritized = true
                        ),
                        VehicleCategory.TAXI_MINIBUS to StationLaneState(
                            vehicleCategory = VehicleCategory.TAXI_MINIBUS,
                            lastAssignedNumber = 32,
                            currentlyServingNumber = 28,
                            avgDurationSeconds = 240
                        ),
                        VehicleCategory.PUBLIC_BUS to StationLaneState(
                            vehicleCategory = VehicleCategory.PUBLIC_BUS,
                            lastAssignedNumber = 10,
                            currentlyServingNumber = 9,
                            avgDurationSeconds = 540
                        ),
                        VehicleCategory.CAR to StationLaneState(
                            vehicleCategory = VehicleCategory.CAR,
                            lastAssignedNumber = 30,
                            currentlyServingNumber = 27,
                            avgDurationSeconds = 180
                        ),
                        VehicleCategory.MOTORCYCLE to StationLaneState(
                            vehicleCategory = VehicleCategory.MOTORCYCLE,
                            lastAssignedNumber = 15,
                            currentlyServingNumber = 14,
                            avgDurationSeconds = 90
                        ),
                        VehicleCategory.TRUCK_HEAVY to StationLaneState(
                            vehicleCategory = VehicleCategory.TRUCK_HEAVY,
                            lastAssignedNumber = 6,
                            currentlyServingNumber = 5,
                            avgDurationSeconds = 900
                        )
                    )
                ),
                StationInfo(
                    id = "ola_datto",
                    nameEn = "Ola Energy Datto",
                    nameAm = "ኦላ ኢነርጂ ዳቶ",
                    neighborhoodEn = "Lake Shore Transit Highway",
                    neighborhoodAm = "ዳቶ ሐይቅ ዳር መንገድ",
                    distanceKm = 4.1,
                    benzeneStatus = FuelStatus.OUT_OF_STOCK,
                    dieselStatus = FuelStatus.AVAILABLE,
                    congestionLevel = CongestionLevel.LOW,
                    lanes = mapOf(
                        VehicleCategory.BAJAJ to StationLaneState(
                            vehicleCategory = VehicleCategory.BAJAJ,
                            lastAssignedNumber = 30,
                            currentlyServingNumber = 29,
                            avgDurationSeconds = 110
                        ),
                        VehicleCategory.TAXI_MINIBUS to StationLaneState(
                            vehicleCategory = VehicleCategory.TAXI_MINIBUS,
                            lastAssignedNumber = 40,
                            currentlyServingNumber = 36,
                            avgDurationSeconds = 250
                        ),
                        VehicleCategory.PUBLIC_BUS to StationLaneState(
                            vehicleCategory = VehicleCategory.PUBLIC_BUS,
                            lastAssignedNumber = 15,
                            currentlyServingNumber = 13,
                            avgDurationSeconds = 520
                        ),
                        VehicleCategory.CAR to StationLaneState(
                            vehicleCategory = VehicleCategory.CAR,
                            lastAssignedNumber = 20,
                            currentlyServingNumber = 19,
                            avgDurationSeconds = 200
                        ),
                        VehicleCategory.MOTORCYCLE to StationLaneState(
                            vehicleCategory = VehicleCategory.MOTORCYCLE,
                            lastAssignedNumber = 8,
                            currentlyServingNumber = 8,
                            avgDurationSeconds = 90
                        ),
                        VehicleCategory.TRUCK_HEAVY to StationLaneState(
                            vehicleCategory = VehicleCategory.TRUCK_HEAVY,
                            lastAssignedNumber = 14,
                            currentlyServingNumber = 11,
                            avgDurationSeconds = 850
                        )
                    )
                ),
                StationInfo(
                    id = "tabor_oil",
                    nameEn = "National Oil Tabor",
                    nameAm = "ናሽናል ኦይል ታቦር",
                    neighborhoodEn = "Mount Tabor Transit Junction",
                    neighborhoodAm = "ታቦር ተራራ መታጠፊያ አደባባይ",
                    distanceKm = 3.3,
                    benzeneStatus = FuelStatus.AVAILABLE,
                    dieselStatus = FuelStatus.AVAILABLE,
                    congestionLevel = CongestionLevel.MODERATE,
                    lanes = mapOf(
                        VehicleCategory.BAJAJ to StationLaneState(
                            vehicleCategory = VehicleCategory.BAJAJ,
                            lastAssignedNumber = 75,
                            currentlyServingNumber = 68,
                            avgDurationSeconds = 110
                        ),
                        VehicleCategory.TAXI_MINIBUS to StationLaneState(
                            vehicleCategory = VehicleCategory.TAXI_MINIBUS,
                            lastAssignedNumber = 50,
                            currentlyServingNumber = 44,
                            avgDurationSeconds = 230
                        ),
                        VehicleCategory.PUBLIC_BUS to StationLaneState(
                            vehicleCategory = VehicleCategory.PUBLIC_BUS,
                            lastAssignedNumber = 18,
                            currentlyServingNumber = 15,
                            avgDurationSeconds = 510
                        ),
                        VehicleCategory.CAR to StationLaneState(
                            vehicleCategory = VehicleCategory.CAR,
                            lastAssignedNumber = 35,
                            currentlyServingNumber = 30,
                            avgDurationSeconds = 190
                        ),
                        VehicleCategory.MOTORCYCLE to StationLaneState(
                            vehicleCategory = VehicleCategory.MOTORCYCLE,
                            lastAssignedNumber = 12,
                            currentlyServingNumber = 11,
                            avgDurationSeconds = 90
                        ),
                        VehicleCategory.TRUCK_HEAVY to StationLaneState(
                            vehicleCategory = VehicleCategory.TRUCK_HEAVY,
                            lastAssignedNumber = 10,
                            currentlyServingNumber = 8,
                            avgDurationSeconds = 850
                        )
                    )
                )
            )
        }
    }
}
