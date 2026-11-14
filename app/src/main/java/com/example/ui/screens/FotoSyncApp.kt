package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GuestSummary
import com.example.data.model.PhotoItemEntity
import com.example.data.model.StudioSocialProfile
import com.example.ui.components.AddSessionDialog
import com.example.ui.components.ClientServerDialog
import com.example.ui.components.DownloadExportDialog
import com.example.ui.components.DriveBackupDialog
import com.example.ui.components.PhotoCard
import com.example.ui.components.PhotoCarouselDialog
import com.example.ui.components.SessionCard
import com.example.ui.components.StudioSocialSettingsDialog
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.DarkroomBase
import com.example.ui.theme.DarkroomSurface
import com.example.ui.theme.StudioGold
import com.example.ui.viewmodel.FotoSyncViewModel
import com.example.ui.viewmodel.PhotoFilter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FotoSyncApp(
    viewModel: FotoSyncViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allSessions by viewModel.allSessions.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val effectivePhotos by viewModel.effectivePhotos.collectAsState()
    val filteredPhotos by viewModel.filteredPhotos.collectAsState()
    val photoFilter by viewModel.photoFilter.collectAsState()
    val sessionGuests by viewModel.sessionGuests.collectAsState()
    val selectedGuestKey by viewModel.selectedGuestKey.collectAsState()
    val guestLikeCountsByPhotoId by viewModel.guestLikeCountsByPhotoId.collectAsState()
    val studioSocialProfile by viewModel.studioSocialProfile.collectAsState()
    val showSocialSettingsDialog by viewModel.showSocialSettingsDialog.collectAsState()
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val runningSessionIds by viewModel.runningSessionIds.collectAsState()
    val sessionServerUrls by viewModel.sessionServerUrls.collectAsState()
    val likesNotification by viewModel.clientLikesNotification.collectAsState()
    val clientConnectedEvent by viewModel.clientConnectedEvent.collectAsState()

    val showServerDialog by viewModel.showServerDialog.collectAsState()
    val showBackupDialog by viewModel.showBackupDialog.collectAsState()
    val showAddSessionDialog by viewModel.showAddSessionDialog.collectAsState()
    val selectedPhotoForDetail by viewModel.selectedPhotoForDetail.collectAsState()
    val backupState by viewModel.backupState.collectAsState()
    val exportLogs by viewModel.exportLogs.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()

    var activeMainTab by remember { mutableIntStateOf(0) } // 0: Galeri Foto, 1: Folder Sesi
    var showDownloadDialog by remember { mutableStateOf(false) }
    var isSpeedDialOpen by remember { mutableStateOf(false) }

    // Automatically sync storage folder whenever user returns to the app from other apps
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        activeSession?.let { session ->
            viewModel.triggerManualFolderSync(session.id) {}
        }
    }

    // Close speed dial on system back gesture
    BackHandler(enabled = isSpeedDialOpen) {
        isSpeedDialOpen = false
    }

    // Android Photo Picker for zero hardcoded files
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty() && activeSession != null) {
            viewModel.importPhotosFromUris(context, activeSession!!.id, uris)
            Toast.makeText(context, "${uris.size} foto berhasil diimpor ke sesi!", Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-dismiss live client connection banner after 4 seconds
    LaunchedEffect(clientConnectedEvent) {
        if (clientConnectedEvent != null) {
            delay(4000L)
            viewModel.dismissClientConnectedEvent()
        }
    }

    val totalPhotos = remember(effectivePhotos) { effectivePhotos.size }
    val selectedGuestSummary = remember(sessionGuests, selectedGuestKey) {
        sessionGuests.find { it.clientKey == selectedGuestKey }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = DarkroomBase,
        topBar = {
            TopStudioHeader(
                activeSession = activeSession,
                isServerRunning = isServerRunning,
                socialProfile = studioSocialProfile,
                onOpenSocialSettings = { viewModel.openSocialSettingsDialog() },
                onOpenDownload = { showDownloadDialog = true }
            )
        },
        floatingActionButton = {
            SpeedDialFab(
                isExpanded = isSpeedDialOpen,
                onToggle = { isSpeedDialOpen = !isSpeedDialOpen },
                isServerRunning = isServerRunning,
                likesCount = likesNotification,
                hasActiveSession = activeSession != null,
                onOpenAddSession = {
                    isSpeedDialOpen = false
                    viewModel.openAddSessionDialog()
                },
                onOpenServer = {
                    isSpeedDialOpen = false
                    viewModel.openServerDialog()
                },
                onOpenDownload = {
                    isSpeedDialOpen = false
                    showDownloadDialog = true
                },
                onOpenSocialSettings = {
                    isSpeedDialOpen = false
                    viewModel.openSocialSettingsDialog()
                },
                onImportPhotos = {
                    isSpeedDialOpen = false
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
            // Live Client Connection Alert Banner
            if (clientConnectedEvent != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF064E3B))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Klien terhubung via pemindaian QR code ($clientConnectedEvent)",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { viewModel.dismissClientConnectedEvent() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Main Navigation Tabs (Galeri Foto vs Folder Sesi Tanggal)
            TabRow(
                selectedTabIndex = activeMainTab,
                containerColor = DarkroomSurface,
                contentColor = StudioGold,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeMainTab]),
                        color = StudioGold,
                        height = 3.dp
                    )
                }
            ) {
                Tab(
                    selected = activeMainTab == 0,
                    onClick = { activeMainTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (activeMainTab == 0) StudioGold else Color(0xFF9CA3AF)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Galeri Foto",
                                fontWeight = if (activeMainTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
                Tab(
                    selected = activeMainTab == 1,
                    onClick = { activeMainTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (activeMainTab == 1) StudioGold else Color(0xFF9CA3AF)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Folder Tanggal (${allSessions.size})",
                                fontWeight = if (activeMainTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                )
            }

            if (activeMainTab == 0) {
                // PHOTO GALLERY VIEW
                PhotoGallerySection(
                    activeSession = activeSession,
                    photos = filteredPhotos,
                    photoFilter = photoFilter,
                    totalPhotos = totalPhotos,
                    sessionGuests = sessionGuests,
                    selectedGuestKey = selectedGuestKey,
                    guestLikeCountsByPhotoId = guestLikeCountsByPhotoId,
                    onSelectMainFilter = { viewModel.selectFilterAndClearGuest(it) },
                    onToggleGuestChip = { viewModel.toggleGuestChipFilter(it) },
                    onPhotoClick = { viewModel.openPhotoDetail(it) },
                    onOpenAddSession = { viewModel.openAddSessionDialog() }
                )
            } else {
                // SESSIONS & DATE FOLDERS VIEW
                SessionsListSection(
                    sessions = allSessions,
                    activeSessionId = activeSession?.id,
                    runningSessionIds = runningSessionIds,
                    sessionServerUrls = sessionServerUrls,
                    onSelectSession = { id ->
                        viewModel.selectSession(id)
                        activeMainTab = 0 // Switch to gallery
                    },
                    onToggleServer = { id ->
                        viewModel.toggleSessionServer(id)
                    },
                    onOpenServerDialog = { id ->
                        viewModel.openSessionServerDialog(id)
                    },
                    onDeleteSession = { id -> viewModel.deleteSession(id) },
                    onOpenAddSession = { viewModel.openAddSessionDialog() }
                )
            }
        }

        // Scrim overlay when speed dial is open
        if (isSpeedDialOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isSpeedDialOpen = false
                    }
            )
        }
    }
}

    // Modal Dialogs
    if (showDownloadDialog) {
        DownloadExportDialog(
            session = activeSession,
            allPhotos = effectivePhotos,
            selectedGuest = selectedGuestSummary,
            onDismiss = { showDownloadDialog = false }
        )
    }

    if (showServerDialog) {
        ClientServerDialog(
            session = activeSession,
            isServerRunning = isServerRunning,
            serverUrl = serverUrl,
            likesCount = likesNotification,
            socialProfile = studioSocialProfile,
            connectedGuests = sessionGuests,
            onOpenSocialSettings = { viewModel.openSocialSettingsDialog() },
            onToggleServer = { viewModel.toggleServer() },
            onDismiss = { viewModel.closeServerDialog() }
        )
    }

    if (showSocialSettingsDialog) {
        StudioSocialSettingsDialog(
            currentProfile = studioSocialProfile,
            onSaveAccounts = { vendorName, accountsMap, lastPlatform ->
                viewModel.saveStudioSocialAccounts(vendorName, accountsMap, lastPlatform)
                val cleanVendor = vendorName.trim()
                val count = accountsMap.size
                val msg = when {
                    count > 0 && cleanVendor.isNotEmpty() -> "$cleanVendor & $count akun sosmed disimpan!"
                    count > 0 -> "$count akun sosmed studio disimpan!"
                    cleanVendor.isNotEmpty() -> "Nama vendor \"$cleanVendor\" disimpan!"
                    else -> "Pengaturan vendor & sosmed diperbarui"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            },
            onRemovePlatform = { platform ->
                viewModel.removeStudioSocialPlatform(platform)
                Toast.makeText(context, "Akun ${platform.displayName} dihapus", Toast.LENGTH_SHORT).show()
            },
            onClearAll = {
                viewModel.clearStudioSocialProfile()
                Toast.makeText(context, "Semua gerbang wajib follow dinonaktifkan", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewModel.closeSocialSettingsDialog() }
        )
    }

    if (showBackupDialog) {
        DriveBackupDialog(
            session = activeSession,
            allPhotos = effectivePhotos,
            backupState = backupState,
            onConfirmBackup = { onlyLiked -> viewModel.executeGoogleDriveBackup(onlyLiked) },
            onDismiss = { viewModel.closeBackupDialog() }
        )
    }

    if (showAddSessionDialog) {
        AddSessionDialog(
            onDismiss = { viewModel.closeAddSessionDialog() },
            onCreateSession = { date, client, cat, notes, folderName, folderUri ->
                viewModel.createSession(date, client, cat, notes, folderName, folderUri)
                activeMainTab = 0
            }
        )
    }

    if (selectedPhotoForDetail != null) {
        PhotoCarouselDialog(
            photos = filteredPhotos,
            initialPhotoId = selectedPhotoForDetail!!.id,
            guestLikeCountsByPhotoId = guestLikeCountsByPhotoId,
            onDismiss = { viewModel.closePhotoDetail() }
        )
    }
}

@Composable
fun TopStudioHeader(
    activeSession: com.example.data.model.PhotoSessionEntity?,
    isServerRunning: Boolean = false,
    socialProfile: StudioSocialProfile = StudioSocialProfile(),
    onOpenSocialSettings: () -> Unit,
    onOpenDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkroomSurface)
            .border(1.dp, Color(0xFF2C3240))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App Brand & Session Indicator
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        Brush.linearGradient(listOf(StudioGold, Color(0xFFD48806))),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFF121316),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "FotoSync",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(StudioGold.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PRO",
                            color = StudioGold,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                // Nama folder tepat di bawah title brand (tanpa ikon folder, kontras rendah)
                Text(
                    text = activeSession?.folderName ?: "Belum ada sesi",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Right side: Studio Social Settings icon + Download button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onOpenSocialSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("top_bar_social_btn")
            ) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = "Atur Vendor & Sosmed Studio",
                        tint = if (socialProfile.hasAnyIdentityOrSocial) StudioGold else Color(0xFF9CA3AF),
                        modifier = Modifier.size(22.dp)
                    )
                    if (socialProfile.isConfigured) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                    }
                }
            }

            // Tombol Unduh di kanan atas
            if (activeSession != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CobaltBlue)
                        .clickable(onClick = onOpenDownload)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("top_bar_download_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Unduh File",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Unduh",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedDialFab(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isServerRunning: Boolean,
    likesCount: Int,
    hasActiveSession: Boolean,
    onOpenAddSession: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenDownload: () -> Unit,
    onOpenSocialSettings: () -> Unit,
    onImportPhotos: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 45f else 0f,
        label = "fab_rotation"
    )

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                // Impor Foto (tersedia jika ada sesi aktif)
                if (hasActiveSession) {
                    SpeedDialActionItem(
                        label = "Impor Foto ke Sesi",
                        icon = Icons.Default.AddPhotoAlternate,
                        containerColor = CobaltBlue,
                        testTag = "speed_dial_import_photos",
                        onClick = onImportPhotos
                    )
                }

                // Unduh & Ekspor
                SpeedDialActionItem(
                    label = "Unduh & Ekspor Foto (ZIP)",
                    icon = Icons.Default.Download,
                    containerColor = Color(0xFF2563EB),
                    testTag = "speed_dial_download",
                    onClick = onOpenDownload
                )

                // Pengaturan Sosmed & Web Studio
                SpeedDialActionItem(
                    label = "Sosmed & Web Studio",
                    icon = Icons.Default.Public,
                    containerColor = Color(0xFF059669),
                    testTag = "speed_dial_social_settings",
                    onClick = onOpenSocialSettings
                )

                // Publish Folder Sebagai Sesi Baru
                SpeedDialActionItem(
                    label = "Publish Folder Baru",
                    icon = Icons.Default.CreateNewFolder,
                    containerColor = StudioGold,
                    contentColor = Color(0xFF121316),
                    testTag = "speed_dial_add_session",
                    onClick = onOpenAddSession
                )
            }
        }

        // Primary Speed Dial Floating Action Button
        FloatingActionButton(
            onClick = onToggle,
            containerColor = if (isServerRunning) CrimsonHeart else StudioGold,
            contentColor = if (isServerRunning) Color.White else Color(0xFF121316),
            shape = CircleShape,
            modifier = Modifier
                .size(56.dp)
                .testTag("primary_speed_dial_fab")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (isExpanded) "Tutup Menu Aksi" else "Buka Menu Aksi",
                modifier = Modifier
                    .size(28.dp)
                    .rotate(rotation)
            )
        }
    }
}

