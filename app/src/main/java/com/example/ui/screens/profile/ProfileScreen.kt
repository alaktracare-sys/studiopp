package com.example.ui.screens.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.AudioController
import com.example.data.repository.MusicRepository
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

    // Upload dialog state
    var showUploadDialog by remember { mutableStateOf(false) }

    // Check connection on entry
    LaunchedEffect(Unit) {
        isCheckingConnection = true
        isConnected = repository.testConnection()
        isCheckingConnection = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile & Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        },
        containerColor = Color(0xFF121212)
    ) { padding ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF282828))
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Avatar",
                    tint = Color.LightGray,
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = user?.username ?: "Music Enthusiast",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = user?.email ?: "guest@tailscale.local",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Stats Cards Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
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
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "$likedCount",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Liked Songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
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
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Offline",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Storage Ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tailscale Server Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(14.dp)
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
                                color = Color.White
                            )
                        }

                        IconButton(onClick = { showServerConfigDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Server", tint = Color.LightGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF1DB954)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = when {
                            isCheckingConnection -> "Testing Tailscale connection..."
                            isConnected == true -> "Connected to FastAPI music server"
                            isConnected == false -> "Server not reachable (verify Tailscale VPN is active)"
                            else -> "Status unknown"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected == true) Color.LightGray else Color(0xFFEF4444)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isCheckingConnection = true
                                isConnected = repository.testConnection()
                                isCheckingConnection = false
                            }
                        },
                        enabled = !isCheckingConnection,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isCheckingConnection) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ping Tailscale Server")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Upload Music Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showUploadDialog = true },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF282828))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Upload Music to Server",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White
                        )
                        Text(
                            text = "Add new MP3 and album art to your library",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }

                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Logout Button
            Button(
                onClick = {
                    repository.authPreferences.clear()
                    audioController.resetForLogout()
                    onLogout()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E1010),
                    contentColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(28.dp),
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
                    text = "Log Out",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // Server Config Dialog
    if (showServerConfigDialog) {
        var tempUrl by remember { mutableStateOf(serverUrl) }
        var pingResult by remember { mutableStateOf<String?>(null) }
        var isPinging by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showServerConfigDialog = false },
            containerColor = Color(0xFF242424),
            title = { Text("Tailscale Server URL", color = Color.White) },
            text = {
                Column {
                    Text(
                        text = "Enter the Tailscale IP and port of your server:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
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
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color.Gray
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
                        Text(if (isPinging) "Connecting..." else "Test Connection", color = Color(0xFF1DB954))
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954), contentColor = Color.Black)
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerConfigDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            }
        )
    }

    // Upload Song Dialog
    if (showUploadDialog) {
        var songTitle by remember { mutableStateOf("") }
        var songArtist by remember { mutableStateOf("") }
        var audioUri by remember { mutableStateOf<Uri?>(null) }
        var coverUri by remember { mutableStateOf<Uri?>(null) }
        var isUploading by remember { mutableStateOf(false) }
        var uploadError by remember { mutableStateOf<String?>(null) }
        var uploadSuccess by remember { mutableStateOf(false) }

        val audioPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            audioUri = uri
        }

        val coverPicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            coverUri = uri
        }

        AlertDialog(
            onDismissRequest = { if (!isUploading) showUploadDialog = false },
            containerColor = Color(0xFF242424),
            title = { Text("Upload Song to Server", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = songTitle,
                        onValueChange = { songTitle = it },
                        label = { Text("Song Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = songArtist,
                        onValueChange = { songArtist = it },
                        label = { Text("Artist") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF1DB954),
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (audioUri != null) "Audio file selected" else "Select MP3 Audio",
                            color = if (audioUri != null) Color(0xFF1DB954) else Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { audioPicker.launch("audio/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151))
                        ) {
                            Text("Pick Audio")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (coverUri != null) "Cover selected" else "Select Cover Image",
                            color = if (coverUri != null) Color(0xFF1DB954) else Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = { coverPicker.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151))
                        ) {
                            Text("Pick Cover")
                        }
                    }

                    if (uploadError != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = uploadError!!, color = Color(0xFFEF4444), fontSize = 12.sp)
                    }

                    if (uploadSuccess) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = "Song uploaded successfully to Tailscale server!", color = Color(0xFF10B981), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (songTitle.isBlank() || songArtist.isBlank() || audioUri == null || coverUri == null) {
                            uploadError = "Please fill in title, artist, audio and cover."
                            return@Button
                        }
                        isUploading = true
                        uploadError = null
                        scope.launch {
                            try {
                                val audioBytes = context.contentResolver.openInputStream(audioUri!!)?.use { it.readBytes() }
                                val coverBytes = context.contentResolver.openInputStream(coverUri!!)?.use { it.readBytes() }

                                if (audioBytes == null || coverBytes == null) {
                                    uploadError = "Failed to read selected files"
                                    isUploading = false
                                    return@launch
                                }

                                val result = repository.uploadSong(
                                    title = songTitle,
                                    artist = songArtist,
                                    audioBytes = audioBytes,
                                    audioFileName = "track.mp3",
                                    coverBytes = coverBytes,
                                    coverFileName = "cover.jpg"
                                )

                                isUploading = false
                                if (result.isSuccess) {
                                    uploadSuccess = true
                                    songTitle = ""
                                    songArtist = ""
                                    audioUri = null
                                    coverUri = null
                                } else {
                                    uploadError = result.exceptionOrNull()?.message ?: "Upload failed"
                                }
                            } catch (e: Exception) {
                                isUploading = false
                                uploadError = e.message ?: "Upload error"
                            }
                        }
                    },
                    enabled = !isUploading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954), contentColor = Color.Black)
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp))
                    } else {
                        Text("Upload")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadDialog = false }, enabled = !isUploading) {
                    Text("Close", color = Color.LightGray)
                }
            }
        )
    }
}
