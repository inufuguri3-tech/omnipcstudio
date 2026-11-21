package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.AiModelOption
import com.example.ai.AiProvider
import com.example.ai.ProviderKeyStatus
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeuralIndigo
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerSheet(
    title: String,
    selectedModel: AiModelOption,
    onSelectModel: (AiModelOption) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val grouped = AiProvider.entries.map { provider ->
        provider to AiModelOption.entries.filter { it.provider == provider }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "アプリ側の会話回数・文字数制限はありません。ChatGPT / Claude は AI Studio の Secrets 設定で利用可能です。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                grouped.forEach { (provider, models) ->
                    item(key = "header_${provider.name}") {
                        val isConfigured = ProviderKeyStatus.isProviderConfigured(provider)
                        val badgeColor = when {
                            provider == AiProvider.UNLIMITED -> EmeraldPulse
                            provider == AiProvider.GEMINI -> ElectricCyan
                            provider == AiProvider.LOCAL_LLM -> EmeraldPulse
                            isConfigured -> ElectricCyan
                            else -> AmberAlert
                        }
                        val badgeText = when {
                            provider == AiProvider.UNLIMITED -> "制限なし・自動最適"
                            provider == AiProvider.GEMINI -> "App Check・キー不要"
                            provider == AiProvider.LOCAL_LLM -> "完全ローカル・クォータ無し"
                            isConfigured -> "APIキー設定済み"
                            else -> "Secrets未設定"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = provider.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = when (provider) {
                                    AiProvider.UNLIMITED -> EmeraldPulse
                                    AiProvider.GEMINI -> ElectricCyan
                                    AiProvider.OPENAI -> EmeraldPulse
                                    AiProvider.ANTHROPIC -> NeuralIndigo
                                    AiProvider.LOCAL_LLM -> ElectricCyan
                                }
                            )
                            Surface(
                                color = badgeColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (provider == AiProvider.GEMINI || provider == AiProvider.UNLIMITED || provider == AiProvider.LOCAL_LLM) {
                                            Icons.Default.VerifiedUser
                                        } else {
                                            Icons.Default.Key
                                        },
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor
                                    )
                                }
                            }
                        }
                    }

                    items(models, key = { it.modelId }) { option ->
                        val isSelected = option == selectedModel
                        Surface(
                            color = if (isSelected) ElectricCyan.copy(alpha = 0.14f) else ObsidianSurfaceElevated,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) ElectricCyan else ObsidianOutline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectModel(option)
                                    onDismiss()
                                }
                                .testTag("model_option_${option.modelId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = option.displayName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) ElectricCyan else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "選択中",
                                        tint = ElectricCyan
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
