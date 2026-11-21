package com.example.ai

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.BuildConfig

enum class AiProvider(val displayName: String, val shortLabel: String) {
    UNLIMITED("無制限AI (自動マルチエンジン)", "無制限AI"),
    GEMINI("Gemini (Firebase AI Logic)", "Gemini"),
    OPENAI("ChatGPT (OpenAI API)", "ChatGPT"),
    ANTHROPIC("Claude (Anthropic API)", "Claude"),
    LOCAL_LLM("ローカルLLM (Ollama / 自前サーバー)", "ローカル")
}

enum class AiModelOption(
    val modelId: String,
    val displayName: String,
    val provider: AiProvider,
    val description: String,
    val isFastOperatorCapable: Boolean = true
) {
    UNLIMITED_AUTO(
        modelId = "unlimited-auto",
        displayName = "無制限AI (自動フェイルオーバー)",
        provider = AiProvider.UNLIMITED,
        description = "アプリ側制限ゼロ・利用可能エンジンを自動切替して継続応答"
    ),
    GEMINI_FLASH(
        modelId = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash",
        provider = AiProvider.GEMINI,
        description = "標準・高速マルチモーダル（App Check接続）"
    ),
    GEMINI_FLASH_LITE(
        modelId = "gemini-3.5-flash-lite",
        displayName = "Gemini 3.5 Flash-Lite",
        provider = AiProvider.GEMINI,
        description = "超低遅延・スマホ操作に最適"
    ),
    GEMINI_ARGON(
        modelId = "gemini-4-argon",
        displayName = "Gemini 4 Argon",
        provider = AiProvider.GEMINI,
        description = "最高峰の高度推論（Firebase Blaze必要）",
        isFastOperatorCapable = false
    ),
    OPENAI_GPT4O(
        modelId = "gpt-4o",
        displayName = "GPT-4o",
        provider = AiProvider.OPENAI,
        description = "OpenAI フラッグシップ・音声/画像対応"
    ),
    OPENAI_GPT4O_MINI(
        modelId = "gpt-4o-mini",
        displayName = "GPT-4o mini",
        provider = AiProvider.OPENAI,
        description = "OpenAI 高速・低コストモデル"
    ),
    OPENAI_GPT4_1(
        modelId = "gpt-4.1",
        displayName = "GPT-4.1",
        provider = AiProvider.OPENAI,
        description = "OpenAI 最新コーディング・高精度推論"
    ),
    CLAUDE_SONNET_4(
        modelId = "claude-sonnet-4-0",
        displayName = "Claude Sonnet 4",
        provider = AiProvider.ANTHROPIC,
        description = "Anthropic 最新バランス・高精度モデル"
    ),
    CLAUDE_3_7_SONNET(
        modelId = "claude-3-7-sonnet-latest",
        displayName = "Claude 3.7 Sonnet",
        provider = AiProvider.ANTHROPIC,
        description = "Anthropic 深い推論・長文構造化"
    ),
    CLAUDE_3_5_HAIKU(
        modelId = "claude-3-5-haiku-latest",
        displayName = "Claude 3.5 Haiku",
        provider = AiProvider.ANTHROPIC,
        description = "Anthropic 超高速レスポンス"
    ),
    LOCAL_OPENAI_COMPAT(
        modelId = "local-llm",
        displayName = "ローカル / Ollama LLM",
        provider = AiProvider.LOCAL_LLM,
        description = "Ollama・LM Studio等のOpenAI互換ローカルAPI（クォータ無し）"
    );

    companion object {
        fun fromId(id: String): AiModelOption =
            entries.firstOrNull { it.modelId == id } ?: UNLIMITED_AUTO
    }
}

object ProviderKeyStatus {
    fun isProviderConfigured(provider: AiProvider): Boolean {
        return when (provider) {
            AiProvider.UNLIMITED -> true
            AiProvider.GEMINI -> true
            AiProvider.LOCAL_LLM -> true
            AiProvider.OPENAI -> isValidSecret(BuildConfig.OPENAI_API_KEY)
            AiProvider.ANTHROPIC -> isValidSecret(BuildConfig.ANTHROPIC_API_KEY)
        }
    }

    fun getOpenAiKey(): String = BuildConfig.OPENAI_API_KEY.trim()
    fun getAnthropicKey(): String = BuildConfig.ANTHROPIC_API_KEY.trim()

    private fun isValidSecret(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val trimmed = value.trim()
        return trimmed != "MY_OPENAI_API_KEY" &&
            trimmed != "MY_ANTHROPIC_API_KEY" &&
            !trimmed.startsWith("MY_")
    }
}

