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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.PhotoItemEntity
import com.example.ui.theme.CobaltBlue
import com.example.ui.theme.CrimsonHeart
import com.example.ui.theme.StudioGold
import java.io.File

@Composable
fun PhotoCard(
    photo: PhotoItemEntity,
    onClick: () -> Unit,
    onToggleClientLike: () -> Unit,
    onTogglePhotographerSelect: () -> Unit,
    onDownloadPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            photo.isPhotographerSelected -> StudioGold.copy(alpha = 0.85f)
            else -> Color(0xFF222632)
        },
        label = "borderColor"
    )

    val imageModel: Any? = if (photo.localUri.startsWith("content://")) {
        android.net.Uri.parse(photo.localUri)
    } else if (photo.localUri.isNotEmpty()) {
        val f = File(photo.localUri)
        if (f.exists()) f else null
    } else null
    val hasValidFile = imageModel != null

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
                if (hasValidFile && imageModel != null) {
                    AsyncImage(
                        model = imageModel,
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

                // Top-Left Selection Checkmark Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(if (photo.isPhotographerSelected) StudioGold else Color(0xAA121318))
                        .clickable(onClick = onTogglePhotographerSelect)
                        .testTag("select_photo_${photo.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Pilih",
                        tint = if (photo.isPhotographerSelected) Color(0xFF121316) else Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Top-Right Rating Badge
                if (photo.rating > 0) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color(0xAA121318), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = StudioGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${photo.rating}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Professional Minimalist Footer with Ghost Icon Buttons (No Box Container Backgrounds)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = photo.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = String.format("%.1f MB • %s", photo.fileSize / (1024f * 1024f), photo.resolution),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8C93A3),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Ghost Icon Buttons (Transparent background, zero box container)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Download Ghost Icon Button
                    IconButton(
                        onClick = onDownloadPhoto,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("quick_download_photo_${photo.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Unduh Foto",
                            tint = CobaltBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Like Ghost Icon Button
                    IconButton(
                        onClick = onToggleClientLike,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("like_button_${photo.id}")
                    ) {
                        Icon(
                            imageVector = if (photo.isClientLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Suka",
                            tint = if (photo.isClientLiked) CrimsonHeart else Color(0xFF8C93A3),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
