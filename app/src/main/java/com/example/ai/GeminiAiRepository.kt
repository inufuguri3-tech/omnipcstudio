package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.LiveMessageEntity
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAiRepository"
private const val LIVE_TIMEOUT_MS = 60_000L
private const val OPERATOR_TIMEOUT_MS = 45_000L

class GeminiAiRepository {

    private val thirdPartyClient = ThirdPartyAiClient()

    private val flashModel by lazy {
        Firebase.ai.generativeModel(modelName = AiModelOption.GEMINI_FLASH.modelId)
    }

    private val liteModel by lazy {
        Firebase.ai.generativeModel(modelName = AiModelOption.GEMINI_FLASH_LITE.modelId)
    }

    private val argonModel by lazy {
        Firebase.ai.generativeModel(modelName = AiModelOption.GEMINI_ARGON.modelId)
    }

    private val operatorPlanSchema = Schema.obj(
        properties = mapOf(
            "actionType" to Schema.enumeration(
                values = OperatorActionType.entries.map { it.name },
                description = "Next Android accessibility action to execute"
            ),
            "targetNodeId" to Schema.integer(
                description = "Index of target node from Visible UI elements, or -1 if not using node index"
            ),
            "x" to Schema.integer(
                description = "Target X pixel coordinate for TAP_COORDS, or -1 if unused"
            ),
            "y" to Schema.integer(
                description = "Target Y pixel coordinate for TAP_COORDS, or -1 if unused"
            ),
            "inputText" to Schema.string(
                description = "Text to type for INPUT_TEXT, or app name/package for LAUNCH_APP, or empty string"
            ),
            "reasoning" to Schema.string(
                description = "Concise explanation in Japanese of why this action advances the user goal"
            ),
            "speechFeedback" to Schema.string(
                description = "Short natural Japanese voice status update for the user"
            ),
            "requiresConfirmation" to Schema.boolean(
                description = "True if the action could send a message, purchase, delete data, or submit sensitive info"
            ),
            "isComplete" to Schema.boolean(
                description = "True only when the user goal is completely achieved or impossible"
            )
        )
    )

    private val pcPlanSchema = Schema.obj(
        properties = mapOf(
            "actionType" to Schema.enumeration(
                values = PcActionType.entries.map { it.name },
                description = "Next Windows/PC desktop action to execute"
            ),
            "x" to Schema.integer(
                description = "Normalized horizontal target 0..1000 (0=left edge, 500=center, 1000=right edge), or -1 if unused"
            ),
            "y" to Schema.integer(
                description = "Normalized vertical target 0..1000 (0=top edge, 500=center, 1000=bottom edge), or -1 if unused"
            ),
            "inputText" to Schema.string(
                description = "Text for TYPE_TEXT, hotkey combo like ctrl+c for HOTKEY, key name like enter for PRESS_KEY, app/URL for OPEN_APP, or empty string"
            ),
            "reasoning" to Schema.string(
                description = "Concise explanation in Japanese of why this PC action advances the goal"
            ),
            "speechFeedback" to Schema.string(
                description = "Short natural Japanese voice status update for the user"
            ),
            "requiresConfirmation" to Schema.boolean(
                description = "True if action sends messages, purchases, deletes files, runs terminal/admin actions, or submits sensitive forms"
            ),
            "isComplete" to Schema.boolean(
                description = "True only when the user goal is completely achieved or impossible"
            )
        )
    )