enum class LiveProMode(
    val displayName: String,
    val subtitle: String,
    val systemPrompt: String
) {
    LIVE_CONVERSATION(
        displayName = "ライブ会話",
        subtitle = "音声・カメラと連動するリアルタイム対話",
        systemPrompt = """
            あなたは「Live Pro」として動作する高度なリアルタイムAIアシスタントです。
            ユーザーの音声やカメラ映像・画像に対して、簡潔で自然かつ親しみやすい日本語（またはユーザーの言語）で応答してください。
            音声読み上げ（TTS）にも適した、聞き取りやすくテンポの良い表現を心がけ、必要に応じて要点や次のアクションを提案してください。
        """.trimIndent()
    ),
    INTERPRETER(
        displayName = "同時通訳",
        subtitle = "日本語 ⇄ 英語・多言語リアルタイム翻訳",
        systemPrompt = """
            あなたはプロフェッショナルなリアルタイム同時通訳AIです。
            入力が日本語なら自然な英語へ翻訳し、英語やその他の言語なら自然な日本語へ翻訳して返答してください。
            形式:
            【翻訳】翻訳文
            （必要なら一言だけニュアンス補足）
            前置きは省き、即座に使える翻訳結果を返してください。
        """.trimIndent()
    ),
    DEEP_PRO(
        displayName = "ディープ分析",
        subtitle = "設計・コード・数学・戦略の高度推論",
        systemPrompt = """
            あなたは「Live Pro（エキスパート分析モード）」です。
            プログラミング、数理、ビジネス戦略、複雑な疑問に対して、構造化された明確な解説と具体的な解決策を提示してください。
            見出しや箇条書きを活用し、実践的な手順まで落とし込んで回答してください。
        """.trimIndent()
    ),
    VISION_COACH(
        displayName = "ビジョン解説",
        subtitle = "カメラや写真を見ながらステップ案内",
        systemPrompt = """
            あなたはライブカメラ映像や画像を解析してガイドする「ビジョンコーチAI」です。
            画像に写っている対象物、文字、UI、機器の状態を正確に読み取り、ユーザーがいま何をすべきか具体的なステップで案内してください。
            画像が添付されていない場合は、カメラや画像選択を使うよう優しく案内してください。
        """.trimIndent()
    ),
    CODE_ARCHITECT(
        displayName = "コード生成",
        subtitle = "実用アプリ・HTMLゲーム・Pythonツールを即生成＆PC送信",
        systemPrompt = """
            あなたは世界トップクラスのソフトウェアアーキテクト兼フルスタックエンジニアです。
            ユーザーの要望に対し、省略やプレースホルダー一切なしの「そのまま動く高品質な完全コード」を生成してください。
            出力ルール:
            1. 最初に1行だけ推奨ファイル名（例: FILENAME: app.html または FILENAME: script.py）を記載してください。
            2. コードは必ず言語タグ付きのMarkdownコードブロック（例: ```html ... ``` や ```python ... ```）で囲んでください。
            3. HTML/Webアプリの要望なら、単一ファイル（HTML+CSS+JS完結）で即ブラウザ動作する美しいUIにしてください。
            4. 解説は簡潔にし、実行方法も一言添えてください。
        """.trimIndent()
    )
}

enum class OperatorActionType {
    TAP_NODE,
    TAP_COORDS,
    INPUT_TEXT,
    SCROLL_DOWN,
    SCROLL_UP,
    GLOBAL_BACK,
    GLOBAL_HOME,
    GLOBAL_RECENTS,
    OPEN_NOTIFICATIONS,
    OPEN_QUICK_SETTINGS,
    LAUNCH_APP,
    WAIT,
    DONE
}

data class OperatorStepPlan(
    val actionType: OperatorActionType,
    val targetNodeId: Int,
    val x: Int,
    val y: Int,
    val inputText: String,
    val reasoning: String,
    val speechFeedback: String,
    val requiresConfirmation: Boolean,
    val isComplete: Boolean
)

data class ScreenNodeInfo(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val viewId: String,
    val className: String,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isChecked: Boolean?
) {
    fun toPromptLine(): String {
        val flags = buildList {
            if (isClickable) add("clickable")
            if (isEditable) add("editable")
            if (isScrollable) add("scrollable")
            if (isChecked != null) add("checked=$isChecked")
        }.joinToString(",")
        val label = buildString {
            if (text.isNotBlank()) append("text=\"${text.take(60)}\" ")
            if (contentDescription.isNotBlank()) append("desc=\"${contentDescription.take(60)}\" ")
            if (viewId.isNotBlank()) append("id=\"$viewId\" ")
        }.trim()
        return "[$index] $className | $label | bounds=(${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}) | [$flags]"
    }
}

data class ScreenSnapshot(
    val packageName: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val nodes: List<ScreenNodeInfo>,
    val capturedAt: Long = System.currentTimeMillis()
) {
    fun toPromptSummary(): String {
        if (nodes.isEmpty()) {
            return "Foreground package: $packageName (${screenWidth}x${screenHeight})\nVisible nodes: (none captured)"
        }
        return buildString {
            appendLine("Foreground package: $packageName (${screenWidth}x${screenHeight})")
            appendLine("Visible UI elements (${nodes.size}):")
            nodes.forEach { appendLine(it.toPromptLine()) }
        }
    }
}

data class InstalledAppSummary(
    val label: String,
    val packageName: String
)

data class ActionExecutionResult(
    val success: Boolean,
    val detail: String
)

enum class PcActionType {
    CLICK,
    DOUBLE_CLICK,
    RIGHT_CLICK,
    TYPE_TEXT,
    HOTKEY,
    PRESS_KEY,
    SCROLL_DOWN,
    SCROLL_UP,
    OPEN_APP,
    WAIT,
    DONE
}

data class PcStepPlan(
    val actionType: PcActionType,
    val x: Int,
    val y: Int,
    val normX: Int,
    val normY: Int,
    val inputText: String,
    val reasoning: String,
    val speechFeedback: String,
    val requiresConfirmation: Boolean,
    val isComplete: Boolean
)

data class PcScreenSnapshot(
    val screenWidth: Int,
    val screenHeight: Int,
    val capturedWidth: Int,
    val capturedHeight: Int,
    val activeWindow: String,
    val platform: String,
    val bitmap: Bitmap,
    val capturedAt: Long = System.currentTimeMillis()
)