@Composable
fun SpeedDialActionItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color = Color.White,
    badgeText: String? = null,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        // Label pill
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = DarkroomSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C3240)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (badgeText != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(CrimsonHeart, CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Mini FloatingActionButton
        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            shape = CircleShape,
            modifier = Modifier
                .size(44.dp)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PhotoGallerySection(
    activeSession: com.example.data.model.PhotoSessionEntity?,
    photos: List<com.example.data.model.PhotoItemEntity>,
    photoFilter: PhotoFilter,
    totalPhotos: Int,
    sessionGuests: List<GuestSummary> = emptyList(),
    selectedGuestKey: String? = null,
    guestLikeCountsByPhotoId: Map<String, Int> = emptyMap(),
    onSelectMainFilter: (PhotoFilter) -> Unit,
    onToggleGuestChip: (String) -> Unit = {},
    onPhotoClick: (com.example.data.model.PhotoItemEntity) -> Unit,
    onOpenAddSession: () -> Unit
) {
    val activeLikedGuests = remember(sessionGuests) {
        sessionGuests.filter { it.likedCount > 0 }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (activeSession == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = StudioGold,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "Belum Ada Sesi Pemotretan",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pilih folder foto yang sudah ada di memori perangkat untuk mulai mempublikasikan dan menyinkronkan foto.",
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = onOpenAddSession,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Pilih Folder & Publish", color = Color(0xFF121316), fontWeight = FontWeight.Bold)
                    }
                }
            }
            return
        }

        // Single Compact Filter Row: Semua Foto, Disukai Guest (Total Unik), + IP Tamu Penyuka
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "filter_all") {
                FilterChipItem(
                    label = "Semua Foto ($totalPhotos)",
                    isSelected = photoFilter == PhotoFilter.ALL && selectedGuestKey == null,
                    onClick = { onSelectMainFilter(PhotoFilter.ALL) }
                )
            }
            item(key = "filter_liked") {
                FilterChipItem(
                    label = "Disukai Guest",
                    isSelected = photoFilter == PhotoFilter.CLIENT_LIKED && selectedGuestKey == null,
                    activeColor = CrimsonHeart,
                    onClick = { onSelectMainFilter(PhotoFilter.CLIENT_LIKED) }
                )
            }
            items(activeLikedGuests, key = { it.clientKey }) { guest ->
                FilterChipItem(
                    label = "${guest.clientIp} (${guest.likedCount})",
                    isSelected = selectedGuestKey == guest.clientKey && photoFilter == PhotoFilter.CLIENT_LIKED,
                    activeColor = CobaltBlue,
                    onClick = { onToggleGuestChip(guest.clientKey) }
                )
            }
        }

        // Photo Grid
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        tint = Color(0xFF4B5563),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Belum Ada Foto pada Sesi Ini",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Foto yang Anda salin langsung ke folder penyimpanan dari aplikasi lain akan otomatis langsung dibaca dan muncul di sini.",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = if (maxWidth > 600.dp) 3 else 2

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 90.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(photos, key = { it.id }) { photo ->
                        PhotoCard(
                            photo = photo,
                            guestLikeCount = guestLikeCountsByPhotoId[photo.id] ?: if (photo.isClientLiked) 1 else 0,
                            onClick = { onPhotoClick(photo) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChipItem(
    label: String,
    isSelected: Boolean,
    activeColor: Color = StudioGold,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) activeColor else Color(0xFF222630))
            .border(
                1.dp,
                if (isSelected) activeColor else Color(0xFF2C3240),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) (if (activeColor == StudioGold) Color(0xFF121316) else Color.White) else Color(0xFFD1D5DB),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun SessionsListSection(
    sessions: List<com.example.data.model.PhotoSessionEntity>,
    activeSessionId: String?,
    runningSessionIds: Set<String>,
    sessionServerUrls: Map<String, String>,
    onSelectSession: (String) -> Unit,
    onToggleServer: (String) -> Unit,
    onOpenServerDialog: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenAddSession: () -> Unit
) {
    var sessionToDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.example.data.model.PhotoSessionEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Daftar Folder",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Urutan berdasarkan tanggal",
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onOpenAddSession,
                colors = ButtonDefaults.buttonColors(containerColor = StudioGold),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    tint = Color(0xFF121316),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFF4B5563),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Belum Ada Folder Sesi",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Kelola foto Anda berdasarkan tanggal pemotretan untuk kemudahan pengarsipan.",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isRunning = runningSessionIds.contains(session.id)
                    val url = sessionServerUrls[session.id]
                    SessionCard(
                        session = session,
                        isSelected = session.id == activeSessionId,
                        isServerRunning = isRunning,
                        serverUrl = url,
                        onSelect = { onSelectSession(session.id) },
                        onToggleServer = { onToggleServer(session.id) },
                        onOpenServerDialog = { onOpenServerDialog(session.id) },
                        onDelete = { sessionToDelete = session }
                    )
                }
            }
        }
    }

    if (sessionToDelete != null) {
        val s = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            containerColor = DarkroomSurface,
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = CrimsonHeart,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Hapus Sesi \"${s.clientName}\"?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Sesi ini hanya akan dihapus dari daftar FotoSync. Folder dan file foto asli di memori perangkat (${s.folderName}) tetap aman dan tidak akan dihapus.",
                    color = Color(0xFFD1D5DB),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = s.id
                        sessionToDelete = null
                        onDeleteSession(id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonHeart),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Hapus Sesi", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { sessionToDelete = null }) {
                    Text("Batal", color = Color(0xFF9CA3AF))
                }
            }
        )
    }
}
