package com.example.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.Log
import com.example.data.model.PhotoItemEntity
import com.example.data.model.PhotoSessionEntity
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Ktor-powered lightweight CIO embedded HTTP server running directly inside Android.
 * Hosts local photo selection content accessible over Wi-Fi, featuring a dedicated
 * /connect endpoint for QR code connection triggering.
 */
class KtorLocalServer(
    private val scope: CoroutineScope,
    private val onClientConnected: (clientInfo: String) -> Unit,
    private val onLikeToggled: (photoId: String, isLiked: Boolean) -> Unit,
    private val sessionProvider: () -> PhotoSessionEntity?,
    private val photosProvider: () -> List<PhotoItemEntity>,
    private val photoBytesProvider: (photoId: String) -> ByteArray?
) {
    private var engine: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    var isRunning: Boolean = false
        private set
    var currentPort: Int = 8080
        private set

    fun start(port: Int = 8080): Boolean {
        if (isRunning) return true
        return try {
            currentPort = port
            val server = embeddedServer(CIO, port = port) {
                routing {
                    // 1. Endpoint dasar pemicu koneksi via pemindaian QR code
                    get("/connect") {
                        val clientAgent = call.request.headers["User-Agent"] ?: "Mobile Device"
                        onClientConnected(clientAgent)
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        val session = sessionProvider()
                        val photos = photosProvider()
                        call.respondText(generateClientHtml(session, photos), ContentType.Text.Html)
                    }

                    get("/") {
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        val session = sessionProvider()
                        val photos = photosProvider()
                        call.respondText(generateClientHtml(session, photos), ContentType.Text.Html)
                    }

                    get("/client") {
                        val clientAgent = call.request.headers["User-Agent"] ?: "Mobile Device"
                        onClientConnected(clientAgent)
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        val session = sessionProvider()
                        val photos = photosProvider()
                        call.respondText(generateClientHtml(session, photos), ContentType.Text.Html)
                    }

                    // 2. Status & handshake endpoint for QR verification
                    get("/api/connect") {
                        val clientAgent = call.request.headers["User-Agent"] ?: "Client"
                        onClientConnected(clientAgent)
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        call.respondText(
                            """{"status":"connected","engine":"Ktor CIO","client":"$clientAgent"}""",
                            ContentType.Application.Json
                        )
                    }

                    // 3. Active session metadata
                    get("/api/session") {
                        val session = sessionProvider()
                        val json = if (session != null) {
                            """{"id":"${session.id}","folderName":"${escapeJson(session.folderName)}","clientName":"${escapeJson(session.clientName)}","sessionDate":"${session.sessionDate}","category":"${escapeJson(session.category)}"}"""
                        } else {
                            """{"error":"no_active_session"}"""
                        }
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        call.respondText(json, ContentType.Application.Json)
                    }

                    // 4. Photos list
                    get("/api/photos") {
                        val photos = photosProvider()
                        val items = photos.joinToString(",") { photo ->
                            """{"id":"${photo.id}","fileName":"${escapeJson(photo.fileName)}","isClientLiked":${photo.isClientLiked},"isPhotographerSelected":${photo.isPhotographerSelected},"rating":${photo.rating},"fileSize":${photo.fileSize},"resolution":"${photo.resolution}","capturedAt":${photo.capturedAt}}"""
                        }
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        call.respondText("[$items]", ContentType.Application.Json)
                    }

                    // 5. Toggle Like
                    post("/api/like") {
                        val photoId = call.request.queryParameters["id"] ?: ""
                        val liked = call.request.queryParameters["liked"]?.toBooleanStrictOrNull() ?: true
                        if (photoId.isNotEmpty()) {
                            onLikeToggled(photoId, liked)
                            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                            call.respondText("""{"status":"ok","id":"$photoId","isClientLiked":$liked}""", ContentType.Application.Json)
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Missing photo id")
                        }
                    }

                    // 6. View Photo
                    get("/photo") {
                        val photoId = call.request.queryParameters["id"] ?: ""
                        val photo = photosProvider().find { it.id == photoId }
                        val label = photo?.fileName ?: "DSC_$photoId"

                        if (photo != null && photo.localUri.isNotEmpty()) {
                            val file = File(photo.localUri)
                            if (file.exists() && file.isFile) {
                                call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                                call.respondBytes(file.readBytes(), ContentType.Image.JPEG)
                                return@get
                            }
                        }

                        val bytes = photoBytesProvider(photoId) ?: generatePlaceholder(label)
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        call.respondBytes(bytes, ContentType.Image.JPEG)
                    }

                    // 7. Download Single Photo
                    get("/download") {
                        val photoId = call.request.queryParameters["id"] ?: ""
                        val photo = photosProvider().find { it.id == photoId }
                        val fileName = photo?.fileName ?: "photo_$photoId.jpg"

                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$fileName\"")

                        if (photo != null && photo.localUri.isNotEmpty()) {
                            val file = File(photo.localUri)
                            if (file.exists() && file.isFile) {
                                call.respondBytes(file.readBytes(), ContentType.Image.JPEG)
                                return@get
                            }
                        }

                        val bytes = photoBytesProvider(photoId) ?: generatePlaceholder(fileName)
                        call.respondBytes(bytes, ContentType.Image.JPEG)
                    }

                    // 8. Download Liked Photos ZIP
                    get("/download/all-liked") {
                        val session = sessionProvider()
                        val liked = photosProvider().filter { it.isClientLiked }
                        val prefix = session?.folderName ?: "FotoSync"
                        val zipName = "${prefix}_Pilihan_Favorit.zip"

                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$zipName\"")

                        val zipBytes = createZipArchive(liked)
                        call.respondBytes(zipBytes, ContentType.Application.Zip)
                    }

                    // 9. Download Full Session ZIP
                    get("/download/session-zip") {
                        val session = sessionProvider()
                        val all = photosProvider()
                        val prefix = session?.folderName ?: "FotoSync"
                        val zipName = "${prefix}_Semua_Foto.zip"

                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"$zipName\"")

                        val zipBytes = createZipArchive(all)
                        call.respondBytes(zipBytes, ContentType.Application.Zip)
                    }
                }
            }

            engine = server
            server.start(wait = false)
            isRunning = true
            true
        } catch (e: Exception) {
            Log.e("KtorLocalServer", "Error starting Ktor CIO server", e)
            isRunning = false
            false
        }
    }

    fun stop() {
        isRunning = false
        try {
            engine?.stop(500, 1500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        engine = null
    }

    private fun createZipArchive(photos: List<PhotoItemEntity>): ByteArray {
        val targetList = if (photos.isEmpty()) photosProvider() else photos
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)
        for (photo in targetList) {
            val entry = ZipEntry(photo.fileName)
            zos.putNextEntry(entry)

            val file = File(photo.localUri)
            val bytes = if (file.exists() && file.isFile) {
                try {
                    file.readBytes()
                } catch (e: Exception) {
                    photoBytesProvider(photo.id) ?: generatePlaceholder(photo.fileName)
                }
            } else {
                photoBytesProvider(photo.id) ?: generatePlaceholder(photo.fileName)
            }

            zos.write(bytes)
            zos.closeEntry()
        }
        zos.finish()
        return baos.toByteArray()
    }

    private fun generatePlaceholder(label: String): ByteArray {
        val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(AndroidColor.rgb(22, 24, 31))

        val paint = Paint().apply {
            color = AndroidColor.rgb(245, 166, 35)
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("FotoSync Studio", 400f, 260f, paint)

        paint.color = AndroidColor.rgb(240, 240, 245)
        paint.textSize = 28f
        canvas.drawText(label, 400f, 315f, paint)

        paint.color = AndroidColor.rgb(156, 163, 175)
        paint.textSize = 18f
        canvas.drawText("Format RAW / JPEG - Ktor Server Engine", 400f, 360f, paint)

        val bos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)
        return bos.toByteArray()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun generateClientHtml(session: PhotoSessionEntity?, photos: List<PhotoItemEntity>): String {
        val clientName = session?.clientName ?: "Portal Klien"
        val folderName = session?.folderName ?: "Sesi Pemotretan"
        val sessionDate = session?.sessionDate ?: ""

        return """
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>FotoSync - Seleksi dan Unduh Foto</title>
    <style>
        :root {
            --bg: #0F1014;
            --surface: #181A20;
            --surface-elevated: #222630;
            --gold: #F5A623;
            --crimson: #E11D48;
            --blue: #3B82F6;
            --text-primary: #F3F4F6;
            --text-secondary: #9CA3AF;
            --border: #2C3240;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
        body {
            background-color: var(--bg);
            color: var(--text-primary);
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            padding-bottom: 100px;
        }
        header {
            background: linear-gradient(180deg, rgba(24,26,32,0.98) 0%, rgba(15,16,20,0.95) 100%);
            border-bottom: 1px solid var(--border);
            padding: 16px 20px;
            position: sticky;
            top: 0;
            z-index: 50;
            backdrop-filter: blur(12px);
        }
        .header-top {
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .brand {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .brand-badge {
            background: var(--gold);
            color: #121316;
            font-weight: 800;
            font-size: 11px;
            padding: 3px 8px;
            border-radius: 6px;
            letter-spacing: 0.5px;
        }
        .live-status {
            display: flex;
            align-items: center;
            gap: 6px;
            font-size: 12px;
            color: #10B981;
            font-weight: 600;
        }
        .live-dot {
            width: 8px;
            height: 8px;
            background-color: #10B981;
            border-radius: 50%;
        }
        .session-info {
            margin-top: 10px;
        }
        .session-title {
            font-size: 18px;
            font-weight: 700;
            color: var(--text-primary);
        }
        .session-subtitle {
            font-size: 13px;
            color: var(--text-secondary);
            margin-top: 2px;
        }
        .tabs {
            display: flex;
            gap: 10px;
            padding: 14px 20px 6px;
            overflow-x: auto;
        }
        .tab-btn {
            background: var(--surface);
            border: 1px solid var(--border);
            color: var(--text-secondary);
            padding: 8px 16px;
            border-radius: 20px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s;
            display: flex;
            align-items: center;
            gap: 6px;
            white-space: nowrap;
        }
        .tab-btn.active {
            background: var(--gold);
            color: #121316;
            border-color: var(--gold);
        }
        .tab-btn.active.tab-liked {
            background: var(--crimson);
            color: white;
            border-color: var(--crimson);
        }
        .gallery-container {
            padding: 12px 16px;
        }
        .photo-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 12px;
        }
        @media(min-width: 600px) {
            .photo-grid { grid-template-columns: repeat(3, 1fr); gap: 16px; }
        }
        .photo-card {
            background: var(--surface);
            border-radius: 14px;
            overflow: hidden;
            border: 1px solid var(--border);
            position: relative;
            display: flex;
            flex-direction: column;
        }
        .photo-thumb-wrap {
            position: relative;
            width: 100%;
            padding-top: 75%;
            background: #121318;
            cursor: pointer;
        }
        .photo-thumb {
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            object-fit: cover;
        }
        .card-actions {
            position: absolute;
            bottom: 8px;
            right: 8px;
            display: flex;
            gap: 6px;
            z-index: 10;
        }
        .like-badge-btn, .dl-badge-btn {
            width: 36px;
            height: 36px;
            border-radius: 50%;
            background: rgba(18, 19, 24, 0.85);
            backdrop-filter: blur(8px);
            border: 1px solid rgba(255, 255, 255, 0.2);
            color: white;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            text-decoration: none;
            transition: transform 0.2s cubic-bezier(0.175, 0.885, 0.32, 1.275);
        }
        .like-badge-btn.liked {
            background: var(--crimson);
            border-color: var(--crimson);
            transform: scale(1.06);
        }
        .dl-badge-btn:hover {
            background: var(--blue);
        }
        .photo-footer {
            padding: 8px 10px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            font-size: 11px;
            color: var(--text-secondary);
        }
        .photo-name {
            font-weight: 600;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            max-width: 110px;
        }
        .bottom-summary-bar {
            position: fixed;
            bottom: 0;
            left: 0;
            right: 0;
            background: rgba(24, 26, 32, 0.96);
            backdrop-filter: blur(14px);
            border-top: 1px solid var(--border);
            padding: 10px 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            z-index: 40;
            gap: 8px;
        }
        .summary-text {
            font-size: 12px;
            display: flex;
            flex-direction: column;
        }
        .summary-count {
            font-size: 16px;
            font-weight: 800;
            color: var(--crimson);
        }
        .bottom-btns {
            display: flex;
            gap: 8px;
            align-items: center;
        }
        .dl-zip-btn {
            background: linear-gradient(135deg, var(--blue) 0%, #1D4ED8 100%);
            color: white;
            font-weight: 700;
            font-size: 12px;
            padding: 9px 14px;
            border-radius: 10px;
            border: none;
            cursor: pointer;
            text-decoration: none;
            display: flex;
            align-items: center;
            gap: 6px;
        }
        .done-btn {
            background: linear-gradient(135deg, var(--gold) 0%, #E08A00 100%);
            color: #121316;
            font-weight: 700;
            font-size: 12px;
            padding: 9px 14px;
            border-radius: 10px;
            border: none;
            cursor: pointer;
        }
        .lightbox-modal {
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.96);
            backdrop-filter: blur(10px);
            z-index: 100;
            display: none;
            flex-direction: column;
            justify-content: space-between;
            align-items: center;
            padding: 20px;
        }
        .lightbox-modal.active {
            display: flex;
        }
        .lightbox-header {
            width: 100%;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .lightbox-close {
            background: rgba(255,255,255,0.15);
            border: none;
            color: white;
            padding: 8px 14px;
            border-radius: 20px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
        }
        .lightbox-img {
            max-width: 85vw;
            max-height: 65vh;
            border-radius: 12px;
            object-fit: contain;
        }
        .lightbox-nav-btn {
            position: absolute;
            background: rgba(255, 255, 255, 0.15);
            color: white;
            border: none;
            width: 48px;
            height: 48px;
            border-radius: 50%;
            font-size: 20px;
            display: flex;
            align-items: center;
            justify-content: center;
            cursor: pointer;
            z-index: 110;
            transition: background 0.2s;
        }
        .lightbox-nav-btn:hover { background: rgba(255, 255, 255, 0.3); }
        .lightbox-nav-btn.prev { left: 10px; }
        .lightbox-nav-btn.next { right: 10px; }
        .lightbox-footer {
            width: 100%;
            display: flex;
            justify-content: center;
            gap: 12px;
            padding-bottom: 20px;
            flex-wrap: wrap;
        }
        .lightbox-like-btn {
            background: var(--surface);
            border: 2px solid var(--border);
            color: white;
            padding: 11px 22px;
            border-radius: 30px;
            font-size: 15px;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 8px;
            cursor: pointer;
        }
        .lightbox-like-btn.liked {
            background: var(--crimson);
            border-color: var(--crimson);
        }
        .lightbox-dl-btn {
            background: var(--blue);
            color: white;
            padding: 11px 22px;
            border-radius: 30px;
            font-size: 15px;
            font-weight: 700;
            display: flex;
            align-items: center;
            gap: 8px;
            text-decoration: none;
            cursor: pointer;
        }
        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: var(--text-secondary);
        }
        .empty-icon {
            display: flex;
            justify-content: center;
            margin-bottom: 12px;
        }
    </style>
</head>
<body>
    <header>
        <div class="header-top">
            <div class="brand">
                <span class="brand-badge">FotoSync</span>
            </div>
            <div class="live-status">
                <div class="live-dot"></div>
                <span>Tersambung</span>
            </div>
        </div>
        <div class="session-info">
            <h1 class="session-title">$clientName</h1>
            <p class="session-subtitle">$folderName</p>
        </div>
    </header>

    <div class="tabs">
        <button id="tabAll" class="tab-btn active" onclick="switchTab('all')">
            Semua Foto (<span id="countAll">0</span>)
        </button>
        <button id="tabLiked" class="tab-btn tab-liked" onclick="switchTab('liked')">
            Pilihan Favorit (<span id="countLiked">0</span>)
        </button>
    </div>

    <div class="gallery-container">
        <div id="photoGrid" class="photo-grid"></div>
        <div id="emptyView" class="empty-state" style="display:none;">
            <div class="empty-icon">
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#9CA3AF" stroke-width="1.5">
                    <rect x="3" y="3" width="18" height="18" rx="3"></rect>
                    <circle cx="8.5" cy="8.5" r="1.5"></circle>
                    <polyline points="21 15 16 10 5 21"></polyline>
                </svg>
            </div>
            <p style="font-weight:600;">Belum ada foto yang disukai</p>
            <p style="font-size:12px;margin-top:4px;">Pilih foto yang ingin Anda tandai sebagai favorit.</p>
        </div>
    </div>

    <div class="bottom-summary-bar">
        <div class="summary-text">
            <span>Favorit: <span id="likedSummaryCount" class="summary-count">0</span> foto</span>
        </div>
        <div class="bottom-btns">
            <a id="btnDlZip" href="/download/all-liked" class="dl-zip-btn" title="Unduh Paket ZIP Foto Favorit">
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                <span>Unduh ZIP</span>
            </a>
        </div>
    </div>

    <!-- Lightbox Modal -->
    <div id="lightbox" class="lightbox-modal">
        <div class="lightbox-header">
            <span id="lbFileName" style="font-size:13px;color:var(--text-secondary);">DSC_0001.JPG</span>
            <button class="lightbox-close" onclick="closeLightbox()">Tutup</button>
        </div>
        <div style="position:relative; width:100%; display:flex; justify-content:center; align-items:center; flex:1;">
            <button class="lightbox-nav-btn prev" onclick="prevPhoto()">&#10094;</button>
            <img id="lbImg" class="lightbox-img" src="" alt="Detail Foto" />
            <button class="lightbox-nav-btn next" onclick="nextPhoto()">&#10095;</button>
        </div>
        <div class="lightbox-footer">
            <button id="lbLikeBtn" class="lightbox-like-btn" onclick="toggleLightboxLike()">
                <svg id="lbHeartSvg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path>
                </svg>
                <span id="lbLikeText">Tandai Favorit</span>
            </button>
            <a id="lbDlBtn" class="lightbox-dl-btn" href="#" download>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                <span>Unduh Foto</span>
            </a>
        </div>
    </div>

    <script>
        let allPhotos = [];
        let currentTab = 'all';
        let currentViewingId = null;

        async function fetchPhotos() {
            try {
                const res = await fetch('/api/photos');
                if (res.ok) {
                    allPhotos = await res.json();
                    renderGallery();
                    updateCounts();
                }
            } catch (err) {
                console.error('Fetch error:', err);
            }
        }

        function updateCounts() {
            const likedCount = allPhotos.filter(p => p.isClientLiked).length;
            document.getElementById('countAll').innerText = allPhotos.length;
            document.getElementById('countLiked').innerText = likedCount;
            document.getElementById('likedSummaryCount').innerText = likedCount;

            const zipBtn = document.getElementById('btnDlZip');
            const iconSvg = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>';
            if (likedCount > 0) {
                zipBtn.innerHTML = iconSvg + '<span>Unduh ZIP (' + likedCount + ')</span>';
                zipBtn.href = '/download/all-liked';
            } else {
                zipBtn.innerHTML = iconSvg + '<span>Unduh Semua ZIP</span>';
                zipBtn.href = '/download/session-zip';
            }
        }

        function switchTab(tab) {
            currentTab = tab;
            document.getElementById('tabAll').classList.toggle('active', tab === 'all');
            document.getElementById('tabLiked').classList.toggle('active', tab === 'liked');
            renderGallery();
        }

        function renderGallery() {
            const grid = document.getElementById('photoGrid');
            const emptyView = document.getElementById('emptyView');
            grid.innerHTML = '';

            const list = currentTab === 'liked' ? allPhotos.filter(p => p.isClientLiked) : allPhotos;

            if (list.length === 0 && currentTab === 'liked') {
                emptyView.style.display = 'block';
                grid.style.display = 'none';
                return;
            } else {
                emptyView.style.display = 'none';
                grid.style.display = 'grid';
            }

            list.forEach(photo => {
                const card = document.createElement('div');
                card.className = 'photo-card';

                const thumbWrap = document.createElement('div');
                thumbWrap.className = 'photo-thumb-wrap';
                thumbWrap.onclick = () => openLightbox(photo.id);

                const img = document.createElement('img');
                img.className = 'photo-thumb';
                img.src = '/photo?id=' + encodeURIComponent(photo.id);
                img.loading = 'lazy';
                img.alt = photo.fileName;

                thumbWrap.appendChild(img);

                const actionWrap = document.createElement('div');
                actionWrap.className = 'card-actions';

                const dlBtn = document.createElement('a');
                dlBtn.className = 'dl-badge-btn';
                dlBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>';
                dlBtn.title = 'Unduh ' + photo.fileName;
                dlBtn.href = '/download?id=' + encodeURIComponent(photo.id);
                dlBtn.setAttribute('download', photo.fileName);
                dlBtn.onclick = (e) => e.stopPropagation();
                actionWrap.appendChild(dlBtn);

                const likeBtn = document.createElement('button');
                likeBtn.className = 'like-badge-btn' + (photo.isClientLiked ? ' liked' : '');
                const heartFill = photo.isClientLiked ? '#FFFFFF' : 'none';
                likeBtn.innerHTML = '<svg width="16" height="16" viewBox="0 0 24 24" fill="' + heartFill + '" stroke="currentColor" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"></path></svg>';
                likeBtn.title = 'Tandai Favorit';
                likeBtn.onclick = (e) => {
                    e.stopPropagation();
                    toggleLike(photo.id, !photo.isClientLiked);
                };
                actionWrap.appendChild(likeBtn);

                thumbWrap.appendChild(actionWrap);

                const footer = document.createElement('div');
                footer.className = 'photo-footer';

                const name = document.createElement('span');
                name.className = 'photo-name';
                name.innerText = photo.fileName;

                const res = document.createElement('span');
                res.innerText = photo.resolution ? photo.resolution.split(' ')[0] : 'RAW';

                footer.appendChild(name);
                footer.appendChild(res);

                card.appendChild(thumbWrap);
                card.appendChild(footer);
                grid.appendChild(card);
            });
        }

        async function toggleLike(photoId, liked) {
            const item = allPhotos.find(p => p.id === photoId);
            if (item) {
                item.isClientLiked = liked;
                updateCounts();
                renderGallery();
            }

            try {
                await fetch('/api/like?id=' + encodeURIComponent(photoId) + '&liked=' + liked, { method: 'POST' });
            } catch (e) {
                console.error('Like failed', e);
            }
        }

        let currentIndex = 0;

        function openLightbox(photoId) {
            currentViewingId = photoId;
            const list = currentTab === 'liked' ? allPhotos.filter(p => p.isClientLiked) : allPhotos;
            currentIndex = list.findIndex(p => p.id === photoId);
            if (currentIndex < 0) currentIndex = 0;

            showCurrentLightboxPhoto();
            document.getElementById('lightbox').classList.add('active');
        }

        function showCurrentLightboxPhoto() {
            const list = currentTab === 'liked' ? allPhotos.filter(p => p.isClientLiked) : allPhotos;
            if (list.length === 0) return;
            if (currentIndex >= list.length) currentIndex = 0;
            if (currentIndex < 0) currentIndex = list.length - 1;

            const photo = list[currentIndex];
            currentViewingId = photo.id;

            document.getElementById('lbFileName').innerText = (currentIndex + 1) + ' dari ' + list.length + ' - ' + photo.fileName;
            document.getElementById('lbImg').src = '/photo?id=' + encodeURIComponent(photo.id);
            document.getElementById('lbDlBtn').href = '/download?id=' + encodeURIComponent(photo.id);
            document.getElementById('lbDlBtn').setAttribute('download', photo.fileName);
            updateLightboxLikeBtn(photo.isClientLiked);
        }

        function prevPhoto() {
            currentIndex--;
            showCurrentLightboxPhoto();
        }

        function nextPhoto() {
            currentIndex++;
            showCurrentLightboxPhoto();
        }

        // Keyboard arrow navigation
        document.addEventListener('keydown', (e) => {
            if (!document.getElementById('lightbox').classList.contains('active')) return;
            if (e.key === 'ArrowLeft') prevPhoto();
            if (e.key === 'ArrowRight') nextPhoto();
            if (e.key === 'Escape') closeLightbox();
        });

        // Touch swipe support
        let touchStartX = 0;
        let touchEndX = 0;
        const lightboxElem = document.getElementById('lightbox');
        lightboxElem.addEventListener('touchstart', e => { touchStartX = e.changedTouches[0].screenX; }, false);
        lightboxElem.addEventListener('touchend', e => {
            touchEndX = e.changedTouches[0].screenX;
            if (touchEndX < touchStartX - 50) nextPhoto();
            if (touchEndX > touchStartX + 50) prevPhoto();
        }, false);

        function closeLightbox() {
            document.getElementById('lightbox').classList.remove('active');
            currentViewingId = null;
        }

        function updateLightboxLikeBtn(isLiked) {
            const btn = document.getElementById('lbLikeBtn');
            const svg = document.getElementById('lbHeartSvg');
            const text = document.getElementById('lbLikeText');

            if (isLiked) {
                btn.classList.add('liked');
                svg.setAttribute('fill', '#FFFFFF');
                text.innerText = 'Disukai';
            } else {
                btn.classList.remove('liked');
                svg.setAttribute('fill', 'none');
                text.innerText = 'Tandai Favorit';
            }
        }

        function toggleLightboxLike() {
            if (!currentViewingId) return;
            const photo = allPhotos.find(p => p.id === currentViewingId);
            if (!photo) return;
            const newLiked = !photo.isClientLiked;
            toggleLike(photo.id, newLiked);
            updateLightboxLikeBtn(newLiked);
        }

        function showDoneDialog() {
            const liked = allPhotos.filter(p => p.isClientLiked).length;
            alert('Pilihan selesai: ' + liked + ' foto favorit telah disinkronkan ke fotografer. Anda juga dapat mengunduh foto pilihan via tombol Unduh ZIP.');
        }

        fetchPhotos();
        setInterval(fetchPhotos, 3500);
    </script>
</body>
</html>
        """.trimIndent()
    }
}
