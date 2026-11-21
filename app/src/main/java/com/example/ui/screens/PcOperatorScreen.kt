package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiModelOption
import com.example.pc.PcAgentCompanionAssets
import com.example.ui.components.ModelPickerSheet
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
import com.example.viewmodel.OmniLiveViewModel
import com.example.viewmodel.PcOperatorUiState
import com.example.viewmodel.VoiceTarget

@Composable
fun PcOperatorScreen(
    viewModel: OmniLiveViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val pcState by viewModel.pcOperatorState.collectAsStateWithLifecycle()
    val pcGoalInput by viewModel.pcGoalInput.collectAsStateWithLifecycle()
    val pcScreenBitmap by viewModel.pcScreenBitmap.collectAsStateWithLifecycle()
    val pcConnectionStatus by viewModel.pcConnectionStatus.collectAsStateWithLifecycle()
    val pcIsConnected by viewModel.pcIsConnected.collectAsStateWithLifecycle()
    val pcActiveWindow by viewModel.pcActiveWindow.collectAsStateWithLifecycle()
    val pcBridgeConnected by viewModel.pcBridgeConnected.collectAsStateWithLifecycle()
    val operatorLogs by viewModel.operatorLogs.collectAsStateWithLifecycle()

    var urlInput by rememberSaveable(prefs.pcAgentUrl) { mutableStateOf(prefs.pcAgentUrl) }
    var tokenInput by rememberSaveable(prefs.pcAgentToken) { mutableStateOf(prefs.pcAgentToken) }
    var showModelPicker by rememberSaveable { mutableStateOf(false) }
    var showExeBuilderCard by rememberSaveable { mutableStateOf(false) }

    val activeModel = AiModelOption.fromId(prefs.selectedOperatorModelId)

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput(VoiceTarget.PC_OPERATOR)
        } else {
            Toast.makeText(context, "音声コマンドにはマイク権限が必要です。", Toast.LENGTH_SHORT).show()
        }
    }

    if (showModelPicker) {
        ModelPickerSheet(
            title = "PC操作 AI モデルを選択",
            selectedModel = activeModel,
            onSelectModel = { viewModel.setOperatorModel(it) },
            onDismiss = { showModelPicker = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI パソコン遠隔操作",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "PC画面認識 × マウス/キーボード自律実行",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = ElectricCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .clickable { showModelPicker = true }
                            .testTag("pc_model_picker_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeModel.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "PC操作モデルを変更",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // PC Agent LAN Connection Card
            item {
                val accent = if (pcIsConnected) EmeraldPulse else ElectricCyan
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (pcIsConnected) Icons.Default.CheckCircle else Icons.Default.Computer,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (pcIsConnected) "PCエージェント: 接続済み" else "Windows / Mac PC エージェント接続",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = accent,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = pcConnectionStatus
                                        ?: "PC側で OmniLivePCAgent.exe (または Python スクリプト) を起動し、LANアドレスを入力してください。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            label = { Text("PCエージェント URL (例: http://192.168.1.10:8765)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pc_agent_url_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = { tokenInput = it },
                            label = { Text("ペアリングトークン (任意・空欄可)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pc_agent_token_input")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { viewModel.testPcConnection(urlInput, tokenInput) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pc_test_connection_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DesktopWindows,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text("接続テスト")
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.setPcAgentConfig(urlInput, tokenInput)
                                    viewModel.capturePcScreenOnly()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pc_capture_screen_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenshotMonitor,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text("PC画面取得")
                            }
                        }
                    }
                }
            }

            // APK Bridge Listener Mode (API-keyless Firebase AI relay for PC GUI)
            item {
                val bridgeAccent = if (prefs.pcBridgeListenerEnabled && pcBridgeConnected) {
                    EmeraldPulse
                } else if (prefs.pcBridgeListenerEnabled) {
                    ElectricCyan
                } else {
                    ObsidianOutline
                }
                Surface(
                    color = if (prefs.pcBridgeListenerEnabled) bridgeAccent.copy(alpha = 0.12f) else ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, bridgeAccent.copy(alpha = 0.55f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = if (pcBridgeConnected && prefs.pcBridgeListenerEnabled) {
                                    "PC GUI連携ブリッジ: オンライン待機中"
                                } else if (prefs.pcBridgeListenerEnabled) {
                                    "PC GUI連携ブリッジ: ON（APIキー不要モード）"
                                } else {
                                    "PC GUI連携ブリッジ: OFF"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (prefs.pcBridgeListenerEnabled) bridgeAccent else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "PC側の OmniLivePCAgent.exe で「APK連携AI (APIキー不要)」を選んで指示すると、このスマホの Firebase AI / 無制限AI がPC操作を自動代行します。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.pcBridgeListenerEnabled,
                            onCheckedChange = {
                                viewModel.setPcAgentConfig(urlInput, tokenInput)
                                viewModel.setPcBridgeListenerEnabled(it)
                            },
                            modifier = Modifier.testTag("pc_bridge_listener_switch")
                        )
                    }
                }
            }

            // Live PC Desktop Preview + Target Crosshair
            pcScreenBitmap?.let { bmp ->
                item {
                    val ratio = (bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat())
                        .coerceIn(0.8f, 2.5f)
                    val pendingPlan = (pcState as? PcOperatorUiState.AwaitingConfirmation)?.plan

                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, ObsidianOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PCライブ画面プレビュー",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (pcActiveWindow.isNotBlank()) {
                                    Text(
                                        text = pcActiveWindow.take(28),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ratio)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianBg)
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "PCデスクトップのライブプレビュー",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                if (pendingPlan != null && pendingPlan.normX in 0..1000 && pendingPlan.normY in 0..1000) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val cx = (pendingPlan.normX / 1000f) * size.width
                                        val cy = (pendingPlan.normY / 1000f) * size.height
                                        drawCircle(
                                            color = AmberAlert.copy(alpha = 0.28f),
                                            radius = 28f,
                                            center = Offset(cx, cy)
                                        )
                                        drawCircle(
                                            color = AmberAlert,
                                            radius = 18f,
                                            center = Offset(cx, cy),
                                            style = Stroke(width = 4f)
                                        )
                                        drawCircle(
                                            color = CrimsonStop,
                                            radius = 6f,
                                            center = Offset(cx, cy)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Safety Switch
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = ElectricCyan
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PC操作の各ステップ前に承認確認する",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (prefs.requireActionConfirmation) {
                                        "安全モードON（クリック座標や入力内容を確認後に実行）"
                                    } else {
                                        "オートパイロットON（最大8ステップまで自動実行）"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = prefs.requireActionConfirmation,
                            onCheckedChange = { viewModel.setRequireActionConfirm(it) },
                            modifier = Modifier.testTag("pc_safety_switch")
                        )
                    }
                }
            }

            // Command Composer Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "PCに実行させたい操作を指示",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = pcGoalInput,
                                onValueChange = { viewModel.updatePcGoal(it) },
                                placeholder = { Text("例: メモ帳を開いて / ChromeでYouTubeを開く") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricCyan,
                                    unfocusedBorderColor = ObsidianOutline,
                                    focusedContainerColor = ObsidianBg,
                                    unfocusedContainerColor = ObsidianBg
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pc_goal_input")
                            )

                            IconButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) viewModel.startVoiceInput(VoiceTarget.PC_OPERATOR)
                                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .background(NeuralIndigo.copy(alpha = 0.25f), CircleShape)
                                    .testTag("pc_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "音声でPC操作を指示",
                                    tint = NeuralIndigo
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val presets = listOf(
                            "メモ帳を開く",
                            "ChromeでGoogleを開く",
                            "電卓を開く",
                            "ウィンドウを切り替える",
                            "下にスクロールする"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEachIndexed { idx, preset ->
                                AssistChip(
                                    onClick = { viewModel.updatePcGoal(preset) },
                                    label = { Text(preset) },
                                    modifier = Modifier.testTag("pc_preset_$idx")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val isRunning = pcState is PcOperatorUiState.CapturingScreen ||
                            pcState is PcOperatorUiState.PlanningStep ||
                            pcState is PcOperatorUiState.ExecutingAction ||
                            pcState is PcOperatorUiState.AwaitingConfirmation

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.setPcAgentConfig(urlInput, tokenInput)
                                    viewModel.startPcTask(pcGoalInput)
                                },
                                enabled = pcGoalInput.isNotBlank() && !isRunning,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("pc_execute_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text("PC操作を開始")
                            }

                            if (isRunning) {
                                Button(
                                    onClick = { viewModel.cancelPcTask() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CrimsonStop,
                                        contentColor = ObsidianBg
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("pc_stop_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                    Text("停止")
                                }
                            }
                        }
                    }
                }
            }

            // Execution State Panel
            item {
                PcOperatorStatePanel(
                    state = pcState,
                    onApprove = { viewModel.approvePendingPcAction() },
                    onCancel = { viewModel.cancelPcTask() },
                    onRetry = { viewModel.startPcTask(pcGoalInput) }
                )
            }

            // Windows .exe Agent Setup & Share Card
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showExeBuilderCard = !showExeBuilderCard },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = EmeraldPulse
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Windows用 .exe エージェント作成ガイド",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "OmniLivePCAgent.exe のビルド手順とスクリプト転送",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showExeBuilderCard = !showExeBuilderCard },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("toggle_pc_exe_guide_button")
                            ) {
                                Icon(
                                    imageVector = if (showExeBuilderCard) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (showExeBuilderCard) "ガイドを閉じる" else "ガイドを開く"
                                )
                            }
                        }

                        AnimatedVisibility(visible = showExeBuilderCard) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "このプロジェクトに同梱された Windows用エージェントを PC で PyInstaller に通すだけで単体起動の OmniLivePCAgent.exe を作成できます。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    color = ObsidianBg,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, ObsidianOutline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = PcAgentCompanionAssets.BUILD_EXE_COMMANDS,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = ElectricCyan,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            copyToClipboard(
                                                context,
                                                "omnilive_pc_agent.py",
                                                PcAgentCompanionAssets.OMNILIVE_PC_AGENT_PY
                                            )
                                            Toast.makeText(
                                                context,
                                                "PCエージェント用 Python スクリプトをコピーしました",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("copy_pc_agent_script_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        Text("スクリプトをコピー")
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "OmniLive PC Agent (.exe ビルド手順 & スクリプト)")
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    PcAgentCompanionAssets.BUILD_EXE_COMMANDS +
                                                        "\n\n--- omnilive_pc_agent.py ---\n" +
                                                        PcAgentCompanionAssets.OMNILIVE_PC_AGENT_PY
                                                )
                                            }
                                            context.startActivity(
                                                Intent.createChooser(shareIntent, "PCへエージェントを共有")
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("share_pc_agent_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        Text("PCへ共有")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val pcLogs = operatorLogs.filter { it.goal.startsWith("[PC]") }.take(6)
            if (pcLogs.isNotEmpty()) {
                item {
                    Text(
                        text = "最近のPC操作ログ",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(pcLogs, key = { "pc_log_${it.id}" }) { log ->
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ObsidianOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Step ${log.stepNumber} • ${log.actionType}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (log.success) EmeraldPulse else CrimsonStop
                                )
                                Text(
                                    text = log.targetSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.reasoning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PcOperatorStatePanel(
    state: PcOperatorUiState,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        PcOperatorUiState.Idle -> Unit
        is PcOperatorUiState.CapturingScreen,
        is PcOperatorUiState.PlanningStep,
        is PcOperatorUiState.ExecutingAction -> {
            val label = when (state) {
                is PcOperatorUiState.CapturingScreen ->
                    "ステップ ${state.step}/${state.maxSteps}: PC画面をキャプチャ中..."
                is PcOperatorUiState.PlanningStep ->
                    if (state.elapsedSeconds > 0) {
                        "ステップ ${state.step}/${state.maxSteps}: AIがPC画面を解析し操作を計画中... (${state.elapsedSeconds}秒)"
                    } else {
                        "ステップ ${state.step}/${state.maxSteps}: AIがPC画面を解析し操作を計画中..."
                    }
                is PcOperatorUiState.ExecutingAction ->
                    "ステップ ${state.step}: PCへ ${state.plan.actionType.name} を送信中..."
                else -> ""
            }
            Surface(
                color = ElectricCyan.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ElectricCyan
                    )
                }
            }
        }
        is PcOperatorUiState.AwaitingConfirmation -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceHighest),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, AmberAlert),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = AmberAlert
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PC操作の実行確認 (ステップ ${state.step}/${state.maxSteps})",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberAlert
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val coordInfo = if (state.plan.x >= 0 && state.plan.y >= 0) {
                        " (${state.plan.x}, ${state.plan.y})"
                    } else ""
                    Text(
                        text = "アクション: ${state.plan.actionType.name}$coordInfo",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                    if (state.plan.inputText.isNotBlank()) {
                        Text(
                            text = "入力 / アプリ / キー: ${state.plan.inputText}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = state.plan.reasoning,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onApprove,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pc_approve_button")
                        ) {
                            Text("承認してPC操作を実行")
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("pc_cancel_confirm_button")
                        ) {
                            Text("中止")
                        }
                    }
                }
            }
        }
        is PcOperatorUiState.Completed -> {
            Surface(
                color = EmeraldPulse.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = EmeraldPulse
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = state.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        is PcOperatorUiState.Error -> {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("pc_retry_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text("再試行")
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
