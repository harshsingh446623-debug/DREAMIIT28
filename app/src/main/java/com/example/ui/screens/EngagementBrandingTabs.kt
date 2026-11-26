package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ChannelBrandingInfo
import com.example.data.model.CommentThreadItem
import com.example.data.model.ComparedVideoMetric
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.ElevatedCardHigh
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.text.NumberFormat
import kotlin.math.max

/**
 * Feature #8: YouTube Comment Manager (fetch, search, unread filter, reply, moderate, delete).
 */
@Composable
fun CommentManagerTab(
    comments: List<CommentThreadItem>,
    statusMessage: String,
    loading: Boolean,
    onRefreshComments: (String?) -> Unit,
    onReplyComment: (parentCommentId: String, replyText: String) -> Unit,
    onModerateComment: (commentId: String, deletePermanently: Boolean) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var unreadOnly by remember { mutableStateOf(false) }
    var activeReplyTargetId by remember { mutableStateOf<String?>(null) }
    var replyDraft by remember { mutableStateOf("") }

    val filtered = remember(comments, searchQuery, unreadOnly) {
        comments.filter { item ->
            val matchUnread = !unreadOnly || item.isUnread
            val matchSearch = searchQuery.isBlank() ||
                item.text.contains(searchQuery, ignoreCase = true) ||
                item.authorName.contains(searchQuery, ignoreCase = true)
            matchUnread && matchSearch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search comments or authors...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("comments_search_input")
                )
                OutlinedButton(
                    onClick = { onRefreshComments(searchQuery.takeIf { it.isNotBlank() }) },
                    modifier = Modifier.testTag("comments_refresh_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Comments", tint = ElectricCyan)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !unreadOnly,
                    onClick = { unreadOnly = false },
                    label = { Text("All (${comments.size})") }
                )
                FilterChip(
                    selected = unreadOnly,
                    onClick = { unreadOnly = true },
                    label = { Text("Unread / Unreplied (${comments.count { it.isUnread }})") },
                    modifier = Modifier.testTag("comments_unread_filter")
                )
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ElectricCyan)
                }
            }
        }

        item {
            Text(statusMessage, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        if (filtered.isEmpty() && !loading) {
            item {
                Text("No matching comments to display.", color = TextSecondary)
            }
        } else {
            items(filtered, key = { it.threadId }) { comment ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, if (comment.isUnread) ElectricCyan.copy(alpha = 0.6f) else BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                if (!comment.authorAvatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = comment.authorAvatarUrl,
                                        contentDescription = comment.authorName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(28.dp).clip(CircleShape)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(comment.authorName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (comment.isUnread) {
                                StatusBadge("UNREAD", ElectricCyan)
                            }
                        }

                        Text(comment.text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ThumbUp, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("${comment.likeCount}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                            }
                            Text("${comment.replyCount} replies • Video ${comment.videoId}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (comment.canReply) {
                                TextButton(onClick = {
                                    activeReplyTargetId = if (activeReplyTargetId == comment.topCommentId) null else comment.topCommentId
                                    replyDraft = ""
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reply", color = ElectricCyan)
                                }
                            }
                            TextButton(onClick = { onModerateComment(comment.topCommentId, false) }) {
                                Icon(Icons.Default.Block, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Hold", color = WarningAmber)
                            }
                            TextButton(onClick = { onModerateComment(comment.topCommentId, true) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = LiveCrimson, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Delete", color = LiveCrimson)
                            }
                        }

                        if (activeReplyTargetId == comment.topCommentId) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = replyDraft,
                                    onValueChange = { replyDraft = it },
                                    label = { Text("Write reply...") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        if (replyDraft.isNotBlank()) {
                                            onReplyComment(comment.topCommentId, replyDraft.trim())
                                            activeReplyTargetId = null
                                            replyDraft = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Reply", tint = ElectricCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Feature #9: Channel Branding Manager (Banner, description, keywords preview & YouTube API upload + avatar preview).
 */
@Composable
fun ChannelBrandingTab(
    branding: ChannelBrandingInfo,
    busy: Boolean,
    onLoadBranding: () -> Unit,
    onSaveBranding: (description: String, keywords: String, bannerJpegBytes: ByteArray?) -> Unit
) {
    val context = LocalContext.current
    var descInput by remember(branding.description) { mutableStateOf(branding.description) }
    var keywordsInput by remember(branding.keywords) { mutableStateOf(branding.keywords) }
    var pickedBannerBytes by remember { mutableStateOf<ByteArray?>(null) }

    val bannerPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pickedBannerBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Channel Branding Preview", style = MaterialTheme.typography.titleMedium, color = Color.White)
                OutlinedButton(onClick = onLoadBranding, enabled = !busy, modifier = Modifier.testTag("load_branding_btn")) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(4.dp))
                    Text("Sync")
                }
            }
        }

        // Live Visual Preview Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 5f)
                            .background(ElevatedCardHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        val bytes = pickedBannerBytes
                        if (bytes != null) {
                            val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "New Channel Banner Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else if (!branding.bannerImageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = branding.bannerImageUrl,
                                contentDescription = "Current YouTube Channel Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Channel Banner Preview (16:9 / 2048×1152 recommended)", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!branding.profileImageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = branding.profileImageUrl,
                                contentDescription = "Channel Profile Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(52.dp).clip(CircleShape)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(branding.title.ifBlank { "Channel Not Loaded" }, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(
                                descInput.ifBlank { branding.statusMessage },
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item {
            Surface(
                color = ObsidianSurface,
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "YouTube Data API updates channel banner, description, and keywords directly. Channel profile picture changes are linked through your Google Account avatar.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { bannerPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().testTag("pick_channel_banner_btn")
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = ElectricCyan)
                Spacer(Modifier.width(8.dp))
                Text(if (pickedBannerBytes != null) "Banner Image Selected (Ready)" else "Select New Channel Banner Image")
            }
        }

        item {
            OutlinedTextField(
                value = descInput,
                onValueChange = { descInput = it },
                label = { Text("Channel Description") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("branding_desc_input")
            )
        }

        item {
            OutlinedTextField(
                value = keywordsInput,
                onValueChange = { keywordsInput = it },
                label = { Text("Channel Keywords (comma/space separated)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = { onSaveBranding(descInput, keywordsInput, pickedBannerBytes) },
                enabled = !busy && branding.channelId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_branding_btn")
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Uploading Branding..." else "Upload Branding via YouTube API")
            }
        }
    }
}

/**
 * Feature #10: Video Comparison (Compare 2+ videos across Views, Likes, Comments & Watch Time with Canvas graphs).
 */
@Composable
fun VideoComparisonTab(
    videos: List<ComparedVideoMetric>,
    statusMessage: String,
    onLoadVideos: () -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    var metricIndex by remember { mutableIntStateOf(0) } // 0=Views, 1=Likes, 2=Comments, 3=WatchTime
    val nf = NumberFormat.getIntegerInstance()

    val selectedVideos = remember(videos, selectedIds.toList()) {
        val chosen = videos.filter { selectedIds.contains(it.videoId) }
        if (chosen.size >= 2) chosen else videos.take(minOf(3, videos.size))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Compare YouTube Videos", style = MaterialTheme.typography.titleMedium, color = Color.White)
                OutlinedButton(onClick = onLoadVideos, modifier = Modifier.testTag("load_compare_videos_btn")) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(4.dp))
                    Text("Load Videos")
                }
            }
        }

        item {
            Text(statusMessage, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        if (videos.isNotEmpty()) {
            item {
                Text("Select 2–4 Videos to Compare:", color = ElectricCyan, style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    videos.forEach { vid ->
                        val isSel = selectedVideos.any { it.videoId == vid.videoId }
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                if (selectedIds.contains(vid.videoId)) {
                                    selectedIds.remove(vid.videoId)
                                } else if (selectedIds.size < 4) {
                                    selectedIds.add(vid.videoId)
                                }
                            },
                            label = { Text(vid.title.take(22), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Views", "Likes", "Comments", "Watch Time").forEachIndexed { idx, label ->
                        FilterChip(
                            selected = metricIndex == idx,
                            onClick = { metricIndex = idx },
                            label = { Text(label) }
                        )
                    }
                }
            }

            item {
                val values = selectedVideos.map { vid ->
                    when (metricIndex) {
                        0 -> vid.viewCount
                        1 -> vid.likeCount
                        2 -> vid.commentCount
                        else -> vid.watchTimeMinutes ?: 0L
                    }
                }
                val maxVal = max(1L, values.maxOrNull() ?: 1L).toFloat()
                val barColors = listOf(ElectricBlue, ElectricCyan, SuccessEmerald, WarningAmber)

                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = when (metricIndex) {
                                0 -> "Views Performance Graph"
                                1 -> "Likes Performance Graph"
                                2 -> "Comments Performance Graph"
                                else -> "Watch Time (Minutes) Graph"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        ) {
                            val count = values.size.coerceAtLeast(1)
                            val slotW = size.width / count
                            val barW = slotW * 0.5f
                            values.forEachIndexed { idx, v ->
                                val ratio = (v.toFloat() / maxVal).coerceIn(0.05f, 1f)
                                val h = size.height * 0.86f * ratio
                                val left = idx * slotW + (slotW - barW) / 2f
                                val top = size.height - h
                                drawRoundRect(
                                    color = barColors[idx % barColors.size],
                                    topLeft = Offset(left, top),
                                    size = Size(barW, h),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                            }
                        }
                        selectedVideos.forEachIndexed { idx, vid ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${idx + 1}. ${vid.title}",
                                    color = barColors[idx % barColors.size],
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = when (metricIndex) {
                                        0 -> "${nf.format(vid.viewCount)} views"
                                        1 -> "${nf.format(vid.likeCount)} likes"
                                        2 -> "${nf.format(vid.commentCount)} comments"
                                        else -> vid.watchTimeMinutes?.let { "${nf.format(it)} min" } ?: "Unavailable"
                                    },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            items(selectedVideos, key = { it.videoId }) { vid ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(vid.title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = "Views: ${nf.format(vid.viewCount)} • Likes: ${nf.format(vid.likeCount)} • Comments: ${nf.format(vid.commentCount)}",
                            color = ElectricCyan,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Watch Time: ${vid.watchTimeMinutes?.let { "${nf.format(it)} min" } ?: "Unavailable (Analytics API scope)"}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
