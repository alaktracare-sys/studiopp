package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioController
import com.example.data.repository.MusicRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    repository: MusicRepository,
    audioController: AudioController,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = remember { repository.authPreferences.getUser() }
    val likedCount = remember { repository.authPreferences.getLikedSongIds().size }

    var serverUrl by remember { mutableStateOf(repository.authPreferences.getServerBaseUrl()) }
    var showServerConfigDialog by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf<Boolean?>(null) }
    var isCheckingConnection by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var downloadedCount by remember { mutableIntStateOf(0) }

    // Listening history state
    var showHistoryPanel by remember { mutableStateOf(false) }
    val historyList by repository.listeningHistory.collectAsState(initial = emptyList())

    // Check connection on entry & load download count & seed history if empty
    LaunchedEffect(Unit) {
        isCheckingConnection = true
        isConnected = repository.testConnection()
        isCheckingConnection = false
        val songs = repository.getSongs()
        downloadedCount = songs.count { it.isDownloaded || it.localPath != null }
        if (songs.isNotEmpty()) {
            repository.seedHistoryIfEmpty(songs)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings", color = AlaktraTextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = AlaktraTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AlaktraBackground)
            )
        },
        containerColor = AlaktraBackground
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AlaktraMint, AlaktraCyan)))
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Avatar",
                    tint = Color(0xFF041C12),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = user?.username ?: "Music Enthusiast",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                ),
                color = AlaktraTextPrimary
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = user?.email ?: "guest@alaktra.local",
                style = MaterialTheme.typography.bodyMedium,
                color = AlaktraTextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stats Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = AlaktraCard),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder)))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = AlaktraMint,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$likedCount",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = AlaktraTextPrimary
                        )
                        Text(
                            text = "Liked Tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = AlaktraTextSecondary
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = AlaktraCard),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder)))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = AlaktraCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$downloadedCount",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = AlaktraTextPrimary
                        )
                        Text(
                            text = "Offline Tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = AlaktraTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tailscale Server Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AlaktraCard),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder)))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (isConnected) {
                                            true -> Color(0xFF10B981)
                                            false -> Color(0xFFEF4444)
                                            null -> Color(0xFFF59E0B)
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tailscale Server",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AlaktraTextPrimary
                            )
                        }

                        IconButton(onClick = { showServerConfigDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Server", tint = AlaktraMint)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = AlaktraMint
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when {
                            isCheckingConnection -> "Testing Tailscale connection..."
                            isConnected == true -> "Connected to FastAPI music server"
                            isConnected == false -> "Server not reachable (verify Tailscale VPN is active)"
                            else -> "Status unknown"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected == true) AlaktraTextSecondary else Color(0xFFEF4444)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isCheckingConnection = true
                                isConnected = repository.testConnection()
                                isCheckingConnection = false
                            }
                        },
                        enabled = !isCheckingConnection,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlaktraTextPrimary),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder))),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCheckingConnection) {
                            CircularProgressIndicator(color = AlaktraMint, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = AlaktraMint)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ping Tailscale Server")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Listening History Summary Card
            ListeningHistorySummaryCard(
                historyList = historyList,
                onOpenFullHistory = { showHistoryPanel = true }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // App Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AlaktraCard),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(AlaktraBorder, AlaktraBorder)))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About Alaktra",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AlaktraTextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Version 1.0.0 (Release)\nBuilt with Kotlin Jetpack Compose & ExoPlayer.\nSecurely stream through Tailscale VPN mesh network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlaktraTextSecondary,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Logout Button
            Button(
                onClick = { showLogoutConfirm = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E1010),
                    contentColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = Color(0xFFEF4444)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign Out",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = AlaktraCard,
            title = { Text("Sign Out of Alaktra?", color = AlaktraTextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("You will need to sign in again to access your music streams.", color = AlaktraTextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        repository.authPreferences.clear()
                        audioController.resetForLogout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = AlaktraTextSecondary)
                }
            }
        )
    }

    // Server Config Dialog
    if (showServerConfigDialog) {
        var tempUrl by remember { mutableStateOf(serverUrl) }
        var pingResult by remember { mutableStateOf<String?>(null) }
        var isPinging by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showServerConfigDialog = false },
            containerColor = AlaktraCard,
            title = { Text("Tailscale Server URL", color = AlaktraTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter the Tailscale IP and port of your server:",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlaktraTextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = {
                            tempUrl = it
                            pingResult = null
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AlaktraTextPrimary,
                            unfocusedTextColor = AlaktraTextPrimary,
                            focusedBorderColor = AlaktraMint,
                            unfocusedBorderColor = AlaktraBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            isPinging = true
                            pingResult = null
                            repository.authPreferences.setServerBaseUrl(tempUrl)
                            scope.launch {
                                val ok = repository.testConnection()
                                isPinging = false
                                pingResult = if (ok) "Server reachable!" else "Could not connect to $tempUrl"
                            }
                        },
                        enabled = !isPinging
                    ) {
                        Text(if (isPinging) "Connecting..." else "Test Connection", color = AlaktraMint)
                    }
                    if (pingResult != null) {
                        Text(
                            text = pingResult!!,
                            color = if (pingResult!!.startsWith("Server")) Color(0xFF10B981) else Color(0xFFEF4444),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.authPreferences.setServerBaseUrl(tempUrl)
                        serverUrl = repository.authPreferences.getServerBaseUrl()
                        scope.launch {
                            isConnected = repository.testConnection()
                        }
                        showServerConfigDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlaktraMint, contentColor = Color(0xFF041C12))
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerConfigDialog = false }) {
                    Text("Cancel", color = AlaktraTextSecondary)
                }
            }
        )
    }

    // Full Listening History Sheet
    if (showHistoryPanel) {
        FullListeningHistorySheet(
            historyList = historyList,
            repository = repository,
            audioController = audioController,
            onDismiss = { showHistoryPanel = false }
        )
    }
}

