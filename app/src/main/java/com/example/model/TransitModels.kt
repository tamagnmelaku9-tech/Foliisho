package com.example.model

enum class Language(val code: String, val displayName: String) {
    AMHARIC("am", "አማርኛ"),
    ENGLISH("en", "English")
}

enum class VehicleCategory(
    val code: String,
    val nameEn: String,
    val nameAm: String,
    val tankSizeLitres: Int,
    val defaultDispenseSeconds: Int,
    val shortLabel: String
) {
    ALL("ALL", "All Vehicles", "ሁሉም ተሽከርካሪዎች", 0, 0, "ALL"),
    BAJAJ("BAJAJ", "Bajaj (3-Wheeler)", "ባጃጅ (ባለ 3 እግር)", 8, 100, "ባጃጅ"),
    TAXI_MINIBUS("TAXI_MINIBUS", "Minibus / Taxi", "ሚኒባስ / ታክሲ (አባዱላ)", 60, 240, "ታክሲ"),
    PUBLIC_BUS("PUBLIC_BUS", "Public Bus", "የህዝብ አውቶቡስ", 180, 540, "አውቶቡስ"),
    CAR("CAR", "Automobile / Car", "አውቶሞቢል / መኪና", 50, 200, "መኪና"),
    MOTORCYCLE("MOTORCYCLE", "Motorcycle", "ሞተር ሳይክል", 12, 90, "ሞተር"),
    TRUCK_HEAVY("TRUCK_HEAVY", "Truck / Heavy", "የጭነት ተሽከርካሪ", 300, 900, "ጭነት")
}

enum class FuelStatus(val code: String, val labelEn: String, val labelAm: String) {
    AVAILABLE("AVAILABLE", "In Stock", "በቂ ክምችት"),
    LOW_STOCK("LOW_STOCK", "Low Stock", "ዝቅተኛ ክምችት"),
    OUT_OF_STOCK("OUT_OF_STOCK", "Out of Stock", "አልቋል")
}

enum class CongestionLevel(val code: String, val labelEn: String, val labelAm: String) {
    LOW("LOW", "Flowing", "የተረጋጋ"),
    MODERATE("MODERATE", "Moderate", "መካከለኛ"),
    HEAVY("HEAVY", "Congested", "ከፍተኛ መጨናነቅ")
}

data class StationLaneState(
    val vehicleCategory: VehicleCategory,
    val isActive: Boolean = true,
    val isPrioritized: Boolean = false,
    val lastAssignedNumber: Int = 100,
    val currentlyServingNumber: Int = 94,
    val avgDurationSeconds: Int = 120
) {
    val waitingCount: Int
        get() = maxOf(0, lastAssignedNumber - currentlyServingNumber)

    val estimatedMinutesWait: Int
        get() = (waitingCount * avgDurationSeconds) / 60
}

data class StationInfo(
    val id: String,
    val nameEn: String,
    val nameAm: String,
    val neighborhoodEn: String,
    val neighborhoodAm: String,
    val distanceKm: Double,
    val benzeneStatus: FuelStatus,
    val dieselStatus: FuelStatus,
    val congestionLevel: CongestionLevel,
    val isOpen: Boolean = true,
    val lanes: Map<VehicleCategory, StationLaneState> = emptyMap(),
    val prioritizedCategory: VehicleCategory = VehicleCategory.TAXI_MINIBUS,
    val activeCalledCategory: VehicleCategory = prioritizedCategory,
    val adminAnnouncementAm: String = "በአስተዳዳሪው የተፈቀደው ተሽከርካሪ",
    val adminAnnouncementEn: String = "Category called by Station Coordinator"
) {
    fun getLane(category: VehicleCategory): StationLaneState {
        return lanes[category] ?: StationLaneState(
            vehicleCategory = category,
            lastAssignedNumber = 50,
            currentlyServingNumber = 46,
            avgDurationSeconds = category.defaultDispenseSeconds
        )
    }
}

enum class TicketStatus {
    WAITING,
    CALLED,
    SERVING,
    COMPLETED,
    SKIPPED,
    CANCELLED
}

data class QueueTicket(
    val id: String,
    val stationId: String,
    val stationNameEn: String,
    val stationNameAm: String,
    val vehicleCategory: VehicleCategory,
    val licensePlate: String,
    val driverPhone: String,
    val driverName: String,
    val ticketNumber: Int,
    val status: TicketStatus = TicketStatus.WAITING,
    val registeredAtMillis: Long = System.currentTimeMillis(),
    val calledPumpNumber: Int? = null,
    val isTenMinAlertTriggered: Boolean = false
) {
    fun calculateVehiclesAhead(currentlyServing: Int): Int {
        return maxOf(0, ticketNumber - currentlyServing)
    }

    fun calculateWaitMinutes(currentlyServing: Int, avgSecondsPerVehicle: Int): Int {
        val ahead = calculateVehiclesAhead(currentlyServing)
        return (ahead * avgSecondsPerVehicle) / 60
    }
}
