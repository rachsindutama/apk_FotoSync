package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.model.PhotoItemEntity
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.StudioGold
import java.io.File

@Composable
fun PhotoCard(
    photo: PhotoItemEntity,
    guestLikeCount: Int = if (photo.isClientLiked) 1 else 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val borderColor by animateColorAsState(
        targetValue = if (photo.isClientLiked) CrimsonHeart.copy(alpha = 0.75f) else Color(0xFF222632),
        label = "borderColor"
    )

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
                .size(400, 300)
                .precision(Precision.INEXACT)
                .allowHardware(true)
                .crossfade(150)
                .memoryCacheKey("thumb_${photo.id}")
                .diskCacheKey("thumb_${photo.id}")
                .build()
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("photo_card_${photo.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161922)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column {
            // Cinematic Photo Preview (4:3 Aspect Ratio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(Color(0xFF0D0E13)),
                contentAlignment = Alignment.Center
            ) {
                if (imageRequest != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = photo.fileName,
                        contentScale = ContentScale.Crop,
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
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                // Passive read-only badge indicating guest favorite(s) on the web portal
                if (photo.isClientLiked) {
                    val badgeText = if (guestLikeCount > 1) "$guestLikeCount Guest" else "Guest"
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(CrimsonHeart.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("guest_liked_badge_${photo.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Disukai Guest",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Clean Minimalist Footer (File name & size only)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = photo.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
                val sizeMb = String.format("%.1f MB", photo.fileSize / (1024f * 1024f))
                val metaText = if (photo.resolution.isNotBlank()) "$sizeMb • ${photo.resolution}" else sizeMb
                Text(
                    text = metaText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8C93A3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
