package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiModelOption
import com.example.ai.AiProvider
import com.example.ai.ProviderKeyStatus
import com.example.ui.components.ModelPickerSheet
import com.example.ui.theme.CrimsonStop
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.viewmodel.OmniLiveViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryAndSettingsScreen(
    viewModel: OmniLiveViewModel,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val logs by viewModel.operatorLogs.collectAsStateWithLifecycle()

    var pickerTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val liveModel = AiModelOption.fromId(prefs.selectedLiveModelId)
    val operatorModel = AiModelOption.fromId(prefs.selectedOperatorModelId)

    if (pickerTarget == "LIVE") {
        ModelPickerSheet(
            title = "Live Pro モデルを選択",
            selectedModel = liveModel,
            onSelectModel = { viewModel.setLiveModel(it) },
            onDismiss = { pickerTarget = null }
        )
    } else if (pickerTarget == "OPERATOR") {
        ModelPickerSheet(
            title = "スマホ操作モデルを選択",
            selectedModel = operatorModel,
            onSelectModel = { viewModel.setOperatorModel(it) },
            onDismiss = { pickerTarget = null }
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
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "AI 設定とローカル履歴",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Multi-Provider & Secrets Info Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurfaceElevated),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "マルチAIプロバイダー接続",
                            style = MaterialTheme.typography.titleMedium,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val openAiOk = ProviderKeyStatus.isProviderConfigured(AiProvider.OPENAI)
                        val anthropicOk = ProviderKeyStatus.isProviderConfigured(AiProvider.ANTHROPIC)
                        Text(
                            text = "• 無制限AI (自動フェイルオーバー): 利用可能な全エンジンを自動切替\n" +
                                "• Gemini: Firebase AI Logic (App Check・キー不要で即利用可能)\n" +
                                "• ChatGPT (OpenAI): ${if (openAiOk) "設定済み" else "未設定 (Secrets パネルで OPENAI_API_KEY を登録)"}\n" +
                                "• Claude (Anthropic): ${if (anthropicOk) "設定済み" else "未設定 (Secrets パネルで ANTHROPIC_API_KEY を登録)"}\n" +
                                "• ローカルLLM (Ollama等): 完全ローカル・クラウドクォータ無し",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = true,
                                onClick = { pickerTarget = "LIVE" },
                                label = { Text("Live: ${liveModel.displayName}") },
                                modifier = Modifier.testTag("settings_pick_live_model")
                            )
                            FilterChip(
                                selected = true,
                                onClick = { pickerTarget = "OPERATOR" },
                                label = { Text("操作: ${operatorModel.displayName}") },
                                modifier = Modifier.testTag("settings_pick_operator_model")
                            )
                        }
                    }
                }
            }

            // Local / Ollama Zero-Quota Endpoint Card
            item {
                var endpointInput by rememberSaveable(prefs.localEndpointUrl) {
                    mutableStateOf(prefs.localEndpointUrl)
                }
                var modelInput by rememberSaveable(prefs.localModelName) {
                    mutableStateOf(prefs.localModelName)
                }
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "ローカルLLM / Ollama 設定（クォータ完全無し）",
                            style = MaterialTheme.typography.titleMedium,
                            color = EmeraldPulse
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "手元の PC / LAN 上の Ollama や LM Studio の OpenAI 互換エンドポイントを設定できます（APIキー不要）。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = endpointInput,
                            onValueChange = { endpointInput = it },
                            label = { Text("エンドポイント URL") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_endpoint_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = modelInput,
                            onValueChange = { modelInput = it },
                            label = { Text("ローカルモデル名 (例: gemma3, llama3.2)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("local_model_name_input")
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { viewModel.setLocalLlmConfig(endpointInput, modelInput) },
                            modifier = Modifier.testTag("save_local_llm_button")
                        ) {
                            Text("ローカルLLM設定を保存")
                        }
                    }
                }
            }

            // Preferences Card
            item {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Live Pro・音声・モデル設定",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        SettingSwitchRow(
                            title = "応答を自動で音声読み上げ (TTS)",
                            subtitle = "Live Pro の返答や操作報告を音声で再生",
                            checked = prefs.autoSpeakResponses,
                            onCheckedChange = { viewModel.setAutoSpeak(it) },
                            testTag = "setting_auto_speak"
                        )

                        SettingSwitchRow(
                            title = "連続ハンズフリー音声ループ",
                            subtitle = "読み上げ完了後に自動で次の音声待機を再開",
                            checked = prefs.continuousVoiceLoop,
                            onCheckedChange = { viewModel.setContinuousLoop(it) },
                            testTag = "setting_continuous_loop"
                        )

                        SettingSwitchRow(
                            title = "レート制限時の自動フォールバック",
                            subtitle = "HTTP 429・クォータ上限時に Gemini 3.5 Flash-Lite へ自動切替",
                            checked = prefs.autoFallbackOnRateLimit,
                            onCheckedChange = { viewModel.setAutoFallback(it) },
                            testTag = "setting_auto_fallback"
                        )

                        SettingSwitchRow(
                            title = "スマホ操作前の安全確認",
                            subtitle = "各アクションの実行前に確認ボタンを表示",
                            checked = prefs.requireActionConfirmation,
                            onCheckedChange = { viewModel.setRequireActionConfirm(it) },
                            testTag = "setting_safety_confirm"
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "音声認識・読み上げ言語",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = prefs.languageTag == "ja-JP",
                                onClick = { viewModel.setLanguageTag("ja-JP") },
                                label = { Text("日本語 (ja-JP)") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("lang_chip_ja")
                            )
                            FilterChip(
                                selected = prefs.languageTag == "en-US",
                                onClick = { viewModel.setLanguageTag("en-US") },
                                label = { Text("English (en-US)") },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("lang_chip_en")
                            )
                        }
                    }
                }
            }

            // Live Pro History Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Pro 会話履歴 (${messages.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearLiveHistory() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_live_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "会話履歴を削除",
                                tint = CrimsonStop
                            )
                        }
                    }
                }
            }

            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "まだ会話履歴がありません。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(messages.reversed().take(12), key = { "msg_${it.id}" }) { msg ->
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
                                    text = "${msg.role} • ${msg.mode}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = formatTimestamp(msg.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = msg.text.take(180),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Operator Logs Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "スマホ操作ログ (${logs.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (logs.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearOperatorLogs() },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("clear_operator_logs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "操作ログを削除",
                                tint = CrimsonStop
                            )
                        }
                    }
                }
            }

            if (logs.isEmpty()) {
                item {
                    Text(
                        text = "まだ操作ログがありません。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(logs.take(15), key = { "log_${it.id}" }) { log ->
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
                                    text = "${log.goal} (Step ${log.stepNumber})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (log.success) EmeraldPulse else CrimsonStop
                                )
                                Text(
                                    text = formatTimestamp(log.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${log.actionType}: ${log.reasoning}",
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
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

private fun formatTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return fmt.format(Date(millis))
}
