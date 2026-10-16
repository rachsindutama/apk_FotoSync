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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.StudioGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddSessionDialog(
    onDismiss: () -> Unit,
    onCreateSession: (date: String, clientName: String, category: String, notes: String) -> Unit
) {
    val today = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date())
    }

    var sessionDate by remember { mutableStateOf(today) }
    var clientName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Wedding") }
    var notes by remember { mutableStateOf("") }

    val categories = listOf("Wedding", "Graduation", "Studio", "Commercial", "Event")

    val folderPreview = remember(sessionDate, clientName, selectedCategory) {
        val sanitizedClient = if (clientName.isBlank()) "NamaKlien" else clientName.trim().replace("\\s+".toRegex(), "-")
        "${sessionDate}_${sanitizedClient}_${selectedCategory}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF181A20),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(StudioGold.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = StudioGold
                    )
                }
                Column {
                    Text(
                        text = "Sesi Foto Baru",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Folder Berbasis Tanggal (YYYY-MM-DD)",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live Folder Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF222630), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF2C3240), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = CobaltBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Format Folder Otomatis:",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.sp
                            )
                            Text(
                                text = folderPreview,
                                color = StudioGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Date Input
                OutlinedTextField(
                    value = sessionDate,
                    onValueChange = { sessionDate = it },
                    label = { Text("Tanggal Sesi (YYYY-MM-DD)") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = StudioGold,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_date_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioGold,
                        unfocusedBorderColor = Color(0xFF2C3240),
                        focusedLabelColor = StudioGold,
                        unfocusedLabelColor = Color(0xFF9CA3AF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                // Client Name Input
                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = { Text("Nama Klien / Pasangan") },
                    placeholder = { Text("Misal: Sarah & Rian") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_client_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioGold,
                        unfocusedBorderColor = Color(0xFF2C3240),
                        focusedLabelColor = StudioGold,
                        unfocusedLabelColor = Color(0xFF9CA3AF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                // Category Chips
                Column {
                    Text(
                        text = "Kategori Sesi:",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { cat ->
                            val isSelected = (selectedCategory == cat)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSelected) StudioGold else Color(0xFF222630))
                                    .clickable { selectedCategory = cat }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color(0xFF121316) else Color(0xFFD1D5DB),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Notes Input
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Catatan Lokasi / Paket (Opsional)") },
                    placeholder = { Text("Misal: Gedung Bidakara, Paket Pre-Wedding") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = StudioGold,
                        unfocusedBorderColor = Color(0xFF2C3240),
                        focusedLabelColor = StudioGold,
                        unfocusedLabelColor = Color(0xFF9CA3AF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (clientName.isNotBlank() && sessionDate.isNotBlank()) {
                        onCreateSession(sessionDate, clientName, selectedCategory, notes)
                    }
                },
                enabled = clientName.isNotBlank() && sessionDate.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = StudioGold),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("submit_create_session")
            ) {
                Text(
                    text = "Buat Folder Sesi",
                    color = Color(0xFF121316),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Batal", color = Color(0xFF9CA3AF))
            }
        }
    )
}
