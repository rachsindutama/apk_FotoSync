package com.example.server

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.util.Log
import com.example.data.model.GuestIdentity
import com.example.data.model.PhotoItemEntity
import com.example.data.model.PhotoSessionEntity
import com.example.data.model.SocialPlatform
import com.example.data.model.StudioSocialProfile
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Ktor-powered lightweight CIO embedded HTTP server running directly inside Android.
 * Hosts local photo selection content accessible over Wi-Fi, isolating favorites
 * per guest IP + persistent device ID and optionally enforcing a studio social follow gate.
 */
class KtorLocalServer(
    private val scope: CoroutineScope,
    private val onClientConnected: (guest: GuestIdentity) -> Unit,
    private val onLikeToggled: suspend (photoId: String, isLiked: Boolean, guest: GuestIdentity) -> Unit,
    private val sessionProvider: suspend () -> PhotoSessionEntity?,
    private val photosProvider: suspend () -> List<PhotoItemEntity>,
    private val guestLikedIdsProvider: suspend (sessionId: String, guest: GuestIdentity) -> Set<String>,
    private val socialProfileProvider: () -> StudioSocialProfile,
    private val photoBytesProvider: suspend (photoId: String) -> ByteArray?,
    private val photoThumbBytesProvider: suspend (photoId: String) -> ByteArray? = photoBytesProvider
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
                        val guest = call.extractGuestIdentity()
                        if (guest.deviceId != "browser") {
                            onClientConnected(guest)
                        }
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        val session = sessionProvider()
                        call.respondText(
                            generateClientHtml(session, socialProfileProvider()),
                            ContentType.Text.Html
                        )
                    }

                    get("/") {
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        val session = sessionProvider()
                        call.respondText(
                            generateClientHtml(session, socialProfileProvider()),
                            ContentType.Text.Html
                        )
                    }

                    get("/client") {
                        val guest = call.extractGuestIdentity()
                        if (guest.deviceId != "browser") {
                            onClientConnected(guest)
                        }
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        val session = sessionProvider()
                        call.respondText(
                            generateClientHtml(session, socialProfileProvider()),
                            ContentType.Text.Html
                        )
                    }

                    // 2. Status & handshake endpoint with per-device/IP registration
                    get("/api/connect") {
                        val guest = call.extractGuestIdentity()
                        onClientConnected(guest)
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        call.respondText(
                            """{"status":"connected","engine":"Ktor CIO","clientIp":"${escapeJson(guest.clientIp)}","deviceLabel":"${escapeJson(guest.deviceLabel)}","clientKey":"${escapeJson(guest.clientKey)}"}""",
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

                    // 4. Photos list scoped to the requesting guest's IP + deviceId
                    get("/api/photos") {
                        val guest = call.extractGuestIdentity()
                        val session = sessionProvider()
                        val photos = photosProvider()
                        val likedIds = if (session != null) {
                            guestLikedIdsProvider(session.id, guest)
                        } else {
                            emptySet()
                        }
                        val items = photos.joinToString(",") { photo ->
                            val isLikedByGuest = likedIds.contains(photo.id)
                            """{"id":"${photo.id}","fileName":"${escapeJson(photo.fileName)}","isClientLiked":$isLikedByGuest,"isPhotographerSelected":${photo.isPhotographerSelected},"rating":${photo.rating},"fileSize":${photo.fileSize},"resolution":"${photo.resolution}","capturedAt":${photo.capturedAt}}"""
                        }
                        call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                        call.respondText("[$items]", ContentType.Application.Json)
                    }

                    // 5. Toggle Like isolated to requesting guest's IP + deviceId
                    post("/api/like") {
                        val guest = call.extractGuestIdentity()
                        onClientConnected(guest)
                        val photoId = call.request.queryParameters["id"] ?: ""
                        val liked = call.request.queryParameters["liked"]?.toBooleanStrictOrNull() ?: true
                        if (photoId.isNotEmpty()) {
                            onLikeToggled(photoId, liked, guest)
                            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                            call.respondText(
                                """{"status":"ok","id":"$photoId","isClientLiked":$liked,"clientKey":"${escapeJson(guest.clientKey)}"}""",
                                ContentType.Application.Json
                            )
                        } else {
                            call.respond(HttpStatusCode.BadRequest, "Missing photo id")
                        }
                    }

                    // 6. View Photo (supports ?thumb=1 for fast gallery grid loading)
                    get("/photo") {
                        val photoId = call.request.queryParameters["id"] ?: ""
                        val isThumb = call.request.queryParameters["thumb"] == "1"
                        val photo = photosProvider().find { it.id == photoId }
                        val label = photo?.fileName ?: "DSC_$photoId"

                        if (isThumb) {
                            val thumbBytes = photoThumbBytesProvider(photoId) ?: generatePlaceholder(label)
                            call.response.header(HttpHeaders.AccessControlAllowOrigin, "*")
                            call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
                            call.respondBytes(thumbBytes, ContentType.Image.JPEG)
                            return@get
                        }

                        if (photo != null && photo.localUri.isNotEmpty() && !photo.localUri.startsWith("content://")) {
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

                        if (photo != null && photo.localUri.isNotEmpty() && !photo.localUri.startsWith("content://")) {
                            val file = File(photo.localUri)
                            if (file.exists() && file.isFile) {
                                call.respondBytes(file.readBytes(), ContentType.Image.JPEG)
                                return@get
                            }
                        }

                        val bytes = photoBytesProvider(photoId) ?: generatePlaceholder(fileName)
                        call.respondBytes(bytes, ContentType.Image.JPEG)
                    }

                    // 8. Download Liked Photos ZIP scoped strictly to requesting guest
                    get("/download/all-liked") {
                        val guest = call.extractGuestIdentity()
                        val session = sessionProvider()
                        val likedIds = if (session != null) {
                            guestLikedIdsProvider(session.id, guest)
                        } else {
                            emptySet()
                        }
                        val liked = photosProvider().filter { likedIds.contains(it.id) }
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

    private fun ApplicationCall.extractGuestIdentity(): GuestIdentity {
        val forwardedIp = request.headers["X-Forwarded-For"]?.split(",")?.firstOrNull()?.trim()
        val rawIp = forwardedIp?.takeIf { it.isNotBlank() } ?: request.local.remoteAddress
        val clientIp = rawIp.removePrefix("::ffff:").ifBlank { "unknown-ip" }

        val rawDeviceId = request.queryParameters["deviceId"]
            ?: request.headers["X-Device-Id"]
            ?: request.cookies["fotosync_device_id"]
            ?: "browser"
        val deviceId = rawDeviceId.replace(Regex("[^a-zA-Z0-9_-]"), "").take(48).ifBlank { "browser" }

        val ua = request.headers["User-Agent"] ?: ""
        val os = when {
            ua.contains("iPhone", ignoreCase = true) -> "iPhone"
            ua.contains("iPad", ignoreCase = true) -> "iPad"
            ua.contains("Android", ignoreCase = true) -> "Android"
            ua.contains("Windows", ignoreCase = true) -> "Windows"
            ua.contains("Mac OS", ignoreCase = true) -> "Mac"
            ua.contains("Linux", ignoreCase = true) -> "Linux"
            else -> "Perangkat"
        }
        val browser = when {
            ua.contains("Edg", ignoreCase = true) -> "Edge"
            ua.contains("OPR", ignoreCase = true) || ua.contains("Opera", ignoreCase = true) -> "Opera"
            ua.contains("SamsungBrowser", ignoreCase = true) -> "Samsung"
            ua.contains("Chrome", ignoreCase = true) -> "Chrome"
            ua.contains("Firefox", ignoreCase = true) -> "Firefox"
            ua.contains("Safari", ignoreCase = true) -> "Safari"
            else -> "Browser"
        }
        val deviceLabel = "$os • $browser"
        val clientKey = "$clientIp::$deviceId"
        return GuestIdentity(
            clientKey = clientKey,
            clientIp = clientIp,
            deviceId = deviceId,
            deviceLabel = deviceLabel
        )
    }

    private suspend fun createZipArchive(photos: List<PhotoItemEntity>): ByteArray {
        val targetList = if (photos.isEmpty()) photosProvider() else photos
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)
        for (photo in targetList) {
            val entry = ZipEntry(photo.fileName)
            zos.putNextEntry(entry)

            val file = if (!photo.localUri.startsWith("content://")) File(photo.localUri) else null
            val bytes = if (file != null && file.exists() && file.isFile) {
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

    private fun escapeHtml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun generateClientHtml(
        session: PhotoSessionEntity?,
        socialProfile: StudioSocialProfile
    ): String {
        val sessionId = session?.id ?: "default"
        val clientName = escapeHtml(session?.clientName ?: "Portal Klien")
        val rawVendorName = socialProfile.vendorName.trim()
        val hasVendorName = rawVendorName.isNotEmpty()
        val vendorNameHtml = escapeHtml(rawVendorName)
        val pageTitleHtml = if (hasVendorName) "$vendorNameHtml — $clientName" else "FotoSync - Seleksi dan Unduh Foto"
        val configuredAccounts = socialProfile.configuredAccounts
        val gateRequired = configuredAccounts.isNotEmpty()
        val socialSignature = configuredAccounts.joinToString("|") { "${it.platform.name}:${it.socialUrl}" }
        val escapedSocialSignatureJs = escapeJson(socialSignature)
        val escapedSessionIdJs = escapeJson(sessionId)

        val socialButtonsHtml = configuredAccounts.joinToString("\n") { acc ->
            val plat = acc.platform
            val platName = escapeHtml(plat.displayName)
            val urlHtml = escapeHtml(acc.socialUrl.trim())
            val labelHtml = escapeHtml(acc.socialName.trim().ifBlank { acc.socialUrl.trim() })
            val bg = when (plat) {
                SocialPlatform.INSTAGRAM -> "linear-gradient(135deg, #F58529 0%, #DD2A7B 55%, #8134AF 100%)"
                SocialPlatform.FACEBOOK -> "linear-gradient(135deg, #1877F2 0%, #0E58C8 100%)"
                SocialPlatform.THREADS -> "linear-gradient(135deg, #1F2937 0%, #090A0D 100%)"
                SocialPlatform.TIKTOK -> "linear-gradient(135deg, #FE2C55 0%, #111827 100%)"
                SocialPlatform.YOUTUBE -> "linear-gradient(135deg, #EF4444 0%, #B91C1C 100%)"
                SocialPlatform.X -> "linear-gradient(135deg, #1F2937 0%, #0B0C10 100%)"
                SocialPlatform.WEBSITE -> "linear-gradient(135deg, #2563EB 0%, #1D4ED8 100%)"
            }
            val btnLabel = when (plat) {
                SocialPlatform.YOUTUBE -> "Subscribe $platName ($labelHtml)"
                SocialPlatform.WEBSITE -> "Kunjungi Website ($labelHtml)"
                else -> "Follow $platName ($labelHtml)"
            }
            """
                <a href="$urlHtml" target="_blank" rel="noopener noreferrer" class="gate-step-link" style="background: $bg; border: 1px solid rgba(255,255,255,0.16);" onclick="handleSocialLinkClick(this)">
                    <span>$btnLabel</span>
                </a>
            """.trimIndent()
        }

        val gateTitleHtml = if (hasVendorName) "Follow $vendorNameHtml Dulu" else "Follow Sosmed Studio Dulu"
        val studioRefHtml = if (hasVendorName) "<strong>$vendorNameHtml</strong>" else "studio"

        val gateSubtitleHtml = if (configuredAccounts.size > 1) {
            "Untuk melihat dan memilih foto pada sesi <strong>$clientName</strong>, silakan klik dan ikuti <strong>salah satu</strong> akun resmi $studioRefHtml di bawah ini."
        } else {
            val singlePlat = escapeHtml(configuredAccounts.firstOrNull()?.platform?.displayName ?: "Studio")
            "Untuk melihat dan memilih foto pada sesi <strong>$clientName</strong>, silakan klik tombol di bawah untuk membuka profil resmi <strong>$singlePlat</strong> $studioRefHtml terlebih dahulu."
        }

        val headerIdentityHtml = if (hasVendorName) {
            """
                <div class="vendor-title">$vendorNameHtml</div>
                <div class="session-subtitle-header">$clientName</div>
            """.trimIndent()
        } else {
            """
                <h1 class="session-title">$clientName</h1>
            """.trimIndent()
        }

        val gateDisplayStyle = if (gateRequired) "flex" else "none"
        val galleryDisplayStyle = if (gateRequired) "none" else "block"

        return """
<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>$pageTitleHtml</title>
    <style>
        :root {
            --bg: #0F1014;
            --surface: #181A20;
            --surface-elevated: #222630;
            --gold: #F5A623;
            --crimson: #E11D48;
            --blue: #3B82F6;
            --emerald: #10B981;
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
        /* Mandatory Follow Gate Overlay */
        .follow-gate-overlay {
            position: fixed;
            inset: 0;
            z-index: 250;
            background: radial-gradient(circle at top, rgba(245, 166, 35, 0.14), transparent 55%), #0F1014;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
            overflow-y: auto;
        }
        .follow-gate-card {
            width: 100%;
            max-width: 420px;
            max-height: 90vh;
            overflow-y: auto;
            background: var(--surface);
            border: 1px solid var(--border);
            border-radius: 22px;
            padding: 24px 20px;
            box-shadow: 0 20px 50px rgba(0, 0, 0, 0.55);
            text-align: center;
        }
        .gate-icon-wrap {
            width: 56px;
            height: 56px;
            margin: 0 auto 14px;
            border-radius: 16px;
            background: rgba(245, 166, 35, 0.16);
            border: 1px solid rgba(245, 166, 35, 0.35);
            display: flex;
            align-items: center;
            justify-content: center;
            color: var(--gold);
        }
        .gate-title {
            font-size: 20px;
            font-weight: 800;
            color: var(--text-primary);
            margin-bottom: 8px;
        }
        .gate-subtitle {
            font-size: 13px;
            line-height: 1.5;
            color: var(--text-secondary);
            margin-bottom: 18px;
        }
        .gate-studio-box {
            background: var(--surface-elevated);
            border: 1px solid var(--border);
            border-radius: 14px;
            padding: 12px 14px;
            margin-bottom: 18px;
            text-align: left;
        }
        .gate-studio-label {
            font-size: 11px;
            color: var(--text-secondary);
            text-transform: uppercase;
            letter-spacing: 0.6px;
            margin-bottom: 4px;
        }
        .gate-studio-name {
            font-size: 16px;
            font-weight: 700;
            color: var(--gold);
            word-break: break-word;
        }
        .gate-studio-url {
            font-size: 12px;
            color: #93C5FD;
            word-break: break-all;
            margin-top: 2px;
        }
        .gate-step-link {
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            width: 100%;
            min-height: 48px;
            padding: 12px 16px;
            border-radius: 14px;
            background: linear-gradient(135deg, #2563EB 0%, #1D4ED8 100%);
            color: #FFFFFF;
            font-size: 14px;
            font-weight: 700;
            text-decoration: none;
            margin-bottom: 0;
            transition: transform 0.15s ease, opacity 0.15s ease;
        }
        .gate-step-link:active { transform: scale(0.98); }
        .gate-step-link.clicked {
            background: linear-gradient(135deg, #059669 0%, #047857 100%);
        }
        .gate-confirm-btn {
            width: 100%;
            min-height: 48px;
            padding: 12px 16px;
            border-radius: 14px;
            border: 1px solid var(--border);
            background: #222630;
            color: #6B7280;
            font-size: 14px;
            font-weight: 700;
            cursor: not-allowed;
            transition: all 0.2s ease;
        }
        .gate-confirm-btn.ready {
            background: linear-gradient(135deg, var(--gold) 0%, #D97706 100%);
            border-color: var(--gold);
            color: #121316;
            cursor: pointer;
        }
        .gate-helper {
            margin-top: 12px;
            font-size: 11px;
            color: var(--text-secondary);
        }

        header {
            background: rgba(18, 20, 26, 0.92);
            border-bottom: 1px solid var(--border);
            padding: 12px 16px;
            position: sticky;
            top: 0;
            z-index: 50;
            backdrop-filter: blur(12px);
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
        }
        .header-identity {
            display: flex;
            flex-direction: column;
            min-width: 0;
            flex: 1;
        }
        .vendor-title {
            font-size: 15px;
            font-weight: 800;
            color: var(--gold);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            line-height: 1.25;
        }
        .session-subtitle-header {
            font-size: 12px;
            font-weight: 600;
            color: var(--text-secondary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            line-height: 1.25;
            margin-top: 2px;
        }
        .session-title {
            font-size: 16px;
            font-weight: 700;
            color: var(--text-primary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
        .live-status {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            padding: 4px 10px;
            border-radius: 999px;
            background: rgba(16, 185, 129, 0.12);
            border: 1px solid rgba(16, 185, 129, 0.28);
            font-size: 12px;
            color: var(--emerald);
            font-weight: 600;
            flex-shrink: 0;
            white-space: nowrap;
        }
        .live-dot {
            width: 7px;
            height: 7px;
            background-color: var(--emerald);
            border-radius: 50%;
        }
        .tabs {
            display: flex;
            gap: 8px;
            padding: 10px 16px 4px;
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
    <!-- Mandatory Social/Website Follow Gate -->
    <div id="followGateOverlay" class="follow-gate-overlay" style="display:$gateDisplayStyle;">
        <div class="follow-gate-card">
            <div class="gate-icon-wrap">
                <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                    <circle cx="8.5" cy="7" r="4"></circle>
                    <line x1="20" y1="8" x2="20" y2="14"></line>
                    <line x1="23" y1="11" x2="17" y2="11"></line>
                </svg>
            </div>
            <h2 class="gate-title">$gateTitleHtml</h2>
            <p class="gate-subtitle">
                $gateSubtitleHtml
            </p>
            <div style="display:flex;flex-direction:column;gap:8px;margin-bottom:12px;">
                $socialButtonsHtml
            </div>
            <button id="confirmFollowBtn" class="gate-confirm-btn" disabled onclick="handleConfirmFollow()">
                Pilih salah satu tombol di atas dulu
            </button>
            <p class="gate-helper">Aplikasi resmi atau browser akan terbuka langsung. Pilihan foto Anda tersimpan khusus untuk perangkat ini saja.</p>
        </div>
    </div>

    <div id="galleryMain" style="display:$galleryDisplayStyle;">
        <header>
            <div class="header-identity">
                $headerIdentityHtml
            </div>
            <div class="live-status">
                <div class="live-dot"></div>
                <span>Tersambung</span>
            </div>
        </header>

        <div class="tabs">
            <button id="tabAll" class="tab-btn active" onclick="switchTab('all')">
                Semua Foto (<span id="countAll">0</span>)
            </button>
            <button id="tabLiked" class="tab-btn tab-liked" onclick="switchTab('liked')">
                Pilihan Saya (<span id="countLiked">0</span>)
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
                <p style="font-weight:600;">Belum ada foto yang Anda pilih</p>
                <p style="font-size:12px;margin-top:4px;">Tap ikon hati pada foto untuk menyimpan pilihan perangkat Anda.</p>
            </div>
        </div>

        <div class="bottom-summary-bar">
            <div class="summary-text">
                <span>Pilihan Anda: <span id="likedSummaryCount" class="summary-count">0</span> foto</span>
            </div>
            <div class="bottom-btns">
                <a id="btnDlZip" href="/download/all-liked" class="dl-zip-btn" title="Unduh Paket ZIP Foto Favorit">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path><polyline points="7 10 12 15 17 10"></polyline><line x1="12" y1="15" x2="12" y2="3"></line></svg>
                    <span>Unduh ZIP</span>
                </a>
            </div>
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
        const GATE_REQUIRED = $gateRequired;
        const SESSION_ID = "$escapedSessionIdJs";
        const SOCIAL_SIGNATURE = "$escapedSocialSignatureJs";
        const DEVICE_STORAGE_KEY = 'fotosync_device_id';

        function getOrCreateDeviceId() {
            let id = '';
            try {
                id = localStorage.getItem(DEVICE_STORAGE_KEY) || '';
            } catch (_) {}
            if (!id) {
                id = 'dev_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 10) + Math.random().toString(36).slice(2, 6);
                try {
                    localStorage.setItem(DEVICE_STORAGE_KEY, id);
                } catch (_) {}
            }
            try {
                document.cookie = 'fotosync_device_id=' + encodeURIComponent(id) + '; path=/; max-age=31536000; SameSite=Lax';
            } catch (_) {}
            return id;
        }

        const DEVICE_ID = getOrCreateDeviceId();

        function hashString(str) {
            let h = 0;
            for (let i = 0; i < str.length; i++) {
                h = ((h << 5) - h) + str.charCodeAt(i);
                h |= 0;
            }
            return Math.abs(h).toString(36);
        }

        const GATE_STORAGE_KEY = 'fotosync_follow_ok_' + SESSION_ID + '_' + hashString(SOCIAL_SIGNATURE);
        const GATE_CLICKED_SESSION_KEY = 'fotosync_follow_clicked_' + SESSION_ID + '_' + hashString(SOCIAL_SIGNATURE);

        let isGateUnlocked = !GATE_REQUIRED;
        let hasClickedSocial = false;

        try {
            if (GATE_REQUIRED && localStorage.getItem(GATE_STORAGE_KEY) === '1') {
                isGateUnlocked = true;
            }
            if (sessionStorage.getItem(GATE_CLICKED_SESSION_KEY) === '1') {
                hasClickedSocial = true;
            }
        } catch (_) {}

        function applyGateUiState() {
            const overlay = document.getElementById('followGateOverlay');
            const gallery = document.getElementById('galleryMain');
            const confirmBtn = document.getElementById('confirmFollowBtn');

            if (isGateUnlocked) {
                overlay.style.display = 'none';
                gallery.style.display = 'block';
            } else {
                overlay.style.display = 'flex';
                gallery.style.display = 'none';
                if (hasClickedSocial) {
                    confirmBtn.disabled = false;
                    confirmBtn.classList.add('ready');
                    confirmBtn.innerText = 'Saya Sudah Follow, Buka Galeri';
                }
            }
        }

        function handleSocialLinkClick(el) {
            hasClickedSocial = true;
            try {
                sessionStorage.setItem(GATE_CLICKED_SESSION_KEY, '1');
            } catch (_) {}
            if (el && el.classList) {
                el.classList.add('clicked');
            }
            const confirmBtn = document.getElementById('confirmFollowBtn');
            confirmBtn.disabled = false;
            confirmBtn.classList.add('ready');
            confirmBtn.innerText = 'Saya Sudah Follow, Buka Galeri';
        }

        function handleConfirmFollow() {
            if (!hasClickedSocial) return;
            isGateUnlocked = true;
            try {
                localStorage.setItem(GATE_STORAGE_KEY, '1');
            } catch (_) {}
            applyGateUiState();
            fetchPhotos();
        }

        let allPhotos = [];
        let currentTab = 'all';
        let currentViewingId = null;
        let lastDataSignature = '';

        async function registerClientConnection() {
            try {
                await fetch('/api/connect?deviceId=' + encodeURIComponent(DEVICE_ID), {
                    headers: { 'X-Device-Id': DEVICE_ID }
                });
            } catch (_) {}
        }

        async function fetchPhotos() {
            if (!isGateUnlocked) return;
            try {
                const res = await fetch('/api/photos?deviceId=' + encodeURIComponent(DEVICE_ID), {
                    headers: { 'X-Device-Id': DEVICE_ID }
                });
                if (res.ok) {
                    const nextPhotos = await res.json();
                    const sig = nextPhotos.map(p => p.id + ':' + (p.isClientLiked ? '1' : '0')).join('|');
                    if (sig !== lastDataSignature) {
                        lastDataSignature = sig;
                        allPhotos = nextPhotos;
                        renderGallery();
                        updateCounts();
                    }
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
                zipBtn.href = '/download/all-liked?deviceId=' + encodeURIComponent(DEVICE_ID);
            } else {
                zipBtn.innerHTML = iconSvg + '<span>Unduh Semua ZIP</span>';
                zipBtn.href = '/download/session-zip?deviceId=' + encodeURIComponent(DEVICE_ID);
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
                img.src = '/photo?id=' + encodeURIComponent(photo.id) + '&thumb=1';
                img.loading = 'lazy';
                img.decoding = 'async';
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
                lastDataSignature = allPhotos.map(p => p.id + ':' + (p.isClientLiked ? '1' : '0')).join('|');
                updateCounts();
                renderGallery();
            }

            try {
                await fetch(
                    '/api/like?id=' + encodeURIComponent(photoId) + '&liked=' + liked + '&deviceId=' + encodeURIComponent(DEVICE_ID),
                    {
                        method: 'POST',
                        headers: { 'X-Device-Id': DEVICE_ID }
                    }
                );
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

        document.addEventListener('keydown', (e) => {
            if (!document.getElementById('lightbox').classList.contains('active')) return;
            if (e.key === 'ArrowLeft') prevPhoto();
            if (e.key === 'ArrowRight') nextPhoto();
            if (e.key === 'Escape') closeLightbox();
        });

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

        applyGateUiState();
        registerClientConnection();
        if (isGateUnlocked) {
            fetchPhotos();
        }
        setInterval(fetchPhotos, 3500);
    </script>
</body>
</html>
        """.trimIndent()
    }
}
