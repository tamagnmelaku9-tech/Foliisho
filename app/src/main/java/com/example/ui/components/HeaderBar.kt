package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Language
import com.example.ui.AppScreen
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOnAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun HeaderBar(
    currentScreen: AppScreen,
    language: Language,
    isOnline: Boolean,
    lastSyncTime: String,
    hasActiveTicket: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleNetwork: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onOpenAi: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Branding (Driver App)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onNavigate(AppScreen.STATION_RADAR) }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SolarAmber),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalGasStation,
                        contentDescription = "Fuel Station",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (language == Language.AMHARIC) "ፎሊሾ (Foliisho)" else "Foliisho - Hawassa",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (language == Language.AMHARIC) "ቤንዚን ማድያ እና ጣቢያዎች" else "Benzene Madya Hub",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolarAmber
                    )
                }
            }

            // Actions: Gemini AI, Language Toggle & Driver Active Ticket Badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Gemini AI Dispatcher Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, SolarAmber.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable { onOpenAi() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("ai_assistant_top_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini AI",
                            tint = SolarAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == Language.AMHARIC) "AI" else "AI",
                            color = SolarAmber,
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Language Switcher
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onToggleLanguage() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("language_toggle_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Language",
                            tint = SolarAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == Language.AMHARIC) "EN" else "አማ",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 12.sp
                        )
                    }
                }

                if (hasActiveTicket) {
                    Spacer(modifier = Modifier.width(8.dp))

                    // My Ticket Quick Switcher
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (currentScreen == AppScreen.MY_QUEUE_TICKET) SolarAmber
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                onNavigate(
                                    if (currentScreen == AppScreen.MY_QUEUE_TICKET) AppScreen.STATION_RADAR
                                    else AppScreen.MY_QUEUE_TICKET
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("my_ticket_tab_btn")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ConfirmationNumber,
                                contentDescription = "My Ticket",
                                tint = if (currentScreen == AppScreen.MY_QUEUE_TICKET) TextOnAmber else SolarAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == Language.AMHARIC) "ተራዬ" else "My Ticket",
                                color = if (currentScreen == AppScreen.MY_QUEUE_TICKET) TextOnAmber else TextPrimary,
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Offline / Online Status Indicator Strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isOnline) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                )
                .border(
                    width = 1.dp,
                    color = if (isOnline) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(6.dp)
                )
                .clickable { onToggleNetwork() }
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag("network_status_badge")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) EmeraldAvailable else MaterialTheme.colorScheme.error)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isOnline) {
                            if (language == Language.AMHARIC) "ቀጥታ መስመር (የተመሳሰለው: $lastSyncTime)" else "Live Online (Synced: $lastSyncTime)"
                        } else {
                            if (language == Language.AMHARIC) "ከመስመር ውጭ (የተቀመጠ መረጃ: $lastSyncTime)" else "Offline Mode (Cached: $lastSyncTime)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOnline) TextSecondary else MaterialTheme.colorScheme.error
                    )
                }
                Icon(
                    imageVector = if (isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "Connection",
                    tint = if (isOnline) TextMuted else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
