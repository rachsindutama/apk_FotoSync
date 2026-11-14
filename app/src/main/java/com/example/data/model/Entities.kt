package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a photography session organized with YYYY-MM-DD folder convention.
 */
@Entity(tableName = "photo_sessions")
data class PhotoSessionEntity(
    @PrimaryKey val id: String,
    val folderName: String, // e.g. "2026-10-15_Sarah-Wedding_Ceremony" or picked folder name
    val folderUri: String = "", // Persisted SAF tree URI for existing device folder
    val sessionDate: String, // "YYYY-MM-DD"
    val clientName: String,
    val category: String, // "Wedding", "Graduation", "Studio", "Commercial", "Event"
    val clientNotes: String = "",
    val coverPhotoUrl: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

/**
 * Individual photo item within a session.
 */
@Entity(
    tableName = "photo_items",
    foreignKeys = [
        ForeignKey(
            entity = PhotoSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class PhotoItemEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val fileName: String, // e.g. "DSC_0412.JPG"
    val localUri: String, // local path, file uri, or drawable resource tag
    val fileSize: Long = 4500000L, // bytes
    val resolution: String = "6000 x 4000",
    val isClientLiked: Boolean = false,
    val isPhotographerSelected: Boolean = false,
    val rating: Int = 0, // 0 - 5 stars
    val capturedAt: Long = System.currentTimeMillis()
)

/**
 * Record of Google Drive cloud backup confirmations and exports.
 */
@Entity(
    tableName = "export_logs",
    foreignKeys = [
        ForeignKey(
            entity = PhotoSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ExportLogEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val cloudDestination: String = "Google Drive",
    val folderPath: String,
    val totalPhotosExported: Int,
    val totalBytes: Long,
    val filterType: String, // "ALL" or "CLIENT_LIKED_ONLY"
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "COMPLETED" // "COMPLETED", "FAILED"
)

/**
 * Per-guest (device + IP) photo favorite selection record so multiple guests
 * marking favorites on the local web portal never overwrite each other.
 */
@Entity(
    tableName = "guest_selections",
    foreignKeys = [
        ForeignKey(
            entity = PhotoSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PhotoItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["photoId"]),
        Index(value = ["sessionId", "photoId", "clientKey"], unique = true)
    ]
)
data class GuestSelectionEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val photoId: String,
    val clientKey: String,   // "$clientIp::$deviceId"
    val clientIp: String,
    val deviceLabel: String, // e.g. "Android • Chrome"
    val likedAt: Long = System.currentTimeMillis()
)

data class GuestIdentity(
    val clientKey: String,
    val clientIp: String,
    val deviceId: String,
    val deviceLabel: String
)

data class GuestSummary(
    val clientKey: String,
    val clientIp: String,
    val deviceLabel: String,
    val likedCount: Int,
    val isConnectedNow: Boolean
)

enum class SocialPlatform(
    val displayName: String,
    val baseUrl: String,
    val exampleHandle: String,
    val allowedDomains: List<String>
) {
    INSTAGRAM(
        displayName = "Instagram",
        baseUrl = "https://www.instagram.com/",
        exampleHandle = "namastudio",
        allowedDomains = listOf("instagram.com", "instagr.am")
    ),
    FACEBOOK(
        displayName = "Facebook",
        baseUrl = "https://www.facebook.com/",
        exampleHandle = "namastudio",
        allowedDomains = listOf("facebook.com", "fb.com", "fb.me")
    ),
    THREADS(
        displayName = "Threads",
        baseUrl = "https://www.threads.net/@",
        exampleHandle = "@namastudio",
        allowedDomains = listOf("threads.net", "threads.com")
    ),
    TIKTOK(
        displayName = "TikTok",
        baseUrl = "https://www.tiktok.com/@",
        exampleHandle = "@namastudio",
        allowedDomains = listOf("tiktok.com")
    ),
    YOUTUBE(
        displayName = "YouTube",
        baseUrl = "https://www.youtube.com/@",
        exampleHandle = "@namastudio",
        allowedDomains = listOf("youtube.com", "youtu.be")
    ),
    X(
        displayName = "X",
        baseUrl = "https://x.com/",
        exampleHandle = "namastudio",
        allowedDomains = listOf("x.com", "twitter.com")
    ),
    WEBSITE(
        displayName = "Website",
        baseUrl = "https://",
        exampleHandle = "studioanda.com",
        allowedDomains = emptyList()
    );

    companion object {
        fun fromNameOrNull(name: String?): SocialPlatform? {
            if (name.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }
}

sealed class SocialResolutionResult {
    data class Success(
        val platform: SocialPlatform,
        val officialUrl: String,
        val suggestedDisplayName: String
    ) : SocialResolutionResult()

    data class Error(val message: String) : SocialResolutionResult()
}

object SocialLinkHelper {
    private val webTldSuffixes = listOf(".com", ".net", ".org", ".id", ".co", ".io", ".me", ".app", ".xyz", ".info")

    fun detectPlatformFromUrl(rawInput: String): SocialPlatform? {
        val host = extractHost(rawInput) ?: return null
        return SocialPlatform.entries.firstOrNull { platform ->
            platform.allowedDomains.any { domain ->
                host == domain || host.endsWith(".$domain")
            }
        }
    }

    fun resolve(platform: SocialPlatform, rawInput: String): SocialResolutionResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return SocialResolutionResult.Error("Masukkan username atau link ${platform.displayName}")
        }
        if (trimmed.contains(' ')) {
            return SocialResolutionResult.Error("Username atau link tidak boleh mengandung spasi")
        }

        val hasScheme = trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        val hasSlash = trimmed.contains('/')
        val lower = trimmed.lowercase()
        val looksLikeKnownDomain = SocialPlatform.entries.any { p ->
            p.allowedDomains.any { domain -> lower.contains(domain) }
        }
        val looksLikeWebDomain = !trimmed.startsWith("@") &&
            (lower.startsWith("www.") || webTldSuffixes.any { tld -> lower.endsWith(tld) || lower.contains("$tld/") })

        val treatAsUrl = platform == SocialPlatform.WEBSITE || hasScheme || hasSlash || looksLikeKnownDomain || looksLikeWebDomain

        return if (treatAsUrl) {
            val normalizedUrl = if (hasScheme) trimmed else "https://$trimmed"
            val host = extractHost(normalizedUrl)
            if (host.isNullOrBlank() || !host.contains('.')) {
                return SocialResolutionResult.Error("Format link ${platform.displayName} tidak valid")
            }

            if (platform == SocialPlatform.WEBSITE) {
                val detected = detectPlatformFromUrl(normalizedUrl) ?: SocialPlatform.WEBSITE
                SocialResolutionResult.Success(
                    platform = SocialPlatform.WEBSITE,
                    officialUrl = normalizedUrl,
                    suggestedDisplayName = extractDisplayLabel(detected, normalizedUrl, host)
                )
            } else {
                val detected = detectPlatformFromUrl(normalizedUrl)
                when {
                    detected == null -> SocialResolutionResult.Error(
                        "Link harus menuju ${platform.displayName} (atau isi di tab Website untuk domain lain)"
                    )
                    detected != platform -> SocialResolutionResult.Error(
                        "Link ini mengarah ke ${detected.displayName}, bukan ${platform.displayName}. Pindah ke tab ${detected.displayName} untuk mengisinya."
                    )
                    else -> SocialResolutionResult.Success(
                        platform = platform,
                        officialUrl = normalizedUrl,
                        suggestedDisplayName = extractDisplayLabel(platform, normalizedUrl, host)
                    )
                }
            }
        } else {
            val cleanHandle = trimmed.trimStart('@').trim('/')
            if (cleanHandle.isEmpty()) {
                return SocialResolutionResult.Error("Username ${platform.displayName} tidak valid")
            }
            val officialUrl = when (platform) {
                SocialPlatform.INSTAGRAM -> "https://www.instagram.com/$cleanHandle/"
                SocialPlatform.FACEBOOK -> "https://www.facebook.com/$cleanHandle"
                SocialPlatform.THREADS -> "https://www.threads.net/@$cleanHandle"
                SocialPlatform.TIKTOK -> "https://www.tiktok.com/@$cleanHandle"
                SocialPlatform.YOUTUBE -> "https://www.youtube.com/@$cleanHandle"
                SocialPlatform.X -> "https://x.com/$cleanHandle"
                SocialPlatform.WEBSITE -> "https://$cleanHandle"
            }
            val suggested = when (platform) {
                SocialPlatform.FACEBOOK -> cleanHandle
                else -> "@$cleanHandle"
            }
            SocialResolutionResult.Success(
                platform = platform,
                officialUrl = officialUrl,
                suggestedDisplayName = suggested
            )
        }
    }

    private fun extractHost(rawUrl: String): String? {
        return try {
            val withScheme = if (rawUrl.startsWith("http://", ignoreCase = true) ||
                rawUrl.startsWith("https://", ignoreCase = true)
            ) rawUrl else "https://$rawUrl"
            java.net.URI(withScheme).host?.lowercase()?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDisplayLabel(platform: SocialPlatform, url: String, host: String): String {
        return try {
            val pathSegment = java.net.URI(url).path
                ?.trim('/')
                ?.split('/')
                ?.firstOrNull { it.isNotBlank() && !it.equals("p", ignoreCase = true) }
            when {
                platform == SocialPlatform.WEBSITE || pathSegment.isNullOrBlank() -> host
                platform == SocialPlatform.FACEBOOK -> pathSegment
                pathSegment.startsWith("@") -> pathSegment
                else -> "@$pathSegment"
            }
        } catch (_: Exception) {
            host
        }
    }
}

data class SocialAccountEntry(
    val platform: SocialPlatform,
    val socialName: String = "",
    val socialUrl: String = ""
) {
    val isConfigured: Boolean
        get() = socialUrl.isNotBlank()
}

data class StudioSocialProfile(
    val vendorName: String = "",
    val accounts: Map<SocialPlatform, SocialAccountEntry> = emptyMap(),
    val lastEditedPlatform: SocialPlatform = SocialPlatform.INSTAGRAM
) {
    val configuredAccounts: List<SocialAccountEntry>
        get() = SocialPlatform.entries.mapNotNull { platform ->
            accounts[platform]?.takeIf { it.isConfigured }
        }

    val isConfigured: Boolean
        get() = configuredAccounts.isNotEmpty()

    val hasAnyIdentityOrSocial: Boolean
        get() = vendorName.isNotBlank() || isConfigured

    fun accountFor(platform: SocialPlatform): SocialAccountEntry? =
        accounts[platform]?.takeIf { it.isConfigured }

    val summaryLabel: String
        get() = configuredAccounts.joinToString(" • ") { acc ->
            val label = acc.socialName.ifBlank { acc.socialUrl }
            "${acc.platform.displayName}: $label"
        }
}


