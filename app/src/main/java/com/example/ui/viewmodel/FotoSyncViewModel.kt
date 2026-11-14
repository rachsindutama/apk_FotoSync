package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FotoSyncDatabase
import com.example.data.model.ExportLogEntity
import com.example.data.model.GuestIdentity
import com.example.data.model.GuestSelectionEntity
import com.example.data.model.GuestSummary
import com.example.data.model.PhotoItemEntity
import com.example.data.model.PhotoSessionEntity
import com.example.data.model.SocialAccountEntry
import com.example.data.model.SocialLinkHelper
import com.example.data.model.SocialPlatform
import com.example.data.model.SocialResolutionResult
import com.example.data.model.StudioSocialProfile
import com.example.data.repository.FotoSyncRepository
import com.example.server.KtorLocalServer
import com.example.server.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class PhotoFilter {
    ALL,
    CLIENT_LIKED,
    PHOTOGRAPHER_SELECTED,
    UNREVIEWED
}

sealed class BackupState {
    object Idle : BackupState()
    data class Uploading(val progress: Float, val currentFile: String, val uploadedCount: Int, val totalCount: Int) : BackupState()
    data class Success(val count: Int, val totalBytes: Long, val folderName: String) : BackupState()
    data class Error(val message: String) : BackupState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class FotoSyncViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: FotoSyncRepository

    val allSessions: StateFlow<List<PhotoSessionEntity>>

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _photoFilter = MutableStateFlow(PhotoFilter.ALL)
    val photoFilter: StateFlow<PhotoFilter> = _photoFilter.asStateFlow()

    private val _categoryFilter = MutableStateFlow<String?>("Semua")
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val _dateFilter = MutableStateFlow<String?>(null)
    val dateFilter: StateFlow<String?> = _dateFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val prefs = application.getSharedPreferences("fotosync_prefs", Context.MODE_PRIVATE)
    private val _customStorageUri = MutableStateFlow(prefs.getString("custom_storage_uri", null))
    val customStorageUri: StateFlow<String?> = _customStorageUri.asStateFlow()

    fun setCustomStorageUri(uriString: String?) {
        _customStorageUri.value = uriString
        prefs.edit().putString("custom_storage_uri", uriString).apply()
    }

    // Global Studio Vendor & Social / Website Follow Gate Profiles (independent per platform)
    private val _studioSocialProfile = MutableStateFlow(loadStudioSocialProfile())
    val studioSocialProfile: StateFlow<StudioSocialProfile> = _studioSocialProfile.asStateFlow()

    private fun loadStudioSocialProfile(): StudioSocialProfile {
        val vendorName = prefs.getString("studio_vendor_name", "")?.trim() ?: ""
        val accounts = mutableMapOf<SocialPlatform, SocialAccountEntry>()
        for (platform in SocialPlatform.entries) {
            val url = prefs.getString("studio_social_${platform.name}_url", "")?.trim() ?: ""
            val name = prefs.getString("studio_social_${platform.name}_name", "")?.trim() ?: ""
            if (url.isNotEmpty()) {
                accounts[platform] = SocialAccountEntry(
                    platform = platform,
                    socialName = name,
                    socialUrl = url
                )
            }
        }

        // Migrate legacy single social entry if no per-platform entries exist yet
        if (accounts.isEmpty()) {
            val legacyUrl = prefs.getString("studio_social_url", "")?.trim() ?: ""
            if (legacyUrl.isNotEmpty()) {
                val legacyName = prefs.getString("studio_social_name", "")?.trim() ?: ""
                val legacyPlatform = SocialPlatform.fromNameOrNull(prefs.getString("studio_social_platform", null))
                    ?: SocialLinkHelper.detectPlatformFromUrl(legacyUrl)
                    ?: SocialPlatform.WEBSITE
                val entry = SocialAccountEntry(
                    platform = legacyPlatform,
                    socialName = legacyName,
                    socialUrl = legacyUrl
                )
                accounts[legacyPlatform] = entry
                prefs.edit()
                    .putString("studio_social_${legacyPlatform.name}_url", entry.socialUrl)
                    .putString("studio_social_${legacyPlatform.name}_name", entry.socialName)
                    .putString("studio_social_last_platform", legacyPlatform.name)
                    .remove("studio_social_platform")
                    .remove("studio_social_name")
                    .remove("studio_social_url")
                    .apply()
            }
        }

        val lastPlatform = SocialPlatform.fromNameOrNull(prefs.getString("studio_social_last_platform", null))
            ?: SocialPlatform.entries.firstOrNull { accounts.containsKey(it) }
            ?: SocialPlatform.INSTAGRAM

        return StudioSocialProfile(
            vendorName = vendorName,
            accounts = accounts.toMap(),
            lastEditedPlatform = lastPlatform
        )
    }

    private val _showSocialSettingsDialog = MutableStateFlow(false)
    val showSocialSettingsDialog: StateFlow<Boolean> = _showSocialSettingsDialog.asStateFlow()

    fun openSocialSettingsDialog() {
        _showSocialSettingsDialog.value = true
    }

    fun closeSocialSettingsDialog() {
        _showSocialSettingsDialog.value = false
    }

    fun saveStudioSocialAccounts(
        vendorName: String,
        resolvedAccounts: Map<SocialPlatform, SocialAccountEntry>,
        lastEditedPlatform: SocialPlatform
    ) {
        val cleanVendorName = vendorName.trim()
        val editor = prefs.edit()
        val sanitized = mutableMapOf<SocialPlatform, SocialAccountEntry>()

        if (cleanVendorName.isNotEmpty()) {
            editor.putString("studio_vendor_name", cleanVendorName)
        } else {
            editor.remove("studio_vendor_name")
        }

        for (platform in SocialPlatform.entries) {
            val entry = resolvedAccounts[platform]
            if (entry != null && entry.isConfigured) {
                sanitized[platform] = entry
                editor.putString("studio_social_${platform.name}_url", entry.socialUrl)
                editor.putString("studio_social_${platform.name}_name", entry.socialName)
            } else {
                editor.remove("studio_social_${platform.name}_url")
                editor.remove("studio_social_${platform.name}_name")
            }
        }

        editor.putString("studio_social_last_platform", lastEditedPlatform.name)
        editor.apply()

        _studioSocialProfile.value = StudioSocialProfile(
            vendorName = cleanVendorName,
            accounts = sanitized.toMap(),
            lastEditedPlatform = lastEditedPlatform
        )
        _showSocialSettingsDialog.value = false
    }

