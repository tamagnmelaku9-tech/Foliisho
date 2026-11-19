package com.example

import com.example.data.QueueRepository
import com.example.model.VehicleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExampleUnitTest {

    private lateinit var repository: QueueRepository

    @Before
    fun setUp() {
        repository = QueueRepository()
    }

    @Test
    fun testHawassaStationInitialization() {
        val stations = repository.stations.value
        assertEquals(5, stations.size)

        val benzeneMadya = stations.find { it.id == "benzene_madya" }
        assertNotNull(benzeneMadya)
        assertEquals("Benzene Madya Central", benzeneMadya?.nameEn)
        assertEquals("ቤንዚን ማድያ ዋና ጣቢያ", benzeneMadya?.nameAm)

        val bajajLane = benzeneMadya?.getLane(VehicleCategory.BAJAJ)
        assertNotNull(bajajLane)
        assertTrue(bajajLane!!.waitingCount > 0)
    }

    @Test
    fun testJoinQueueSuccess() {
        val result = repository.joinQueue(
            stationId = "benzene_madya",
            vehicleCategory = VehicleCategory.BAJAJ,
            driverName = "Abebe",
            driverPhone = "0916123456",
            licensePlate = "AA-2-84920"
        )

        assertTrue(result.isSuccess)
        val ticket = result.getOrNull()
        assertNotNull(ticket)
        assertEquals("AA-2-84920", ticket?.licensePlate)
        assertEquals(VehicleCategory.BAJAJ, ticket?.vehicleCategory)
        assertEquals(126, ticket?.ticketNumber) // 125 was last assigned in initial data
    }

    @Test
    fun testDuplicateLicensePlateRejection() {
        val firstResult = repository.joinQueue(
            stationId = "benzene_madya",
            vehicleCategory = VehicleCategory.BAJAJ,
            driverName = "Abebe",
            driverPhone = "0916123456",
            licensePlate = "AA-2-99999"
        )
        assertTrue(firstResult.isSuccess)

        // Attempting to register the same plate again must fail
        val duplicateResult = repository.joinQueue(
            stationId = "total_menahariya",
            vehicleCategory = VehicleCategory.TAXI_MINIBUS,
            driverName = "Chala",
            driverPhone = "0920123456",
            licensePlate = "AA-2-99999"
        )
        assertTrue(duplicateResult.isFailure)
        assertTrue(duplicateResult.exceptionOrNull()?.message?.contains("already queued") == true)
    }

    @Test
    fun testHeadwayAndWaitTimeCalculation() {
        val joinResult = repository.joinQueue(
            stationId = "benzene_madya",
            vehicleCategory = VehicleCategory.BAJAJ,
            driverName = "Tadesse",
            driverPhone = "0911223344",
            licensePlate = "ET-3-11223"
        )
        val ticket = joinResult.getOrThrow()

        // currentlyServing in initial data is 117, ticket number is 126
        val vehiclesAhead = ticket.calculateVehiclesAhead(117)
        assertEquals(9, vehiclesAhead)

        val waitMinutes = ticket.calculateWaitMinutes(currentlyServing = 117, avgSecondsPerVehicle = 120)
        // 9 vehicles * 120 sec = 1080 sec / 60 = 18 min
        assertEquals(18, waitMinutes)
    }

    @Test
    fun testCoordinatorAdvanceQueue() {
        val initialStation = repository.stations.value.find { it.id == "benzene_madya" }!!
        val initialServing = initialStation.getLane(VehicleCategory.BAJAJ).currentlyServingNumber

        repository.advanceLaneQueue("benzene_madya", VehicleCategory.BAJAJ)

        val updatedStation = repository.stations.value.find { it.id == "benzene_madya" }!!
        val updatedServing = updatedStation.getLane(VehicleCategory.BAJAJ).currentlyServingNumber

        assertEquals(initialServing + 1, updatedServing)
    }

    @Test
    fun testDriverJoinWithoutCategoryUsesAdminPermittedCategory() {
        // Admin announces Minibus as the permitted category for Benzene Madya
        repository.setAdminActiveCategory("benzene_madya", VehicleCategory.TAXI_MINIBUS)

        // Driver registers without inputting vehicle category
        val joinResult = repository.joinQueue(
            stationId = "benzene_madya",
            driverName = "Mulugeta",
            driverPhone = "0933445566",
            licensePlate = "ET-3-88776"
        )

        assertTrue(joinResult.isSuccess)
        val ticket = joinResult.getOrThrow()
        // Must automatically bind to TAXI_MINIBUS
        assertEquals(VehicleCategory.TAXI_MINIBUS, ticket.vehicleCategory)
    }

    @Test
    fun testCloudTicketAndStationSyncUpdatesTicketStatus() {
        val joinResult = repository.joinQueue(
            stationId = "benzene_madya",
            vehicleCategory = VehicleCategory.BAJAJ,
            driverName = "Dawit",
            driverPhone = "0911998877",
            licensePlate = "AA-2-55443"
        )
        val ticket = joinResult.getOrThrow()
        repository.setActiveTicketFromCloud(ticket)
        assertEquals(com.example.model.TicketStatus.WAITING, repository.currentTicket.value?.status)

        // Simulate station snapshot advancing currentlyServing to match ticketNumber
        val updatedStations = repository.stations.value.map { st ->
            if (st.id == "benzene_madya") {
                val updatedLanes = st.lanes.toMutableMap()
                val currentLane = st.getLane(VehicleCategory.BAJAJ)
                updatedLanes[VehicleCategory.BAJAJ] = currentLane.copy(currentlyServingNumber = ticket.ticketNumber)
                st.copy(lanes = updatedLanes)
            } else {
                st
            }
        }
        repository.updateStationsFromCloud(updatedStations)

        assertEquals(com.example.model.TicketStatus.CALLED, repository.currentTicket.value?.status)
    }
}
