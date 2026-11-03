package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.QueueRepository
import com.example.engine.NotificationAlertEngine
import com.example.model.Language
import com.example.model.QueueTicket
import com.example.model.StationInfo
import com.example.model.TicketStatus
import com.example.model.VehicleCategory
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppScreen {
    STATION_RADAR,
    MY_QUEUE_TICKET
}

class TransitViewModel(application: Application) : AndroidViewModel(application) {

    val repository = QueueRepository()
    private val alertEngine = NotificationAlertEngine(application.applicationContext)
    private val cloudSync = com.example.data.HawassaCloudSync(repository, viewModelScope)
    val isCloudConnected: StateFlow<Boolean> = cloudSync.isCloudConnected

    private val _currentScreen = MutableStateFlow(AppScreen.STATION_RADAR)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _language = MutableStateFlow(Language.AMHARIC)
    val language: StateFlow<Language> = _language.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow(VehicleCategory.ALL)
    val selectedCategoryFilter: StateFlow<VehicleCategory> = _selectedCategoryFilter.asStateFlow()

    // Join Queue Dialog State
    private val _joinDialogStation = MutableStateFlow<StationInfo?>(null)
    val joinDialogStation: StateFlow<StationInfo?> = _joinDialogStation.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    val stations: StateFlow<List<StationInfo>> = repository.stations
    val currentTicket: StateFlow<QueueTicket?> = repository.currentTicket
    val isOnline: StateFlow<Boolean> = repository.isOnline
    val lastSyncTime: StateFlow<String> = repository.lastSyncTime

    init {
        // Monitor ticket headway to auto-trigger 10-minute warning
        viewModelScope.launch {
            combine(repository.stations, repository.currentTicket) { stationList, ticket ->
                if (ticket != null && ticket.status == TicketStatus.WAITING && !ticket.isTenMinAlertTriggered) {
                    val station = stationList.find { it.id == ticket.stationId }
                    if (station != null) {
                        val lane = station.getLane(ticket.vehicleCategory)
                        val ahead = ticket.calculateVehiclesAhead(lane.currentlyServingNumber)
                        val waitMinutes = ticket.calculateWaitMinutes(lane.currentlyServingNumber, lane.avgDurationSeconds)
                        // Trigger if 10 mins or fewer, or 3 or fewer vehicles ahead
                        if (waitMinutes <= 10 || ahead <= 3) {
                            repository.markTenMinuteAlertTriggered()
                            val stationName = if (_language.value == Language.AMHARIC) station.nameAm else station.nameEn
                            alertEngine.triggerTenMinuteAlert(
                                stationName = stationName,
                                ticketNumber = ticket.ticketNumber,
                                vehiclesAhead = ahead,
                                estimatedMinutes = maxOf(1, waitMinutes),
                                language = _language.value
                            )
                        }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, Unit)
        }

        // Start real-time cloud sync with Hawassa backend server
        cloudSync.startRealtimeSync()
    }

    fun setScreen(screen: AppScreen) {
        _currentScreen.value = screen
    }

    fun toggleLanguage() {
        _language.value = if (_language.value == Language.AMHARIC) Language.ENGLISH else Language.AMHARIC
    }

    fun setCategoryFilter(category: VehicleCategory) {
        _selectedCategoryFilter.value = category
    }

    fun openJoinDialog(station: StationInfo) {
        _joinDialogStation.value = station
    }

    fun closeJoinDialog() {
        _joinDialogStation.value = null
    }

    fun submitJoinQueue(
        stationId: String,
        driverName: String,
        phone: String,
        plate: String
    ) {
        val result = repository.joinQueue(
            stationId = stationId,
            driverName = driverName,
            driverPhone = phone,
            licensePlate = plate
        )

        result.onSuccess { ticket ->
            _joinDialogStation.value = null
            _currentScreen.value = AppScreen.MY_QUEUE_TICKET
            viewModelScope.launch {
                val msg = if (_language.value == Language.AMHARIC) {
                    "የተራ ቁጥርዎ #${ticket.ticketNumber} (${ticket.vehicleCategory.shortLabel}) ተመዝግቧል!"
                } else {
                    "Queue ticket #${ticket.ticketNumber} (${ticket.vehicleCategory.name}) registered successfully!"
                }
                _userMessage.emit(msg)
            }
        }.onFailure { err ->
            viewModelScope.launch {
                _userMessage.emit(err.message ?: "Failed to join queue")
            }
        }
    }

    fun submitJoinQueue(
        stationId: String,
        category: VehicleCategory,
        driverName: String,
        phone: String,
        plate: String
    ) {
        val result = repository.joinQueue(
            stationId = stationId,
            vehicleCategory = category,
            driverName = driverName,
            driverPhone = phone,
            licensePlate = plate
        )

        result.onSuccess { ticket ->
            _joinDialogStation.value = null
            _currentScreen.value = AppScreen.MY_QUEUE_TICKET
            viewModelScope.launch {
                val msg = if (_language.value == Language.AMHARIC) {
                    "የተራ ቁጥርዎ #${ticket.ticketNumber} ተመዝግቧል!"
                } else {
                    "Queue ticket #${ticket.ticketNumber} registered successfully!"
                }
                _userMessage.emit(msg)
            }
        }.onFailure { err ->
            viewModelScope.launch {
                _userMessage.emit(err.message ?: "Failed to join queue")
            }
        }
    }

    fun leaveQueue() {
        repository.leaveQueue()
        _currentScreen.value = AppScreen.STATION_RADAR
        viewModelScope.launch {
            val msg = if (_language.value == Language.AMHARIC) {
                "ከተራው በተሳካ ሁኔታ ወጥተዋል"
            } else {
                "Left queue successfully"
            }
            _userMessage.emit(msg)
        }
    }

    fun testTenMinuteAlert() {
        val ticket = repository.currentTicket.value
        val stationName = ticket?.stationNameAm ?: "ቤንዚን ማድያ ዋና ጣቢያ"
        val ticketNumber = ticket?.ticketNumber ?: 125
        alertEngine.triggerTenMinuteAlert(
            stationName = stationName,
            ticketNumber = ticketNumber,
            vehiclesAhead = 2,
            estimatedMinutes = 8,
            language = _language.value
        )
        viewModelScope.launch {
            val msg = if (_language.value == Language.AMHARIC) {
                "ተራዎ ሊደርስ 10 ደቂቃ ቀረዎት፤ ወደ ማድያ ይምጡ!"
            } else {
                "Your turn will arrive in 10 minutes; please come to the station!"
            }
            _userMessage.emit(msg)
        }
    }

    fun toggleNetwork() {
        repository.toggleNetworkConnection()
    }

    override fun onCleared() {
        super.onCleared()
        alertEngine.shutdown()
    }
}