    fun removeStudioSocialPlatform(platform: SocialPlatform) {
        prefs.edit()
            .remove("studio_social_${platform.name}_url")
            .remove("studio_social_${platform.name}_name")
            .apply()

        val updatedAccounts = _studioSocialProfile.value.accounts - platform
        _studioSocialProfile.value = _studioSocialProfile.value.copy(
            accounts = updatedAccounts,
            lastEditedPlatform = platform
        )
    }

    fun clearStudioSocialProfile() {
        val editor = prefs.edit()
        for (platform in SocialPlatform.entries) {
            editor.remove("studio_social_${platform.name}_url")
            editor.remove("studio_social_${platform.name}_name")
        }
        editor
            .remove("studio_social_platform")
            .remove("studio_social_name")
            .remove("studio_social_url")
            .remove("studio_social_last_platform")
            .apply()

        // Preserve vendorName so header branding remains active even without follow gate
        _studioSocialProfile.value = StudioSocialProfile(
            vendorName = _studioSocialProfile.value.vendorName
        )
        _showSocialSettingsDialog.value = false
    }

    // Active session entity
    val activeSession: StateFlow<PhotoSessionEntity?>

    // Per-guest isolation state & reactive selectors
    private val _connectedGuestsBySession = MutableStateFlow<Map<String, Map<String, GuestIdentity>>>(emptyMap())
    private val _selectedGuestKey = MutableStateFlow<String?>(null)
    val selectedGuestKey: StateFlow<String?> = _selectedGuestKey.asStateFlow()

    val sessionGuestSelections: StateFlow<List<GuestSelectionEntity>>
    val sessionGuests: StateFlow<List<GuestSummary>>
    val guestLikeCountsByPhotoId: StateFlow<Map<String, Int>>

    // Photos for active session (effectivePhotos respects selectedGuestKey)
    val rawPhotos: StateFlow<List<PhotoItemEntity>>
    val effectivePhotos: StateFlow<List<PhotoItemEntity>>
    val filteredPhotos: StateFlow<List<PhotoItemEntity>>

    // Ktor CIO Server State
    private var ktorServer: KtorLocalServer? = null
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow("")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _clientLikesNotification = MutableStateFlow(0)
    val clientLikesNotification: StateFlow<Int> = _clientLikesNotification.asStateFlow()

    // Multi-session concurrent server maps & states
    private val sessionServers = mutableMapOf<String, KtorLocalServer>()
    private val _runningSessionIds = MutableStateFlow<Set<String>>(emptySet())
    val runningSessionIds: StateFlow<Set<String>> = _runningSessionIds.asStateFlow()

    private val _sessionServerUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val sessionServerUrls: StateFlow<Map<String, String>> = _sessionServerUrls.asStateFlow()

    private val _sessionServerPorts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val sessionServerPorts: StateFlow<Map<String, Int>> = _sessionServerPorts.asStateFlow()

    private val _clientConnectedEvent = MutableStateFlow<String?>(null)
    val clientConnectedEvent: StateFlow<String?> = _clientConnectedEvent.asStateFlow()

    // Backup & Export Dialog State
    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _showBackupDialog = MutableStateFlow(false)
    val showBackupDialog: StateFlow<Boolean> = _showBackupDialog.asStateFlow()

    // Modals
    private val _showServerDialog = MutableStateFlow(false)
    val showServerDialog: StateFlow<Boolean> = _showServerDialog.asStateFlow()

    private val _showAddSessionDialog = MutableStateFlow(false)
    val showAddSessionDialog: StateFlow<Boolean> = _showAddSessionDialog.asStateFlow()

    private val _selectedPhotoForDetail = MutableStateFlow<PhotoItemEntity?>(null)
    val selectedPhotoForDetail: StateFlow<PhotoItemEntity?> = _selectedPhotoForDetail.asStateFlow()

    // Export logs for current session
    val exportLogs: StateFlow<List<ExportLogEntity>>

    private val activeFileObservers: MutableList<FileObserver> = java.util.Collections.synchronizedList(mutableListOf())
    private var activeContentObserver: ContentObserver? = null
    private val syncMutex = Mutex()
    @Volatile
    private var lastSyncSessionId: String? = null
    @Volatile
    private var lastSyncTimestampMs: Long = 0L

