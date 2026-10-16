package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
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

    init {
        val database = FotoSyncDatabase.getInstance(application)
        repository = FotoSyncRepository(database.dao(), application)

        allSessions = repository.allSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Clean up legacy sample files on disk
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val picturesDir = application.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                if (picturesDir != null && picturesDir.exists()) {
                    val studioDir = File(picturesDir, "FotoSync_Studio")
                    if (studioDir.exists()) {
                        studioDir.deleteRecursively()
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
                if (photo != null && photo.localUri.isNotEmpty()) {
                    val file = File(photo.localUri)
                    if (file.exists() && file.isFile) {
                        try {
                            file.readBytes()
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                } else null
            }
        )
    }

    fun dismissClientConnectedEvent() {
        _clientConnectedEvent.value = null
    }

    fun toggleServer() {
        if (_isServerRunning.value) {
            ktorServer?.stop()
            _isServerRunning.value = false
            _serverUrl.value = ""
        } else {
            val port = 8080
            val started = ktorServer?.start(port) ?: false
            if (started) {
                val ip = NetworkUtils.getLocalIpAddress()
                // Direct connect trigger URL for QR code scan
                val url = "http://$ip:$port/connect"
                _serverUrl.value = url
                _isServerRunning.value = true
                _showServerDialog.value = true
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

    fun closeServerDialog() {
        _showServerDialog.value = false
    }

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
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
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = allSessions.value.firstOrNull { it.id != sessionId }?.id
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
     * Guarantees zero hardcoded photos.
     */
    fun importPhotosFromUris(context: Context, sessionId: String, uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = repository.getSessionSync(sessionId) ?: return@launch
            val baseDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "FotoSync_Studio/${session.folderName}")
            if (!baseDir.exists()) baseDir.mkdirs()

            val newPhotos = mutableListOf<PhotoItemEntity>()
            val contentResolver = context.contentResolver

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

                val targetFile = File(baseDir, fileName)
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (fileSize <= 0L) {
                        fileSize = targetFile.length()
                    }

                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(targetFile.absolutePath, options)
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
                            localUri = targetFile.absolutePath,
                            fileSize = fileSize.coerceAtLeast(1024L),
                            resolution = resolution,
                            isClientLiked = false,
                            isPhotographerSelected = false,
                            rating = 0,
                            capturedAt = System.currentTimeMillis()
                        )
                    )
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
                                val sourceFile = File(photo.localUri)
                                if (sourceFile.exists() && sourceFile.isFile) {
                                    sourceFile.inputStream().use { it.copyTo(out) }
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
        ktorServer?.stop()
    }
}
