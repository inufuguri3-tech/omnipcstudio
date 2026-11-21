package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.example.R
import com.example.ai.AiModelOption
import com.example.ai.AiProvider
import com.example.ai.LiveProMode
import com.example.ai.ProviderKeyStatus
import com.example.data.local.LiveMessageEntity
import com.example.ui.components.CameraLivePreviewCard
import com.example.ui.components.LiveOrbCanvas
import com.example.ui.components.ModelPickerSheet
import com.example.ui.components.OrbPhase
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonStop
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeuralIndigo
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.theme.ObsidianSurfaceHighest
import com.example.viewmodel.LiveAiUiState
import com.example.viewmodel.OmniLiveViewModel
import com.example.viewmodel.VoiceTarget
import com.example.voice.SpeechUiState

@Composable
fun LiveProScreen(
    viewModel: OmniLiveViewModel,
    onQuickSwitchToOperator: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val liveAiState by viewModel.liveAiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val pendingBitmap by viewModel.pendingBitmap.collectAsStateWithLifecycle()
    val speechState by viewModel.speechState.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    var showCameraPreview by rememberSaveable { mutableStateOf(false) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    val activeModel = AiModelOption.fromId(prefs.selectedLiveModelId)
    val listState = rememberLazyListState()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput(VoiceTarget.LIVE_PRO)
        } else {
            Toast.makeText(context, "音声対話にはマイク権限が必要です。", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCameraPreview = true
        } else {
            Toast.makeText(context, "ライブビジョンにはカメラ権限が必要です。", Toast.LENGTH_SHORT).show()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.attachImageFromUri(it) }
    }

    LaunchedEffect(messages.size, liveAiState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val orbPhase = when {
        speechState is SpeechUiState.Listening -> OrbPhase.LISTENING
        liveAiState is LiveAiUiState.Loading || liveAiState is LiveAiUiState.Streaming -> OrbPhase.THINKING
        isSpeaking -> OrbPhase.SPEAKING
        else -> OrbPhase.IDLE
    }

    val orbStatusText = when (val s = speechState) {
        is SpeechUiState.Listening -> if (s.partialText.isNotBlank()) "認識中: ${s.partialText}" else "話しかけてください..."
        is SpeechUiState.Error -> s.message
        SpeechUiState.Idle -> when (liveAiState) {
            LiveAiUiState.Loading -> "Gemini Live Pro 推論中..."
            is LiveAiUiState.Streaming -> "リアルタイム応答ストリーミング中..."
            is LiveAiUiState.Error -> "エラーが発生しました"
            else -> if (isSpeaking) "音声で回答中..." else "${selectedMode.displayName} ・ 待機中"
        }
    }

    val rmsLevel = (speechState as? SpeechUiState.Listening)?.rmsDb ?: 0f

    if (showModelPicker) {
        ModelPickerSheet(
            title = "Live Pro モデルを選択",
            selectedModel = activeModel,
            onSelectModel = { viewModel.setLiveModel(it) },
            onDismiss = { showModelPicker = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .imePadding()
        ) {
            // Header Banner with neural art backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, ObsidianOutline, RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_live_orb_banner),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.35f)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ObsidianSurface.copy(alpha = 0.72f),
                                    ObsidianBg.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "OmniLive Pro",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Surface(
                                color = ElectricCyan.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { showModelPicker = true }
                                    .testTag("live_model_picker_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${activeModel.provider.shortLabel}: ${activeModel.displayName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "モデルを変更",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        val keyReady = ProviderKeyStatus.isProviderConfigured(activeModel.provider)
                        val badgeColor = if (keyReady) EmeraldPulse else AmberAlert
                        val badgeLabel = when {
                            activeModel.provider == AiProvider.UNLIMITED -> "無制限・自動ルート"
                            activeModel.provider == AiProvider.LOCAL_LLM -> "ローカル・制限なし"
                            activeModel.provider == AiProvider.GEMINI -> "App Check・キー不要"
                            keyReady -> "${activeModel.provider.shortLabel} 接続可能"
                            else -> "Secrets未設定"
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = badgeLabel,
                                    tint = badgeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = badgeColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LiveOrbCanvas(
                        phase = orbPhase,
                        rmsLevel = rmsLevel,
                        statusLabel = orbStatusText
                    )
                }
            }

            // Multi-function Live Pro Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiveProMode.entries.forEach { mode ->
                    val selected = mode == selectedMode
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectLiveMode(mode) },
                        label = {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                            selectedLabelColor = ElectricCyan,
                            selectedLeadingIconColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("mode_chip_${mode.name.lowercase()}")
                    )
                }
            }

            // Live CameraX Preview (collapsible)
            AnimatedVisibility(visible = showCameraPreview) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    CameraLivePreviewCard(
                        onCaptureFrame = { bitmap ->
                            viewModel.attachBitmap(bitmap)
                            Toast.makeText(context, "カメラ映像を取得しました。質問を送信してください。", Toast.LENGTH_SHORT).show()
                        },
                        onCloseCamera = { showCameraPreview = false }
                    )
                }
            }

            // Conversation Transcript
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (messages.isEmpty() && liveAiState !is LiveAiUiState.Streaming) {
                    item {
                        EmptyLiveStarterCard(
                            mode = selectedMode,
                            onSamplePrompt = { prompt -> viewModel.sendLivePrompt(prompt) },
                            onTryOperator = { onQuickSwitchToOperator("設定アプリを開いて") }
                        )
                    }
                }

                items(messages, key = { it.id }) { message ->
                    LiveMessageBubble(
                        message = message,
                        onSpeak = { viewModel.speakText(message.text) },
                        onCopy = { copyToClipboard(context, message.text) }
                    )
                }

                if (liveAiState is LiveAiUiState.Streaming) {
                    val streamingText = (liveAiState as LiveAiUiState.Streaming).text
                    item(key = "streaming_bubble") {
                        LiveMessageBubble(
                            message = LiveMessageEntity(
                                role = "AI",
                                mode = selectedMode.name,
                                text = streamingText
                            ),
                            onSpeak = {},
                            onCopy = {},
                            isStreaming = true
                        )
                    }
                }

                if (liveAiState is LiveAiUiState.Error) {
                    val errorMsg = (liveAiState as LiveAiUiState.Error).message
                    item(key = "error_banner") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            // Attached Image Thumbnail Preview
            AnimatedVisibility(visible = pendingBitmap != null) {
                pendingBitmap?.let { bmp ->
                    AttachedBitmapBar(
                        bitmap = bmp,
                        onClear = { viewModel.attachBitmap(null) }
                    )
                }
            }

            // Bottom Voice & Multimodal Input Dock
            Surface(
                color = ObsidianSurface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Camera toggle
                        IconButton(
                            onClick = {
                                if (showCameraPreview) {
                                    showCameraPreview = false
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) showCameraPreview = true
                                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .background(
                                    if (showCameraPreview) ElectricCyan.copy(alpha = 0.2f) else ObsidianSurfaceElevated,
                                    CircleShape
                                )
                                .testTag("live_camera_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "ライブカメラ",
                                tint = if (showCameraPreview) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Photo Picker
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .background(ObsidianSurfaceElevated, CircleShape)
                                .testTag("live_photo_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = "画像を選択",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Text input
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    text = "Gemini Live Pro に話しかけるか入力...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = ObsidianOutline,
                                focusedContainerColor = ObsidianSurfaceElevated,
                                unfocusedContainerColor = ObsidianSurfaceElevated
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank() || pendingBitmap != null) {
                                        val msg = inputText
                                        inputText = ""
                                        viewModel.sendLivePrompt(msg)
                                    }
                                }
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("live_prompt_input")
                        )

                        // Send OR Voice Mic / Stop button
                        if (inputText.isNotBlank() || pendingBitmap != null) {
                            IconButton(
                                onClick = {
                                    val msg = inputText
                                    inputText = ""
                                    viewModel.sendLivePrompt(msg)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .background(ElectricCyan, CircleShape)
                                    .testTag("live_send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "送信",
                                    tint = ObsidianBg
                                )
                            }
                        } else if (speechState is SpeechUiState.Listening || isSpeaking) {
                            IconButton(
                                onClick = { viewModel.stopVoiceSession() },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .background(CrimsonStop, CircleShape)
                                    .testTag("live_stop_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "音声を停止",
                                    tint = ObsidianBg
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) viewModel.startVoiceInput(VoiceTarget.LIVE_PRO)
                                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .background(
                                        Brush.linearGradient(listOf(ElectricCyan, NeuralIndigo)),
                                        CircleShape
                                    )
                                    .testTag("live_mic_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "音声で話す",
                                    tint = ObsidianBg
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
private fun EmptyLiveStarterCard(
    mode: LiveProMode,
    onSamplePrompt: (String) -> Unit,
    onTryOperator: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = ElectricCyan
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${mode.displayName} モード",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = mode.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            val samples = when (mode) {
                LiveProMode.LIVE_CONVERSATION -> listOf(
                    "今日のスケジュールを整理するコツを教えて",
                    "音声で雑談しながらアイデア出しを手伝って"
                )
                LiveProMode.INTERPRETER -> listOf(
                    "「明日の会議は午後3時に変更になりました」を自然な英語に",
                    "Translate: Can you walk me through the architecture?"
                )
                LiveProMode.DEEP_PRO -> listOf(
                    "AndroidのAccessibilityServiceを安全に使う設計ポイントを教えて",
                    "高速なキャッシュアルゴリズムを例付きで解説して"
                )
                LiveProMode.VISION_COACH -> listOf(
                    "カメラや画像で写したものの手順をガイドして",
                    "画面のエラーメッセージの直し方を教えて"
                )
            }
            samples.forEachIndexed { idx, sample ->
                AssistChip(
                    onClick = { onSamplePrompt(sample) },
                    label = { Text(sample, style = MaterialTheme.typography.bodyMedium) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = ObsidianSurfaceHighest
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .testTag("starter_prompt_$idx")
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            AssistChip(
                onClick = onTryOperator,
                label = { Text("スマホ操作AIを試す →", color = EmeraldPulse) },
                modifier = Modifier.testTag("starter_switch_operator")
            )
        }
    }
}

@Composable
private fun LiveMessageBubble(
    message: LiveMessageEntity,
    onSpeak: () -> Unit,
    onCopy: () -> Unit,
    isStreaming: Boolean = false
) {
    val isUser = message.role == "USER"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) ElectricCyan.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isUser) ElectricCyan.copy(alpha = 0.45f) else ObsidianOutline
            ),
            modifier = Modifier.widthIn(max = 540.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isUser) {
                            if (message.hasVisualContext) "あなた ・ 画像付き" else "あなた"
                        } else {
                            if (isStreaming) "Gemini Live Pro ・ 応答中..." else "Gemini Live Pro"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) ElectricCyan else NeuralIndigo,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isUser && !isStreaming) {
                        Row {
                            IconButton(
                                onClick = onSpeak,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "読み上げる",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = onCopy,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "コピー",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun AttachedBitmapBar(
    bitmap: Bitmap,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianSurfaceElevated)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "添付画像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "ビジョン画像が添付されています",
                style = MaterialTheme.typography.bodyMedium,
                color = ElectricCyan
            )
        }
        IconButton(
            onClick = onClear,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("clear_attached_image_button")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "添付を取り消す",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("Gemini Live Pro", text))
    Toast.makeText(context, "クリップボードにコピーしました", Toast.LENGTH_SHORT).show()
}
