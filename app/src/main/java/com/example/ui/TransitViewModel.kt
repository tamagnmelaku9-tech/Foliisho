package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.FirestoreQueueSync
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
    private val firestoreSync = FirestoreQueueSync(application.applicationContext, repository, viewModelScope)
    val isCloudConnected: StateFlow<Boolean> = firestoreSync.isCloudConnected

    private val _currentScreen = MutableStateFlow(AppScreen.STATION_RADAR)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _language = MutableStateFlow(Language.AMHARIC)
    val language: StateFlow<Language> = _language.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow(VehicleCategory.ALL)
    val selectedCategoryFilter: StateFlow<VehicleCategory> = _selectedCategoryFilter.asStateFlow()

    // Join Queue Dialog State
    private val _joinDialogStation = MutableStateFlow<StationInfo?>(null)
    val joinDialogStation: StateFlow<StationInfo?> = _joinDialogStation.asStateFlow()

    private val _isJoiningQueue = MutableStateFlow(false)
    val isJoiningQueue: StateFlow<Boolean> = _isJoiningQueue.asStateFlow()

    private val _joinDialogError = MutableStateFlow<String?>(null)
    val joinDialogError: StateFlow<String?> = _joinDialogError.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    val stations: StateFlow<List<StationInfo>> = repository.stations
    val currentTicket: StateFlow<QueueTicket?> = repository.currentTicket
    val isOnline: StateFlow<Boolean> = repository.isOnline
    val lastSyncTime: StateFlow<String> = repository.lastSyncTime

    // Gemini 3.8 Flash AI Assistant States
    private val aiAssistant = com.example.data.HawassaAiAssistant()
    private val _aiDialogVisible = MutableStateFlow(false)
    val aiDialogVisible: StateFlow<Boolean> = _aiDialogVisible.asStateFlow()

    private val _aiQueryLoading = MutableStateFlow(false)
    val aiQueryLoading: StateFlow<Boolean> = _aiQueryLoading.asStateFlow()

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse.asStateFlow()

    private val _aiLastQuery = MutableStateFlow("")
    val aiLastQuery: StateFlow<String> = _aiLastQuery.asStateFlow()

    init {
        // Monitor ticket headway or coordinator CALLED transition to auto-trigger 10-minute warning
        viewModelScope.launch {
            combine(repository.stations, repository.currentTicket) { stationList, ticket ->
                if (ticket != null && !ticket.isTenMinAlertTriggered &&
                    (ticket.status == TicketStatus.WAITING || ticket.status == TicketStatus.CALLED)
                ) {
                    val station = stationList.find { it.id == ticket.stationId }
                    if (station != null) {
                        val lane = station.getLane(ticket.vehicleCategory)
                        val ahead = ticket.calculateVehiclesAhead(lane.currentlyServingNumber)
                        val waitMinutes = ticket.calculateWaitMinutes(lane.currentlyServingNumber, lane.avgDurationSeconds)
                        if (ticket.status == TicketStatus.CALLED || waitMinutes <= 10 || ahead <= 3) {
                            repository.markTenMinuteAlertTriggered()
                            val stationName = if (_language.value == Language.AMHARIC) station.nameAm else station.nameEn
                            alertEngine.triggerTenMinuteAlert(
                                stationName = stationName,
                                ticketNumber = ticket.ticketNumber,
                                vehiclesAhead = ahead,
                                estimatedMinutes = if (ticket.status == TicketStatus.CALLED) 0 else maxOf(1, waitMinutes),
                                language = _language.value
                            )
                        }
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, Unit)
        }

        // Start real-time Cloud Firestore listener (stations + saved ticket + coordinator alert broadcasts)
        firestoreSync.startRealtimeSync { broadcastStationId, _ ->
            val active = repository.currentTicket.value
            val station = repository.stations.value.find { it.id == broadcastStationId }
            if (active == null || active.stationId == broadcastStationId) {
                val stationLabel = if (_language.value == Language.AMHARIC) {
                    station?.nameAm ?: "ቤንዚን ማድያ"
                } else {
                    station?.nameEn ?: "Hawassa Station"
                }
                alertEngine.triggerTenMinuteAlert(
                    stationName = stationLabel,
                    ticketNumber = active?.ticketNumber ?: 1,
                    vehiclesAhead = 2,
                    estimatedMinutes = 10,
                    language = _language.value
                )
            }
        }
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
        _joinDialogError.value = null
        _joinDialogStation.value = station
    }

    fun closeJoinDialog() {
        if (!_isJoiningQueue.value) {
            _joinDialogError.value = null
            _joinDialogStation.value = null
        }
    }

    fun submitJoinQueue(
        stationId: String,
        driverName: String,
        phone: String,
        plate: String
    ) {
        if (_isJoiningQueue.value) return
        _isJoiningQueue.value = true
        _joinDialogError.value = null

        viewModelScope.launch {
            val result = firestoreSync.joinQueue(
                stationId = stationId,
                driverName = driverName,
                driverPhone = phone,
                licensePlate = plate
            )
            _isJoiningQueue.value = false

            result.onSuccess { ticket ->
                _joinDialogStation.value = null
                _joinDialogError.value = null
                _currentScreen.value = AppScreen.MY_QUEUE_TICKET
                val msg = if (_language.value == Language.AMHARIC) {
                    "የተራ ቁጥርዎ #${ticket.ticketNumber} (${ticket.vehicleCategory.shortLabel}) ተመዝግቧል!"
                } else {
                    "Queue ticket #${ticket.ticketNumber} (${ticket.vehicleCategory.name}) booked live!"
                }
                _userMessage.emit(msg)
            }.onFailure { err ->
                val errText = err.message ?: "Failed to book queue slot"
                _joinDialogError.value = errText
                _userMessage.emit(errText)
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
        if (_isJoiningQueue.value) return
        _isJoiningQueue.value = true
        _joinDialogError.value = null

        viewModelScope.launch {
            val result = firestoreSync.joinQueue(
                stationId = stationId,
                driverName = driverName,
                driverPhone = phone,
                licensePlate = plate,
                explicitCategory = category
            )
            _isJoiningQueue.value = false

            result.onSuccess { ticket ->
                _joinDialogStation.value = null
                _joinDialogError.value = null
                _currentScreen.value = AppScreen.MY_QUEUE_TICKET
                val msg = if (_language.value == Language.AMHARIC) {
                    "የተራ ቁጥርዎ #${ticket.ticketNumber} ተመዝግቧል!"
                } else {
                    "Queue ticket #${ticket.ticketNumber} booked live!"
                }
                _userMessage.emit(msg)
            }.onFailure { err ->
                val errText = err.message ?: "Failed to book queue slot"
                _joinDialogError.value = errText
                _userMessage.emit(errText)
            }
        }
    }

    fun leaveQueue() {
        val activeTicketId = repository.currentTicket.value?.id
        _currentScreen.value = AppScreen.STATION_RADAR
        viewModelScope.launch {
            firestoreSync.leaveQueue(activeTicketId)
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

    fun openAiDialog(initialQuery: String = "") {
        _aiLastQuery.value = initialQuery
        _aiDialogVisible.value = true
        if (initialQuery.isNotBlank() && _aiResponse.value == null) {
            submitAiQuery(initialQuery)
        }
    }

    fun closeAiDialog() {
        _aiDialogVisible.value = false
    }

    fun submitAiQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _aiLastQuery.value = trimmed
        _aiQueryLoading.value = true
        viewModelScope.launch {
            try {
                val advice = aiAssistant.askAssistant(
                    stations = stations.value,
                    userQuery = trimmed,
                    language = _language.value,
                    selectedCategory = _selectedCategoryFilter.value
                )
                _aiResponse.value = advice
            } catch (e: Exception) {
                _aiResponse.value = if (_language.value == Language.AMHARIC) {
                    "የ AI ረዳት ምላሽ መስጠት አልቻለም: ${e.localizedMessage}"
                } else {
                    "AI Assistant error: ${e.localizedMessage}"
                }
            } finally {
                _aiQueryLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        firestoreSync.stopSync()
        alertEngine.shutdown()
    }
}