    private val thumbCache = object : LruCache<String, ByteArray>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    init {
        val database = FotoSyncDatabase.getInstance(application)
        repository = FotoSyncRepository(database.dao(), application)

        allSessions = repository.allSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Auto select first session when available
        viewModelScope.launch {
            allSessions.collect { list ->
                if (_activeSessionId.value == null && list.isNotEmpty()) {
                    _activeSessionId.value = list.first().id
                }
            }
        }

        // Automatically observe and silently sync physical folder whenever active session changes
        viewModelScope.launch {
            _activeSessionId.collect { id ->
                if (id != null) {
                    val session = repository.getSessionSync(id)
                    if (session != null) {
                        startObservingSession(session)
                    }
                    syncSessionFolder(id, silent = true)
                } else {
                    stopObservingSession()
                }
            }
        }

        activeSession = _activeSessionId.flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.allSessions.flatMapLatest { sessions ->
                flowOf(sessions.find { it.id == id })
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

        rawPhotos = _activeSessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getPhotosForSession(id)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        sessionGuestSelections = _activeSessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getGuestSelectionsForSession(id)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        sessionGuests = combine(
            _activeSessionId,
            _connectedGuestsBySession,
            sessionGuestSelections
        ) { sessionId, connectedMap, selections ->
            if (sessionId == null) return@combine emptyList()
            val connectedForSession = connectedMap[sessionId] ?: emptyMap()
            val selectionsByKey = selections.groupBy { it.clientKey }
            val allKeys = (connectedForSession.keys + selectionsByKey.keys).distinct()

            allKeys.map { key ->
                val connected = connectedForSession[key]
                val guestSelections = selectionsByKey[key].orEmpty()
                val firstSel = guestSelections.firstOrNull()
                GuestSummary(
                    clientKey = key,
                    clientIp = connected?.clientIp ?: firstSel?.clientIp ?: key.substringBefore("::"),
                    deviceLabel = connected?.deviceLabel ?: firstSel?.deviceLabel ?: "Perangkat Tamu",
                    likedCount = guestSelections.size,
                    isConnectedNow = connected != null
                )
            }.sortedWith(
                compareByDescending<GuestSummary> { it.likedCount }
                    .thenByDescending { it.isConnectedNow }
                    .thenBy { it.clientIp }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        guestLikeCountsByPhotoId = combine(
            sessionGuestSelections,
            _selectedGuestKey
        ) { selections, selectedGuest ->
            if (selectedGuest != null) {
                selections
                    .filter { it.clientKey == selectedGuest }
                    .associate { it.photoId to 1 }
            } else {
                selections
                    .groupBy { it.photoId }
                    .mapValues { (_, list) -> list.distinctBy { it.clientKey }.size }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

        effectivePhotos = combine(
            rawPhotos,
            sessionGuestSelections,
            _selectedGuestKey
        ) { photos, selections, selectedGuest ->
            if (selectedGuest != null) {
                val guestLikedIds = selections
                    .filter { it.clientKey == selectedGuest }
                    .mapTo(HashSet()) { it.photoId }
                photos.map { photo ->
                    photo.copy(isClientLiked = guestLikedIds.contains(photo.id))
                }
            } else {
                val allGuestLikedIds = selections.mapTo(HashSet()) { it.photoId }
                photos.map { photo ->
                    val liked = photo.isClientLiked || allGuestLikedIds.contains(photo.id)
                    if (liked == photo.isClientLiked) photo else photo.copy(isClientLiked = liked)
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        filteredPhotos = combine(
            effectivePhotos,
            _photoFilter,
            _searchQuery
        ) { photos, filter, query ->
            photos.filter { photo ->
                val matchesFilter = when (filter) {
                    PhotoFilter.ALL -> true
                    PhotoFilter.CLIENT_LIKED -> photo.isClientLiked
                    PhotoFilter.PHOTOGRAPHER_SELECTED -> photo.isPhotographerSelected
                    PhotoFilter.UNREVIEWED -> !photo.isClientLiked
                }
                val matchesQuery = if (query.isBlank()) true else {
                    photo.fileName.contains(query, ignoreCase = true)
                }
                matchesFilter && matchesQuery
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        exportLogs = _activeSessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getExportLogs(id)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        setupKtorServer()
    }

    private fun readPhotoBytes(localUri: String): ByteArray? {
        if (localUri.isEmpty()) return null
        return try {
            if (localUri.startsWith("content://")) {
                getApplication<Application>().contentResolver.openInputStream(Uri.parse(localUri))?.use { it.readBytes() }
            } else {
                val file = File(localUri)
                if (file.exists() && file.isFile) file.readBytes() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readPhotoThumbBytes(photoId: String, localUri: String): ByteArray? {
        if (localUri.isEmpty()) return null
        thumbCache.get(photoId)?.let { return it }

        val contentResolver = getApplication<Application>().contentResolver
        fun openStream() = if (localUri.startsWith("content://")) {
            contentResolver.openInputStream(Uri.parse(localUri))
        } else {
            val file = File(localUri)
            if (file.exists() && file.isFile) FileInputStream(file) else null
        }

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return readPhotoBytes(localUri)
            }

            var sampleSize = 1
            val maxDim = 480
            while ((bounds.outWidth / sampleSize) > maxDim * 2 || (bounds.outHeight / sampleSize) > maxDim * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val bitmap = openStream()?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                ?: return readPhotoBytes(localUri)

            val bos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 78, bos)
            bitmap.recycle()
            val bytes = bos.toByteArray()
            thumbCache.put(photoId, bytes)
            bytes
        } catch (_: Exception) {
            readPhotoBytes(localUri)
        }
    }

    private fun registerGuestConnection(sessionId: String, guest: GuestIdentity) {
        val currentMap = _connectedGuestsBySession.value
        val sessionMap = currentMap[sessionId] ?: emptyMap()
        val isNewGuest = !sessionMap.containsKey(guest.clientKey)
        val updatedSessionMap = sessionMap + (guest.clientKey to guest)
        _connectedGuestsBySession.value = currentMap + (sessionId to updatedSessionMap)
        if (isNewGuest && guest.deviceId != "browser") {
            _clientConnectedEvent.value = "${guest.clientIp} • ${guest.deviceLabel}"
        }
    }

    private fun setupKtorServer() {
        ktorServer = KtorLocalServer(
            scope = viewModelScope,
            onClientConnected = { guest ->
                val sessionId = _activeSessionId.value ?: return@KtorLocalServer
                viewModelScope.launch {
                    registerGuestConnection(sessionId, guest)
                }
            },
            onLikeToggled = { photoId, isLiked, guest ->
                val sessionId = _activeSessionId.value ?: return@KtorLocalServer
                registerGuestConnection(sessionId, guest)
                repository.setGuestPhotoLiked(sessionId, photoId, guest, isLiked)
                _clientLikesNotification.value += 1
            },
            sessionProvider = {
                activeSession.value
            },
            photosProvider = {
                rawPhotos.value
            },
            guestLikedIdsProvider = { sessionId, guest ->
                withContext(Dispatchers.IO) {
                    repository.getGuestLikedPhotoIdsSync(sessionId, guest.clientKey)
                }
            },
            socialProfileProvider = {
                _studioSocialProfile.value
            },
            photoBytesProvider = { photoId ->
                withContext(Dispatchers.IO) {
                    val photo = rawPhotos.value.find { it.id == photoId }
                    if (photo != null) readPhotoBytes(photo.localUri) else null
                }
            },
            photoThumbBytesProvider = { photoId ->
                withContext(Dispatchers.IO) {
                    val photo = rawPhotos.value.find { it.id == photoId }
                    if (photo != null) readPhotoThumbBytes(photo.id, photo.localUri) else null
                }
            }
        )
    }

    fun dismissClientConnectedEvent() {
        _clientConnectedEvent.value = null
    }

    fun toggleSessionServer(sessionId: String) {
        if (_runningSessionIds.value.contains(sessionId)) {
            sessionServers[sessionId]?.stop()
            sessionServers.remove(sessionId)
            _runningSessionIds.value = _runningSessionIds.value - sessionId
            _sessionServerUrls.value = _sessionServerUrls.value - sessionId
            _sessionServerPorts.value = _sessionServerPorts.value - sessionId

            if (_activeSessionId.value == sessionId) {
                _isServerRunning.value = false
                _serverUrl.value = ""
            }
        } else {
            val usedPorts = _sessionServerPorts.value.values.toSet()
            var port = 8080
            while (usedPorts.contains(port) && port < 8200) {
                port++
            }

            val sessionServer = KtorLocalServer(
                scope = viewModelScope,
                onClientConnected = { guest ->
                    viewModelScope.launch {
                        registerGuestConnection(sessionId, guest)
                    }
                },
                onLikeToggled = { photoId, isLiked, guest ->
                    registerGuestConnection(sessionId, guest)
                    repository.setGuestPhotoLiked(sessionId, photoId, guest, isLiked)
                    _clientLikesNotification.value += 1
                },
                sessionProvider = {
                    withContext(Dispatchers.IO) { repository.getSessionSync(sessionId) }
                },
                photosProvider = {
                    withContext(Dispatchers.IO) { repository.getPhotosForSessionSync(sessionId) }
                },
                guestLikedIdsProvider = { sid, guest ->
                    withContext(Dispatchers.IO) {
                        repository.getGuestLikedPhotoIdsSync(sid, guest.clientKey)
                    }
                },
                socialProfileProvider = {
                    _studioSocialProfile.value
                },
                photoBytesProvider = { photoId ->
                    withContext(Dispatchers.IO) {
                        val photos = repository.getPhotosForSessionSync(sessionId)
                        val photo = photos.find { it.id == photoId }
                        if (photo != null) readPhotoBytes(photo.localUri) else null
                    }
                },
                photoThumbBytesProvider = { photoId ->
                    withContext(Dispatchers.IO) {
                        val photos = repository.getPhotosForSessionSync(sessionId)
                        val photo = photos.find { it.id == photoId }
                        if (photo != null) readPhotoThumbBytes(photo.id, photo.localUri) else null
                    }
                }
            )

            val started = sessionServer.start(port)
            if (started) {
                sessionServers[sessionId] = sessionServer
                _runningSessionIds.value = _runningSessionIds.value + sessionId
                val ip = NetworkUtils.getLocalIpAddress()
                val url = "http://$ip:$port/connect"
                _sessionServerUrls.value = _sessionServerUrls.value + (sessionId to url)
                _sessionServerPorts.value = _sessionServerPorts.value + (sessionId to port)

                if (_activeSessionId.value == sessionId) {
                    _isServerRunning.value = true
                    _serverUrl.value = url
                    ktorServer = sessionServer
                }
                _showServerDialog.value = true
            }
        }
    }

    fun toggleServer() {
        val activeId = _activeSessionId.value
        if (activeId != null) {
            toggleSessionServer(activeId)
        } else {
            val first = allSessions.value.firstOrNull()
            if (first != null) {
                toggleSessionServer(first.id)
            }
        }
    }

    fun openServerDialog() {
        if (!_isServerRunning.value) {
            toggleServer()
        } else {
            _showServerDialog.value = true
        }
    }

    fun openSessionServerDialog(sessionId: String) {
        val session = allSessions.value.find { it.id == sessionId }
        if (session != null) {
            selectSession(session.id)
        }
        val url = _sessionServerUrls.value[sessionId]
        if (url != null) {
            _serverUrl.value = url
            _isServerRunning.value = true
            ktorServer = sessionServers[sessionId]
            _showServerDialog.value = true
        } else {
            toggleSessionServer(sessionId)
        }
    }

    fun closeServerDialog() {
        _showServerDialog.value = false
    }

    private val _isScanningFolder = MutableStateFlow(false)
    val isScanningFolder: StateFlow<Boolean> = _isScanningFolder.asStateFlow()

    fun getSessionDirectory(session: PhotoSessionEntity): File {
        val picturesDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val studioDir = File(picturesDir, "FotoSync_Studio")
        val sessionDir = File(studioDir, session.folderName)
        if (!sessionDir.exists()) {
            sessionDir.mkdirs()
        }
        return sessionDir
    }

    fun getSessionDirectoryPath(session: PhotoSessionEntity): String {
        return getSessionDirectory(session).absolutePath
    }

    private fun startObservingSession(session: PhotoSessionEntity) {
        stopObservingSession()

        // If session is linked directly to an existing SAF folder in device memory, observe only that folder URI
        if (session.folderUri.isNotEmpty()) {
            try {
                val treeUri = Uri.parse(session.folderUri)
                val contentObs = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        viewModelScope.launch(Dispatchers.IO) {
                            delay(500L)
                            syncSessionFolder(session.id, silent = true)
                        }
                    }
                }
                getApplication<Application>().contentResolver.registerContentObserver(treeUri, true, contentObs)
                activeContentObserver = contentObs
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }

        // Legacy fallback observation for older sessions without folderUri
        val dirsToWatch = mutableListOf<File>()
        dirsToWatch.add(getSessionDirectory(session))
        val publicPictures = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "FotoSync_Studio/${session.folderName}"
        )
        if (publicPictures.exists()) dirsToWatch.add(publicPictures)
        val publicDcim = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "FotoSync_Studio/${session.folderName}"
        )
        if (publicDcim.exists()) dirsToWatch.add(publicDcim)

        for (dir in dirsToWatch.distinctBy { it.absolutePath }) {
            if (!dir.exists()) dir.mkdirs()
            try {
                val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    object : FileObserver(dir, CREATE or MOVED_TO or CLOSE_WRITE or DELETE) {
                        override fun onEvent(event: Int, path: String?) {
                            viewModelScope.launch(Dispatchers.IO) {
                                delay(300L)
                                syncSessionFolder(session.id, silent = true)
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    object : FileObserver(dir.absolutePath, CREATE or MOVED_TO or CLOSE_WRITE or DELETE) {
                        override fun onEvent(event: Int, path: String?) {
                            viewModelScope.launch(Dispatchers.IO) {
                                delay(300L)
                                syncSessionFolder(session.id, silent = true)
                            }
                        }
                    }
                }
                observer.startWatching()
                synchronized(activeFileObservers) {
                    activeFileObservers.add(observer)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val customUriStr = _customStorageUri.value
        if (!customUriStr.isNullOrEmpty()) {
            try {
                val treeUri = Uri.parse(customUriStr)
                val contentObs = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        viewModelScope.launch(Dispatchers.IO) {
                            delay(500L)
                            syncSessionFolder(session.id, silent = true)
                        }
                    }
                }
                getApplication<Application>().contentResolver.registerContentObserver(treeUri, true, contentObs)
                activeContentObserver = contentObs
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopObservingSession() {
        try {
            val toStop = synchronized(activeFileObservers) {
                val copy = ArrayList(activeFileObservers)
                activeFileObservers.clear()
                copy
            }
            toStop.forEach {
                try { it.stopWatching() } catch (_: Exception) { }
            }
        } catch (_: Exception) { }

        try {
            activeContentObserver?.let {
                try {
                    getApplication<Application>().contentResolver.unregisterContentObserver(it)
                } catch (_: Exception) { }
            }
            activeContentObserver = null
        } catch (_: Exception) { }
    }

    /**
     * Scans the published folder (per-session SAF folderUri or legacy paths) for new photos.
     * Uses a single bulk DocumentsContract ContentResolver query and batch DB inserts without
     * per-file BitmapFactory decoding so large folders load almost instantaneously.
     */
    suspend fun syncSessionFolder(sessionId: String, silent: Boolean = true): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (silent && sessionId == lastSyncSessionId && (now - lastSyncTimestampMs) < 2000L) {
            return@withContext 0
        }

        syncMutex.withLock {
            val lockTime = System.currentTimeMillis()
            if (silent && sessionId == lastSyncSessionId && (lockTime - lastSyncTimestampMs) < 2000L) {
                return@withLock 0
            }

            if (!silent) {
                _isScanningFolder.value = true
            }
            try {
                val session = repository.getSessionSync(sessionId) ?: return@withLock 0
                val existingPhotos = repository.getPhotosForSessionSync(sessionId)
                val existingPaths = existingPhotos.mapTo(HashSet(existingPhotos.size)) { it.localUri }
                val existingNames = existingPhotos.mapTo(HashSet(existingPhotos.size)) { it.fileName.lowercase() }
                val batchBuffer = ArrayList<PhotoItemEntity>(40)
                var totalAdded = 0
                val contentResolver = getApplication<Application>().contentResolver
                val supportedExtensions = setOf(
                    "jpg", "jpeg", "png", "webp", "dng", "heic", "heif",
                    "raw", "cr2", "cr3", "nef", "arw", "rw2", "orf"
                )

                suspend fun flushBatch(force: Boolean = false) {
                    if (batchBuffer.size >= 40 || (force && batchBuffer.isNotEmpty())) {
                        repository.insertPhotos(batchBuffer.toList())
                        batchBuffer.clear()
                    }
                }

                suspend fun scanSafTreeFast(treeUri: Uri, targetDocUri: Uri? = null) {
                    var usedBulkQuery = false
                    try {
                        val parentDocId = if (targetDocUri != null) {
                            DocumentsContract.getDocumentId(targetDocUri)
                        } else {
                            DocumentsContract.getTreeDocumentId(treeUri)
                        }
                        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                        val projection = arrayOf(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                            DocumentsContract.Document.COLUMN_SIZE,
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED
                        )
                        contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                            usedBulkQuery = true
                            val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                            val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                            val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                            val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                            val modCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                            while (cursor.moveToNext()) {
                                val docId = if (idCol != -1) cursor.getString(idCol) else null
                                if (docId.isNullOrEmpty()) continue
                                val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) else null
                                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue

                                val fileName = (if (nameCol != -1) cursor.getString(nameCol) else null)
                                    ?: "photo_${System.currentTimeMillis()}.jpg"
                                val ext = fileName.substringAfterLast('.', "").lowercase()
                                val isImageMime = mimeType?.startsWith("image/") == true
                                if (!isImageMime && ext !in supportedExtensions) continue

                                val fileUriStr = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                                val lowerName = fileName.lowercase()
                                if (fileUriStr in existingPaths || lowerName in existingNames) continue

                                existingPaths.add(fileUriStr)
                                existingNames.add(lowerName)

                                val fileSize = if (sizeCol != -1 && !cursor.isNull(sizeCol)) {
                                    cursor.getLong(sizeCol).coerceAtLeast(1024L)
                                } else {
                                    1024L
                                }
                                val capturedAt = if (modCol != -1 && !cursor.isNull(modCol)) {
                                    cursor.getLong(modCol).takeIf { it > 0L } ?: System.currentTimeMillis()
                                } else {
                                    System.currentTimeMillis()
                                }

                                batchBuffer.add(
                                    PhotoItemEntity(
                                        id = UUID.randomUUID().toString(),
                                        sessionId = sessionId,
                                        fileName = fileName,
                                        localUri = fileUriStr,
                                        fileSize = fileSize,
                                        resolution = "",
                                        isClientLiked = false,
                                        isPhotographerSelected = false,
                                        rating = 0,
                                        capturedAt = capturedAt
                                    )
                                )
                                totalAdded++
                                flushBatch(force = false)
                            }
                        }
                    } catch (_: Exception) {
                        usedBulkQuery = false
                    }

                    if (!usedBulkQuery) {
                        val fallbackDir = if (targetDocUri != null) {
                            androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), targetDocUri)
                        } else {
                            androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), treeUri)
                        } ?: return

                        for (docFile in fallbackDir.listFiles()) {
                            if (docFile.isFile) {
                                val fileName = docFile.name ?: "photo_${System.currentTimeMillis()}.jpg"
                                val ext = fileName.substringAfterLast('.', "").lowercase()
                                val isImageMime = docFile.type?.startsWith("image/") == true
                                if (!isImageMime && ext !in supportedExtensions) continue

                                val fileUriStr = docFile.uri.toString()
                                val lowerName = fileName.lowercase()
                                if (fileUriStr in existingPaths || lowerName in existingNames) continue

                                existingPaths.add(fileUriStr)
                                existingNames.add(lowerName)

                                batchBuffer.add(
                                    PhotoItemEntity(
                                        id = UUID.randomUUID().toString(),
                                        sessionId = sessionId,
                                        fileName = fileName,
                                        localUri = fileUriStr,
                                        fileSize = docFile.length().coerceAtLeast(1024L),
                                        resolution = "",
                                        isClientLiked = false,
                                        isPhotographerSelected = false,
                                        rating = 0,
                                        capturedAt = docFile.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                                    )
                                )
                                totalAdded++
                                flushBatch(force = false)
                            }
                        }
                    }
                }

                // 1. Primary path: Session linked directly to an existing SAF folder in device memory
                if (session.folderUri.isNotEmpty()) {
                    try {
                        val treeUri = Uri.parse(session.folderUri)
                        scanSafTreeFast(treeUri)
                        flushBatch(force = true)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    lastSyncSessionId = sessionId
                    lastSyncTimestampMs = System.currentTimeMillis()
                    return@withLock totalAdded
                }

                // 2. Legacy fallback: Scan global SAF directories if configured
                val customUriStr = _customStorageUri.value
                if (!customUriStr.isNullOrEmpty()) {
                    try {
                        val treeUri = Uri.parse(customUriStr)
                        val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), treeUri)
                        if (pickedDir != null && pickedDir.exists()) {
                            val candidateSafDirs = mutableListOf<androidx.documentfile.provider.DocumentFile>()
                            pickedDir.findFile("FotoSync_Studio")?.findFile(session.folderName)?.let { candidateSafDirs.add(it) }
                            pickedDir.findFile(session.folderName)?.let { candidateSafDirs.add(it) }
                            if (pickedDir.name.equals(session.folderName, ignoreCase = true) || candidateSafDirs.isEmpty()) {
                                candidateSafDirs.add(pickedDir)
                            }
                            for (dir in candidateSafDirs.distinctBy { it.uri.toString() }) {
                                scanSafTreeFast(treeUri, dir.uri)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // 3. Legacy fallback: Scan standard local directories
                val candidateLocalDirs = mutableListOf<File>()
                candidateLocalDirs.add(getSessionDirectory(session))

                val publicPictures = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FotoSync_Studio/${session.folderName}")
                if (publicPictures.exists()) candidateLocalDirs.add(publicPictures)

                val publicDcim = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "FotoSync_Studio/${session.folderName}")
                if (publicDcim.exists()) candidateLocalDirs.add(publicDcim)

                for (dir in candidateLocalDirs.distinctBy { it.absolutePath }) {
                    if (!dir.exists() || !dir.isDirectory) continue
                    val filesOnDisk = dir.listFiles { file ->
                        file.isFile && file.extension.lowercase() in supportedExtensions
                    } ?: emptyArray()

                    for (file in filesOnDisk) {
                        val lowerName = file.name.lowercase()
                        if (file.absolutePath in existingPaths || lowerName in existingNames) {
                            continue
                        }
                        existingPaths.add(file.absolutePath)
                        existingNames.add(lowerName)

                        batchBuffer.add(
                            PhotoItemEntity(
                                id = UUID.randomUUID().toString(),
                                sessionId = sessionId,
                                fileName = file.name,
                                localUri = file.absolutePath,
                                fileSize = file.length().coerceAtLeast(1024L),
                                resolution = "",
                                isClientLiked = false,
                                isPhotographerSelected = false,
                                rating = 0,
                                capturedAt = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                            )
                        )
                        totalAdded++
                        flushBatch(force = false)
                    }
                }

                flushBatch(force = true)
                lastSyncSessionId = sessionId
                lastSyncTimestampMs = System.currentTimeMillis()
                totalAdded
            } catch (e: Exception) {
                e.printStackTrace()
                0
            } finally {
                if (!silent) {
                    _isScanningFolder.value = false
                }
            }
        }
    }

    fun triggerManualFolderSync(sessionId: String, onResult: (newCount: Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = syncSessionFolder(sessionId, silent = true)
            withContext(Dispatchers.Main) {
                onResult(count)
            }
        }
    }

    fun selectSession(sessionId: String) {
        if (_activeSessionId.value != sessionId) {
            _selectedGuestKey.value = null
        }
        _activeSessionId.value = sessionId
    }

    fun setSelectedGuestFilter(clientKey: String?) {
        _selectedGuestKey.value = clientKey
    }

    fun selectFilterAndClearGuest(filter: PhotoFilter) {
        _selectedGuestKey.value = null
        _photoFilter.value = filter
    }

    fun toggleGuestChipFilter(clientKey: String) {
        if (_selectedGuestKey.value == clientKey && _photoFilter.value == PhotoFilter.CLIENT_LIKED) {
            _selectedGuestKey.value = null
            _photoFilter.value = PhotoFilter.CLIENT_LIKED
        } else {
            _selectedGuestKey.value = clientKey
            _photoFilter.value = PhotoFilter.CLIENT_LIKED
        }
    }

    fun selectAllPhotographerMarked(selectAll: Boolean) {
        val sessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.updateAllPhotographerSelected(sessionId, selectAll)
        }
    }

    /**
     * Deletes all photos marked by photographer/admin permanently from both physical storage and database.
     */
    fun deleteSelectedPhotosFromStorage(context: Context, onComplete: (count: Int) -> Unit = {}) {
        val currentSessionId = _activeSessionId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val allPhotos = repository.getPhotosForSessionSync(currentSessionId)
            val selectedPhotos = allPhotos.filter { it.isPhotographerSelected }
            if (selectedPhotos.isEmpty()) {
                withContext(Dispatchers.Main) { onComplete(0) }
                return@launch
            }

            val contentResolver = context.contentResolver
            for (photo in selectedPhotos) {
                try {
                    if (photo.localUri.startsWith("content://")) {
                        val uri = Uri.parse(photo.localUri)
                        val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                        if (doc != null && doc.exists()) {
                            doc.delete()
                        } else {
                            try { contentResolver.delete(uri, null, null) } catch (e: Exception) { }
                        }
                    } else if (photo.localUri.isNotEmpty()) {
                        val path = if (photo.localUri.startsWith("file://")) photo.localUri.removePrefix("file://") else photo.localUri
                        val f = File(path)
                        if (f.exists()) f.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val photoIds = selectedPhotos.map { it.id }
            repository.deletePhotos(photoIds)

            if (_selectedPhotoForDetail.value?.id in photoIds) {
                _selectedPhotoForDetail.value = null
            }

            withContext(Dispatchers.Main) {
                onComplete(selectedPhotos.size)
            }
        }
    }

    fun deletePhotoFromStorage(context: Context, photo: PhotoItemEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (photo.localUri.startsWith("content://")) {
                    val uri = Uri.parse(photo.localUri)
                    val doc = androidx.documentfile.provider.DocumentFile.fromSingleUri(context, uri)
                    if (doc != null && doc.exists()) {
                        doc.delete()
                    } else {
                        try { context.contentResolver.delete(uri, null, null) } catch (e: Exception) { }
                    }
                } else if (photo.localUri.isNotEmpty()) {
                    val path = if (photo.localUri.startsWith("file://")) photo.localUri.removePrefix("file://") else photo.localUri
                    val f = File(path)
                    if (f.exists()) f.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            repository.deletePhoto(photo.id)
            if (_selectedPhotoForDetail.value?.id == photo.id) {
                _selectedPhotoForDetail.value = null
            }
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    fun setPhotoFilter(filter: PhotoFilter) {
        _photoFilter.value = filter
    }

    fun setCategoryFilter(category: String?) {
        _categoryFilter.value = category
    }

    fun setDateFilter(date: String?) {
        _dateFilter.value = date
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleClientLiked(photoId: String, currentVal: Boolean) {
        viewModelScope.launch {
            repository.toggleClientLiked(photoId, !currentVal)
        }
    }

    fun togglePhotographerSelected(photoId: String, currentVal: Boolean) {
        viewModelScope.launch {
            repository.togglePhotographerSelected(photoId, !currentVal)
        }
    }

    fun updatePhotoRating(photoId: String, rating: Int) {
        viewModelScope.launch {
            repository.updatePhotoRating(photoId, rating)
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            repository.deletePhoto(photoId)
            if (_selectedPhotoForDetail.value?.id == photoId) {
                _selectedPhotoForDetail.value = null
            }
        }
    }

    /**
     * Removes the session entry from the app database only, leaving original folders and files
     * in device memory completely untouched and safe.
     */
    fun deleteSession(sessionId: String) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Stop Ktor server if running for this session
                if (_runningSessionIds.value.contains(sessionId)) {
                    sessionServers[sessionId]?.stop()
                    sessionServers.remove(sessionId)
                    _runningSessionIds.value = _runningSessionIds.value - sessionId
                    _sessionServerUrls.value = _sessionServerUrls.value - sessionId
                    _sessionServerPorts.value = _sessionServerPorts.value - sessionId
                }

                if (_activeSessionId.value == sessionId) {
                    stopObservingSession()
                    _isServerRunning.value = false
                    _serverUrl.value = ""
                    if (_selectedPhotoForDetail.value?.sessionId == sessionId) {
                        _selectedPhotoForDetail.value = null
                    }
                }

                // Delete session & photo index rows from Room DB only (Cascade deletes photo_items/export_logs rows)
                repository.deleteSession(sessionId)

                withContext(Dispatchers.Main) {
                    if (_activeSessionId.value == sessionId) {
                        _activeSessionId.value = allSessions.value.firstOrNull { it.id != sessionId }?.id
                    }
                    Toast.makeText(
                        context,
                        "Sesi dihapus dari daftar (file foto asli di memori tetap aman)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Gagal menghapus sesi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun openAddSessionDialog() {
        _showAddSessionDialog.value = true
    }

    fun closeAddSessionDialog() {
        _showAddSessionDialog.value = false
    }

    fun createSession(
        date: String,
        clientName: String,
        category: String,
        notes: String,
        folderName: String,
        folderUri: String
    ) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val created = repository.createSession(
                date = date,
                clientName = clientName,
                category = category,
                clientNotes = notes,
                initialPhotoCount = 0,
                folderName = folderName,
                folderUri = folderUri
            )
            _activeSessionId.value = created.id
            _showAddSessionDialog.value = false

            val scannedCount = syncSessionFolder(created.id, silent = false)
            Toast.makeText(
                context,
                "Folder \"${created.folderName}\" dipublikasikan ($scannedCount foto ditemukan)",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun addMorePhotos() {
        val currentSessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.addPhotosToSession(currentSessionId, 4)
        }
    }

    /**
     * Imports genuine images chosen from the device gallery or file manager
     * into the session's linked folder (or configured SAF storage fallback).
     */
    fun importPhotosFromUris(context: Context, sessionId: String, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = repository.getSessionSync(sessionId) ?: return@launch
            val contentResolver = context.contentResolver

            val sessionSubDir = if (session.folderUri.isNotEmpty()) {
                val treeUri = Uri.parse(session.folderUri)
                val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                if (pickedDir == null || !pickedDir.canWrite()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Izin tulis pada folder sesi tidak tersedia. Pilih ulang folder!", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                pickedDir
            } else {
                val customUriStr = _customStorageUri.value
                if (customUriStr.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Wajib memilih folder penyimpanan SAF terlebih dahulu!", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val treeUri = Uri.parse(customUriStr)
                val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                if (pickedDir == null || !pickedDir.canWrite()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Izin folder SAF tidak valid atau kedaluwarsa. Pilih ulang folder!", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val studioSubDir = pickedDir.findFile("FotoSync_Studio") ?: pickedDir.createDirectory("FotoSync_Studio") ?: pickedDir
                studioSubDir.findFile(session.folderName) ?: studioSubDir.createDirectory(session.folderName) ?: studioSubDir
            }

            val newPhotos = mutableListOf<PhotoItemEntity>()

            for ((idx, uri) in uris.withIndex()) {
                var fileName = "IMG_${System.currentTimeMillis()}_$idx.jpg"
                var fileSize = 0L

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) {
                                val name = cursor.getString(nameIndex)
                                if (!name.isNullOrBlank()) fileName = name
                            }
                            if (sizeIndex != -1) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try {
                    val targetDoc = sessionSubDir.createFile("image/jpeg", fileName)
                    if (targetDoc != null) {
                        contentResolver.openInputStream(uri)?.use { input ->
                            contentResolver.openOutputStream(targetDoc.uri)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (fileSize <= 0L) {
                            fileSize = targetDoc.length()
                        }

                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        contentResolver.openInputStream(targetDoc.uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, options)
                        }
                        val resolution = if (options.outWidth > 0 && options.outHeight > 0) {
                            "${options.outWidth} x ${options.outHeight}"
                        } else {
                            "4000 x 3000"
                        }

                        newPhotos.add(
                            PhotoItemEntity(
                                id = UUID.randomUUID().toString(),
                                sessionId = sessionId,
                                fileName = fileName,
                                localUri = targetDoc.uri.toString(),
                                fileSize = fileSize.coerceAtLeast(1024L),
                                resolution = resolution,
                                isClientLiked = false,
                                isPhotographerSelected = false,
                                rating = 0,
                                capturedAt = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (newPhotos.isNotEmpty()) {
                repository.insertPhotos(newPhotos)
            }
        }
    }

    /**
     * Downloads an individual photo directly to the device's public Download directory.
     */
    suspend fun downloadPhotoToDevice(context: Context, photo: PhotoItemEntity): File? = withContext(Dispatchers.IO) {
        try {
            val customUriStr = _customStorageUri.value
            if (!customUriStr.isNullOrEmpty()) {
                try {
                    val treeUri = Uri.parse(customUriStr)
                    val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                    if (pickedDir != null && pickedDir.canWrite()) {
                        val subDir = pickedDir.findFile("FotoSync_Studio") ?: pickedDir.createDirectory("FotoSync_Studio") ?: pickedDir
                        val newDoc = subDir.createFile("image/jpeg", photo.fileName)
                        if (newDoc != null) {
                            context.contentResolver.openOutputStream(newDoc.uri)?.use { out ->
                                val sourceInput = if (photo.localUri.startsWith("content://")) {
                                    try { context.contentResolver.openInputStream(Uri.parse(photo.localUri)) } catch(e:Exception){null}
                                } else {
                                    val f = File(photo.localUri)
                                    if (f.exists() && f.isFile) f.inputStream() else null
                                }

                                if (sourceInput != null) {
                                    sourceInput.use { it.copyTo(out) }
                                } else {
                                    val bitmap = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bitmap)
                                    canvas.drawColor(android.graphics.Color.rgb(18, 20, 26))
                                    val paint = Paint().apply {
                                        color = android.graphics.Color.rgb(245, 166, 35)
                                        textSize = 44f
                                        textAlign = Paint.Align.CENTER
                                        isAntiAlias = true
                                    }
                                    canvas.drawText("FotoSync Studio - ${photo.fileName}", 800f, 500f, paint)
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                    bitmap.recycle()
                                }
                            }
                            return@withContext File(context.cacheDir, photo.fileName)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "FotoSync_Studio"
            )
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val destFile = File(downloadDir, photo.fileName)
            val sourceFile = File(photo.localUri)
            if (sourceFile.exists() && sourceFile.isFile) {
                sourceFile.copyTo(destFile, overwrite = true)
            } else {
                val bitmap = Bitmap.createBitmap(1600, 1000, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.rgb(18, 20, 26))
                val paint = Paint().apply {
                    color = android.graphics.Color.rgb(245, 166, 35)
                    textSize = 44f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                canvas.drawText("FotoSync Studio - ${photo.fileName}", 800f, 500f, paint)
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                bitmap.recycle()
            }
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Downloads all or liked photos as a ZIP archive to the device's public Download directory.
     */
    suspend fun downloadZipToDevice(
        context: Context,
        session: PhotoSessionEntity,
        photos: List<PhotoItemEntity>,
        likedOnly: Boolean
    ): File? = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "FotoSync_Studio"
            )
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val prefix = session.folderName
            val zipName = if (likedOnly) "${prefix}_Favorit_Klien.zip" else "${prefix}_Semua_Foto.zip"
            val destZip = File(downloadDir, zipName)

            val targetPhotos = if (likedOnly) photos.filter { it.isClientLiked } else photos
            val list = if (targetPhotos.isEmpty()) photos else targetPhotos

            val zos = ZipOutputStream(FileOutputStream(destZip))
            for (photo in list) {
                val entry = ZipEntry(photo.fileName)
                zos.putNextEntry(entry)
                val photoBytes = readPhotoBytes(photo.localUri)
                if (photoBytes != null) {
                    zos.write(photoBytes)
                } else {
                    val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.rgb(18, 20, 26))
                    val paint = Paint().apply {
                        color = android.graphics.Color.rgb(245, 166, 35)
                        textSize = 36f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText("FotoSync Studio - ${photo.fileName}", 600f, 400f, paint)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, zos)
                    bitmap.recycle()
                }
                zos.closeEntry()
            }
            zos.finish()
            zos.close()
            destZip
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openPhotoDetail(photo: PhotoItemEntity) {
        _selectedPhotoForDetail.value = photo
    }

    fun closePhotoDetail() {
        _selectedPhotoForDetail.value = null
    }

    fun openBackupDialog() {
        _backupState.value = BackupState.Idle
        _showBackupDialog.value = true
    }

    fun closeBackupDialog() {
        _showBackupDialog.value = false
        _backupState.value = BackupState.Idle
    }

    fun executeGoogleDriveBackup(onlyLikedPhotos: Boolean) {
        val session = activeSession.value ?: return
        val currentPhotos = rawPhotos.value
        val targetPhotos = if (onlyLikedPhotos) {
            currentPhotos.filter { it.isClientLiked }
        } else {
            currentPhotos
        }

        if (targetPhotos.isEmpty()) {
            _backupState.value = BackupState.Error("Tidak ada foto yang memenuhi kriteria untuk diekspor.")
            return
        }

        viewModelScope.launch {
            val totalBytes = targetPhotos.sumOf { it.fileSize }
            val gDriveFolder = "Google Drive/FotoSync_Studio/${session.folderName}"

            val total = targetPhotos.size
            for (i in 1..total) {
                val photo = targetPhotos[i - 1]
                val progress = i.toFloat() / total.toFloat()
                _backupState.value = BackupState.Uploading(
                    progress = progress,
                    currentFile = photo.fileName,
                    uploadedCount = i,
                    totalCount = total
                )
                delay(220L)
            }

            repository.recordExportLog(
                sessionId = session.id,
                folderPath = gDriveFolder,
                totalPhotos = total,
                totalBytes = totalBytes,
                filterType = if (onlyLikedPhotos) "CLIENT_LIKED_ONLY" else "ALL"
            )

            _backupState.value = BackupState.Success(
                count = total,
                totalBytes = totalBytes,
                folderName = gDriveFolder
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopObservingSession()
        ktorServer?.stop()
        sessionServers.values.forEach { it.stop() }
        sessionServers.clear()
    }
}
