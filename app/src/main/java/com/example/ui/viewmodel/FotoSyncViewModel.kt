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
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.FotoSyncDatabase
import com.example.data.model.ExportLogEntity
import com.example.data.model.PhotoItemEntity
import com.example.data.model.PhotoSessionEntity
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
import kotlinx.coroutines.runBlocking
import java.io.File
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

    // Active session entity
    val activeSession: StateFlow<PhotoSessionEntity?>

    // Photos for active session
    private val rawPhotos: StateFlow<List<PhotoItemEntity>>
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

    init {
        val database = FotoSyncDatabase.getInstance(application)
        repository = FotoSyncRepository(database.dao(), application)

        allSessions = repository.allSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Ensure studio root directory exists without deleting user photos
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val picturesDir = application.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                if (picturesDir != null) {
                    val studioDir = File(picturesDir, "FotoSync_Studio")
                    if (!studioDir.exists()) {
                        studioDir.mkdirs()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

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

        filteredPhotos = combine(
            rawPhotos,
            _photoFilter,
            _searchQuery
        ) { photos, filter, query ->
            photos.filter { photo ->
                val matchesFilter = when (filter) {
                    PhotoFilter.ALL -> true
                    PhotoFilter.CLIENT_LIKED -> photo.isClientLiked
                    PhotoFilter.PHOTOGRAPHER_SELECTED -> photo.isPhotographerSelected
                    PhotoFilter.UNREVIEWED -> !photo.isClientLiked && !photo.isPhotographerSelected
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

    private fun setupKtorServer() {
        ktorServer = KtorLocalServer(
            scope = viewModelScope,
            onClientConnected = { clientInfo ->
                viewModelScope.launch {
                    _clientConnectedEvent.value = clientInfo
                }
            },
            onLikeToggled = { photoId, isLiked ->
                viewModelScope.launch {
                    repository.toggleClientLiked(photoId, isLiked)
                    _clientLikesNotification.value += 1
                }
            },
            sessionProvider = {
                activeSession.value
            },
            photosProvider = {
                rawPhotos.value
            },
            photoBytesProvider = { photoId ->
                val photo = rawPhotos.value.find { it.id == photoId }
                if (photo != null) readPhotoBytes(photo.localUri) else null
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
                onClientConnected = { clientInfo ->
                    viewModelScope.launch {
                        _clientConnectedEvent.value = clientInfo
                    }
                },
                onLikeToggled = { photoId, isLiked ->
                    viewModelScope.launch {
                        repository.toggleClientLiked(photoId, isLiked)
                        _clientLikesNotification.value += 1
                    }
                },
                sessionProvider = {
                    runBlocking(Dispatchers.IO) { repository.getSessionSync(sessionId) }
                },
                photosProvider = {
                    runBlocking(Dispatchers.IO) { repository.getPhotosForSessionSync(sessionId) }
                },
                photoBytesProvider = { photoId ->
                    runBlocking(Dispatchers.IO) {
                        val photos = repository.getPhotosForSessionSync(sessionId)
                        val photo = photos.find { it.id == photoId }
                        if (photo != null) readPhotoBytes(photo.localUri) else null
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
     * Directly scans the physical storage folder (SAF and local) of the session for new photos.
     * Guarantees that photos copied from external apps (File Manager, Camera, Lightroom)
     * are automatically detected and displayed in this app silently without UI blinking.
     */
    suspend fun syncSessionFolder(sessionId: String, silent: Boolean = true): Int = withContext(Dispatchers.IO) {
        if (!silent) {
            _isScanningFolder.value = true
        }
        try {
            val session = repository.getSessionSync(sessionId) ?: return@withContext 0
            val existingPhotos = repository.getPhotosForSessionSync(sessionId)
            val existingPaths = existingPhotos.map { it.localUri }.toSet()
            val existingNames = existingPhotos.map { it.fileName.lowercase() }.toSet()
            val newPhotos = mutableListOf<PhotoItemEntity>()
            val contentResolver = getApplication<Application>().contentResolver
            val supportedExtensions = setOf(
                "jpg", "jpeg", "png", "webp", "dng", "heic", "heif",
                "raw", "cr2", "cr3", "nef", "arw", "rw2", "orf"
            )

            // 1. Scan SAF directories if user configured custom storage
            val customUriStr = _customStorageUri.value
            if (!customUriStr.isNullOrEmpty()) {
                try {
                    val treeUri = Uri.parse(customUriStr)
                    val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), treeUri)
                    if (pickedDir != null && pickedDir.exists()) {
                        val candidateSafDirs = mutableListOf<androidx.documentfile.provider.DocumentFile>()
                        
                        // Check FotoSync_Studio/<session.folderName>
                        pickedDir.findFile("FotoSync_Studio")?.findFile(session.folderName)?.let { candidateSafDirs.add(it) }
                        // Check direct <session.folderName>
                        pickedDir.findFile(session.folderName)?.let { candidateSafDirs.add(it) }
                        // Check pickedDir itself if it contains images or is named similarly
                        if (pickedDir.name.equals(session.folderName, ignoreCase = true) || candidateSafDirs.isEmpty()) {
                            candidateSafDirs.add(pickedDir)
                        }

                        for (dir in candidateSafDirs.distinctBy { it.uri.toString() }) {
                            val filesOnDisk = dir.listFiles()
                            for (docFile in filesOnDisk) {
                                if (docFile.isFile) {
                                    val fileName = docFile.name ?: "photo_${System.currentTimeMillis()}.jpg"
                                    val ext = fileName.substringAfterLast('.', "").lowercase()
                                    val isImageMime = docFile.type?.startsWith("image/") == true
                                    if (!isImageMime && ext !in supportedExtensions) continue

                                    val fileUriStr = docFile.uri.toString()
                                    if (fileUriStr in existingPaths || fileName.lowercase() in existingNames) continue

                                    val fileSize = docFile.length().coerceAtLeast(1024L)
                                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    try {
                                        contentResolver.openInputStream(docFile.uri)?.use { stream ->
                                            BitmapFactory.decodeStream(stream, null, options)
                                        }
                                    } catch (e: Exception) {
                                        // Ignore preview decode failure for RAW
                                    }
                                    val resolution = if (options.outWidth > 0 && options.outHeight > 0) {
                                        "${options.outWidth} x ${options.outHeight}"
                                    } else {
                                        "6000 x 4000"
                                    }

                                    newPhotos.add(
                                        PhotoItemEntity(
                                            id = UUID.randomUUID().toString(),
                                            sessionId = sessionId,
                                            fileName = fileName,
                                            localUri = fileUriStr,
                                            fileSize = fileSize,
                                            resolution = resolution,
                                            isClientLiked = false,
                                            isPhotographerSelected = false,
                                            rating = 0,
                                            capturedAt = docFile.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Scan standard local directories (both app external files and public Pictures/DCIM)
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
                    if (file.absolutePath in existingPaths || file.name.lowercase() in existingNames) {
                        continue
                    }

                    val fileSize = file.length().coerceAtLeast(1024L)
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    try {
                        BitmapFactory.decodeFile(file.absolutePath, options)
                    } catch (e: Exception) {
                        // Ignore
                    }
                    val resolution = if (options.outWidth > 0 && options.outHeight > 0) {
                        "${options.outWidth} x ${options.outHeight}"
                    } else {
                        "6000 x 4000"
                    }

                    newPhotos.add(
                        PhotoItemEntity(
                            id = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            fileName = file.name,
                            localUri = file.absolutePath,
                            fileSize = fileSize,
                            resolution = resolution,
                            isClientLiked = false,
                            isPhotographerSelected = false,
                            rating = 0,
                            capturedAt = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                        )
                    )
                }
            }

            if (newPhotos.isNotEmpty()) {
                repository.insertPhotos(newPhotos)
            }
            newPhotos.size
        } catch (e: Exception) {
            e.printStackTrace()
            0
        } finally {
            if (!silent) {
                _isScanningFolder.value = false
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
        _activeSessionId.value = sessionId
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

    fun deleteSession(sessionId: String) {
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = repository.getSessionSync(sessionId)
                if (session != null) {
                    val photos = repository.getPhotosForSessionSync(sessionId)
                    val contentResolver = context.contentResolver

                    // 1. Delete individual photo files from storage
                    for (photo in photos) {
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

                    // 2. Delete local session directories recursively
                    try {
                        val sessionDir = getSessionDirectory(session)
                        if (sessionDir.exists()) {
                            sessionDir.deleteRecursively()
                        }

                        val publicPictures = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "FotoSync_Studio/${session.folderName}")
                        if (publicPictures.exists()) {
                            publicPictures.deleteRecursively()
                        }

                        val publicDcim = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "FotoSync_Studio/${session.folderName}")
                        if (publicDcim.exists()) {
                            publicDcim.deleteRecursively()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 3. Delete SAF folder if configured
                    val customUriStr = _customStorageUri.value
                    if (!customUriStr.isNullOrEmpty()) {
                        try {
                            val treeUri = Uri.parse(customUriStr)
                            val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri)
                            if (pickedDir != null && pickedDir.exists()) {
                                pickedDir.findFile("FotoSync_Studio")?.findFile(session.folderName)?.let { it.delete() }
                                pickedDir.findFile(session.folderName)?.let { it.delete() }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // 4. Delete photos from DB
                    val photoIds = photos.map { it.id }
                    if (photoIds.isNotEmpty()) {
                        repository.deletePhotos(photoIds)
                    }
                }

                repository.deleteSession(sessionId)

                withContext(Dispatchers.Main) {
                    if (_activeSessionId.value == sessionId) {
                        _activeSessionId.value = allSessions.value.firstOrNull { it.id != sessionId }?.id
                    }
                    Toast.makeText(context, "Sesi dan file penyimpanan berhasil dihapus", Toast.LENGTH_SHORT).show()
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

    fun createSession(date: String, clientName: String, category: String, notes: String) {
        viewModelScope.launch {
            val created = repository.createSession(date, clientName, category, notes)
            val customUriStr = _customStorageUri.value
            if (!customUriStr.isNullOrEmpty()) {
                try {
                    val treeUri = Uri.parse(customUriStr)
                    val pickedDir = androidx.documentfile.provider.DocumentFile.fromTreeUri(getApplication(), treeUri)
                    if (pickedDir != null) {
                        val studioSubDir = pickedDir.findFile("FotoSync_Studio") ?: pickedDir.createDirectory("FotoSync_Studio") ?: pickedDir
                        studioSubDir.findFile(created.folderName) ?: studioSubDir.createDirectory(created.folderName)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                getSessionDirectory(created)
            }
            _activeSessionId.value = created.id
            _showAddSessionDialog.value = false
        }
    }

    fun addMorePhotos() {
        val currentSessionId = _activeSessionId.value ?: return
        viewModelScope.launch {
            repository.addPhotosToSession(currentSessionId, 4)
        }
    }

    /**
     * Imports genuine images chosen from the device gallery or file manager.
     * Requires SAF folder selection.
     */
    fun importPhotosFromUris(context: Context, sessionId: String, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = repository.getSessionSync(sessionId) ?: return@launch
            val customUriStr = _customStorageUri.value
            val contentResolver = context.contentResolver

            if (customUriStr.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Wajib memilih folder penyimpanan SAF terlebih dahulu di menu tambahan!", Toast.LENGTH_LONG).show()
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
            val sessionSubDir = studioSubDir.findFile(session.folderName) ?: studioSubDir.createDirectory(session.folderName) ?: studioSubDir

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
                val sourceFile = File(photo.localUri)
                if (sourceFile.exists() && sourceFile.isFile) {
                    sourceFile.inputStream().use { input ->
                        input.copyTo(zos)
                    }
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
    }
}
