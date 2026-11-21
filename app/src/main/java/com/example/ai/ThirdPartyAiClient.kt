package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.local.LiveMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "ThirdPartyAiClient"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

class ThirdPartyApiException(
    val statusCode: Int,
    val provider: AiProvider,
    message: String
) : Exception(message)

class ThirdPartyAiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun streamChat(
        model: AiModelOption,
        systemPrompt: String,
        history: List<LiveMessageEntity>,
        prompt: String,
        imageBitmap: Bitmap?,
        localEndpointUrl: String = "",
        localModelName: String = ""
    ): Flow<String> = flow {
        when (model.provider) {
            AiProvider.OPENAI -> {
                val key = ProviderKeyStatus.getOpenAiKey()
                if (!ProviderKeyStatus.isProviderConfigured(AiProvider.OPENAI)) {
                    throw ThirdPartyApiException(
                        401,
                        AiProvider.OPENAI,
                        "OpenAI APIキーが未設定です。AI Studio の Secrets パネルで OPENAI_API_KEY を設定してください。"
                    )
                }
                streamOpenAiCompatible(
                    endpointUrl = "https://api.openai.com/v1/chat/completions",
                    apiKey = key,
                    modelId = model.modelId,
                    provider = AiProvider.OPENAI,
                    systemPrompt = systemPrompt,
                    history = history,
                    prompt = prompt,
                    imageBitmap = imageBitmap
                ).collect { emit(it) }
            }
            AiProvider.LOCAL_LLM -> {
                val url = localEndpointUrl.trim().ifBlank { "http://10.0.2.2:11434/v1/chat/completions" }
                val mdl = localModelName.trim().ifBlank { "gemma3" }
                streamOpenAiCompatible(
                    endpointUrl = url,
                    apiKey = null,
                    modelId = mdl,
                    provider = AiProvider.LOCAL_LLM,
                    systemPrompt = systemPrompt,
                    history = history,
                    prompt = prompt,
                    imageBitmap = imageBitmap
                ).collect { emit(it) }
            }
            AiProvider.ANTHROPIC -> {
                val key = ProviderKeyStatus.getAnthropicKey()
                if (!ProviderKeyStatus.isProviderConfigured(AiProvider.ANTHROPIC)) {
                    throw ThirdPartyApiException(
                        401,
                        AiProvider.ANTHROPIC,
                        "Anthropic APIキーが未設定です。AI Studio の Secrets パネルで ANTHROPIC_API_KEY を設定してください。"
                    )
                }
                streamAnthropic(key, model.modelId, systemPrompt, history, prompt, imageBitmap)
                    .collect { emit(it) }
            }
            AiProvider.GEMINI, AiProvider.UNLIMITED -> {
                throw IllegalArgumentException("Gemini/Unlimited routing is handled in GeminiAiRepository.")
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun generateOperatorJson(
        model: AiModelOption,
        promptText: String,
        imageBitmap: Bitmap? = null,
        schemaHintOverride: String? = null,
        localEndpointUrl: String = "",
        localModelName: String = ""
    ): String = withContext(Dispatchers.IO) {
        when (model.provider) {
            AiProvider.OPENAI -> {
                if (!ProviderKeyStatus.isProviderConfigured(AiProvider.OPENAI)) {
                    throw ThirdPartyApiException(
                        401,
                        AiProvider.OPENAI,
                        "OpenAI APIキーが未設定です。AI Studio の Secrets パネルで OPENAI_API_KEY を設定してください。"
                    )
                }
                callOpenAiCompatibleJson(
                    endpointUrl = "https://api.openai.com/v1/chat/completions",
                    apiKey = ProviderKeyStatus.getOpenAiKey(),
                    modelId = model.modelId,
                    provider = AiProvider.OPENAI,
                    promptText = promptText,
                    imageBitmap = imageBitmap,
                    schemaHintOverride = schemaHintOverride
                )
            }
            AiProvider.LOCAL_LLM -> {
                val url = localEndpointUrl.trim().ifBlank { "http://10.0.2.2:11434/v1/chat/completions" }
                val mdl = localModelName.trim().ifBlank { "gemma3" }
                callOpenAiCompatibleJson(
                    endpointUrl = url,
                    apiKey = null,
                    modelId = mdl,
                    provider = AiProvider.LOCAL_LLM,
                    promptText = promptText,
                    imageBitmap = imageBitmap,
                    schemaHintOverride = schemaHintOverride
                )
            }
            AiProvider.ANTHROPIC -> {
                if (!ProviderKeyStatus.isProviderConfigured(AiProvider.ANTHROPIC)) {
                    throw ThirdPartyApiException(
                        401,
                        AiProvider.ANTHROPIC,
                        "Anthropic APIキーが未設定です。AI Studio の Secrets パネルで ANTHROPIC_API_KEY を設定してください。"
                    )
                }
                callAnthropicJson(
                    apiKey = ProviderKeyStatus.getAnthropicKey(),
                    modelId = model.modelId,
                    promptText = promptText,
                    imageBitmap = imageBitmap,
                    schemaHintOverride = schemaHintOverride
                )
            }
            AiProvider.GEMINI, AiProvider.UNLIMITED -> {
                throw IllegalArgumentException("Gemini/Unlimited routing is handled in GeminiAiRepository.")
            }
        }
    }

    private fun streamOpenAiCompatible(
        endpointUrl: String,
        apiKey: String?,
        modelId: String,
        provider: AiProvider,
        systemPrompt: String,
        history: List<LiveMessageEntity>,
        prompt: String,
        imageBitmap: Bitmap?
    ): Flow<String> = flow {
        val messagesJson = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.takeLast(16).forEach { msg ->
                val role = if (msg.role == "USER") "user" else "assistant"
                put(JSONObject().put("role", role).put("content", msg.text))
            }
            if (imageBitmap != null) {
                val base64 = bitmapToBase64Jpeg(imageBitmap)
                val userParts = JSONArray().apply {
                    put(JSONObject().put("type", "text").put("text", prompt))
                    put(
                        JSONObject().put("type", "image_url").put(
                            "image_url",
                            JSONObject().put("url", "data:image/jpeg;base64,$base64")
                        )
                    )
                }
                put(JSONObject().put("role", "user").put("content", userParts))
            } else {
                put(JSONObject().put("role", "user").put("content", prompt))
            }
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("messages", messagesJson)
            put("stream", true)
        }

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (!apiKey.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        val accumulated = StringBuilder()
        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                throw mapHttpError(provider, response.code, errBody)
            }
            val reader = response.body?.byteStream()?.bufferedReader()
                ?: throw ThirdPartyApiException(500, provider, "${provider.shortLabel} レスポンスストリームが空です。")

            reader.useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isBlank()) continue
                    runCatching {
                        val obj = JSONObject(data)
                        val choices = obj.optJSONArray("choices") ?: return@runCatching
                        if (choices.length() > 0) {
                            val delta = choices.getJSONObject(0).optJSONObject("delta")
                            val content = delta?.optString("content", "").orEmpty()
                            if (content.isNotEmpty()) {
                                accumulated.append(content)
                            }
                        }
                    }
                    if (accumulated.isNotEmpty()) {
                        emit(accumulated.toString())
                    }
                }
            }
        }
        if (accumulated.isBlank()) {
            throw ThirdPartyApiException(500, provider, "${provider.shortLabel} から空の応答が返されました。")
        }
    }

    private fun streamAnthropic(
        apiKey: String,
        modelId: String,
        systemPrompt: String,
        history: List<LiveMessageEntity>,
        prompt: String,
        imageBitmap: Bitmap?
    ): Flow<String> = flow {
        val messagesJson = buildAnthropicMessages(history, prompt, imageBitmap)
        val payload = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", 2048)
            put("system", systemPrompt)
            put("messages", messagesJson)
            put("stream", true)
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val accumulated = StringBuilder()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errBody = response.body?.string().orEmpty()
                throw mapHttpError(AiProvider.ANTHROPIC, response.code, errBody)
            }
            val reader = response.body?.byteStream()?.bufferedReader()
                ?: throw ThirdPartyApiException(500, AiProvider.ANTHROPIC, "Claude レスポンスストリームが空です。")

            reader.useLines { lines ->
                for (line in lines) {
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isBlank()) continue
                    runCatching {
                        val obj = JSONObject(data)
                        if (obj.optString("type") == "content_block_delta") {
                            val delta = obj.optJSONObject("delta")
                            val text = delta?.optString("text", "").orEmpty()
                            if (text.isNotEmpty()) {
                                accumulated.append(text)
                            }
                        }
                    }
                    if (accumulated.isNotEmpty()) {
                        emit(accumulated.toString())
                    }
                }
            }
        }
        if (accumulated.isBlank()) {
            throw ThirdPartyApiException(500, AiProvider.ANTHROPIC, "Claude から空の応答が返されました。")
        }
    }

    private fun callOpenAiCompatibleJson(
        endpointUrl: String,
        apiKey: String?,
        modelId: String,
        provider: AiProvider,
        promptText: String,
        imageBitmap: Bitmap? = null,
        schemaHintOverride: String? = null
    ): String {
        val schemaHint = schemaHintOverride ?: """
            Return ONLY valid JSON matching:
            {"actionType":"TAP_NODE|TAP_COORDS|INPUT_TEXT|SCROLL_DOWN|SCROLL_UP|GLOBAL_BACK|GLOBAL_HOME|GLOBAL_RECENTS|OPEN_NOTIFICATIONS|OPEN_QUICK_SETTINGS|LAUNCH_APP|WAIT|DONE","targetNodeId":-1,"x":-1,"y":-1,"inputText":"","reasoning":"日本語","speechFeedback":"日本語","requiresConfirmation":false,"isComplete":false}
        """.trimIndent()

        val userMessage = if (imageBitmap != null) {
            val base64 = bitmapToBase64Jpeg(imageBitmap)
            val parts = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", promptText))
                put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$base64")
                    )
                )
            }
            JSONObject().put("role", "user").put("content", parts)
        } else {
            JSONObject().put("role", "user").put("content", promptText)
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("temperature", 0.2)
            put("max_tokens", 512)
            if (provider == AiProvider.OPENAI) {
                put("response_format", JSONObject().put("type", "json_object"))
            }
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", schemaHint))
                    put(userMessage)
                }
            )
        }

        val reqBuilder = Request.Builder()
            .url(endpointUrl)
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
        if (!apiKey.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        client.newCall(reqBuilder.build()).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw mapHttpError(provider, response.code, bodyStr)
            }
            val root = JSONObject(bodyStr)
            return root.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        }
    }

    private fun callAnthropicJson(
        apiKey: String,
        modelId: String,
        promptText: String,
        imageBitmap: Bitmap? = null,
        schemaHintOverride: String? = null
    ): String {
        val schemaHint = schemaHintOverride ?: """
            Return ONLY raw valid JSON (no markdown fences) matching:
            {"actionType":"TAP_NODE|TAP_COORDS|INPUT_TEXT|SCROLL_DOWN|SCROLL_UP|GLOBAL_BACK|GLOBAL_HOME|GLOBAL_RECENTS|OPEN_NOTIFICATIONS|OPEN_QUICK_SETTINGS|LAUNCH_APP|WAIT|DONE","targetNodeId":-1,"x":-1,"y":-1,"inputText":"","reasoning":"日本語","speechFeedback":"日本語","requiresConfirmation":false,"isComplete":false}
        """.trimIndent()

        val userMessage = if (imageBitmap != null) {
            val base64 = bitmapToBase64Jpeg(imageBitmap)
            val parts = JSONArray().apply {
                put(
                    JSONObject().put("type", "image").put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/jpeg")
                            .put("data", base64)
                    )
                )
                put(JSONObject().put("type", "text").put("text", promptText))
            }
            JSONObject().put("role", "user").put("content", parts)
        } else {
            JSONObject().put("role", "user").put("content", promptText)
        }

        val payload = JSONObject().apply {
            put("model", modelId)
            put("max_tokens", 512)
            put("temperature", 0.2)
            put("system", schemaHint)
            put(
                "messages",
                JSONArray().apply {
                    put(userMessage)
                    put(JSONObject().put("role", "assistant").put("content", "{"))
                }
            )
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw mapHttpError(AiProvider.ANTHROPIC, response.code, bodyStr)
            }
            val root = JSONObject(bodyStr)
            val contentArray = root.getJSONArray("content")
            val text = contentArray.getJSONObject(0).getString("text")
            return "{$text"
        }
    }

    private fun buildAnthropicMessages(
        history: List<LiveMessageEntity>,
        prompt: String,
        imageBitmap: Bitmap?
    ): JSONArray {
        val array = JSONArray()
        var lastRole: String? = null
        history.takeLast(12).forEach { msg ->
            val role = if (msg.role == "USER") "user" else "assistant"
            if (role == "user" && lastRole == null) {
                array.put(JSONObject().put("role", role).put("content", msg.text))
                lastRole = role
            } else if (lastRole != null && role != lastRole) {
                array.put(JSONObject().put("role", role).put("content", msg.text))
                lastRole = role
            }
        }
        if (lastRole == "user" && array.length() > 0) {
            array.remove(array.length() - 1)
        }

        if (imageBitmap != null) {
            val base64 = bitmapToBase64Jpeg(imageBitmap)
            val parts = JSONArray().apply {
                put(
                    JSONObject().put("type", "image").put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/jpeg")
                            .put("data", base64)
                    )
                )
                put(JSONObject().put("type", "text").put("text", prompt))
            }
            array.put(JSONObject().put("role", "user").put("content", parts))
        } else {
            array.put(JSONObject().put("role", "user").put("content", prompt))
        }
        return array
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun mapHttpError(provider: AiProvider, code: Int, body: String): ThirdPartyApiException {
        Log.e(TAG, "${provider.shortLabel} HTTP $code: $body")
        val parsedMsg = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        }.getOrDefault("")
        val userMsg = when (code) {
            401 -> "${provider.shortLabel} のAPIキーが無効です。Secrets パネルの設定を確認してください。"
            429 -> "${provider.shortLabel} のレート制限・クォータ上限に達しました (HTTP 429)。別モデルへ切り替えるか、しばらくお待ちください。"
            else -> "${provider.shortLabel} APIエラー (HTTP $code): ${parsedMsg.ifBlank { body.take(160) }}"
        }
        return ThirdPartyApiException(code, provider, userMsg)
    }
}
