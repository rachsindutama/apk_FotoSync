package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.StudioGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSessionDialog(
    onDismiss: () -> Unit,
    onCreateSession: (
        date: String,
        clientName: String,
        category: String,
        notes: String,
        folderName: String,
        folderUri: String
    ) -> Unit
) {
    val context = LocalContext.current
    val today = remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.format(Date())
    }

    var selectedFolderUri by remember { mutableStateOf<String?>(null) }
    var selectedFolderName by remember { mutableStateOf("") }
    var lastAutoFilledName by remember { mutableStateOf("") }

    var sessionDate by remember { mutableStateOf(today) }
    var clientName by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Wedding") }
    var isCategoryExpanded by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val categories = listOf("Wedding", "Graduation", "Studio", "Commercial", "Event")

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            try {
                val rwFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, rwFlags)
            } catch (_: SecurityException) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            } catch (_: Exception) {
            }

            val docDir = DocumentFile.fromTreeUri(context, uri)
            val resolvedName = docDir?.name?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment
                    ?.substringAfterLast(':')
                    ?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() }
                ?: "Folder_Foto"

            selectedFolderUri = uri.toString()
            selectedFolderName = resolvedName

            if (clientName.isBlank() || clientName == lastAutoFilledName) {
                clientName = resolvedName
                lastAutoFilledName = resolvedName
            }
        }
    }

    val isFolderSelected = !selectedFolderUri.isNullOrEmpty() && selectedFolderName.isNotBlank()

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = StudioGold,
        unfocusedBorderColor = Color(0xFF2C3240),
        focusedLabelColor = StudioGold,
        unfocusedLabelColor = Color(0xFF9CA3AF),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedTrailingIconColor = StudioGold,
        unfocusedTrailingIconColor = Color(0xFF9CA3AF)
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF181A20),
            border = BorderStroke(1.dp, Color(0xFF2C3240)),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Clean, tightly spaced header row (icon aligned with 1-line title + compact subtitle)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(StudioGold.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = StudioGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Publish Folder Sesi",
                            color = Color.White,
                            fontSize = 18.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Pilih folder dari memori perangkat",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF252A36))

                // Scrollable Form Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Existing Memory Folder Picker Card (Mandatory)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isFolderSelected) Color(0xFF064E3B).copy(alpha = 0.35f)
                                else Color(0xFF222630)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isFolderSelected) Color(0xFF10B981) else StudioGold.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { folderPickerLauncher.launch(null) }
                            .padding(12.dp)
                            .testTag("pick_existing_folder_btn")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (isFolderSelected) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                                    contentDescription = "Pilih Folder di Memori",
                                    tint = if (isFolderSelected) Color(0xFF34D399) else StudioGold,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = if (isFolderSelected) "Folder Sumber Terpilih" else "Pilih Folder di Memori Perangkat",
                                        color = if (isFolderSelected) Color(0xFFA7F3D0) else Color.White,
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isFolderSelected) {
                                            selectedFolderName
                                        } else {
                                            "Tap untuk memilih folder foto (DCIM, Pictures, SD Card)"
                                        },
                                        color = if (isFolderSelected) StudioGold else Color(0xFF9CA3AF),
                                        fontSize = if (isFolderSelected) 13.sp else 11.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = if (isFolderSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (isFolderSelected) {
                                TextButton(
                                    onClick = { folderPickerLauncher.launch(null) },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(
                                        text = "Ganti",
                                        color = CobaltBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Client / Session Name Input (auto-filled from picked folder name)
                    OutlinedTextField(
                        value = clientName,
                        onValueChange = { clientName = it },
                        label = { Text("Nama Sesi / Klien") },
                        placeholder = { Text("Otomatis terisi dari nama folder") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("session_client_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        singleLine = true
                    )

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
                        colors = fieldColors,
                        singleLine = true
                    )

                    // Session Category Dropdown (Material 3 ExposedDropdownMenuBox)
                    ExposedDropdownMenuBox(
                        expanded = isCategoryExpanded,
                        onExpandedChange = { isCategoryExpanded = !isCategoryExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori Sesi") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = null,
                                    tint = StudioGold,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("session_category_dropdown"),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                            singleLine = true
                        )

                        ExposedDropdownMenu(
                            expanded = isCategoryExpanded,
                            onDismissRequest = { isCategoryExpanded = false },
                            modifier = Modifier.background(Color(0xFF222630))
                        ) {
                            categories.forEach { cat ->
                                val isSelected = (cat == selectedCategory)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) StudioGold else Color.White,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 14.sp
                                        )
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = StudioGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        selectedCategory = cat
                                        isCategoryExpanded = false
                                    },
                                    modifier = Modifier.testTag("category_option_$cat")
                                )
                            }
                        }
                    }

                    // Notes Input
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Catatan Lokasi / Paket") },
                        placeholder = { Text("Opsional, misal: Gedung Bidakara") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                        maxLines = 2
                    )
                }

                // Bottom Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF374151)),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Batal", color = Color(0xFFD1D5DB))
                    }

                    Button(
                        onClick = {
                            val uri = selectedFolderUri
                            if (uri.isNullOrEmpty()) {
                                Toast.makeText(context, "Pilih folder di memori perangkat terlebih dahulu!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (clientName.isNotBlank() && sessionDate.isNotBlank()) {
                                onCreateSession(
                                    sessionDate.trim(),
                                    clientName.trim(),
                                    selectedCategory,
                                    notes.trim(),
                                    selectedFolderName,
                                    uri
                                )
                            }
                        },
                        enabled = isFolderSelected && clientName.isNotBlank() && sessionDate.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioGold,
                            disabledContainerColor = Color(0xFF2C3240),
                            disabledContentColor = Color(0xFF6B7280)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("submit_create_session")
                    ) {
                        Text(
                            text = "Publish Folder",
                            color = if (isFolderSelected && clientName.isNotBlank() && sessionDate.isNotBlank()) {
                                Color(0xFF121316)
                            } else {
                                Color(0xFF9CA3AF)
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
