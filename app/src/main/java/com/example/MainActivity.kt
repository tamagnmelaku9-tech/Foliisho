package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.TransitViewModel
import com.example.ui.components.HeaderBar
import com.example.ui.screens.JoinQueueDialog
import com.example.ui.screens.LiveQueueScreen
import com.example.ui.screens.StationRadarScreen
import com.example.ui.theme.HawassaTransitTheme
import com.example.util.configureAppCheck
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            HawassaTransitTheme {
                TransitApp()
            }
        }
    }
}

@Composable
fun TransitApp(viewModel: TransitViewModel = viewModel()) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val language by viewModel.language.collectAsState()
    val selectedCategory by viewModel.selectedCategoryFilter.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val currentTicket by viewModel.currentTicket.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val lastSyncTime by viewModel.lastSyncTime.collectAsState()
    val joinDialogStation by viewModel.joinDialogStation.collectAsState()
    val isJoiningQueue by viewModel.isJoiningQueue.collectAsState()
    val joinDialogError by viewModel.joinDialogError.collectAsState()

    // Gemini 3.8 Flash AI Assistant States
    val aiDialogVisible by viewModel.aiDialogVisible.collectAsState()
    val aiQueryLoading by viewModel.aiQueryLoading.collectAsState()
    val aiResponse by viewModel.aiResponse.collectAsState()
    val aiLastQuery by viewModel.aiLastQuery.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Handle back button: return to Station Radar if currently viewing Ticket
    BackHandler(enabled = currentScreen != AppScreen.STATION_RADAR) {
        viewModel.setScreen(AppScreen.STATION_RADAR)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HeaderBar(
                currentScreen = currentScreen,
                language = language,
                isOnline = isOnline,
                lastSyncTime = lastSyncTime,
                hasActiveTicket = currentTicket != null,
                onToggleLanguage = { viewModel.toggleLanguage() },
                onToggleNetwork = { viewModel.toggleNetwork() },
                onNavigate = { screen -> viewModel.setScreen(screen) },
                onOpenAi = { viewModel.openAiDialog() }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentScreen) {
                AppScreen.STATION_RADAR -> {
                    StationRadarScreen(
                        stations = stations,
                        selectedCategory = selectedCategory,
                        language = language,
                        onCategorySelected = { category ->
                            viewModel.setCategoryFilter(category)
                        },
                        onJoinStationQueue = { station ->
                            viewModel.openJoinDialog(station)
                        },
                        onOpenAi = { viewModel.openAiDialog() }
                    )
                }

                AppScreen.MY_QUEUE_TICKET -> {
                    if (currentTicket != null) {
                        val activeStation = stations.find { it.id == currentTicket?.stationId }
                        LiveQueueScreen(
                            ticket = currentTicket!!,
                            station = activeStation,
                            language = language,
                            onBackToRadar = { viewModel.setScreen(AppScreen.STATION_RADAR) },
                            onLeaveQueue = { viewModel.leaveQueue() },
                            onTestAlert = { viewModel.testTenMinuteAlert() }
                        )
                    } else {
                        // If no ticket, display Station Radar
                        StationRadarScreen(
                            stations = stations,
                            selectedCategory = selectedCategory,
                            language = language,
                            onCategorySelected = { viewModel.setCategoryFilter(it) },
                            onJoinStationQueue = { viewModel.openJoinDialog(it) },
                            onOpenAi = { viewModel.openAiDialog() }
                        )
                    }
                }
            }

            // Driver Join Queue Dialog (Vehicle category is managed and announced by the Web Admin)
            if (joinDialogStation != null) {
                JoinQueueDialog(
                    station = joinDialogStation!!,
                    language = language,
                    isSubmitting = isJoiningQueue,
                    remoteError = joinDialogError,
                    onDismiss = { viewModel.closeJoinDialog() },
                    onConfirm = { stationId, name, phone, plate ->
                        viewModel.submitJoinQueue(stationId, name, phone, plate)
                    }
                )
            }

            // Gemini 3.8 Flash AI Transit & Fuel Assistant Dialog
            if (aiDialogVisible) {
                com.example.ui.components.HawassaAiAssistantDialog(
                    language = language,
                    isLoading = aiQueryLoading,
                    aiResponse = aiResponse,
                    lastQuery = aiLastQuery,
                    onDismiss = { viewModel.closeAiDialog() },
                    onSubmitQuery = { query -> viewModel.submitAiQuery(query) }
                )
            }
        }
    }
}
