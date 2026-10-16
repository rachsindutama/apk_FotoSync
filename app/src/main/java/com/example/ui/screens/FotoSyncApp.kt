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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.runtime.Composable
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
import com.example.data.model.PhotoItemEntity
import com.example.ui.components.AddSessionDialog
import com.example.ui.components.ClientServerDialog
import com.example.ui.components.DownloadExportDialog
import com.example.ui.components.DriveBackupDialog
import com.example.ui.components.PhotoCard
import com.example.ui.components.PhotoCarouselDialog
import com.example.ui.components.SessionCard
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.DarkroomBase
import com.example.ui.theme.DarkroomSurface
import com.example.ui.theme.StudioGold
import com.example.ui.viewmodel.FotoSyncViewModel
import com.example.ui.viewmodel.PhotoFilter
import kotlinx.coroutines.launch

@Composable
fun FotoSyncApp(
    viewModel: FotoSyncViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allSessions by viewModel.allSessions.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val filteredPhotos by viewModel.filteredPhotos.collectAsState()
    val photoFilter by viewModel.photoFilter.collectAsState()
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
    val isScanningFolder by viewModel.isScanningFolder.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()

    var activeMainTab by remember { mutableIntStateOf(0) } // 0: Galeri Foto, 1: Folder Sesi
    var showDownloadDialog by remember { mutableStateOf(false) }
    var isSpeedDialOpen by remember { mutableStateOf(false) }

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

    // Storage Access Framework folder picker for manual storage path selection
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            viewModel.setCustomStorageUri(uri.toString())
            Toast.makeText(context, "Jalur penyimpanan manual berhasil diset!", Toast.LENGTH_LONG).show()
        }
    }

    // Counts
    val totalPhotos = remember(filteredPhotos) { filteredPhotos.size }
    val clientLikedCount = remember(filteredPhotos) { filteredPhotos.count { it.isClientLiked } }
    val photographerPickCount = remember(filteredPhotos) { filteredPhotos.count { it.isPhotographerSelected } }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = DarkroomBase,
        topBar = {
            TopStudioHeader(
                activeSessionName = activeSession?.folderName ?: "Aplikasi seleksi dan sinkronisasi foto real-time",
                clientName = activeSession?.clientName ?: "Pilih Sesi",
                isServerRunning = isServerRunning
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
                onOpenBackup = {
                    isSpeedDialOpen = false
                    viewModel.openBackupDialog()
                },
                onOpenDownload = {
                    isSpeedDialOpen = false
                    showDownloadDialog = true
                },
                onSelectStorageFolder = {
                    isSpeedDialOpen = false
                    folderPickerLauncher.launch(null)
                },
                onImportPhotos = {
                    isSpeedDialOpen = false
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSyncFolder = {
                    isSpeedDialOpen = false
                    activeSession?.let { session ->
                        viewModel.triggerManualFolderSync(session.id) { count ->
                            if (count > 0) {
                                Toast.makeText(context, "$count berkas baru disinkronkan dari folder fisik!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Folder sudah tersinkronkan (tidak ada berkas baru)", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } ?: run {
                        Toast.makeText(context, "Pilih atau buat sesi terlebih dahulu", Toast.LENGTH_SHORT).show()
                    }
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
                    searchQuery = searchQuery,
                    totalPhotos = totalPhotos,
                    clientLikedCount = clientLikedCount,
                    photographerPickCount = photographerPickCount,
                    onSetFilter = { viewModel.setPhotoFilter(it) },
                    onSearchQueryChanged = { viewModel.setSearchQuery(it) },
                    onPhotoClick = { viewModel.openPhotoDetail(it) },
                    onToggleClientLike = { id, currentVal -> viewModel.toggleClientLiked(id, currentVal) },
                    onTogglePhotographerSelect = { id, currentVal -> viewModel.togglePhotographerSelected(id, currentVal) },
                    onDownloadPhoto = { photo ->
                        scope.launch {
                            val file = viewModel.downloadPhotoToDevice(context, photo)
                            if (file != null) {
                                Toast.makeText(context, "Foto ${photo.fileName} berhasil disimpan di folder Download!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Gagal mengunduh foto", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onOpenAddSession = { viewModel.openAddSessionDialog() },
                    onOpenDownload = { showDownloadDialog = true },
                    onImportPhotos = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onSyncFolder = {
                        activeSession?.let { session ->
                            viewModel.triggerManualFolderSync(session.id) { count ->
                                if (count > 0) {
                                    Toast.makeText(context, "$count berkas baru disinkronkan dari folder!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Folder sudah tersinkronkan (tidak ada berkas baru)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    isScanningFolder = isScanningFolder
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
            allPhotos = filteredPhotos,
            onDismiss = { showDownloadDialog = false }
        )
    }

    if (showServerDialog) {
        ClientServerDialog(
            session = activeSession,
            isServerRunning = isServerRunning,
            serverUrl = serverUrl,
            likesCount = likesNotification,
            onToggleServer = { viewModel.toggleServer() },
            onDismiss = { viewModel.closeServerDialog() }
        )
    }

    if (showBackupDialog) {
        DriveBackupDialog(
            session = activeSession,
            allPhotos = filteredPhotos,
            backupState = backupState,
            onConfirmBackup = { onlyLiked -> viewModel.executeGoogleDriveBackup(onlyLiked) },
            onDismiss = { viewModel.closeBackupDialog() }
        )
    }

    if (showAddSessionDialog) {
        AddSessionDialog(
            onDismiss = { viewModel.closeAddSessionDialog() },
            onCreateSession = { date, client, cat, notes ->
                viewModel.createSession(date, client, cat, notes)
                activeMainTab = 0
            }
        )
    }

    if (selectedPhotoForDetail != null) {
        PhotoCarouselDialog(
            photos = filteredPhotos,
            initialPhotoId = selectedPhotoForDetail!!.id,
            onDismiss = { viewModel.closePhotoDetail() },
            onToggleClientLiked = { id, cur -> viewModel.toggleClientLiked(id, cur) },
            onTogglePhotographerSelected = { id, cur -> viewModel.togglePhotographerSelected(id, cur) },
            onUpdateRating = { id, rating -> viewModel.updatePhotoRating(id, rating) },
            onDeletePhoto = { id -> viewModel.deletePhoto(id) },
            onDownloadPhoto = { photo ->
                scope.launch {
                    val file = viewModel.downloadPhotoToDevice(context, photo)
                    if (file != null) {
                        Toast.makeText(context, "Foto ${photo.fileName} berhasil disimpan di folder Download!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Gagal mengunduh foto", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
fun TopStudioHeader(
    activeSessionName: String,
    clientName: String,
    isServerRunning: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkroomSurface)
            .border(1.dp, Color(0xFF2C3240))
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                Text(
                    text = if (clientName.isNotEmpty() && clientName != "Pilih Sesi") clientName else activeSessionName,
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Live Server Status Indicator (No buttons in header)
        if (isServerRunning) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF064E3B), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0xFF10B981), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF34D399), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "QR Aktif",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
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
    onOpenBackup: () -> Unit,
    onOpenDownload: () -> Unit,
    onSelectStorageFolder: () -> Unit,
    onImportPhotos: () -> Unit,
    onSyncFolder: () -> Unit
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
                // 1. QR Portal Klien
                SpeedDialActionItem(
                    label = if (isServerRunning) "QR Klien Aktif (Buka)" else "Buka QR Portal Klien",
                    icon = Icons.Default.QrCode,
                    containerColor = if (isServerRunning) CrimsonHeart else Color(0xFF10B981),
                    badgeText = if (likesCount > 0) "$likesCount like" else null,
                    testTag = "speed_dial_qr_portal",
                    onClick = onOpenServer
                )

                // 2. Pindai Berkas Folder Fisik (Tersedia jika ada sesi aktif)
                if (hasActiveSession) {
                    SpeedDialActionItem(
                        label = "Pindai File Folder Fisik",
                        icon = Icons.Default.Refresh,
                        containerColor = Color(0xFF059669),
                        testTag = "speed_dial_sync_folder",
                        onClick = onSyncFolder
                    )
                }

                // 3. Impor Foto (tersedia jika ada sesi aktif)
                if (hasActiveSession) {
                    SpeedDialActionItem(
                        label = "Impor Foto ke Sesi",
                        icon = Icons.Default.AddPhotoAlternate,
                        containerColor = CobaltBlue,
                        testTag = "speed_dial_import_photos",
                        onClick = onImportPhotos
                    )
                }

                // 3. Unduh & Ekspor
                SpeedDialActionItem(
                    label = "Unduh & Ekspor Foto (ZIP)",
                    icon = Icons.Default.Download,
                    containerColor = Color(0xFF2563EB),
                    testTag = "speed_dial_download",
                    onClick = onOpenDownload
                )

                // 4. Folder Penyimpanan
                SpeedDialActionItem(
                    label = "Jalur Penyimpanan SAF",
                    icon = Icons.Default.Folder,
                    containerColor = Color(0xFFD97706),
                    testTag = "speed_dial_storage_folder",
                    onClick = onSelectStorageFolder
                )

                // 5. Cadangan Google Drive
                SpeedDialActionItem(
                    label = "Cadangan Google Drive",
                    icon = Icons.Default.CloudUpload,
                    containerColor = CobaltBlue,
                    testTag = "speed_dial_google_drive",
                    onClick = onOpenBackup
                )

                // 6. Buat Sesi Baru
                SpeedDialActionItem(
                    label = "Buat Sesi Baru",
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
    searchQuery: String,
    totalPhotos: Int,
    clientLikedCount: Int,
    photographerPickCount: Int,
    isScanningFolder: Boolean = false,
    onSetFilter: (PhotoFilter) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onPhotoClick: (com.example.data.model.PhotoItemEntity) -> Unit,
    onToggleClientLike: (String, Boolean) -> Unit,
    onTogglePhotographerSelect: (String, Boolean) -> Unit,
    onDownloadPhoto: (com.example.data.model.PhotoItemEntity) -> Unit,
    onOpenAddSession: () -> Unit,
    onOpenDownload: () -> Unit,
    onImportPhotos: () -> Unit,
    onSyncFolder: () -> Unit
) {
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
                        text = "Buat folder sesi berbasis tanggal pertama Anda untuk mulai sinkronisasi foto.",
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = onOpenAddSession,
                        colors = ButtonDefaults.buttonColors(containerColor = StudioGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Buat Sesi Baru", color = Color(0xFF121316), fontWeight = FontWeight.Bold)
                    }
                }
            }
            return
        }

        // Active Session Subtitle Bar with Direct Download & Sync Action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF14161C))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = StudioGold,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = activeSession.folderName,
                    color = Color(0xFFE5E7EB),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "${photos.size} foto",
                    color = StudioGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                // Sync / Scan Physical Folder Action
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222630))
                        .border(1.dp, StudioGold.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable(onClick = onSyncFolder)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("gallery_sync_folder_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = StudioGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isScanningFolder) "Memindai..." else "Pindai Folder",
                            color = StudioGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Import Photos Action
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222630))
                        .border(1.dp, Color(0xFF374151), RoundedCornerShape(8.dp))
                        .clickable(onClick = onImportPhotos)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("gallery_import_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Impor",
                            color = Color(0xFFD1D5DB),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Prominent Download Button in Gallery Header
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CobaltBlue)
                        .clickable(onClick = onOpenDownload)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("gallery_download_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Unduh File",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Filter Chips Row (Semua, Disukai Klien, Pilihan Fotografer, Belum Direview)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChipItem(
                    label = "Semua Foto ($totalPhotos)",
                    isSelected = photoFilter == PhotoFilter.ALL,
                    onClick = { onSetFilter(PhotoFilter.ALL) }
                )
            }
            item {
                FilterChipItem(
                    label = "Disukai Klien ($clientLikedCount)",
                    isSelected = photoFilter == PhotoFilter.CLIENT_LIKED,
                    activeColor = CrimsonHeart,
                    onClick = { onSetFilter(PhotoFilter.CLIENT_LIKED) }
                )
            }
            item {
                FilterChipItem(
                    label = "Pilihan Fotografer ($photographerPickCount)",
                    isSelected = photoFilter == PhotoFilter.PHOTOGRAPHER_SELECTED,
                    activeColor = StudioGold,
                    onClick = { onSetFilter(PhotoFilter.PHOTOGRAPHER_SELECTED) }
                )
            }
            item {
                FilterChipItem(
                    label = "Belum Direview",
                    isSelected = photoFilter == PhotoFilter.UNREVIEWED,
                    onClick = { onSetFilter(PhotoFilter.UNREVIEWED) }
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
                        text = "Taruh berkas foto langsung ke folder /Pictures/FotoSync_Studio/${activeSession.folderName} lalu klik 'Pindai Folder', atau impor langsung dari galeri.",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onSyncFolder,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioGold),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("empty_sync_folder_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF121316))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isScanningFolder) "Memindai..." else "Pindai Folder", color = Color(0xFF121316), fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onImportPhotos,
                            colors = ButtonDefaults.buttonColors(containerColor = CobaltBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Impor Galeri", color = Color.White)
                        }
                    }
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
                            onClick = { onPhotoClick(photo) },
                            onToggleClientLike = { onToggleClientLike(photo.id, photo.isClientLiked) },
                            onTogglePhotographerSelect = { onTogglePhotographerSelect(photo.id, photo.isPhotographerSelected) },
                            onDownloadPhoto = { onDownloadPhoto(photo) }
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
    onDeleteSession: (String) -> Unit,
    onOpenAddSession: () -> Unit
) {
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
                        onDelete = { onDeleteSession(session.id) }
                    )
                }
            }
        }
    }
}
