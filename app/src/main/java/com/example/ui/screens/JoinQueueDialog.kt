package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.Language
import com.example.model.StationInfo
import com.example.ui.theme.SkyCyan
import com.example.ui.theme.SolarAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOnAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun JoinQueueDialog(
    station: StationInfo,
    language: Language,
    isSubmitting: Boolean = false,
    remoteError: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (stationId: String, driverName: String, phone: String, plate: String) -> Unit
) {
    var driverName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("09") }
    var licensePlate by remember { mutableStateOf("AA-2-") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val adminAllowedCategory = station.activeCalledCategory

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .testTag("join_queue_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(SolarAmber),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalGasStation,
                                contentDescription = null,
                                tint = TextOnAmber,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (language == Language.AMHARIC) "ተራ መያዣ" else "Join Station Queue",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = if (language == Language.AMHARIC) station.nameAm else station.nameEn,
                                style = MaterialTheme.typography.labelSmall,
                                color = SolarAmber
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_join_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Admin-Controlled Vehicle Category Banner (Driver cannot change, managed by Web Admin)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SkyCyan.copy(alpha = 0.15f))
                        .border(1.dp, SkyCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = SkyCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (language == Language.AMHARIC) "በአስተዳዳሪው የተፈቀደው ተሽከርካሪ:" else "Admin-Permitted Category:",
                                style = MaterialTheme.typography.labelSmall,
                                color = SkyCyan,
                                fontSize = 11.sp
                            )
                            Text(
                                text = if (language == Language.AMHARIC) adminAllowedCategory.nameAm else adminAllowedCategory.nameEn,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // License Plate Input
                OutlinedTextField(
                    value = licensePlate,
                    onValueChange = {
                        licensePlate = it
                        errorMessage = null
                    },
                    label = {
                        Text(if (language == Language.AMHARIC) "የሰሌዳ ቁጥር (ምሳሌ: AA-2-84920)" else "License Plate (e.g. AA-2-84920)")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = null,
                            tint = SolarAmber
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SolarAmber,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_license_plate")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Phone Number Input
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = {
                        phoneNumber = it
                        errorMessage = null
                    },
                    label = {
                        Text(if (language == Language.AMHARIC) "ስልክ ቁጥር (ምሳሌ: 0916123456)" else "Phone Number (e.g. 0916123456)")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = SolarAmber
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SolarAmber,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_phone_number")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Driver Name Input (Optional)
                OutlinedTextField(
                    value = driverName,
                    onValueChange = { driverName = it },
                    label = {
                        Text(if (language == Language.AMHARIC) "የአሽከርካሪ ስም (አማራጭ)" else "Driver Name (Optional)")
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = SolarAmber
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SolarAmber,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_driver_name")
                )

                val displayError = errorMessage ?: remoteError
                if (displayError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = displayError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Confirm Button
                Button(
                    onClick = {
                        if (licensePlate.trim().length < 4) {
                            errorMessage = if (language == Language.AMHARIC)
                                "እባክዎ ትክክለኛ የሰሌዳ ቁጥር ያስገቡ"
                            else
                                "Please enter a valid license plate"
                            return@Button
                        }
                        if (phoneNumber.trim().length < 9) {
                            errorMessage = if (language == Language.AMHARIC)
                                "እባክዎ ትክክለኛ ስልክ ቁጥር ያስገቡ"
                            else
                                "Please enter a valid phone number"
                            return@Button
                        }
                        onConfirm(
                            station.id,
                            driverName,
                            phoneNumber,
                            licensePlate
                        )
                    },
                    enabled = !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SolarAmber,
                        contentColor = TextOnAmber
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("confirm_join_queue_btn")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = TextOnAmber,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == Language.AMHARIC) "በመመዝገብ ላይ..." else "Booking Live...",
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        Text(
                            text = if (language == Language.AMHARIC) "ተራ ያረጋግጡ" else "Confirm Queue Token",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
