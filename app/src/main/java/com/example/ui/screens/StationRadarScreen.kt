package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.example.model.CongestionLevel
import com.example.model.FuelStatus
import com.example.model.Language
import com.example.model.StationInfo
import com.example.model.VehicleCategory
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldAvailable
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOnAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun StationRadarScreen(
    stations: List<StationInfo>,
    selectedCategory: VehicleCategory,
    language: Language,
    onCategorySelected: (VehicleCategory) -> Unit,
    onJoinStationQueue: (StationInfo) -> Unit,
    onOpenAi: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // AI Transit & Fuel Queue Assistant Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SolarAmber.copy(alpha = 0.12f))
                .border(1.dp, SolarAmber.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .clickable { onOpenAi() }
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("ai_radar_banner")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SolarAmber),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (language == Language.AMHARIC) "የሐዋሳ AI ረዳት (Gemini 3.8)" else "Hawassa AI Dispatcher (Gemini 3.8)",
                            style = MaterialTheme.typography.titleSmall,
                            color = SolarAmber
                        )
                        Text(
                            text = if (language == Language.AMHARIC) "ፈጣን ወረፋ እና ነዳጅ ያለበትን ጣቢያ ይጠይቁ" else "Tap to ask for fastest fuel queue or least congested station",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SolarAmber)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (language == Language.AMHARIC) "ጠይቅ" else "Ask AI",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextOnAmber,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Vehicle Category Horizontal Filter Chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (language == Language.AMHARIC) "የተሽከርካሪ አይነት ይምረጡ:" else "Filter by Vehicle Type:",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VehicleCategory.values().forEach { category ->
                    val isSelected = selectedCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelected(category) },
                        label = {
                            Text(
                                text = if (language == Language.AMHARIC) category.nameAm else category.nameEn,
                                style = MaterialTheme.typography.labelLarge,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SolarAmber,
                            selectedLabelColor = TextOnAmber,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = SolarAmber
                        ),
                        modifier = Modifier.testTag("filter_chip_${category.name.lowercase()}")
                    )
                }
            }
        }

        // Section Title: Station List
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (language == Language.AMHARIC) "የሀዋሳ ከተማ ነዳጅ ጣቢያዎች" else "Hawassa Fuel Stations",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = "${stations.size} ${if (language == Language.AMHARIC) "ጣቢያዎች" else "Stations"}",
                style = MaterialTheme.typography.labelSmall,
                color = SolarAmber
            )
        }

        // Station Cards List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(stations, key = { it.id }) { station ->
                StationCard(
                    station = station,
                    selectedCategory = selectedCategory,
                    language = language,
                    onJoinQueue = { onJoinStationQueue(station) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun StationCard(
    station: StationInfo,
    selectedCategory: VehicleCategory,
    language: Language,
    onJoinQueue: () -> Unit
) {
    val targetCategory = if (selectedCategory == VehicleCategory.ALL) {
        station.prioritizedCategory
    } else {
        selectedCategory
    }

    val lane = station.getLane(targetCategory)
    val waitingVehicles = lane.waitingCount
    val estimatedMinutes = lane.estimatedMinutesWait

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("station_card_${station.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Name & Distance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (language == Language.AMHARIC) station.nameAm else station.nameEn,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = "Location",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (language == Language.AMHARIC) station.neighborhoodAm else station.neighborhoodEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Distance pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${station.distanceKm} km",
                        style = MaterialTheme.typography.labelSmall,
                        color = SolarAmber,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fuel Stock Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FuelBadge(
                    label = if (language == Language.AMHARIC) "ቤንዚን" else "Benzene",
                    status = station.benzeneStatus,
                    language = language,
                    modifier = Modifier.weight(1f)
                )
                FuelBadge(
                    label = if (language == Language.AMHARIC) "ናፍጣ" else "Diesel",
                    status = station.dieselStatus,
                    language = language,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Traffic & Queue Flow Indicators
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Congestion
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Traffic,
                        contentDescription = "Traffic",
                        tint = when (station.congestionLevel) {
                            CongestionLevel.LOW -> EmeraldAvailable
                            CongestionLevel.MODERATE -> AmberWarning
                            CongestionLevel.HEAVY -> CoralError
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (language == Language.AMHARIC) station.congestionLevel.labelAm else station.congestionLevel.labelEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                // Vehicle Lane Queue Stats
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HourglassTop,
                        contentDescription = "Wait time",
                        tint = SolarAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$waitingVehicles ${if (language == Language.AMHARIC) targetCategory.shortLabel else targetCategory.name} (~$estimatedMinutes min)",
                        style = MaterialTheme.typography.labelLarge,
                        color = SolarAmber,
                        fontSize = 12.sp
                    )
                }
            }

            // Admin-Controlled Vehicle Category Announcement
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(SkyCyan.copy(alpha = 0.12f))
                    .border(1.dp, SkyCyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = SkyCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (language == Language.AMHARIC)
                            "በአስተዳዳሪው የተፈቀደው: ${station.activeCalledCategory.nameAm}"
                        else
                            "Admin Permitted: ${station.activeCalledCategory.nameEn}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyCyan,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Join Queue Button
            Button(
                onClick = onJoinQueue,
                enabled = (station.benzeneStatus != FuelStatus.OUT_OF_STOCK || station.dieselStatus != FuelStatus.OUT_OF_STOCK),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SolarAmber,
                    contentColor = TextOnAmber,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = TextMuted
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("join_queue_btn_${station.id}")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (language == Language.AMHARIC) "ወደዚህ ጣቢያ ተሰለፍ" else "Join Station Queue",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun FuelBadge(
    label: String,
    status: FuelStatus,
    language: Language,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (status) {
        FuelStatus.AVAILABLE -> EmeraldAvailable to (if (language == Language.AMHARIC) "በቂ ክምችት" else "In Stock")
        FuelStatus.LOW_STOCK -> AmberWarning to (if (language == Language.AMHARIC) "ዝቅተኛ" else "Low")
        FuelStatus.OUT_OF_STOCK -> CoralError to (if (language == Language.AMHARIC) "አልቋል" else "Empty")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontSize = 11.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontSize = 11.sp
                )
            }
        }
    }
}
