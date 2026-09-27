package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.audio.AudioController
import com.example.data.local.ListeningHistoryEntity
import com.example.data.repository.MusicRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class DaywiseGroup(
    val date: LocalDate,
    val dayTitle: String,
    val fullDateFormatted: String,
    val items: List<ListeningHistoryEntity>,
    val totalSeconds: Long,
    val uniqueSongCount: Int
)

fun formatListeningSeconds(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}

fun formatTimeShort(timestampMs: Long): String {
    return try {
        Instant.ofEpochMilli(timestampMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    } catch (_: Exception) {
        ""
    }
}

@Composable
fun ListeningHistorySummaryCard(
    historyList: List<ListeningHistoryEntity>,
    onOpenFullHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }

    val todayItems = remember(historyList) {
        historyList.filter {
            val d = Instant.ofEpochMilli(it.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            d == today
        }
    }

    val todaySeconds = remember(todayItems) {
        todayItems.sumOf { it.effectiveListenedSeconds }
    }

    val monthItems = remember(historyList) {
        historyList.filter {
            val d = Instant.ofEpochMilli(it.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            d.year == today.year && d.monthValue == today.monthValue
        }
    }

    val monthName = remember {
        today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpenFullHistory() },
        colors = CardDefaults.cardColors(containerColor = AlaktraCard),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder))
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AlaktraSurface)
                        .border(1.dp, AlaktraBorder, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = AlaktraMint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Listening History",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AlaktraTextPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AlaktraMint.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (todayItems.isNotEmpty()) "Active Today" else monthName,
                                color = AlaktraMint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = if (todayItems.isNotEmpty()) {
                            "${todayItems.size} tracks played today • ${formatListeningSeconds(todaySeconds)} listened"
                        } else if (monthItems.isNotEmpty()) {
                            "${monthItems.size} tracks this month • ${formatListeningSeconds(monthItems.sumOf { it.effectiveListenedSeconds })} listened"
                        } else {
                            "Track songs listened to day by day with real listen time"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = AlaktraTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View History",
                    tint = AlaktraTextSecondary
                )
            }

            // Preview of recent played tracks
            if (historyList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = AlaktraBorder.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (todayItems.isNotEmpty()) "PLAYED TODAY" else "RECENT PLAYS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlaktraTextMuted,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Full Day Stats →",
                        color = AlaktraMint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                val previewItems = if (todayItems.isNotEmpty()) todayItems.take(2) else historyList.take(2)
                previewItems.forEach { item ->
                    val timeStr = remember(item.playedAt) { formatTimeShort(item.playedAt) }
                    val listenedStr = remember(item.effectiveListenedSeconds) {
                        formatListeningSeconds(item.effectiveListenedSeconds)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(AlaktraSurface)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = AlaktraTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.artist} • $timeStr",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlaktraTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AlaktraSurface)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = listenedStr,
                                color = AlaktraMint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullListeningHistorySheet(
    historyList: List<ListeningHistoryEntity>,
    repository: MusicRepository,
    audioController: AudioController,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var searchQuery by remember { mutableStateOf("") }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Search query filter over all recorded history
    val filteredItems = remember(historyList, searchQuery) {
        if (searchQuery.isBlank()) {
            historyList
        } else {
            val q = searchQuery.trim().lowercase()
            historyList.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
    }

    // Group items daywise, newest day first
    val daywiseGroups = remember(filteredItems) {
        filteredItems.groupBy { item ->
            Instant.ofEpochMilli(item.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.entries
            .sortedByDescending { it.key }
            .map { (date, items) ->
                val dayTitle = when {
                    date == today -> "Today"
                    date == today.minusDays(1) -> "Yesterday"
                    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                }
                val fullDate = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
                val totalSec = items.sumOf { it.effectiveListenedSeconds }
                val uniqueSongs = items.map { it.songId }.distinct().size
                DaywiseGroup(
                    date = date,
                    dayTitle = dayTitle,
                    fullDateFormatted = fullDate,
                    items = items.sortedByDescending { it.playedAt },
                    totalSeconds = totalSec,
                    uniqueSongCount = uniqueSongs
                )
            }
    }

    // Today specific stats
    val todayStats = remember(historyList) {
        val items = historyList.filter {
            val d = Instant.ofEpochMilli(it.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            d == today
        }
        val totalSec = items.sumOf { it.effectiveListenedSeconds }
        val uniqueCount = items.map { it.songId }.distinct().size
        Triple(items.size, uniqueCount, totalSec)
    }

    // Current selection stats
    val totalTracksInView = filteredItems.size
    val totalSecondsInView = remember(filteredItems) {
        filteredItems.sumOf { it.effectiveListenedSeconds }
    }
    val uniqueTracksInView = remember(filteredItems) {
        filteredItems.map { it.songId }.distinct().size
    }

    Scaffold(
        containerColor = AlaktraBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Listening History",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AlaktraTextPrimary
                        )
                        Text(
                            text = "${historyList.size} total plays recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = AlaktraMint
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AlaktraTextPrimary
                        )
                    }
                },
                actions = {
                    if (historyList.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear History",
                                tint = AlaktraTextSecondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AlaktraBackground)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                // Today's Live Listening Banner (Always visible at top)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = AlaktraCard),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder))
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(AlaktraMint, AlaktraCyan)))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = Color(0xFF041C12),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Today's Listening Stats",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = AlaktraTextPrimary
                                    )
                                    Text(
                                        text = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AlaktraTextSecondary
                                    )
                                }
                            }

                            // Quick Play Today Button
                            val todayList = remember(historyList) {
                                historyList.filter {
                                    val d = Instant.ofEpochMilli(it.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                                    d == today
                                }
                            }
                            if (todayList.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        val songs = todayList.map { it.toSong() }
                                        audioController.playQueue(songs, 0, "Today's History")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AlaktraMint,
                                        contentColor = Color(0xFF041C12)
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Play Today", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats Pillars (Songs listened to, time listened for the whole day, unique songs)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Total Songs Played Today
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AlaktraSurface)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "SONGS TODAY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AlaktraTextMuted,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${todayStats.first}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AlaktraTextPrimary
                                    )
                                    Text(
                                        text = "${todayStats.second} unique",
                                        fontSize = 11.sp,
                                        color = AlaktraTextSecondary
                                    )
                                }
                            }

                            // Total Time Listened for the Whole Day
                            Box(
                                modifier = Modifier
                                    .weight(1.3f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AlaktraSurface)
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "LISTEN TIME TODAY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AlaktraTextMuted,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatListeningSeconds(todayStats.third),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AlaktraMint
                                    )
                                    Text(
                                        text = if (todayStats.first > 0) "Accurate to seconds" else "No plays yet today",
                                        fontSize = 11.sp,
                                        color = AlaktraTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // Search Bar inside History
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search songs or artists in history...", color = AlaktraTextMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = AlaktraTextMuted, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search", tint = AlaktraTextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AlaktraSurface,
                        unfocusedContainerColor = AlaktraSurface,
                        focusedBorderColor = AlaktraMint,
                        unfocusedBorderColor = AlaktraBorder,
                        focusedTextColor = AlaktraTextPrimary,
                        unfocusedTextColor = AlaktraTextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Main Content: Daywise List or Empty State
                if (daywiseGroups.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(AlaktraSurface)
                                    .border(1.dp, AlaktraBorder, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = AlaktraMint,
                                    modifier = Modifier.size(34.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (searchQuery.isNotBlank()) "No songs match \"$searchQuery\"" else "No listening history recorded yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AlaktraTextPrimary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = if (searchQuery.isNotBlank()) "Try checking the spelling or searching another artist or song title." else "Play songs from your library — each played track and your exact listening time will be recorded here automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlaktraTextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        daywiseGroups.forEach { dayGroup ->
                            // Sticky Day Header showing: Date, Songs count for the day, and Total listen time for the whole day!
                            item(key = "header_${dayGroup.date}") {
                                DaywiseHeader(
                                    group = dayGroup,
                                    onPlayAllDay = {
                                        val daySongs = dayGroup.items.map { it.toSong() }
                                        audioController.playQueue(daySongs, 0, dayGroup.dayTitle)
                                    }
                                )
                            }

                            // Individual songs listened to during that day
                            items(dayGroup.items, key = { it.id }) { historyItem ->
                                HistorySongRow(
                                    item = historyItem,
                                    onPlay = {
                                        val song = historyItem.toSong()
                                        val allSongs = dayGroup.items.map { it.toSong() }
                                        val index = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                        audioController.playQueue(allSongs, index, dayGroup.dayTitle)
                                    },
                                    onDelete = {
                                        scope.launch {
                                            repository.deleteHistoryItem(historyItem.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirm Clear All Dialog
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Clear Listening History?", color = AlaktraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will permanently delete all stored listening history and daily analytics.",
                    color = AlaktraTextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.clearListeningHistory()
                            showClearConfirmDialog = false
                        }
                    }
                ) {
                    Text("Clear All", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Cancel", color = AlaktraTextSecondary)
                }
            },
            containerColor = AlaktraCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun DaywiseHeader(
    group: DaywiseGroup,
    onPlayAllDay: () -> Unit
) {
    val durationText = formatListeningSeconds(group.totalSeconds)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(AlaktraBackground)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(AlaktraSurface)
                    .border(1.dp, AlaktraBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = AlaktraMint,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = group.dayTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = AlaktraTextPrimary
                )
                Text(
                    text = group.fullDateFormatted,
                    fontSize = 11.sp,
                    color = AlaktraTextMuted
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Day Summary Pill: exact song count and total listen time for the day
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AlaktraSurface)
                    .border(1.dp, AlaktraBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${group.items.size} ${if (group.items.size == 1) "song" else "songs"} • $durationText",
                    color = AlaktraMint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onPlayAllDay,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(AlaktraMint.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Day",
                    tint = AlaktraMint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun HistorySongRow(
    item: ListeningHistoryEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormatted = remember(item.playedAt) { formatTimeShort(item.playedAt) }
    val listenedSec = item.effectiveListenedSeconds
    val listenedFormatted = remember(listenedSec) { formatListeningSeconds(listenedSec) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        // Cover Art
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AlaktraSurface)
                .border(1.dp, AlaktraBorder, RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title, Artist, and Listen Time details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = AlaktraTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = AlaktraTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Text(
                    text = " • $timeFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlaktraTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time listened badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(AlaktraSurface)
                .border(1.dp, AlaktraBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = AlaktraMint,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = listenedFormatted,
                    color = AlaktraMint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Play Button
        IconButton(
            onClick = onPlay,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Track",
                tint = AlaktraTextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Delete Item Button
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove from history",
                tint = AlaktraTextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
