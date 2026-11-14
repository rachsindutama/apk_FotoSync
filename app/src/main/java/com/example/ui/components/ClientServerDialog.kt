package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GuestSummary
import com.example.data.model.PhotoSessionEntity
import com.example.data.model.StudioSocialProfile
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.StudioGold

@Composable
fun ClientServerDialog(
    session: PhotoSessionEntity?,
    isServerRunning: Boolean,
    serverUrl: String,
    likesCount: Int,
    socialProfile: StudioSocialProfile,
    connectedGuests: List<GuestSummary>,
    onOpenSocialSettings: () -> Unit,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
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

                // Studio Social Follow Gate Config Summary + Edit Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF222630), RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (socialProfile.isConfigured) StudioGold.copy(alpha = 0.45f) else Color(0xFF2C3240),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = if (socialProfile.isConfigured) StudioGold else Color(0xFF9CA3AF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val accounts = socialProfile.configuredAccounts
                            val vendorLabel = socialProfile.vendorName.trim()
                            Text(
                                text = when {
                                    vendorLabel.isNotEmpty() && accounts.isNotEmpty() ->
                                        "$vendorLabel • ${accounts.size} Sosmed Aktif"
                                    vendorLabel.isNotEmpty() ->
                                        "Vendor: $vendorLabel"
                                    accounts.isNotEmpty() ->
                                        "Wajib Follow Aktif (${accounts.size} Sosmed)"
                                    else ->
                                        "Nama Vendor & Link Sosmed"
                                },
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (accounts.isNotEmpty()) {
                                    socialProfile.summaryLabel
                                } else if (vendorLabel.isNotEmpty()) {
                                    "Header web tamu: $vendorLabel (tanpa wajib follow)"
                                } else {
                                    "Belum diatur (tamu langsung masuk galeri)"
                                },
                                color = if (socialProfile.hasAnyIdentityOrSocial) StudioGold else Color(0xFF9CA3AF),
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    TextButton(
                        onClick = onOpenSocialSettings,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("qr_edit_social_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Atur Sosmed Studio",
                            tint = StudioGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (socialProfile.isConfigured) "Ubah" else "Atur",
                            color = StudioGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isServerRunning && serverUrl.isNotEmpty()) {
                    // QR Code
                    QrCodeView(
                        content = serverUrl,
                        size = 185.dp
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
                                text = "Setiap perangkat/IP tamu memiliki daftar favorit terpisah agar tidak saling menimpa.",
                                color = Color(0xFFD1D5DB),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // Connected Guests List (Per-IP / Device)
                    if (connectedGuests.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF14161C), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF2C3240), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Tamu Terdeteksi (${connectedGuests.size} Perangkat/IP)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            connectedGuests.take(5).forEach { guest ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${guest.clientIp} • ${guest.deviceLabel}",
                                            color = Color(0xFFD1D5DB),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${guest.likedCount} suka",
                                        color = CrimsonHeart,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    } else if (likesCount > 0) {
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
                                text = "$likesCount aktivitas favorit diterima dari tamu",
                                color = CrimsonHeart,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Nyalakan server lokal untuk membagikan akses pemilihan foto ke tamu.",
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
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("toggle_server_btn")
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Tutup", color = Color(0xFF9CA3AF))
            }
        }
    )
}
