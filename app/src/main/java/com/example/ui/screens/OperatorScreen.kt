package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import com.example.ai.AiModelOption
import com.example.ai.ScreenSnapshot
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
import com.example.viewmodel.OperatorUiState
import com.example.viewmodel.VoiceTarget

@Composable
fun OperatorScreen(
    viewModel: OmniLiveViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()
    val isServiceConnected by viewModel.isServiceConnected.collectAsStateWithLifecycle()
    val operatorState by viewModel.operatorState.collectAsStateWithLifecycle()
    val goalInput by viewModel.operatorGoalInput.collectAsStateWithLifecycle()
    val latestSnapshot by viewModel.latestSnapshot.collectAsStateWithLifecycle()
    val operatorLogs by viewModel.operatorLogs.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var showNodeInspector by rememberSaveable { mutableStateOf(false) }
    var showOperatorModelPicker by rememberSaveable { mutableStateOf(false) }
    val activeOperatorModel = AiModelOption.fromId(prefs.selectedOperatorModelId)

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceInput(VoiceTarget.OPERATOR)
        } else {
            Toast.makeText(context, "音声コマンドにはマイク権限が必要です。", Toast.LENGTH_SHORT).show()
        }
    }

    // Re-check AccessibilityService connection when returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccessibilityStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val serviceActive = isServiceConnected || isAccessibilityEnabled

    if (showOperatorModelPicker) {
        ModelPickerSheet(
            title = "スマホ操作 AI モデルを選択",
            selectedModel = activeOperatorModel,
            onSelectModel = { viewModel.setOperatorModel(it) },
            onDismiss = { showOperatorModelPicker = false }
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
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AI スマホ操作エージェント",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Android ユーザー補助 × マルチAIプランニング",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = ElectricCyan.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .clickable { showOperatorModelPicker = true }
                            .testTag("operator_model_picker_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeOperatorModel.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "操作モデルを変更",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Accessibility Service Connection Banner
            item {
                AccessibilityStatusCard(
                    isConnected = serviceActive,
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        context.startActivity(intent)
                    },
                    onInspectScreen = { viewModel.inspectCurrentScreenOnly() }
                )
            }

            // Safety Gate Toggle Card
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline),
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
                                    text = "各ステップ実行前に確認する",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (prefs.requireActionConfirmOrDefault()) {
                                        "安全モードON（タップ・入力前に承認画面を表示）"
                                    } else {
                                        "オートパイロットON（最大8ステップまで連続実行）"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = prefs.requireActionConfirmation,
                            onCheckedChange = { viewModel.setRequireActionConfirm(it) },
                            modifier = Modifier.testTag("operator_safety_switch")
                        )
                    }
                }
            }

            // Command Composer Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "実行したいスマホ操作を指示",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = goalInput,
                                onValueChange = { viewModel.updateOperatorGoal(it) },
                                placeholder = { Text("例: 設定アプリを開いて / 通知を開いて") },
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
                                    .testTag("operator_goal_input")
                            )

                            IconButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) viewModel.startVoiceInput(VoiceTarget.OPERATOR)
                                    else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .background(NeuralIndigo.copy(alpha = 0.25f), CircleShape)
                                    .testTag("operator_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "音声で操作を指示",
                                    tint = NeuralIndigo
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick task preset chips
                        val presets = listOf(
                            "設定アプリを開く",
                            "通知シェードを開く",
                            "クイック設定を開く",
                            "ホーム画面に戻る",
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
                                    onClick = { viewModel.updateOperatorGoal(preset) },
                                    label = { Text(preset) },
                                    modifier = Modifier.testTag("operator_preset_$idx")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val isRunning = operatorState is OperatorUiState.InspectingScreen ||
                            operatorState is OperatorUiState.PlanningStep ||
                            operatorState is OperatorUiState.ExecutingAction ||
                            operatorState is OperatorUiState.AwaitingConfirmation

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.startPhoneTask(goalInput) },
                                enabled = goalInput.isNotBlank() && !isRunning,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("operator_execute_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text("AI自律操作を開始")
                            }

                            if (isRunning) {
                                Button(
                                    onClick = { viewModel.cancelPhoneTask() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CrimsonStop,
                                        contentColor = ObsidianBg
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("operator_stop_button")
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

            // Live Operator Execution State / Confirmation Dialog Card
            item {
                OperatorStatePanel(
                    state = operatorState,
                    onApprove = { viewModel.approvePendingAction() },
                    onCancel = { viewModel.cancelPhoneTask() },
                    onRetry = { viewModel.startPhoneTask(goalInput) }
                )
            }

            // Expandable Accessibility Screen Node Inspector
            latestSnapshot?.let { snapshot ->
                item {
                    ScreenInspectorCard(
                        snapshot = snapshot,
                        expanded = showNodeInspector,
                        onToggle = { showNodeInspector = !showNodeInspector }
                    )
                }
            }

            // Recent Execution Timeline
            if (operatorLogs.isNotEmpty()) {
                item {
                    Text(
                        text = "最近の自律操作ログ",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(operatorLogs.take(6), key = { it.id }) { log ->
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline),
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

private fun com.example.data.local.UserPreferences.requireActionConfirmOrDefault(): Boolean =
    requireActionConfirmation

@Composable
private fun AccessibilityStatusCard(
    isConnected: Boolean,
    onOpenSettings: () -> Unit,
    onInspectScreen: () -> Unit
) {
    val accent = if (isConnected) EmeraldPulse else AmberAlert
    Card(
        colors = CardDefaults.cardColors(
            containerColor = accent.copy(alpha = 0.12f)
        ),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.AccessibilityNew,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isConnected) "ユーザー補助サービス: 接続済み" else "ユーザー補助サービス: 未有効",
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isConnected) {
                            "画面要素の読み取り・タップ・スクロール・文字入力・アプリ起動が利用可能です。"
                        } else {
                            "Android設定 → ユーザー補助 →「OmniLive AI スマホ操作エージェント」をONにしてください。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("open_accessibility_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    Text("ユーザー補助設定を開く")
                }

                if (isConnected) {
                    OutlinedButton(
                        onClick = onInspectScreen,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("inspect_screen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text("画面解析テスト")
                    }
                }
            }
        }
    }
}

@Composable
private fun OperatorStatePanel(
    state: OperatorUiState,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        OperatorUiState.Idle -> Unit
        is OperatorUiState.InspectingScreen,
        is OperatorUiState.PlanningStep,
        is OperatorUiState.ExecutingAction -> {
            val statusLabel = when (state) {
                is OperatorUiState.InspectingScreen -> "ステップ ${state.step}/${state.maxSteps}: 画面構造をスキャン中..."
                is OperatorUiState.PlanningStep -> {
                    if (state.elapsedSeconds > 0) {
                        "ステップ ${state.step}/${state.maxSteps}: Gemini が操作を計画中... (${state.elapsedSeconds}秒)"
                    } else {
                        "ステップ ${state.step}/${state.maxSteps}: Gemini が操作を計画中..."
                    }
                }
                is OperatorUiState.ExecutingAction -> "ステップ ${state.step}: ${state.plan.actionType.name} を実行中..."
                else -> ""
            }
            Surface(
                color = ElectricCyan.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
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
                        text = statusLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = ElectricCyan
                    )
                }
            }
        }
        is OperatorUiState.AwaitingConfirmation -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceHighest),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, AmberAlert),
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
                            text = "実行確認 (ステップ ${state.step}/${state.maxSteps})",
                            style = MaterialTheme.typography.titleMedium,
                            color = AmberAlert
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "アクション: ${state.plan.actionType.name}" +
                            if (state.plan.targetNodeId >= 0) " (node[${state.plan.targetNodeId}])" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                    if (state.plan.inputText.isNotBlank()) {
                        Text(
                            text = "対象 / 入力: ${state.plan.inputText}",
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
                                .testTag("operator_approve_button")
                        ) {
                            Text("承認して実行")
                        }
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("operator_cancel_confirm_button")
                        ) {
                            Text("中止")
                        }
                    }
                }
            }
        }
        is OperatorUiState.Completed -> {
            Surface(
                color = EmeraldPulse.copy(alpha = 0.14f),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldPulse.copy(alpha = 0.45f)),
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
        is OperatorUiState.Error -> {
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
                            .testTag("operator_retry_button")
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

@Composable
private fun ScreenInspectorCard(
    snapshot: ScreenSnapshot,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ライブ画面インスペクター (${snapshot.nodes.size} 要素)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${snapshot.packageName} • ${snapshot.screenWidth}x${snapshot.screenHeight}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_inspector_button")
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "ノード一覧を閉じる" else "ノード一覧を開く"
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    snapshot.nodes.take(25).forEach { node ->
                        Text(
                            text = node.toPromptLine(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
