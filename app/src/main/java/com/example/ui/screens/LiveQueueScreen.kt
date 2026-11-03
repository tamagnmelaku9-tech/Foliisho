package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Language
import com.example.model.QueueTicket
import com.example.model.StationInfo
import com.example.model.TicketStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.DarkCharcoalBg
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOnAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LiveQueueScreen(
    ticket: QueueTicket,
    station: StationInfo?,
    language: Language,
    onBackToRadar: () -> Unit,
    onLeaveQueue: () -> Unit,
    onTestAlert: () -> Unit
) {
    val lane = station?.getLane(ticket.vehicleCategory)
    val currentlyServing = lane?.currentlyServingNumber ?: (ticket.ticketNumber - 3)
    val vehiclesAhead = ticket.calculateVehiclesAhead(currentlyServing)
    val avgSeconds = lane?.avgDurationSeconds ?: ticket.vehicleCategory.defaultDispenseSeconds
    val waitMinutes = ticket.calculateWaitMinutes(currentlyServing, avgSeconds)
    val isNearTurn = waitMinutes <= 10 || vehiclesAhead <= 3 || ticket.isTenMinAlertTriggered

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkCharcoalBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("live_queue_screen")
    ) {
        // Navigation Back Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBackToRadar,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("back_to_radar_btn")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (language == Language.AMHARIC) "ጣቢያዎች" else "Stations",
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // Plate Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${ticket.vehicleCategory.shortLabel} • ${ticket.licensePlate}",
                    style = MaterialTheme.typography.labelLarge,
                    color = SolarAmber,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Station Header Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SolarAmber.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = null,
                        tint = SolarAmber,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (language == Language.AMHARIC) ticket.stationNameAm else ticket.stationNameEn,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (language == Language.AMHARIC) "የተመደበው መስመር: ${ticket.vehicleCategory.nameAm}" else "Queue Lane: ${ticket.vehicleCategory.nameEn}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Massive Token Number Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 2.dp,
                    color = if (isNearTurn) SolarAmber else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(vertical = 24.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (language == Language.AMHARIC) "የእርስዎ የተራ ቁጥር" else "YOUR QUEUE TOKEN",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "#${ticket.ticketNumber}",
                    style = MaterialTheme.typography.displayLarge,
                    color = SolarAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 58.sp,
                    modifier = Modifier.testTag("driver_token_number")
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Status Pill
                val (statusColor, statusText) = when (ticket.status) {
                    TicketStatus.WAITING -> AmberWarning to (if (language == Language.AMHARIC) "በመጠባበቅ ላይ / Waiting" else "Waiting in Queue")
                    TicketStatus.CALLED -> EmeraldAvailable to (if (language == Language.AMHARIC) "አሁን ይግቡ! ፓምፕ #${ticket.calledPumpNumber ?: 1}" else "NOW CALLED: PUMP #${ticket.calledPumpNumber ?: 1}")
                    TicketStatus.SERVING -> SkyCyan to (if (language == Language.AMHARIC) "ነዳጅ እየተቀዳ ነው" else "Currently Fueling")
                    TicketStatus.COMPLETED -> EmeraldAvailable to (if (language == Language.AMHARIC) "ተጠናቋል" else "Service Completed")
                    TicketStatus.SKIPPED -> CoralError to (if (language == Language.AMHARIC) "ተዘልሏል (ለአስተባባሪው ያመልክቱ)" else "Skipped - Contact Coordinator")
                    TicketStatus.CANCELLED -> CoralError to (if (language == Language.AMHARIC) "ተሰርዟል" else "Cancelled")
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(statusColor.copy(alpha = 0.2f))
                        .border(1.dp, statusColor, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        color = statusColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Headway Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Vehicles Ahead Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HourglassTop,
                            contentDescription = null,
                            tint = SkyCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == Language.AMHARIC) "የሚቀሩ" else "Vehicles Ahead",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "$vehiclesAhead",
                        style = MaterialTheme.typography.displayMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("vehicles_ahead_count")
                    )
                    Text(
                        text = if (language == Language.AMHARIC) "ተሽከርካሪዎች" else "vehicles",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Estimated Wait Time Card
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PinDrop,
                            contentDescription = null,
                            tint = SolarAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (language == Language.AMHARIC) "የጥበቃ ጊዜ" else "Estimated Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "~$waitMinutes",
                        style = MaterialTheme.typography.displayMedium,
                        color = SolarAmber,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("estimated_wait_minutes")
                    )
                    Text(
                        text = if (language == Language.AMHARIC) "ደቂቃዎች" else "minutes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 10-Minute Alert Notification Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isNearTurn) SolarAmber.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surface
                )
                .border(
                    width = 1.dp,
                    color = if (isNearTurn) SolarAmber else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp)
                .testTag("ten_minute_alert_card")
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = if (isNearTurn) SolarAmber else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isNearTurn) {
                                if (language == Language.AMHARIC) "⚠️ የ10 ደቂቃ ማስጠንቀቂያ ንቁ ነው!" else "⚠️ 10-Minute Warning Active!"
                            } else {
                                if (language == Language.AMHARIC) "የ10 ደቂቃ ራስ-ሰር ማስጠንቀቂያ" else "10-Minute Automated Alert"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isNearTurn) SolarAmber else TextPrimary,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (language == Language.AMHARIC)
                        "ተራዎ 10 ደቂቃ ሲቀረው ስልክዎ በከፍተኛ ድምፅ እና በአማርኛ የድምፅ መልዕክት ያሳውቀዎታል።"
                    else
                        "When 10 minutes remain, your device will ring with high-volume siren and Amharic voice warning.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Test Alert Button (Simulate loud voice cue & siren)
                Button(
                    onClick = onTestAlert,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isNearTurn) SolarAmber else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isNearTurn) TextOnAmber else TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("test_alert_sound_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == Language.AMHARIC) "ድምፅ እና ንዝረት ሞክር" else "Test 10-Min Siren & Voice",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Leave Queue Action
        Button(
            onClick = onLeaveQueue,
            colors = ButtonDefaults.buttonColors(
                containerColor = CoralError.copy(alpha = 0.15f),
                contentColor = CoralError
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("leave_queue_btn")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == Language.AMHARIC) "ከተራ ውጣ (ሰርዝ)" else "Leave Queue (Cancel Ticket)",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
