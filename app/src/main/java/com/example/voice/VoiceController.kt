package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

private const val TAG = "VoiceController"

sealed interface SpeechUiState {
    data object Idle : SpeechUiState
    data class Listening(val partialText: String, val rmsDb: Float) : SpeechUiState
    data class Error(val message: String) : SpeechUiState
}

class VoiceController(
    private val context: Context,
    private val onFinalTranscript: (String) -> Unit
) {
    private val _speechState = MutableStateFlow<SpeechUiState>(SpeechUiState.Idle)
    val speechState: StateFlow<SpeechUiState> = _speechState.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsReady = MutableStateFlow(false)
    val ttsReady: StateFlow<Boolean> = _ttsReady.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var currentLanguageTag: String = "ja-JP"
    private var onTtsDoneCallback: (() -> Unit)? = null

    init {
        textToSpeech = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale.forLanguageTag(currentLanguageTag)
                textToSpeech?.language = locale
                textToSpeech?.setSpeechRate(1.05f)
                _ttsReady.value = true
            } else {
                Log.w(TAG, "TextToSpeech init failed with status=$status")
            }
        }.apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    onTtsDoneCallback?.invoke()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                }
            })
        }
    }

    fun setLanguage(languageTag: String) {
        currentLanguageTag = languageTag
        if (_ttsReady.value) {
            textToSpeech?.language = Locale.forLanguageTag(languageTag)
        }
    }

    fun startListening(languageTag: String = currentLanguageTag) {
        currentLanguageTag = languageTag
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _speechState.value = SpeechUiState.Error("この端末では音声認識サービスを利用できません。テキスト入力をご利用ください。")
            return
        }

        stopSpeaking()
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _speechState.value = SpeechUiState.Listening(partialText = "", rmsDb = 0f)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        val current = _speechState.value
                        if (current is SpeechUiState.Listening) {
                            _speechState.value = current.copy(rmsDb = rmsdB.coerceIn(0f, 12f))
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "音声を認識できませんでした。"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "音声入力がタイムアウトしました。"
                            SpeechRecognizer.ERROR_AUDIO -> "マイクの音声取得に失敗しました。"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "マイク権限が必要です。"
                            else -> "音声認識エラー (code=$error)"
                        }
                        _speechState.value = SpeechUiState.Error(msg)
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        _speechState.value = SpeechUiState.Idle
                        if (text.isNotEmpty()) {
                            onFinalTranscript(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim().orEmpty()
                        if (partial.isNotEmpty()) {
                            val current = _speechState.value
                            val rms = (current as? SpeechUiState.Listening)?.rmsDb ?: 2f
                            _speechState.value = SpeechUiState.Listening(partialText = partial, rmsDb = rms)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        _speechState.value = SpeechUiState.Listening(partialText = "", rmsDb = 0f)
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed", e)
            _speechState.value = SpeechUiState.Error("音声認識の開始に失敗しました。")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        _speechState.value = SpeechUiState.Idle
    }

    fun speak(text: String, onCompleted: (() -> Unit)? = null) {
        if (!_ttsReady.value || text.isBlank()) {
            onCompleted?.invoke()
            return
        }
        onTtsDoneCallback = onCompleted
        val cleanText = text
            .replace("**", "")
            .replace("`", "")
            .replace("#", "")
            .take(1200)
        textToSpeech?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        onTtsDoneCallback = null
        textToSpeech?.stop()
        _isSpeaking.value = false
    }

    fun destroy() {
        stopListening()
        stopSpeaking()
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}
