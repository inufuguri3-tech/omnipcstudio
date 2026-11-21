package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiModelOption
import com.example.ai.GeminiAiRepository
import com.example.ai.LiveProMode
import com.example.ai.OperatorActionType
import com.example.ai.OperatorStepPlan
import com.example.ai.PcActionType
import com.example.ai.PcScreenSnapshot
import com.example.ai.PcStepPlan
import com.example.ai.ScreenSnapshot
import com.example.data.local.AppDatabase
import com.example.data.local.LiveMessageEntity
import com.example.data.local.OperatorLogEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.UserPreferences
import com.example.pc.PcAgentClient
import com.example.service.AccessibilityBridge
import com.example.voice.SpeechUiState
import com.example.voice.VoiceController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val TAG = "OmniLiveViewModel"

sealed interface LiveAiUiState {
    data object Idle : LiveAiUiState
    data object Loading : LiveAiUiState
    data class Streaming(val text: String) : LiveAiUiState
    data object Success : LiveAiUiState
    data class Error(val message: String) : LiveAiUiState
}

sealed interface OperatorUiState {
    data object Idle : OperatorUiState
    data class InspectingScreen(val step: Int, val maxSteps: Int) : OperatorUiState
    data class PlanningStep(val step: Int, val maxSteps: Int, val elapsedSeconds: Int = 0) : OperatorUiState
    data class AwaitingConfirmation(
        val plan: OperatorStepPlan,
        val snapshot: ScreenSnapshot,
        val step: Int,
        val maxSteps: Int
    ) : OperatorUiState
    data class ExecutingAction(val plan: OperatorStepPlan, val step: Int) : OperatorUiState
    data class Completed(val summary: String) : OperatorUiState
    data class Error(val message: String) : OperatorUiState
}

sealed interface PcOperatorUiState {
    data object Idle : PcOperatorUiState
    data class CapturingScreen(val step: Int, val maxSteps: Int) : PcOperatorUiState
    data class PlanningStep(val step: Int, val maxSteps: Int, val elapsedSeconds: Int = 0) : PcOperatorUiState
    data class AwaitingConfirmation(
        val plan: PcStepPlan,
        val snapshot: PcScreenSnapshot,
        val step: Int,
        val maxSteps: Int
    ) : PcOperatorUiState
    data class ExecutingAction(val plan: PcStepPlan, val step: Int) : PcOperatorUiState
    data class Completed(val summary: String) : PcOperatorUiState
    data class Error(val message: String) : PcOperatorUiState
}

enum class VoiceTarget {
    LIVE_PRO,
    OPERATOR,
    PC_OPERATOR
}

class OmniLiveViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val liveMessageDao = db.liveMessageDao()
    private val operatorLogDao = db.operatorLogDao()
    private val settingsStore = SettingsDataStore(application)
    private val aiRepository = GeminiAiRepository()
    private val pcAgentClient = PcAgentClient()

    val preferences: StateFlow<UserPreferences> = settingsStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    val messages: StateFlow<List<LiveMessageEntity>> = liveMessageDao.observeAllMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val operatorLogs: StateFlow<List<OperatorLogEntity>> = operatorLogDao.observeRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedMode = MutableStateFlow(LiveProMode.LIVE_CONVERSATION)
    val selectedMode: StateFlow<LiveProMode> = _selectedMode.asStateFlow()

    private val _liveAiState = MutableStateFlow<LiveAiUiState>(LiveAiUiState.Idle)
    val liveAiState: StateFlow<LiveAiUiState> = _liveAiState.asStateFlow()

    private val _pendingBitmap = MutableStateFlow<Bitmap?>(null)
    val pendingBitmap: StateFlow<Bitmap?> = _pendingBitmap.asStateFlow()

    private val _isLiveSessionActive = MutableStateFlow(false)
    val isLiveSessionActive: StateFlow<Boolean> = _isLiveSessionActive.asStateFlow()

    private val _operatorState = MutableStateFlow<OperatorUiState>(OperatorUiState.Idle)
    val operatorState: StateFlow<OperatorUiState> = _operatorState.asStateFlow()

    private val _operatorGoalInput = MutableStateFlow("")
    val operatorGoalInput: StateFlow<String> = _operatorGoalInput.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(
        AccessibilityBridge.isServiceEnabledInSettings(application)
    )
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    val isServiceConnected: StateFlow<Boolean> = AccessibilityBridge.isConnected
    val latestSnapshot: StateFlow<ScreenSnapshot?> = AccessibilityBridge.latestSnapshot

    private val _pcOperatorState = MutableStateFlow<PcOperatorUiState>(PcOperatorUiState.Idle)
    val pcOperatorState: StateFlow<PcOperatorUiState> = _pcOperatorState.asStateFlow()

    private val _pcGoalInput = MutableStateFlow("")
    val pcGoalInput: StateFlow<String> = _pcGoalInput.asStateFlow()

    private val _pcScreenBitmap = MutableStateFlow<Bitmap?>(null)
    val pcScreenBitmap: StateFlow<Bitmap?> = _pcScreenBitmap.asStateFlow()

    private val _pcConnectionStatus = MutableStateFlow<String?>(null)
    val pcConnectionStatus: StateFlow<String?> = _pcConnectionStatus.asStateFlow()

    private val _pcIsConnected = MutableStateFlow(false)
    val pcIsConnected: StateFlow<Boolean> = _pcIsConnected.asStateFlow()

    private val _pcActiveWindow = MutableStateFlow("")
    val pcActiveWindow: StateFlow<String> = _pcActiveWindow.asStateFlow()

    private var activeVoiceTarget = VoiceTarget.LIVE_PRO
    private var liveStreamJob: Job? = null
    private var operatorJob: Job? = null
    private var pcOperatorJob: Job? = null
    private var confirmationDeferred: CompletableDeferred<Boolean>? = null
    private var pcConfirmationDeferred: CompletableDeferred<Boolean>? = null

    private val voiceController = VoiceController(application) { finalTranscript ->
        when (activeVoiceTarget) {
            VoiceTarget.LIVE_PRO -> sendLivePrompt(finalTranscript)
            VoiceTarget.OPERATOR -> {
                _operatorGoalInput.value = finalTranscript
                startPhoneTask(finalTranscript)
            }
            VoiceTarget.PC_OPERATOR -> {
                _pcGoalInput.value = finalTranscript
                startPcTask(finalTranscript)
            }
        }
    }

    val speechState: StateFlow<SpeechUiState> = voiceController.speechState
    val isSpeaking: StateFlow<Boolean> = voiceController.isSpeaking

    init {
        viewModelScope.launch {
            AccessibilityBridge.emergencyStopRequested.collect { stopRequested ->
                if (stopRequested) {
                    cancelPhoneTask("ユーザーにより緊急停止されました。")
                }
            }
        }
    }

    fun refreshAccessibilityStatus() {
        _isAccessibilityEnabled.value =
            AccessibilityBridge.isServiceEnabledInSettings(getApplication()) ||
                AccessibilityBridge.isConnected.value
    }

    fun selectLiveMode(mode: LiveProMode) {
        _selectedMode.value = mode
    }

    fun attachBitmap(bitmap: Bitmap?) {
        _pendingBitmap.value = bitmap
    }

    fun attachImageFromUri(uri: Uri) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = getApplication<Application>().contentResolver
                    val stream: InputStream? = resolver.openInputStream(uri)
                    stream?.use { decodeScaledBitmap(it, maxDimension = 1024) }
                }.getOrNull()
            }
            if (loaded != null) {
                _pendingBitmap.value = loaded
            } else {
                _liveAiState.value = LiveAiUiState.Error("画像の読み込みに失敗しました。")
            }
        }
    }

    private fun decodeScaledBitmap(stream: InputStream, maxDimension: Int): Bitmap? {
        val original = BitmapFactory.decodeStream(stream) ?: return null
        val maxSide = maxOf(original.width, original.height)
        if (maxSide <= maxDimension) return original
        val scale = maxDimension.toFloat() / maxSide.toFloat()
        val w = (original.width * scale).toInt().coerceAtLeast(1)
        val h = (original.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(original, w, h, true).also {
            if (it != original) original.recycle()
        }
    }

    fun startVoiceInput(target: VoiceTarget) {
        activeVoiceTarget = target
        if (target == VoiceTarget.LIVE_PRO) {
            _isLiveSessionActive.value = true
        }
        voiceController.startListening(preferences.value.languageTag)
    }

    fun stopVoiceSession() {
        _isLiveSessionActive.value = false
        voiceController.stopListening()
        voiceController.stopSpeaking()
    }

    fun stopSpeakingOnly() {
        voiceController.stopSpeaking()
    }

    fun speakText(text: String) {
        voiceController.setLanguage(preferences.value.languageTag)
        voiceController.speak(text)
    }

    fun sendLivePrompt(rawPrompt: String, bitmapOverride: Bitmap? = null) {
        val prompt = rawPrompt.trim()
        val attached = bitmapOverride ?: _pendingBitmap.value
        if (prompt.isBlank() && attached == null) return

        val effectivePrompt = prompt.ifBlank { "この画像・カメラ映像を詳しく分析して教えてください。" }
        val mode = _selectedMode.value
        val prefs = preferences.value
        _pendingBitmap.value = null

        liveStreamJob?.cancel()
        liveStreamJob = viewModelScope.launch {
            _liveAiState.value = LiveAiUiState.Loading
            liveMessageDao.insertMessage(
                LiveMessageEntity(
                    role = "USER",
                    mode = mode.name,
                    text = effectivePrompt,
                    hasVisualContext = attached != null
                )
            )

            var finalText = ""
            val liveModel = AiModelOption.fromId(prefs.selectedLiveModelId)
            try {
                aiRepository.streamLiveProResponse(
                    prompt = effectivePrompt,
                    mode = mode,
                    history = messages.value,
                    imageBitmap = attached,
                    modelOption = liveModel,
                    autoFallbackOnRateLimit = prefs.autoFallbackOnRateLimit,
                    localEndpointUrl = prefs.localEndpointUrl,
                    localModelName = prefs.localModelName
                ).collect { partial ->
                    finalText = partial
                    _liveAiState.value = LiveAiUiState.Streaming(partial)
                }

                if (finalText.isNotBlank()) {
                    liveMessageDao.insertMessage(
                        LiveMessageEntity(
                            role = "AI",
                            mode = mode.name,
                            text = finalText,
                            hasVisualContext = false
                        )
                    )
                    _liveAiState.value = LiveAiUiState.Success
                    if (prefs.autoSpeakResponses) {
                        voiceController.setLanguage(prefs.languageTag)
                        voiceController.speak(finalText) {
                            if (_isLiveSessionActive.value && preferences.value.continuousVoiceLoop) {
                                viewModelScope.launch(Dispatchers.Main) {
                                    delay(350)
                                    if (_isLiveSessionActive.value) {
                                        startVoiceInput(VoiceTarget.LIVE_PRO)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    _liveAiState.value = LiveAiUiState.Error("Gemini から空の応答が返されました。")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Live Pro timed out", e)
                _liveAiState.value = LiveAiUiState.Error("応答がタイムアウトしました（60秒）。再度お試しください。")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live Pro failed", e)
                _liveAiState.value = LiveAiUiState.Error(
                    e.localizedMessage ?: "Gemini との通信中にエラーが発生しました。"
                )
            }
        }
    }

    fun updateOperatorGoal(goal: String) {
        _operatorGoalInput.value = goal
    }

    fun inspectCurrentScreenOnly() {
        refreshAccessibilityStatus()
        if (!AccessibilityBridge.isConnected.value) {
            _operatorState.value = OperatorUiState.Error(
                "ユーザー補助サービスが有効になっていません。上のボタンから「OmniLive AI スマホ操作エージェント」をONにしてください。"
            )
            return
        }
        val snap = AccessibilityBridge.captureScreenNow()
        if (snap == null) {
            _operatorState.value = OperatorUiState.Error("画面ツリーを取得できませんでした。")
        } else {
            _operatorState.value = OperatorUiState.Completed(
                "現在の画面（${snap.packageName}）から ${snap.nodes.size} 個のUI要素を取得しました。"
            )
        }
    }

    fun startPhoneTask(goalRaw: String) {
        val goal = goalRaw.trim()
        if (goal.isBlank()) return
        refreshAccessibilityStatus()

        if (!AccessibilityBridge.isConnected.value) {
            _operatorState.value = OperatorUiState.Error(
                "スマホを操作するには、Android設定の「ユーザー補助」で OmniLive を有効にする必要があります。"
            )
            return
        }

        operatorJob?.cancel()
        AccessibilityBridge.clearEmergencyStop()
        val maxSteps = 8

        operatorJob = viewModelScope.launch {
            val context = getApplication<Application>()
            val installedApps = withContext(Dispatchers.IO) {
                AccessibilityBridge.queryLaunchableApps(context)
            }
            val takenSteps = mutableListOf<String>()
            AccessibilityBridge.showFloatingHud("OmniLive: $goal")

            try {
                for (step in 1..maxSteps) {
                    _operatorState.value = OperatorUiState.InspectingScreen(step, maxSteps)
                    val snapshot = AccessibilityBridge.captureScreenNow()
                        ?: throw IllegalStateException("現在の画面ノードを取得できませんでした。")

                    _operatorState.value = OperatorUiState.PlanningStep(step, maxSteps, elapsedSeconds = 0)
                    val tickerJob = launch {
                        var secs = 0
                        while (true) {
                            delay(1_000L)
                            secs += 1
                            val current = _operatorState.value
                            if (current is OperatorUiState.PlanningStep && current.step == step) {
                                _operatorState.value = current.copy(elapsedSeconds = secs)
                            } else {
                                break
                            }
                        }
                    }
                    val operatorModel = AiModelOption.fromId(preferences.value.selectedOperatorModelId)
                    val plan = try {
                        aiRepository.planNextPhoneAction(
                            goal = goal,
                            snapshot = snapshot,
                            installedApps = installedApps,
                            previousSteps = takenSteps,
                            stepIndex = step,
                            modelOption = operatorModel,
                            autoFallbackOnRateLimit = preferences.value.autoFallbackOnRateLimit,
                            localEndpointUrl = preferences.value.localEndpointUrl,
                            localModelName = preferences.value.localModelName
                        )
                    } finally {
                        tickerJob.cancel()
                    }

                    if (plan.actionType == OperatorActionType.DONE || plan.isComplete) {
                        operatorLogDao.insertLog(
                            OperatorLogEntity(
                                goal = goal,
                                stepNumber = step,
                                actionType = plan.actionType.name,
                                targetSummary = "完了",
                                reasoning = plan.reasoning,
                                success = true
                            )
                        )
                        AccessibilityBridge.hideFloatingHud()
                        _operatorState.value = OperatorUiState.Completed(plan.reasoning)
                        if (preferences.value.autoSpeakResponses) {
                            voiceController.speak(plan.speechFeedback)
                        }
                        return@launch
                    }

                    val needsConfirm = preferences.value.requireActionConfirmation || plan.requiresConfirmation
                    if (needsConfirm) {
                        val deferred = CompletableDeferred<Boolean>()
                        confirmationDeferred = deferred
                        _operatorState.value = OperatorUiState.AwaitingConfirmation(
                            plan = plan,
                            snapshot = snapshot,
                            step = step,
                            maxSteps = maxSteps
                        )
                        val approved = deferred.await()
                        confirmationDeferred = null
                        if (!approved) {
                            AccessibilityBridge.hideFloatingHud()
                            _operatorState.value = OperatorUiState.Idle
                            return@launch
                        }
                    }

                    _operatorState.value = OperatorUiState.ExecutingAction(plan, step)
                    AccessibilityBridge.showFloatingHud("ステップ $step/$maxSteps: ${plan.actionType.name}")
                    val result = AccessibilityBridge.executePlan(plan, snapshot)

                    val targetDesc = when {
                        plan.inputText.isNotBlank() -> plan.inputText
                        plan.targetNodeId >= 0 -> "node[${plan.targetNodeId}]"
                        else -> "(${plan.x}, ${plan.y})"
                    }

                    operatorLogDao.insertLog(
                        OperatorLogEntity(
                            goal = goal,
                            stepNumber = step,
                            actionType = plan.actionType.name,
                            targetSummary = targetDesc,
                            reasoning = "${plan.reasoning} -> ${result.detail}",
                            success = result.success
                        )
                    )

                    takenSteps.add("Step $step: ${plan.actionType.name}($targetDesc) => ${result.detail}")

                    if (!result.success) {
                        AccessibilityBridge.hideFloatingHud()
                        _operatorState.value = OperatorUiState.Error(
                            "ステップ $step で操作に失敗しました: ${result.detail}"
                        )
                        return@launch
                    }

                    delay(1_150L)
                }

                AccessibilityBridge.hideFloatingHud()
                _operatorState.value = OperatorUiState.Completed(
                    "最大ステップ数（$maxSteps ステップ）に達したため操作を終了しました。"
                )
            } catch (e: TimeoutCancellationException) {
                AccessibilityBridge.hideFloatingHud()
                _operatorState.value = OperatorUiState.Error("操作プランの生成がタイムアウトしました。")
            } catch (e: CancellationException) {
                AccessibilityBridge.hideFloatingHud()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Phone operator failed", e)
                AccessibilityBridge.hideFloatingHud()
                _operatorState.value = OperatorUiState.Error(
                    e.localizedMessage ?: "スマホ操作の実行中にエラーが発生しました。"
                )
            }
        }
    }

    fun approvePendingAction() {
        confirmationDeferred?.complete(true)
    }

    fun cancelPhoneTask(reason: String = "操作を中止しました。") {
        confirmationDeferred?.complete(false)
        confirmationDeferred = null
        operatorJob?.cancel()
        operatorJob = null
        AccessibilityBridge.hideFloatingHud()
        _operatorState.value = OperatorUiState.Error(reason)
    }

    fun clearLiveHistory() {
        viewModelScope.launch { liveMessageDao.clearAll() }
    }

    fun clearOperatorLogs() {
        viewModelScope.launch { operatorLogDao.clearAll() }
    }

    fun setAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoSpeak(enabled) }
    }

    fun setContinuousLoop(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setContinuousVoiceLoop(enabled) }
    }

    fun setUseLiteModel(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setUseLiteModel(enabled) }
    }

    fun setRequireActionConfirm(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setRequireActionConfirm(enabled) }
    }

    fun setLanguageTag(tag: String) {
        viewModelScope.launch {
            settingsStore.setLanguageTag(tag)
            voiceController.setLanguage(tag)
        }
    }

    fun setLiveModel(option: AiModelOption) {
        viewModelScope.launch { settingsStore.setLiveModelId(option.modelId) }
    }

    fun setOperatorModel(option: AiModelOption) {
        viewModelScope.launch { settingsStore.setOperatorModelId(option.modelId) }
    }

    fun setAutoFallback(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoFallbackOnRateLimit(enabled) }
    }

    fun setLocalLlmConfig(endpointUrl: String, modelName: String) {
        viewModelScope.launch { settingsStore.setLocalLlmConfig(endpointUrl, modelName) }
    }

    fun setPcAgentConfig(url: String, token: String) {
        viewModelScope.launch { settingsStore.setPcAgentConfig(url, token) }
    }

    fun updatePcGoal(goal: String) {
        _pcGoalInput.value = goal
    }

    fun testPcConnection(urlOverride: String? = null, tokenOverride: String? = null) {
        val url = (urlOverride ?: preferences.value.pcAgentUrl).trim()
        val token = (tokenOverride ?: preferences.value.pcAgentToken).trim()
        viewModelScope.launch {
            settingsStore.setPcAgentConfig(url, token)
            _pcConnectionStatus.value = "PCエージェントへ接続中..."
            pcAgentClient.checkHealth(url, token).fold(
                onSuccess = { info ->
                    _pcIsConnected.value = true
                    _pcActiveWindow.value = info.activeWindow
                    _pcConnectionStatus.value = "接続成功: ${info.platform} (${info.screenWidth}x${info.screenHeight})"
                },
                onFailure = { err ->
                    _pcIsConnected.value = false
                    _pcConnectionStatus.value = "接続失敗: ${err.localizedMessage ?: "PC側の OmniLivePCAgent を起動してください"}"
                }
            )
        }
    }

    fun capturePcScreenOnly() {
        val url = preferences.value.pcAgentUrl
        val token = preferences.value.pcAgentToken
        viewModelScope.launch {
            _pcOperatorState.value = PcOperatorUiState.CapturingScreen(1, 1)
            pcAgentClient.captureScreen(url, token).fold(
                onSuccess = { snap ->
                    _pcIsConnected.value = true
                    _pcScreenBitmap.value = snap.bitmap
                    _pcActiveWindow.value = snap.activeWindow
                    _pcConnectionStatus.value = "画面取得成功: ${snap.platform} (${snap.screenWidth}x${snap.screenHeight})"
                    _pcOperatorState.value = PcOperatorUiState.Completed(
                        "PC画面 (${snap.screenWidth}x${snap.screenHeight}) を取得しました。アクティブ窓: ${snap.activeWindow.ifBlank { "デスクトップ" }}"
                    )
                },
                onFailure = { err ->
                    _pcIsConnected.value = false
                    _pcOperatorState.value = PcOperatorUiState.Error(
                        "PC画面の取得に失敗しました: ${err.localizedMessage ?: "PC側エージェントが起動しているか確認してください。"}"
                    )
                }
            )
        }
    }

    fun startPcTask(goalRaw: String) {
        val goal = goalRaw.trim()
        if (goal.isBlank()) return
        val url = preferences.value.pcAgentUrl
        val token = preferences.value.pcAgentToken
        val maxSteps = 8

        pcOperatorJob?.cancel()
        pcOperatorJob = viewModelScope.launch {
            val takenSteps = mutableListOf<String>()
            try {
                for (step in 1..maxSteps) {
                    _pcOperatorState.value = PcOperatorUiState.CapturingScreen(step, maxSteps)
                    val snapshot = pcAgentClient.captureScreen(url, token).getOrElse { err ->
                        _pcIsConnected.value = false
                        throw IllegalStateException(
                            "PCエージェントに接続できません ($url): ${err.localizedMessage ?: "PC側で OmniLivePCAgent.exe を起動してください"}"
                        )
                    }
                    _pcIsConnected.value = true
                    _pcScreenBitmap.value = snapshot.bitmap
                    _pcActiveWindow.value = snapshot.activeWindow

                    _pcOperatorState.value = PcOperatorUiState.PlanningStep(step, maxSteps, elapsedSeconds = 0)
                    val tickerJob = launch {
                        var secs = 0
                        while (true) {
                            delay(1_000L)
                            secs += 1
                            val current = _pcOperatorState.value
                            if (current is PcOperatorUiState.PlanningStep && current.step == step) {
                                _pcOperatorState.value = current.copy(elapsedSeconds = secs)
                            } else {
                                break
                            }
                        }
                    }

                    val operatorModel = AiModelOption.fromId(preferences.value.selectedOperatorModelId)
                    val plan = try {
                        aiRepository.planNextPcAction(
                            goal = goal,
                            snapshot = snapshot,
                            previousSteps = takenSteps,
                            stepIndex = step,
                            modelOption = operatorModel,
                            autoFallbackOnRateLimit = preferences.value.autoFallbackOnRateLimit,
                            localEndpointUrl = preferences.value.localEndpointUrl,
                            localModelName = preferences.value.localModelName
                        )
                    } finally {
                        tickerJob.cancel()
                    }

                    if (plan.actionType == PcActionType.DONE || plan.isComplete) {
                        operatorLogDao.insertLog(
                            OperatorLogEntity(
                                goal = "[PC] $goal",
                                stepNumber = step,
                                actionType = plan.actionType.name,
                                targetSummary = "PC完了",
                                reasoning = plan.reasoning,
                                success = true
                            )
                        )
                        _pcOperatorState.value = PcOperatorUiState.Completed(plan.reasoning)
                        if (preferences.value.autoSpeakResponses) {
                            voiceController.speak(plan.speechFeedback)
                        }
                        return@launch
                    }

                    val needsConfirm = preferences.value.requireActionConfirmation || plan.requiresConfirmation
                    if (needsConfirm) {
                        val deferred = CompletableDeferred<Boolean>()
                        pcConfirmationDeferred = deferred
                        _pcOperatorState.value = PcOperatorUiState.AwaitingConfirmation(
                            plan = plan,
                            snapshot = snapshot,
                            step = step,
                            maxSteps = maxSteps
                        )
                        val approved = deferred.await()
                        pcConfirmationDeferred = null
                        if (!approved) {
                            _pcOperatorState.value = PcOperatorUiState.Idle
                            return@launch
                        }
                    }

                    _pcOperatorState.value = PcOperatorUiState.ExecutingAction(plan, step)
                    val execResult = pcAgentClient.executeAction(url, token, plan).getOrElse { err ->
                        throw IllegalStateException("PC操作送信エラー: ${err.localizedMessage}")
                    }

                    val targetDesc = when {
                        plan.inputText.isNotBlank() -> plan.inputText
                        plan.x >= 0 && plan.y >= 0 -> "(${plan.x}, ${plan.y})"
                        else -> plan.actionType.name
                    }

                    operatorLogDao.insertLog(
                        OperatorLogEntity(
                            goal = "[PC] $goal",
                            stepNumber = step,
                            actionType = plan.actionType.name,
                            targetSummary = targetDesc,
                            reasoning = "${plan.reasoning} -> ${execResult.detail}",
                            success = execResult.success
                        )
                    )

                    takenSteps.add("Step $step: ${plan.actionType.name}($targetDesc) => ${execResult.detail}")

                    if (!execResult.success) {
                        _pcOperatorState.value = PcOperatorUiState.Error(
                            "ステップ $step でPC操作に失敗しました: ${execResult.detail}"
                        )
                        return@launch
                    }

                    delay(1_150L)
                }

                _pcOperatorState.value = PcOperatorUiState.Completed(
                    "最大ステップ数（$maxSteps ステップ）に達したためPC操作を終了しました。"
                )
            } catch (e: TimeoutCancellationException) {
                _pcOperatorState.value = PcOperatorUiState.Error("PC操作プランの生成がタイムアウトしました。")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PC operator failed", e)
                _pcOperatorState.value = PcOperatorUiState.Error(
                    e.localizedMessage ?: "PC操作中にエラーが発生しました。"
                )
            }
        }
    }

    fun approvePendingPcAction() {
        pcConfirmationDeferred?.complete(true)
    }

    fun cancelPcTask(reason: String = "PC操作を中止しました。") {
        pcConfirmationDeferred?.complete(false)
        pcConfirmationDeferred = null
        pcOperatorJob?.cancel()
        pcOperatorJob = null
        _pcOperatorState.value = PcOperatorUiState.Error(reason)
    }

    override fun onCleared() {
        voiceController.destroy()
        AccessibilityBridge.hideFloatingHud()
        super.onCleared()
    }
}
