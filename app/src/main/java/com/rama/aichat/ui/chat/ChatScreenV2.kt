package com.rama.aichat.ui.chat

import android.Manifest
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rama.aichat.data.model.ChatMessage
import com.rama.aichat.inference.GemmaInferenceManager
import com.rama.aichat.ui.components.MessageBubble
import com.rama.aichat.ui.components.rememberBitmapFromPath
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreenV2(
    onOpenDrawer: () -> Unit
) {
    val activity = LocalContext.current as ComponentActivity
    val viewModel: GemmaChatViewModel = viewModel(viewModelStoreOwner = activity)
    val uiState by viewModel.uiState.collectAsState()
    val loadState by viewModel.loadState.collectAsState()

    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var isAttachMenuExpanded by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val pendingPreviewBitmap by rememberBitmapFromPath(uiState.pendingImagePath, maxDimension = 512)
    val recordingPulse = rememberInfiniteTransition(label = "recordingPulse")
    val pulseScale by recordingPulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by recordingPulse.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    val recordAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission is required for voice prompts.")
            }
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onImagePicked(uri)
        }
    }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraUri
        pendingCameraUri = null
        if (success && capturedUri != null) {
            viewModel.onImagePicked(capturedUri)
        }
    }
    val requestCameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val captureUri = viewModel.createCameraCaptureUri()
            pendingCameraUri = captureUri
            takePictureLauncher.launch(captureUri)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Camera permission is required to take photos.")
            }
        }
    }

    LaunchedEffect(uiState.messages.size, uiState.isGenerating) {
        val targetIndex = uiState.messages.size + (if (uiState.isGenerating) 1 else 0)
        if (targetIndex > 0) {
            listState.animateScrollToItem(targetIndex - 1)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.sessions
                            .firstOrNull { it.id == uiState.currentSessionId }
                            ?.title
                            ?: "AiChat (Gemma)"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open menu"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::createNewSession) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New chat"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        when (loadState) {
            GemmaInferenceManager.LoadState.Ready -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .imePadding()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            MessageBubble(message = message)
                        }

                        if (uiState.isGenerating && uiState.streamingText.isNotEmpty()) {
                            item(key = "streaming") {
                                val streamingMessage = ChatMessage().apply {
                                    content = uiState.streamingText
                                    role = "model"
                                }
                                MessageBubble(message = streamingMessage)
                            }
                        }

                        if (uiState.isGenerating && uiState.streamingText.isEmpty()) {
                            item(key = "typing") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    if (uiState.pendingImagePath != null || uiState.isAttachingImage) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (uiState.isAttachingImage) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Attaching image...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                pendingPreviewBitmap?.let { bitmap ->
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Selected image preview",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                IconButton(
                                    onClick = viewModel::clearPendingImage,
                                    enabled = !uiState.isGenerating
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove image"
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.inputText,
                            onValueChange = viewModel::updateInput,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    uiState.recordingHint
                                        ?: if (uiState.isRecording) "Listening..." else "Type a message..."
                                )
                            },
                            enabled = !uiState.isGenerating && !uiState.isRecording,
                            maxLines = 4,
                            shape = MaterialTheme.shapes.large
                        )
                        Box {
                            IconButton(
                                onClick = { isAttachMenuExpanded = true },
                                enabled = !uiState.isGenerating &&
                                    !uiState.isAttachingImage &&
                                    !uiState.isRecording &&
                                    uiState.currentSessionId != null
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Attach image"
                                )
                            }
                            DropdownMenu(
                                expanded = isAttachMenuExpanded,
                                onDismissRequest = { isAttachMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Choose photo") },
                                    onClick = {
                                        isAttachMenuExpanded = false
                                        pickImageLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Take photo") },
                                    onClick = {
                                        isAttachMenuExpanded = false
                                        val hasPermission = ContextCompat.checkSelfPermission(
                                            activity,
                                            Manifest.permission.CAMERA
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (hasPermission) {
                                            val captureUri = viewModel.createCameraCaptureUri()
                                            pendingCameraUri = captureUri
                                            takePictureLauncher.launch(captureUri)
                                        } else {
                                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    }
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                if (uiState.isRecording) {
                                    viewModel.stopVoiceInput()
                                } else {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        activity,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        viewModel.startVoiceInput()
                                    } else {
                                        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            enabled = !uiState.isGenerating && uiState.currentSessionId != null
                        ) {
                            Icon(
                                modifier = if (uiState.isRecording) {
                                    Modifier.graphicsLayer(
                                        scaleX = pulseScale,
                                        scaleY = pulseScale,
                                        alpha = pulseAlpha
                                    )
                                } else {
                                    Modifier
                                },
                                imageVector = Icons.Default.Mic,
                                contentDescription = if (uiState.isRecording) {
                                    "Stop voice input"
                                } else {
                                    "Start voice input"
                                },
                                tint = if (uiState.isRecording) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        IconButton(
                            onClick = viewModel::sendMessage,
                            enabled = !uiState.isGenerating &&
                                !uiState.isRecording &&
                                !uiState.isAttachingImage &&
                                (uiState.inputText.isNotBlank() || uiState.pendingImagePath != null)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send message"
                            )
                        }
                    }
                }
            }

            else -> {
                ModelLoadingOverlay(
                    loadState = loadState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun ModelLoadingOverlay(
    loadState: GemmaInferenceManager.LoadState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (loadState) {
                    is GemmaInferenceManager.LoadState.Downloading -> {
                        val progress = loadState.progress
                        Text(
                            text = "Downloading model…",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (progress != null) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }

                    GemmaInferenceManager.LoadState.Initializing -> {
                        Text(
                            text = "Initializing model…",
                            style = MaterialTheme.typography.titleSmall
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    else -> {
                        Text(
                            text = "Preparing model…",
                            style = MaterialTheme.typography.titleSmall
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}
