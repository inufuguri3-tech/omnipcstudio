package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai.AiModelOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "omnilive_settings")

data class UserPreferences(
    val autoSpeakResponses: Boolean = true,
    val continuousVoiceLoop: Boolean = false,
    val useLiteModelForSpeed: Boolean = false,
    val requireActionConfirmation: Boolean = true,
    val languageTag: String = "ja-JP",
    val selectedLiveModelId: String = AiModelOption.UNLIMITED_AUTO.modelId,
    val selectedOperatorModelId: String = AiModelOption.UNLIMITED_AUTO.modelId,
    val autoFallbackOnRateLimit: Boolean = true,
    val localEndpointUrl: String = "http://10.0.2.2:11434/v1/chat/completions",
    val localModelName: String = "gemma3",
    val pcAgentUrl: String = "http://192.168.1.10:8765",
    val pcAgentToken: String = "",
    val pcBridgeListenerEnabled: Boolean = true
)

class SettingsDataStore(private val context: Context) {
    private object Keys {
        val AUTO_SPEAK = booleanPreferencesKey("auto_speak")
        val CONTINUOUS_LOOP = booleanPreferencesKey("continuous_loop")
        val USE_LITE_MODEL = booleanPreferencesKey("use_lite_model")
        val REQUIRE_CONFIRM = booleanPreferencesKey("require_action_confirm")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val LIVE_MODEL_ID = stringPreferencesKey("live_model_id")
        val OPERATOR_MODEL_ID = stringPreferencesKey("operator_model_id")
        val AUTO_FALLBACK = booleanPreferencesKey("auto_fallback_rate_limit")
        val LOCAL_ENDPOINT_URL = stringPreferencesKey("local_endpoint_url")
        val LOCAL_MODEL_NAME = stringPreferencesKey("local_model_name")
        val PC_AGENT_URL = stringPreferencesKey("pc_agent_url")
        val PC_AGENT_TOKEN = stringPreferencesKey("pc_agent_token")
        val PC_BRIDGE_LISTENER = booleanPreferencesKey("pc_bridge_listener_enabled")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            autoSpeakResponses = prefs[Keys.AUTO_SPEAK] ?: true,
            continuousVoiceLoop = prefs[Keys.CONTINUOUS_LOOP] ?: false,
            useLiteModelForSpeed = prefs[Keys.USE_LITE_MODEL] ?: false,
            requireActionConfirmation = prefs[Keys.REQUIRE_CONFIRM] ?: true,
            languageTag = prefs[Keys.LANGUAGE_TAG] ?: "ja-JP",
            selectedLiveModelId = prefs[Keys.LIVE_MODEL_ID] ?: AiModelOption.UNLIMITED_AUTO.modelId,
            selectedOperatorModelId = prefs[Keys.OPERATOR_MODEL_ID] ?: AiModelOption.UNLIMITED_AUTO.modelId,
            autoFallbackOnRateLimit = prefs[Keys.AUTO_FALLBACK] ?: true,
            localEndpointUrl = prefs[Keys.LOCAL_ENDPOINT_URL] ?: "http://10.0.2.2:11434/v1/chat/completions",
            localModelName = prefs[Keys.LOCAL_MODEL_NAME] ?: "gemma3",
            pcAgentUrl = prefs[Keys.PC_AGENT_URL] ?: "http://192.168.1.10:8765",
            pcAgentToken = prefs[Keys.PC_AGENT_TOKEN] ?: "",
            pcBridgeListenerEnabled = prefs[Keys.PC_BRIDGE_LISTENER] ?: true
        )
    }

    suspend fun setAutoSpeak(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SPEAK] = enabled }
    }

    suspend fun setContinuousVoiceLoop(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CONTINUOUS_LOOP] = enabled }
    }

    suspend fun setUseLiteModel(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.USE_LITE_MODEL] = enabled
            it[Keys.LIVE_MODEL_ID] = if (enabled) {
                AiModelOption.GEMINI_FLASH_LITE.modelId
            } else {
                AiModelOption.GEMINI_FLASH.modelId
            }
        }
    }

    suspend fun setRequireActionConfirm(enabled: Boolean) {
        context.dataStore.edit { it[Keys.REQUIRE_CONFIRM] = enabled }
    }

    suspend fun setLanguageTag(tag: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_TAG] = tag }
    }

    suspend fun setLiveModelId(modelId: String) {
        context.dataStore.edit {
            it[Keys.LIVE_MODEL_ID] = modelId
            it[Keys.USE_LITE_MODEL] = (modelId == AiModelOption.GEMINI_FLASH_LITE.modelId)
        }
    }

    suspend fun setOperatorModelId(modelId: String) {
        context.dataStore.edit { it[Keys.OPERATOR_MODEL_ID] = modelId }
    }

    suspend fun setAutoFallbackOnRateLimit(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_FALLBACK] = enabled }
    }

    suspend fun setLocalLlmConfig(endpointUrl: String, modelName: String) {
        context.dataStore.edit {
            it[Keys.LOCAL_ENDPOINT_URL] = endpointUrl.trim()
            it[Keys.LOCAL_MODEL_NAME] = modelName.trim()
        }
    }

    suspend fun setPcAgentConfig(url: String, token: String) {
        context.dataStore.edit {
            it[Keys.PC_AGENT_URL] = url.trim()
            it[Keys.PC_AGENT_TOKEN] = token.trim()
        }
    }

    suspend fun setPcBridgeListenerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PC_BRIDGE_LISTENER] = enabled }
    }
}
