package com.example.ui.components

import android.content.Context
import android.os.Environment
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PhotoItemEntity
import com.example.data.model.PhotoSessionEntity
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.StudioGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
fun DownloadExportDialog(
    session: PhotoSessionEntity?,
    allPhotos: List<PhotoItemEntity>,
    onDismiss: () -> Unit
) {
    if (session == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exportOption by remember { mutableStateOf(0) } // 0: Liked Only ZIP, 1: All Photos ZIP, 2: Save to Device Gallery
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadSuccess by remember { mutableStateOf(false) }
    var downloadedFileName by remember { mutableStateOf("") }

    val likedCount = remember(allPhotos) { allPhotos.count { it.isClientLiked } }
    val totalCount = allPhotos.size

    val targetCount = when (exportOption) {
        0 -> likedCount
        1 -> totalCount
        else -> if (likedCount > 0) likedCount else totalCount
    }

    AlertDialog(
        onDismissRequest = {
            if (!isDownloading) onDismiss()
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
                        .background(CobaltBlue.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = CobaltBlue
                    )
                }
                Column {
                    Text(
                        text = "Unduh File Foto",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Ekspor ke Penyimpanan Lokal HP",
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
                if (downloadSuccess) {
                    // Success View
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .background(Color(0xFF065F46), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = "File Berhasil Diunduh!",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tersimpan di: Download/FotoSync_Studio/$downloadedFileName",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else if (isDownloading) {
                    // Downloading Progress
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Membuat Paket File & Mengunduh...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(CircleShape),
                            color = CobaltBlue,
                            trackColor = Color(0xFF2C3240)
                        )
                        Text(
                            text = "${(downloadProgress * 100).toInt()}% selesai ($targetCount file)",
                            color = StudioGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    // Option Selection
                    Text(
                        text = "Pilih Format Unduhan:",
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Option 0: ZIP of Liked Photos
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (exportOption == 0) Color(0xFF222630) else Color.Transparent)
                            .border(1.dp, if (exportOption == 0) CrimsonHeart else Color(0xFF2C3240), RoundedCornerShape(12.dp))
                            .clickable { exportOption = 0 }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = exportOption == 0,
                            onClick = { exportOption = 0 },
                            colors = RadioButtonDefaults.colors(selectedColor = CrimsonHeart)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Paket ZIP Foto Favorit Klien",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = CrimsonHeart,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "$likedCount foto yang disukai klien • Format .ZIP",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Option 1: ZIP of All Photos
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (exportOption == 1) Color(0xFF222630) else Color.Transparent)
                            .border(1.dp, if (exportOption == 1) StudioGold else Color(0xFF2C3240), RoundedCornerShape(12.dp))
                            .clickable { exportOption = 1 }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = exportOption == 1,
                            onClick = { exportOption = 1 },
                            colors = RadioButtonDefaults.colors(selectedColor = StudioGold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Paket ZIP Semua Foto Sesi",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = StudioGold,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = "$totalCount foto lengkap dalam sesi • Format .ZIP",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Destination Info
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF14161C), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "File akan disimpan langsung ke direktori Download perangkat Anda dan dapat dibuka melalui File Manager.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!downloadSuccess) {
                Button(
                    onClick = {
                        isDownloading = true
                        scope.launch {
                            val targetPhotos = when (exportOption) {
                                0 -> allPhotos.filter { it.isClientLiked }
                                else -> allPhotos
                            }
                            val safePhotos = if (targetPhotos.isEmpty()) allPhotos else targetPhotos

                            // Write real ZIP archive to Download directory
                            val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "FotoSync_Studio")
                            if (!downloadDir.exists()) downloadDir.mkdirs()

                            val fileName = if (exportOption == 0) {
                                "${session.folderName}_Favorit_Klien.zip"
                            } else {
                                "${session.folderName}_Lengkap.zip"
                            }
                            val destFile = File(downloadDir, fileName)

                            val zos = ZipOutputStream(FileOutputStream(destFile))
                            val count = safePhotos.size
                            safePhotos.forEachIndexed { idx, photo ->
                                val entry = ZipEntry(photo.fileName)
                                zos.putNextEntry(entry)
                                val sourceFile = File(photo.localUri)
                                if (sourceFile.exists() && sourceFile.isFile) {
                                    sourceFile.inputStream().use { input ->
                                        input.copyTo(zos)
                                    }
                                } else {
                                    val bitmap = android.graphics.Bitmap.createBitmap(1200, 800, android.graphics.Bitmap.Config.ARGB_8888)
                                    val canvas = android.graphics.Canvas(bitmap)
                                    canvas.drawColor(android.graphics.Color.rgb(18, 20, 26))
                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.rgb(245, 166, 35)
                                        textSize = 36f
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias = true
                                    }
                                    canvas.drawText("FotoSync Studio - ${photo.fileName}", 600f, 400f, paint)
                                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, zos)
                                    bitmap.recycle()
                                }
                                zos.closeEntry()
                                downloadProgress = (idx + 1).toFloat() / count.toFloat()
                                delay(60L)
                            }
                            zos.finish()
                            zos.close()

                            downloadedFileName = fileName
                            isDownloading = false
                            downloadSuccess = true
                            Toast.makeText(context, "Berhasil diunduh: $fileName", Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(containerColor = CobaltBlue),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("start_download_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Unduh Sekarang ($targetCount Foto)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = StudioGold),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Tutup", color = Color(0xFF121316), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isDownloading && !downloadSuccess) {
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
