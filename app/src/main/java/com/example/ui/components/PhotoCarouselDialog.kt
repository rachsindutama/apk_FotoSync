package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.model.PhotoItemEntity
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.StudioGold
import java.io.File

@Composable
fun PhotoCarouselDialog(
    photos: List<PhotoItemEntity>,
    initialPhotoId: String,
    guestLikeCountsByPhotoId: Map<String, Int> = emptyMap(),
    onDismiss: () -> Unit
) {
    if (photos.isEmpty()) return

    val initialIndex = remember(initialPhotoId, photos) {
        val idx = photos.indexOfFirst { it.id == initialPhotoId }
        if (idx >= 0) idx else 0
    }

    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { photos.size })
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D0E12)),
            color = Color(0xFF0D0E12)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                val currentPhoto = photos.getOrNull(pagerState.currentPage)
                val fileSizeMb = currentPhoto?.let { String.format("%.2f MB", it.fileSize / (1024f * 1024f)) } ?: ""

                // Clean Top Header with Close Button, File Metadata, and Passive Guest-Liked Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .background(Color(0xFF1F2430), CircleShape)
                            .size(48.dp)
                            .testTag("carousel_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = Color.White
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = currentPhoto?.fileName ?: "Foto Studio",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitleParts = buildList {
                            add("${pagerState.currentPage + 1} dari ${photos.size}")
                            currentPhoto?.resolution?.takeIf { it.isNotBlank() }?.let { add(it) }
                            if (fileSizeMb.isNotBlank()) add(fileSizeMb)
                        }.joinToString(" • ")
                        Text(
                            text = subtitleParts,
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (currentPhoto?.isClientLiked == true) {
                        val likeCount = guestLikeCountsByPhotoId[currentPhoto.id] ?: 1
                        val badgeLabel = if (likeCount > 1) "$likeCount Guest" else "Guest"
                        Row(
                            modifier = Modifier
                                .background(CrimsonHeart.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("carousel_guest_liked_badge"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Disukai Guest",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = badgeLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }

                // Clean Distraction-Free Full-Screen Horizontal Swipe Pager
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 14.dp)
                ) { page ->
                    val photo = photos[page]
                    val imageRequest = remember(photo.id, photo.localUri) {
                        if (photo.localUri.isEmpty()) {
                            null
                        } else {
                            val data: Any = if (photo.localUri.startsWith("content://")) {
                                android.net.Uri.parse(photo.localUri)
                            } else {
                                File(photo.localUri)
                            }
                            ImageRequest.Builder(context)
                                .data(data)
                                .size(1600, 1600)
                                .precision(Precision.INEXACT)
                                .crossfade(150)
                                .memoryCacheKey("full_${photo.id}")
                                .build()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF14161C)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageRequest != null) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = photo.fileName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Photo,
                                    contentDescription = null,
                                    tint = StudioGold.copy(alpha = 0.5f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = photo.fileName,
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