    private fun getOperatorGeminiModel(modelOption: AiModelOption) = when (modelOption) {
        AiModelOption.GEMINI_FLASH -> Firebase.ai.generativeModel(
            modelName = AiModelOption.GEMINI_FLASH.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = operatorPlanSchema
                temperature = 0.2f
                maxOutputTokens = 512
            }
        )
        else -> Firebase.ai.generativeModel(
            modelName = AiModelOption.GEMINI_FLASH_LITE.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = operatorPlanSchema
                temperature = 0.2f
                maxOutputTokens = 512
            }
        )
    }

    private fun getPcOperatorGeminiModel(modelOption: AiModelOption) = when (modelOption) {
        AiModelOption.GEMINI_FLASH -> Firebase.ai.generativeModel(
            modelName = AiModelOption.GEMINI_FLASH.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = pcPlanSchema
                temperature = 0.2f
                maxOutputTokens = 512
            }
        )
        else -> Firebase.ai.generativeModel(
            modelName = AiModelOption.GEMINI_FLASH_LITE.modelId,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = pcPlanSchema
                temperature = 0.2f
                maxOutputTokens = 512
            }
        )
    }

    fun streamLiveProResponse(
        prompt: String,
        mode: LiveProMode,
        history: List<LiveMessageEntity>,
        imageBitmap: Bitmap?,
        modelOption: AiModelOption,
        autoFallbackOnRateLimit: Boolean,
        localEndpointUrl: String = "",
        localModelName: String = ""
    ): Flow<String> = flow {
        if (modelOption == AiModelOption.UNLIMITED_AUTO) {
            val candidates = buildUnlimitedCandidates(preferFast = false)
            var lastError: Throwable? = null
            for (candidate in candidates) {
                try {
                    streamForModel(
                        modelOption = candidate,
                        prompt = prompt,
                        mode = mode,
                        history = history,
                        imageBitmap = imageBitmap,
                        localEndpointUrl = localEndpointUrl,
                        localModelName = localModelName
                    ).collect { emit(it) }
                    return@flow
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Unlimited auto-route failed on ${candidate.displayName}: ${e.message}")
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("利用可能なAIエンジンからの応答に失敗しました。")
        }

        try {
            streamForModel(
                modelOption = modelOption,
                prompt = prompt,
                mode = mode,
                history = history,
                imageBitmap = imageBitmap,
                localEndpointUrl = localEndpointUrl,
                localModelName = localModelName
            ).collect { emit(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val isRateLimit = isRateLimitOrQuotaError(e)
            if (autoFallbackOnRateLimit && isRateLimit && modelOption != AiModelOption.GEMINI_FLASH_LITE) {
                Log.w(TAG, "Rate limit hit on ${modelOption.displayName}, falling back to Gemini 3.5 Flash-Lite", e)
                streamForModel(
                    modelOption = AiModelOption.GEMINI_FLASH_LITE,
                    prompt = prompt,
                    mode = mode,
                    history = history,
                    imageBitmap = imageBitmap,
                    localEndpointUrl = localEndpointUrl,
                    localModelName = localModelName
                ).collect { emit(it) }
            } else {
                throw e
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun streamForModel(
        modelOption: AiModelOption,
        prompt: String,
        mode: LiveProMode,
        history: List<LiveMessageEntity>,
        imageBitmap: Bitmap?,
        localEndpointUrl: String,
        localModelName: String
    ): Flow<String> {
        return when (modelOption.provider) {
            AiProvider.GEMINI -> streamGemini(modelOption, prompt, mode, history, imageBitmap)
            AiProvider.OPENAI, AiProvider.ANTHROPIC, AiProvider.LOCAL_LLM -> thirdPartyClient.streamChat(
                model = modelOption,
                systemPrompt = mode.systemPrompt,
                history = history,
                prompt = prompt,
                imageBitmap = imageBitmap,
                localEndpointUrl = localEndpointUrl,
                localModelName = localModelName
            )
            AiProvider.UNLIMITED -> streamGemini(AiModelOption.GEMINI_FLASH, prompt, mode, history, imageBitmap)
        }
    }

    private fun streamGemini(
        modelOption: AiModelOption,
        prompt: String,
        mode: LiveProMode,
        history: List<LiveMessageEntity>,
        imageBitmap: Bitmap?
    ): Flow<String> = flow {
        val activeModel = when (modelOption) {
            AiModelOption.GEMINI_FLASH_LITE -> liteModel
            AiModelOption.GEMINI_ARGON -> argonModel
            else -> flashModel
        }
        val recentHistory = history.takeLast(14)

        val composedContent = content {
            text("[システム指示]\n${mode.systemPrompt}")
            if (recentHistory.isNotEmpty()) {
                val historyBlock = recentHistory.joinToString("\n") { msg ->
                    val speaker = if (msg.role == "USER") "User" else "Assistant"
                    "$speaker: ${msg.text.take(600)}"
                }
                text("[直近の会話履歴]\n$historyBlock")
            }
            if (imageBitmap != null) {
                image(imageBitmap)
            }
            text("[ユーザーの最新入力]\n$prompt")
        }

        val accumulated = StringBuilder()
        try {
            withTimeout(LIVE_TIMEOUT_MS) {
                activeModel.generateContentStream(composedContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("Gemini から空の応答が返されました。")
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Live Pro streaming timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Live Pro streaming failed", e)
            throw e
        }
    }

    suspend fun planNextPhoneAction(
        goal: String,
        snapshot: ScreenSnapshot,
        installedApps: List<InstalledAppSummary>,
        previousSteps: List<String>,
        stepIndex: Int,
        modelOption: AiModelOption,
        autoFallbackOnRateLimit: Boolean,
        localEndpointUrl: String = "",
        localModelName: String = ""
    ): OperatorStepPlan = withContext(Dispatchers.IO) {
        val compactPrompt = buildOperatorPrompt(
            goal = goal,
            snapshot = snapshot,
            installedApps = installedApps,
            previousSteps = previousSteps,
            stepIndex = stepIndex,
            maxNodes = 30,
            maxApps = 20
        )

        if (modelOption == AiModelOption.UNLIMITED_AUTO) {
            val candidates = buildUnlimitedCandidates(preferFast = true)
            var lastError: Throwable? = null
            for (candidate in candidates) {
                try {
                    return@withContext executeOperatorForModel(
                        modelOption = candidate,
                        promptText = compactPrompt,
                        timeoutMs = OPERATOR_TIMEOUT_MS,
                        localEndpointUrl = localEndpointUrl,
                        localModelName = localModelName
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Unlimited operator candidate ${candidate.displayName} failed: ${e.message}")
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("操作プランの生成に失敗しました。")
        }

        try {
            executeOperatorForModel(
                modelOption = modelOption,
                promptText = compactPrompt,
                timeoutMs = OPERATOR_TIMEOUT_MS,
                localEndpointUrl = localEndpointUrl,
                localModelName = localModelName
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Operator primary inference timed out; retrying with minimal snapshot", e)
            val minimalPrompt = buildOperatorPrompt(
                goal = goal,
                snapshot = snapshot,
                installedApps = installedApps,
                previousSteps = previousSteps.takeLast(3),
                stepIndex = stepIndex,
                maxNodes = 14,
                maxApps = 10
            )
            executeOperatorForModel(
                modelOption = modelOption,
                promptText = minimalPrompt,
                timeoutMs = 30_000L,
                localEndpointUrl = localEndpointUrl,
                localModelName = localModelName
            )
        } catch (e: Exception) {
            if (autoFallbackOnRateLimit && isRateLimitOrQuotaError(e) && modelOption != AiModelOption.GEMINI_FLASH_LITE) {
                Log.w(TAG, "Operator rate limit on ${modelOption.displayName}; falling back to Gemini 3.5 Flash-Lite")
                executeOperatorForModel(
                    modelOption = AiModelOption.GEMINI_FLASH_LITE,
                    promptText = compactPrompt,
                    timeoutMs = OPERATOR_TIMEOUT_MS,
                    localEndpointUrl = localEndpointUrl,
                    localModelName = localModelName
                )
            } else {
                throw e
            }
        }
    }

    private suspend fun executeOperatorForModel(
        modelOption: AiModelOption,
        promptText: String,
        timeoutMs: Long,
        localEndpointUrl: String,
        localModelName: String
    ): OperatorStepPlan {
        val rawText = when (modelOption.provider) {
            AiProvider.GEMINI, AiProvider.UNLIMITED -> {
                val model = getOperatorGeminiModel(modelOption)
                val response = withTimeout(timeoutMs) {
                    model.generateContent(promptText)
                }
                response.text ?: throw IllegalStateException("操作プランのJSONが空でした。")
            }
            AiProvider.OPENAI, AiProvider.ANTHROPIC, AiProvider.LOCAL_LLM -> {
                withTimeout(timeoutMs) {
                    thirdPartyClient.generateOperatorJson(
                        model = modelOption,
                        promptText = promptText,
                        localEndpointUrl = localEndpointUrl,
                        localModelName = localModelName
                    )
                }
            }
        }

        val jsonText = extractJsonObjectString(rawText)
        val json = JSONObject(jsonText)
        val actionName = json.optString("actionType", OperatorActionType.DONE.name)
        val actionType = runCatching { OperatorActionType.valueOf(actionName) }
            .getOrDefault(OperatorActionType.DONE)

        return OperatorStepPlan(
            actionType = actionType,
            targetNodeId = json.optInt("targetNodeId", -1),
            x = json.optInt("x", -1),
            y = json.optInt("y", -1),
            inputText = json.optString("inputText", ""),
            reasoning = json.optString("reasoning", "画面状態に基づいて次の操作を選択しました。"),
            speechFeedback = json.optString("speechFeedback", "操作を実行します。"),
            requiresConfirmation = json.optBoolean("requiresConfirmation", false),
            isComplete = json.optBoolean("isComplete", actionType == OperatorActionType.DONE)
        )
    }

    private fun extractJsonObjectString(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            trimmed.substring(start, end + 1)
        } else {
            trimmed
        }
    }

    private fun buildUnlimitedCandidates(preferFast: Boolean): List<AiModelOption> {
        val list = mutableListOf<AiModelOption>()
        if (preferFast) {
            list.add(AiModelOption.GEMINI_FLASH_LITE)
            list.add(AiModelOption.GEMINI_FLASH)
        } else {
            list.add(AiModelOption.GEMINI_FLASH)
            list.add(AiModelOption.GEMINI_FLASH_LITE)
        }
        if (ProviderKeyStatus.isProviderConfigured(AiProvider.OPENAI)) {
            list.add(AiModelOption.OPENAI_GPT4O_MINI)
        }
        if (ProviderKeyStatus.isProviderConfigured(AiProvider.ANTHROPIC)) {
            list.add(AiModelOption.CLAUDE_3_5_HAIKU)
        }
        return list
    }

    private fun isRateLimitOrQuotaError(e: Throwable): Boolean {
        return (e is ThirdPartyApiException && e.statusCode == 429) ||
            e.localizedMessage?.contains("429") == true ||
            e.localizedMessage?.contains("quota", ignoreCase = true) == true ||
            e.localizedMessage?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true
    }

    private fun buildOperatorPrompt(
        goal: String,
        snapshot: ScreenSnapshot,
        installedApps: List<InstalledAppSummary>,
        previousSteps: List<String>,
        stepIndex: Int,
        maxNodes: Int,
        maxApps: Int
    ): String {
        val prioritizedApps = prioritizeApps(goal, installedApps, maxApps)
        val appsSummary = prioritizedApps.joinToString(", ") { "${it.label}(${it.packageName})" }

        val prioritizedNodes = snapshot.nodes
            .sortedByDescending { node ->
                (if (node.isClickable) 4 else 0) +
                    (if (node.isEditable) 4 else 0) +
                    (if (node.isScrollable) 2 else 0) +
                    (if (node.text.isNotBlank() || node.contentDescription.isNotBlank()) 1 else 0)
            }
            .take(maxNodes)
            .sortedBy { it.index }

        val nodesSummary = if (prioritizedNodes.isEmpty()) {
            "(none)"
        } else {
            prioritizedNodes.joinToString("\n") { it.toPromptLine() }
        }

        val historyText = if (previousSteps.isEmpty()) "None" else previousSteps.takeLast(5).joinToString(" | ")

        return """
            Android Accessibility AI Agent. Pick the single best next action for the goal.
            Goal: "$goal"
            Step: $stepIndex
            Previous: $historyText
            Foreground: ${snapshot.packageName} (${snapshot.screenWidth}x${snapshot.screenHeight})
            Apps: $appsSummary
            UI Elements:
            $nodesSummary

            Rules:
            - Use LAUNCH_APP (inputText=package or label) to open another app.
            - Use TAP_NODE (targetNodeId=index) for buttons/items, or INPUT_TEXT (targetNodeId + inputText).
            - Use SCROLL_DOWN/SCROLL_UP, GLOBAL_BACK/GLOBAL_HOME/GLOBAL_RECENTS/OPEN_NOTIFICATIONS/OPEN_QUICK_SETTINGS as needed.
            - Set actionType=DONE and isComplete=true once achieved.
            - Keep reasoning and speechFeedback brief in Japanese.
        """.trimIndent()
    }

    private fun prioritizeApps(
        goal: String,
        apps: List<InstalledAppSummary>,
        limit: Int
    ): List<InstalledAppSummary> {
        val lowerGoal = goal.lowercase()
        val matched = apps.filter { app ->
            lowerGoal.contains(app.label.lowercase()) ||
                app.label.lowercase().split(" ").any { token -> token.length >= 2 && lowerGoal.contains(token) } ||
                lowerGoal.contains(app.packageName.substringAfterLast(".").lowercase())
        }
        val commonKeywords = listOf("settings", "設定", "camera", "カメラ", "chrome", "clock", "時計", "calendar", "phone", "電話", "message", "gmail", "youtube", "map")
        val common = apps.filter { app ->
            commonKeywords.any { kw -> app.label.lowercase().contains(kw) || app.packageName.lowercase().contains(kw) }
        }
        return (matched + common + apps).distinctBy { it.packageName }.take(limit)
    }

    suspend fun planNextPcAction(
        goal: String,
        snapshot: PcScreenSnapshot,
        previousSteps: List<String>,
        stepIndex: Int,
        modelOption: AiModelOption,
        autoFallbackOnRateLimit: Boolean,
        localEndpointUrl: String = "",
        localModelName: String = ""
    ): PcStepPlan = withContext(Dispatchers.IO) {
        val promptText = buildPcOperatorPrompt(goal, snapshot, previousSteps, stepIndex)

        if (modelOption == AiModelOption.UNLIMITED_AUTO) {
            val candidates = buildUnlimitedCandidates(preferFast = true)
            var lastError: Throwable? = null
            for (candidate in candidates) {
                try {
                    return@withContext executePcOperatorForModel(
                        modelOption = candidate,
                        snapshot = snapshot,
                        promptText = promptText,
                        timeoutMs = OPERATOR_TIMEOUT_MS,
                        localEndpointUrl = localEndpointUrl,
                        localModelName = localModelName
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Unlimited PC operator candidate ${candidate.displayName} failed: ${e.message}")
                    lastError = e
                }
            }
            throw lastError ?: IllegalStateException("PC操作プランの生成に失敗しました。")
        }

        try {
            executePcOperatorForModel(
                modelOption = modelOption,
                snapshot = snapshot,
                promptText = promptText,
                timeoutMs = OPERATOR_TIMEOUT_MS,
                localEndpointUrl = localEndpointUrl,
                localModelName = localModelName
            )
        } catch (e: Exception) {
            if (autoFallbackOnRateLimit && isRateLimitOrQuotaError(e) && modelOption != AiModelOption.GEMINI_FLASH_LITE) {
                Log.w(TAG, "PC operator rate limit on ${modelOption.displayName}; falling back to Gemini 3.5 Flash-Lite")
                executePcOperatorForModel(
                    modelOption = AiModelOption.GEMINI_FLASH_LITE,
                    snapshot = snapshot,
                    promptText = promptText,
                    timeoutMs = OPERATOR_TIMEOUT_MS,
                    localEndpointUrl = localEndpointUrl,
                    localModelName = localModelName
                )
            } else {
                throw e
            }
        }
    }

    private suspend fun executePcOperatorForModel(
        modelOption: AiModelOption,
        snapshot: PcScreenSnapshot,
        promptText: String,
        timeoutMs: Long,
        localEndpointUrl: String,
        localModelName: String
    ): PcStepPlan {
        val pcSchemaHint = """
            Return ONLY raw valid JSON matching:
            {"actionType":"CLICK|DOUBLE_CLICK|RIGHT_CLICK|TYPE_TEXT|HOTKEY|PRESS_KEY|SCROLL_DOWN|SCROLL_UP|OPEN_APP|WAIT|DONE","x":500,"y":500,"inputText":"","reasoning":"日本語","speechFeedback":"日本語","requiresConfirmation":false,"isComplete":false}
            Note: x and y MUST be normalized integers 0..1000 (0=left/top, 1000=right/bottom), or -1 if unused.
        """.trimIndent()

        val rawText = when (modelOption.provider) {
            AiProvider.GEMINI, AiProvider.UNLIMITED -> {
                val model = getPcOperatorGeminiModel(modelOption)
                val multimodal = content {
                    image(snapshot.bitmap)
                    text(promptText)
                }
                val response = withTimeout(timeoutMs) {
                    model.generateContent(multimodal)
                }
                response.text ?: throw IllegalStateException("PC操作プランのJSONが空でした。")
            }
            AiProvider.OPENAI, AiProvider.ANTHROPIC, AiProvider.LOCAL_LLM -> {
                withTimeout(timeoutMs) {
                    thirdPartyClient.generateOperatorJson(
                        model = modelOption,
                        promptText = promptText,
                        imageBitmap = snapshot.bitmap,
                        schemaHintOverride = pcSchemaHint,
                        localEndpointUrl = localEndpointUrl,
                        localModelName = localModelName
                    )
                }
            }
        }

        val jsonText = extractJsonObjectString(rawText)
        val json = JSONObject(jsonText)
        val actionName = json.optString("actionType", PcActionType.DONE.name)
        val actionType = runCatching { PcActionType.valueOf(actionName) }
            .getOrDefault(PcActionType.DONE)

        val rawX = json.optInt("x", -1)
        val rawY = json.optInt("y", -1)
        val normX = if (rawX in 0..1000) rawX else -1
        val normY = if (rawY in 0..1000) rawY else -1
        val pixelX = if (normX >= 0) ((normX / 1000f) * snapshot.screenWidth).toInt().coerceIn(0, snapshot.screenWidth - 1) else -1
        val pixelY = if (normY >= 0) ((normY / 1000f) * snapshot.screenHeight).toInt().coerceIn(0, snapshot.screenHeight - 1) else -1

        return PcStepPlan(
            actionType = actionType,
            x = pixelX,
            y = pixelY,
            normX = normX,
            normY = normY,
            inputText = json.optString("inputText", ""),
            reasoning = json.optString("reasoning", "PC画面の内容に基づいて次の操作を選択しました。"),
            speechFeedback = json.optString("speechFeedback", "PC操作を実行します。"),
            requiresConfirmation = json.optBoolean("requiresConfirmation", false),
            isComplete = json.optBoolean("isComplete", actionType == PcActionType.DONE)
        )
    }

    private fun buildPcOperatorPrompt(
        goal: String,
        snapshot: PcScreenSnapshot,
        previousSteps: List<String>,
        stepIndex: Int
    ): String {
        val historyText = if (previousSteps.isEmpty()) "None" else previousSteps.takeLast(5).joinToString(" | ")
        return """
            Desktop GUI Vision AI Agent (${snapshot.platform}). Inspect the attached PC desktop screenshot and pick the single best next action.
            Goal: "$goal"
            Step: $stepIndex
            Active Window: "${snapshot.activeWindow.ifBlank { "Unknown" }}"
            Native Resolution: ${snapshot.screenWidth}x${snapshot.screenHeight}
            Previous Steps: $historyText

            Rules:
            - Coordinates x and y MUST be normalized integers from 0 to 1000 (0=left/top edge, 500=center, 1000=right/bottom edge), or -1 if unused.
            - Use OPEN_APP (inputText="notepad", "calc", "chrome", or "https://...") to launch Windows apps or websites directly without needing to hunt the Start menu.
            - Use CLICK / DOUBLE_CLICK / RIGHT_CLICK with normalized x,y (0..1000) to click buttons, icons, or text fields seen in the screenshot.
            - Use TYPE_TEXT (optional x,y to click first, inputText="...") to type ASCII text.
            - Use HOTKEY (inputText="ctrl+c", "alt+tab", "win+r", etc.) or PRESS_KEY (inputText="enter", "tab", "esc").
            - Use SCROLL_DOWN or SCROLL_UP to scroll.
            - Set actionType=DONE and isComplete=true once the goal is completed.
            - Keep reasoning and speechFeedback concise in Japanese.
        """.trimIndent()
    }
}
