package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.SocialAccountEntry
import com.example.data.model.SocialLinkHelper
import com.example.data.model.SocialPlatform
import com.example.data.model.SocialResolutionResult
import com.example.data.model.StudioSocialProfile
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.StudioGold

@Composable
fun StudioSocialSettingsDialog(
    currentProfile: StudioSocialProfile,
    onSaveAccounts: (vendorName: String, accounts: Map<SocialPlatform, SocialAccountEntry>, lastEditedPlatform: SocialPlatform) -> Unit,
    onRemovePlatform: (SocialPlatform) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var vendorName by remember { mutableStateOf(currentProfile.vendorName) }
    var selectedPlatform by remember {
        mutableStateOf(
            if (currentProfile.accountFor(currentProfile.lastEditedPlatform) != null) {
                currentProfile.lastEditedPlatform
            } else {
                currentProfile.configuredAccounts.firstOrNull()?.platform ?: currentProfile.lastEditedPlatform
            }
        )
    }

    // Independent per-platform draft state so switching platforms never shares or overwrites text
    val draftUrls = remember {
        mutableStateMapOf<SocialPlatform, String>().apply {
            SocialPlatform.entries.forEach { platform ->
                this[platform] = currentProfile.accountFor(platform)?.socialUrl ?: ""
            }
        }
    }
    val draftNames = remember {
        mutableStateMapOf<SocialPlatform, String>().apply {
            SocialPlatform.entries.forEach { platform ->
                this[platform] = currentProfile.accountFor(platform)?.socialName ?: ""
            }
        }
    }

    var inputError by remember { mutableStateOf<String?>(null) }

    val activeHandleOrUrl = draftUrls[selectedPlatform] ?: ""
    val activeDisplayName = draftNames[selectedPlatform] ?: ""

    val liveResolution = remember(selectedPlatform, activeHandleOrUrl) {
        if (activeHandleOrUrl.isBlank()) null else SocialLinkHelper.resolve(selectedPlatform, activeHandleOrUrl)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = StudioGold,
        unfocusedBorderColor = Color(0xFF2C3240),
        focusedLabelColor = StudioGold,
        unfocusedLabelColor = Color(0xFF9CA3AF),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = StudioGold
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .widthIn(max = 420.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF181A20)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(StudioGold.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = StudioGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Link Sosial Media",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Atur link Sosial Media anda dari sini.",
                            color = Color(0xFF9CA3AF),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = vendorName,
                        onValueChange = { vendorName = it },
                        label = { Text("Nama Vendor / Studio") },
                        placeholder = { Text("Contoh: PVS Photography", color = Color(0xFF6B7280)) },
                        supportingText = {
                            Text(
                                text = "Ditampilkan di header halaman web tamu",
                                color = Color(0xFF9CA3AF),
                                fontSize = 11.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = null,
                                tint = StudioGold,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vendor_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )

                    val filledCount = SocialPlatform.entries.count { !(draftUrls[it].isNullOrBlank()) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pilih Platform:",
                            color = Color(0xFFD1D5DB),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (filledCount > 0) {
                            Text(
                                text = "$filledCount platform terisi",
                                color = Color(0xFF34D399),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(SocialPlatform.entries) { platform ->
                            val isSelected = selectedPlatform == platform
                            val isPlatformFilled = !draftUrls[platform].isNullOrBlank()
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) StudioGold else Color(0xFF222630))
                                    .border(
                                        1.dp,
                                        when {
                                            isSelected -> StudioGold
                                            isPlatformFilled -> Color(0xFF10B981).copy(alpha = 0.6f)
                                            else -> Color(0xFF2C3240)
                                        },
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        selectedPlatform = platform
                                        inputError = null
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                                    .testTag("platform_chip_${platform.name.lowercase()}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isPlatformFilled) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                if (isSelected) Color(0xFF121316) else Color(0xFF10B981),
                                                CircleShape
                                            )
                                    )
                                }
                                Text(
                                    text = platform.displayName,
                                    color = if (isSelected) Color(0xFF121316) else Color(0xFFD1D5DB),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = activeHandleOrUrl,
                        onValueChange = { next ->
                            draftUrls[selectedPlatform] = next
                            if (inputError != null) inputError = null
                        },
                        label = {
                            Text(
                                if (selectedPlatform == SocialPlatform.WEBSITE) {
                                    "Alamat Website Studio"
                                } else {
                                    "Username atau Link ${selectedPlatform.displayName}"
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                text = if (selectedPlatform == SocialPlatform.WEBSITE) {
                                    "studioanda.com atau https://studioanda.com"
                                } else {
                                    "${selectedPlatform.exampleHandle} atau ${selectedPlatform.baseUrl}${selectedPlatform.exampleHandle.trimStart('@')}"
                                },
                                color = Color(0xFF6B7280),
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (selectedPlatform == SocialPlatform.WEBSITE) Icons.Default.Link else Icons.Default.AlternateEmail,
                                contentDescription = null,
                                tint = StudioGold,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        isError = inputError != null,
                        supportingText = inputError?.let { err ->
                            { Text(err, color = CrimsonHeart, fontSize = 11.sp) }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("social_url_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )

                    // Live Official URL Preview Card for currently selected platform
                    when (liveResolution) {
                        is SocialResolutionResult.Success -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF064E3B).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Tujuan ${liveResolution.platform.displayName} Resmi:",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = liveResolution.officialUrl,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        is SocialResolutionResult.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CrimsonHeart.copy(alpha = 0.14f), RoundedCornerShape(12.dp))
                                    .border(1.dp, CrimsonHeart.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = liveResolution.message,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        null -> Unit
                    }

                    OutlinedTextField(
                        value = activeDisplayName,
                        onValueChange = { next ->
                            draftNames[selectedPlatform] = next
                        },
                        label = { Text("Nama Tampilan ${selectedPlatform.displayName}") },
                        placeholder = { Text("Contoh: PVS Studio Foto", color = Color(0xFF6B7280)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                tint = StudioGold,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("social_name_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors
                    )

                    val hasCurrentPlatformInput = activeHandleOrUrl.isNotBlank() ||
                        activeDisplayName.isNotBlank() ||
                        currentProfile.accountFor(selectedPlatform) != null

                    if (hasCurrentPlatformInput || currentProfile.isConfigured) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasCurrentPlatformInput) {
                                TextButton(
                                    onClick = {
                                        draftUrls[selectedPlatform] = ""
                                        draftNames[selectedPlatform] = ""
                                        inputError = null
                                        if (currentProfile.accountFor(selectedPlatform) != null) {
                                            onRemovePlatform(selectedPlatform)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("remove_single_social_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus ${selectedPlatform.displayName}",
                                        tint = Color(0xFFFCA5A5),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Hapus ${selectedPlatform.displayName}",
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            if (currentProfile.isConfigured) {
                                TextButton(
                                    onClick = onClearAll,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("clear_social_settings_btn")
                                ) {
                                    Text(
                                        text = "Matikan Semua",
                                        color = CrimsonHeart,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
                    color = Color(0xFF2C3240)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("Batal", color = Color(0xFF9CA3AF), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            val resolvedMap = mutableMapOf<SocialPlatform, SocialAccountEntry>()
                            for (platform in SocialPlatform.entries) {
                                val rawInput = draftUrls[platform]?.trim() ?: ""
                                if (rawInput.isNotEmpty()) {
                                    when (val check = SocialLinkHelper.resolve(platform, rawInput)) {
                                        is SocialResolutionResult.Error -> {
                                            selectedPlatform = platform
                                            inputError = check.message
                                            return@Button
                                        }
                                        is SocialResolutionResult.Success -> {
                                            val customName = draftNames[platform]?.trim() ?: ""
                                            resolvedMap[platform] = SocialAccountEntry(
                                                platform = platform,
                                                socialName = customName.ifBlank { check.suggestedDisplayName },
                                                socialUrl = check.officialUrl
                                            )
                                        }
                                    }
                                }
                            }
                            onSaveAccounts(vendorName, resolvedMap, selectedPlatform)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioGold),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_social_settings_btn")
                    ) {
                        Text("Simpan", color = Color(0xFF121316), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
