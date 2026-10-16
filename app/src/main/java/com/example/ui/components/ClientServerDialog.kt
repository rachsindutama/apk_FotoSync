package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PhotoSessionEntity
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.StudioGold

@Composable
fun ClientServerDialog(
    session: PhotoSessionEntity?,
    isServerRunning: Boolean,
    serverUrl: String,
    likesCount: Int,
    onToggleServer: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

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
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = StudioGold
                    )
                }
                Column {
                    Text(
                        text = "Portal Klien (QR Code)",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Akses Tautan Privat Lokal",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Status Pill
                Row(
                    modifier = Modifier
                        .background(
                            if (isServerRunning) Color(0xFF064E3B) else Color(0xFF374151),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (isServerRunning) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                CircleShape
                            )
                    )
                    Text(
                        text = if (isServerRunning) "Web Server Aktif (Real-time)" else "Server Dimatikan",
                        color = if (isServerRunning) Color(0xFF6EE7B7) else Color(0xFFD1D5DB),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isServerRunning && serverUrl.isNotEmpty()) {
                    // QR Code
                    QrCodeView(
                        content = serverUrl,
                        size = 190.dp
                    )

                    // URL Box with Copy Button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF222630), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF2C3240), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Tautan Langsung Klien:",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = serverUrl,
                                    color = StudioGold,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("FotoSync URL", serverUrl)
                                    clipboard.setPrimaryClip(clip)
                                    copied = true
                                    Toast.makeText(context, "Tautan berhasil disalin!", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Salin Tautan",
                                    tint = if (copied) Color(0xFF10B981) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Network instructions
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A1C24), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Pastikan ponsel klien terhubung ke Wi-Fi atau Hotspot pribadi Anda. Klien cukup memindai QR code dengan kamera bawaan tanpa instal aplikasi.",
                                color = Color(0xFFD1D5DB),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Live likes indicator
                    if (likesCount > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CrimsonHeart.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = CrimsonHeart,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$likesCount tanda suka diterima dari klien",
                                color = CrimsonHeart,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Nyalakan server lokal untuk membagikan akses pemilihan foto ke klien.",
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onToggleServer,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isServerRunning) Color(0xFFDC2626) else StudioGold
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("toggle_server_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = if (isServerRunning) Color.White else Color(0xFF121316),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isServerRunning) "Matikan Server" else "Nyalakan Server",
                    color = if (isServerRunning) Color.White else Color(0xFF121316),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Tutup", color = Color(0xFF9CA3AF))
            }
        }
    )
}
