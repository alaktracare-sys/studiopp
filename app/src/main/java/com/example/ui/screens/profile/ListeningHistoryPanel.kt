package com.example.ui.screens.profile

import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
    val totalSeconds: Long
)

@Composable
fun ListeningHistorySummaryCard(
    historyList: List<ListeningHistoryEntity>,
    onOpenFullHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMonthItems = remember(historyList) {
        val now = LocalDate.now()
        historyList.filter {
            val d = Instant.ofEpochMilli(it.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            d.year == now.year && d.monthValue == now.monthValue
        }
    }

    val totalMinutes = remember(currentMonthItems) {
        val totalSec = currentMonthItems.sumOf { it.duration.toLong() }
        totalSec / 60
    }

    val monthName = remember {
        LocalDate.now().month.getDisplayName(TextStyle.FULL, Locale.getDefault())
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AlaktraSurface)
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
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
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
                                text = monthName,
                                color = AlaktraMint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (currentMonthItems.isEmpty()) {
                            "Track songs listened to in $monthName day by day"
                        } else {
                            "${currentMonthItems.size} tracks played this month • ${totalMinutes}m listened"
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

            // Preview of last 2 played tracks if available
            if (currentMonthItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = AlaktraBorder.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "RECENTLY PLAYED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = AlaktraTextMuted,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                currentMonthItems.take(2).forEach { item ->
                    val timeStr = remember(item.playedAt) {
                        Instant.ofEpochMilli(item.playedAt)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("h:mm a"))
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
                                .size(34.dp)
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
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to view daywise history →",
                    color = AlaktraMint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
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
    val currentMonthName = remember { today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
    val currentYear = remember { today.year }

    // Filter automatically for the current month only
    val monthItems = remember(historyList) {
        historyList.filter { item ->
            val itemDate = Instant.ofEpochMilli(item.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            itemDate.year == currentYear && itemDate.monthValue == today.monthValue
        }
    }

    // Group items daywise, sorted with newest day first
    val daywiseGroups = remember(monthItems) {
        val grouped = monthItems.groupBy { item ->
            Instant.ofEpochMilli(item.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }

        grouped.entries
            .sortedByDescending { it.key }
            .map { (date, items) ->
                val dayTitle = when {
                    date == today -> "Today"
                    date == today.minusDays(1) -> "Yesterday"
                    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                }
                val fullDate = date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
                val totalSec = items.sumOf { it.duration.toLong() }
                DaywiseGroup(
                    date = date,
                    dayTitle = dayTitle,
                    fullDateFormatted = fullDate,
                    items = items.sortedByDescending { it.playedAt },
                    totalSeconds = totalSec
                )
            }
    }

    // Monthly summary stats
    val totalTracksInMonth = monthItems.size
    val totalMinutesInMonth = remember(monthItems) {
        monthItems.sumOf { it.duration.toLong() } / 60
    }
    val activeDaysInMonth = remember(monthItems) {
        monthItems.map {
            Instant.ofEpochMilli(it.playedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }.distinct().size
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
                            text = "$currentMonthName $currentYear",
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
                    .widthIn(max = 760.dp)
            ) {
            // Month Stats Hero Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        Column {
                            Text(
                                text = "$currentMonthName $currentYear",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AlaktraTextPrimary
                            )
                            Text(
                                text = "Monthly Listening Summary",
                                style = MaterialTheme.typography.bodySmall,
                                color = AlaktraTextSecondary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AlaktraSurface)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$activeDaysInMonth active days",
                                color = AlaktraMint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total tracks pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AlaktraSurface)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "TRACKS PLAYED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlaktraTextMuted,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$totalTracksInMonth",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlaktraTextPrimary
                                )
                            }
                        }

                        // Total time pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AlaktraSurface)
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL LISTEN TIME",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlaktraTextMuted,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val hours = totalMinutesInMonth / 60
                                val mins = totalMinutesInMonth % 60
                                val timeText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                                Text(
                                    text = timeText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlaktraMint
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Daywise List
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
                            text = "No tracks played in $currentMonthName",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = AlaktraTextPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Songs you play on Alaktra this month will automatically be organized here day by day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = AlaktraTextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        if (historyList.isEmpty()) {
                            Spacer(modifier = Modifier.height(18.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        val songs = repository.getSongs()
                                        repository.seedHistoryIfEmpty(songs)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AlaktraSurface,
                                    contentColor = AlaktraMint
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder))
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Load Sample Listening History")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    daywiseGroups.forEach { dayGroup ->
                        // Sticky Day Header
                        item(key = "header_${dayGroup.date}") {
                            DaywiseHeader(group = dayGroup)
                        }

                        // Day's songs
                        items(dayGroup.items, key = { it.id }) { historyItem ->
                            HistorySongRow(
                                item = historyItem,
                                onPlay = {
                                    val song = historyItem.toSong()
                                    val allSongs = dayGroup.items.map { it.toSong() }
                                    val index = allSongs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                    audioController.playQueue(allSongs, index)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun DaywiseHeader(group: DaywiseGroup) {
    val totalMin = group.totalSeconds / 60
    val durationText = if (totalMin > 0) "${totalMin}m listened" else "${group.totalSeconds}s listened"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(AlaktraBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AlaktraSurface)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = null,
                    tint = AlaktraMint,
                    modifier = Modifier.size(14.dp)
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

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(AlaktraSurface)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = "${group.items.size} tracks • $durationText",
                color = AlaktraTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HistorySongRow(
    item: ListeningHistoryEntity,
    onPlay: () -> Unit
) {
    val timeFormatted = remember(item.playedAt) {
        Instant.ofEpochMilli(item.playedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    val durSec = item.duration.toLong()
    val durationFormatted = remember(durSec) {
        "${durSec / 60}:${(durSec % 60).toString().padStart(2, '0')}"
    }

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
        ) {
            AsyncImage(
                model = item.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist
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
                    text = " • $durationFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = AlaktraTextMuted
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Time played pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(AlaktraSurface)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Text(
                text = timeFormatted,
                color = AlaktraMint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Instant play button
        IconButton(
            onClick = onPlay,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Track",
                tint = AlaktraMint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
