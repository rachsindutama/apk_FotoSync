package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import com.example.data.model.PhotoItemEntity
import com.example.data.model.PhotoSessionEntity
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.StudioGold
import com.example.ui.viewmodel.BackupState

@Composable
fun DriveBackupDialog(
    session: PhotoSessionEntity?,
    allPhotos: List<PhotoItemEntity>,
    backupState: BackupState,
    onConfirmBackup: (onlyLiked: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    if (session == null) return

    var onlyLiked by remember { mutableStateOf(false) }
    val likedPhotosCount = remember(allPhotos) { allPhotos.count { it.isClientLiked } }
    val totalCount = allPhotos.size

    val targetCount = if (onlyLiked) likedPhotosCount else totalCount
    val totalBytes = remember(onlyLiked, allPhotos) {
        val list = if (onlyLiked) allPhotos.filter { it.isClientLiked } else allPhotos
        list.sumOf { it.fileSize }
    }
    val sizeMb = remember(totalBytes) {
        String.format("%.1f MB", totalBytes / (1024f * 1024f))
    }

    AlertDialog(
        onDismissRequest = {
            if (backupState !is BackupState.Uploading) onDismiss()
        },
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
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = StudioGold
                    )
                }
                Column {
                    Text(
                        text = "Ekspor ke Google Drive",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Konfirmasi Cadangan Manual",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (backupState) {
                    is BackupState.Idle -> {
                        // Warning banner as requested: "jangan auto upload! / wajib konfirmasi!"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF261D10), RoundedCornerShape(12.dp))
                                .border(1.dp, StudioGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = StudioGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Proteksi Kuota & Privasi",
                                        color = StudioGold,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Sesuai pengaturan, sistem tidak melakukan auto-upload. Konfirmasi Anda dibutuhkan untuk memulai pencadangan.",
                                        color = Color(0xFFD1D5DB),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Target folder
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF222630), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = CobaltBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Folder Tujuan Google Drive:",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "FotoSync_Studio/${session.folderName}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Options
                        Text(
                            text = "Pilih Cakupan Foto:",
                            color = Color(0xFF9CA3AF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        // Option 1: All photos
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!onlyLiked) Color(0xFF2C3240) else Color.Transparent)
                                .clickable { onlyLiked = false }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !onlyLiked,
                                onClick = { onlyLiked = false },
                                colors = RadioButtonDefaults.colors(selectedColor = StudioGold)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Semua Foto Sesi ($totalCount foto)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Termasuk foto yang belum direview",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Option 2: Only Client Liked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (onlyLiked) Color(0xFF2C3240) else Color.Transparent)
                                .clickable { onlyLiked = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = onlyLiked,
                                onClick = { onlyLiked = true },
                                colors = RadioButtonDefaults.colors(selectedColor = CrimsonHeart)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Foto Favorit Klien Saja ($likedPhotosCount foto)",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Favorite,
                                        contentDescription = null,
                                        tint = CrimsonHeart,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = "Hanya foto yang diberi tanda suka",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Summary box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E222B), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Total Data yang Diunggah:",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "$targetCount File ($sizeMb)",
                                    color = StudioGold,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    is BackupState.Uploading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = CobaltBlue,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Mengunggah Cadangan ke Google Drive...",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            LinearProgressIndicator(
                                progress = { backupState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = StudioGold,
                                trackColor = Color(0xFF2C3240)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = backupState.currentFile,
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${backupState.uploadedCount}/${backupState.totalCount} File (${(backupState.progress * 100).toInt()}%)",
                                    color = StudioGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    is BackupState.Success -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color(0xFF065F46), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Text(
                                text = "Cadangan Berhasil Diekspor!",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${backupState.count} foto berhasil disimpan di ${backupState.folderName}",
                                color = Color(0xFF9CA3AF),
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }

                    is BackupState.Error -> {
                        Text(
                            text = backupState.message,
                            color = CrimsonHeart,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (backupState) {
                is BackupState.Idle -> {
                    Button(
                        onClick = { onConfirmBackup(onlyLiked) },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioGold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("confirm_backup_button")
                    ) {
                        Text(
                            text = "Konfirmasi & Unggah ($targetCount Foto)",
                            color = Color(0xFF121316),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is BackupState.Success -> {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Selesai",
                            color = Color(0xFF121316),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {}
            }
        },
        dismissButton = {
            if (backupState is BackupState.Idle) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Batal", color = Color(0xFF9CA3AF))
                }
            }
        }
    )
}
