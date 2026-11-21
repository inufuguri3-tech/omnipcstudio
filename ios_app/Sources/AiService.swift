import Foundation

enum IosAiProvider: String, CaseIterable, Identifiable {
    case gemini = "Gemini"
    case openai = "ChatGPT (OpenAI)"
    case anthropic = "Claude (Anthropic)"

    var id: String { rawValue }
    var defaultModel: String {
        switch self {
        case .gemini: return "gemini-3.8-flash"
        case .openai: return "gpt-4o-mini"
        case .anthropic: return "claude-sonnet-4-0"
        }
    }
}

enum IosLiveMode: String, CaseIterable, Identifiable {
    case liveChat = "ライブ会話"
    case codeArchitect = "コード生成"
    case deepAnalysis = "ディープ分析"
    case interpreter = "同時通訳"

    var id: String { rawValue }

    var systemPrompt: String {
        switch self {
        case .liveChat:
            return "あなたは OmniLive AI アシスタントです。自然で簡潔な日本語で回答してください。"
        case .codeArchitect:
            return """
            あなたは熟練のソフトウェアエンジニアです。省略やプレースホルダーのない完全に動くコードを生成してください。
            最初に1行 `FILENAME: app.html` のように推奨ファイル名を記載し、コードは必ず ```html ... ``` のように言語タグ付きMarkdownコードブロックで囲んでください。
            """
        case .deepAnalysis:
            return "あなたは高度な技術・戦略アナリストです。見出しと手順を使って分かりやすく解説してください。"
        case .interpreter:
            return "あなたはプロフェッショナルな同時通訳です。日本語なら自然な英語へ、英語なら自然な日本語へ翻訳してください。"
        }
    }
}

struct IosPcPlan: Decodable {
    let actionType: String
    let x: Int?
    let y: Int?
    let inputText: String?
    let reasoning: String?
    let isComplete: Bool?
}

final class IosAiService {
    private let session: URLSession

    init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 45
        self.session = URLSession(configuration: config)
    }

    func generateText(
        provider: IosAiProvider,
        model: String,
        apiKey: String,
        mode: IosLiveMode,
        prompt: String
    ) async throws -> String {
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else {
            throw NSError(domain: "OmniLive", code: 400, userInfo: [
                NSLocalizedDescriptionKey: "設定タブで \(provider.rawValue) のAPIキーを入力してください。"
            ])
        }

        switch provider {
        case .gemini:
            return try await callGeminiTextWithFailover(
                model: model,
                apiKey: key,
                systemPrompt: mode.systemPrompt,
                userPrompt: prompt
            )
        case .openai:
            return try await callOpenAiText(model: model, apiKey: key, systemPrompt: mode.systemPrompt, userPrompt: prompt)
        case .anthropic:
            return try await callAnthropicText(model: model, apiKey: key, systemPrompt: mode.systemPrompt, userPrompt: prompt)
        }
    }

    func planPcAction(
        provider: IosAiProvider,
        model: String,
        apiKey: String,
        goal: String,
        screenshotBase64: String,
        history: [String],
        step: Int
    ) async throws -> IosPcPlan {
        let key = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else {
            throw NSError(domain: "OmniLive", code: 400, userInfo: [
                NSLocalizedDescriptionKey: "設定タブで \(provider.rawValue) のAPIキーを入力してください。"
            ])
        }

        let instruction = """
        あなたはPC自律操作AIです。デスクトップ画面のスクリーンショットを確認し、次の1ステップをJSONのみで返してください。
        座標 x, y は 0..1000 の正規化座標です。
        JSONスキーマ:
        {"actionType":"CLICK|DOUBLE_CLICK|RIGHT_CLICK|TYPE_TEXT|HOTKEY|PRESS_KEY|SCROLL_DOWN|SCROLL_UP|OPEN_APP|WAIT|DONE","x":500,"y":500,"inputText":"","reasoning":"理由","isComplete":false}
        目標: \(goal)
        ステップ: \(step)
        これまでの履歴: \(history.joined(separator: " | "))
        """

        let raw: String
        switch provider {
        case .gemini:
            raw = try await callGeminiVisionWithFailover(model: model, apiKey: key, prompt: instruction, jpegBase64: screenshotBase64)
        case .openai:
            raw = try await callOpenAiVision(model: model, apiKey: key, prompt: instruction, jpegBase64: screenshotBase64)
        case .anthropic:
            raw = try await callAnthropicVision(model: model, apiKey: key, prompt: instruction, jpegBase64: screenshotBase64)
        }

        guard let start = raw.firstIndex(of: "{"),
              let end = raw.lastIndex(of: "}") else {
            throw NSError(domain: "OmniLive", code: 500, userInfo: [
                NSLocalizedDescriptionKey: "AIから有効なJSONが返りませんでした: \(raw.prefix(120))"
            ])
        }
        let jsonString = String(raw[start...end])
        let data = Data(jsonString.utf8)
        return try JSONDecoder().decode(IosPcPlan.self, from: data)
    }

    private func normalizedGeminiModel(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty || trimmed.hasPrefix("gemini-1.") || trimmed.hasPrefix("gemini-2.") {
            return "gemini-3.8-flash"
        }
        return trimmed
    }

    private func callGeminiTextWithFailover(
        model: String,
        apiKey: String,
        systemPrompt: String,
        userPrompt: String
    ) async throws -> String {
        let primary = normalizedGeminiModel(model)
        let candidates = Array(NSOrderedSet(array: [primary, "gemini-3.5-flash-lite", "gemini-3.8-flash"])).compactMap { $0 as? String }
        var lastError: Error?
        for candidate in candidates {
            do {
                return try await callGeminiOnce(model: candidate, apiKey: apiKey, systemPrompt: systemPrompt, userPrompt: userPrompt, jpegBase64: nil)
            } catch {
                lastError = error
            }
        }
        throw lastError ?? URLError(.cannotLoadFromNetwork)
    }

    private func callGeminiVisionWithFailover(
        model: String,
        apiKey: String,
        prompt: String,
        jpegBase64: String
    ) async throws -> String {
        let primary = normalizedGeminiModel(model)
        let candidates = Array(NSOrderedSet(array: [primary, "gemini-3.5-flash-lite", "gemini-3.8-flash"])).compactMap { $0 as? String }
        var lastError: Error?
        for candidate in candidates {
            do {
                return try await callGeminiOnce(model: candidate, apiKey: apiKey, systemPrompt: nil, userPrompt: prompt, jpegBase64: jpegBase64)
            } catch {
                lastError = error
            }
        }
        throw lastError ?? URLError(.cannotLoadFromNetwork)
    }

    private func callGeminiOnce(
        model: String,
        apiKey: String,
        systemPrompt: String?,
        userPrompt: String,
        jpegBase64: String?
    ) async throws -> String {
        guard let url = URL(string: "https://generativelanguage.googleapis.com/v1beta/models/\(model):generateContent?key=\(apiKey)") else {
            throw URLError(.badURL)
        }
        var parts: [[String: Any]] = [["text": userPrompt]]
        if let img = jpegBase64 {
            parts.append(["inlineData": ["mimeType": "image/jpeg", "data": img]])
        }
        var payload: [String: Any] = [
            "contents": [["role": "user", "parts": parts]]
        ]
        if let systemPrompt {
            payload["systemInstruction"] = ["parts": [["text": systemPrompt]]]
        }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)

        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let errText = String(data: data, encoding: .utf8) ?? "HTTP Error"
            throw NSError(domain: "OmniLive", code: (response as? HTTPURLResponse)?.statusCode ?? 500, userInfo: [
                NSLocalizedDescriptionKey: errText
            ])
        }
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let candidates = json?["candidates"] as? [[String: Any]]
        let content = candidates?.first?["content"] as? [String: Any]
        let outParts = content?["parts"] as? [[String: Any]]
        return outParts?.compactMap { $0["text"] as? String }.joined() ?? ""
    }

    private func callOpenAiText(model: String, apiKey: String, systemPrompt: String, userPrompt: String) async throws -> String {
        let url = URL(string: "https://api.openai.com/v1/chat/completions")!
        let payload: [String: Any] = [
            "model": model.isEmpty ? "gpt-4o-mini" : model,
            "messages": [
                ["role": "system", "content": systemPrompt],
                ["role": "user", "content": userPrompt]
            ]
        ]
        return try await executeOpenAiRequest(url: url, apiKey: apiKey, payload: payload)
    }

    private func callOpenAiVision(model: String, apiKey: String, prompt: String, jpegBase64: String) async throws -> String {
        let url = URL(string: "https://api.openai.com/v1/chat/completions")!
        let payload: [String: Any] = [
            "model": model.isEmpty ? "gpt-4o-mini" : model,
            "messages": [
                [
                    "role": "user",
                    "content": [
                        ["type": "text", "text": prompt],
                        ["type": "image_url", "image_url": ["url": "data:image/jpeg;base64,\(jpegBase64)"]]
                    ]
                ]
            ]
        ]
        return try await executeOpenAiRequest(url: url, apiKey: apiKey, payload: payload)
    }

    private func executeOpenAiRequest(url: URL, apiKey: String, payload: [String: Any]) async throws -> String {
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let errText = String(data: data, encoding: .utf8) ?? "HTTP Error"
            throw NSError(domain: "OmniLive", code: (response as? HTTPURLResponse)?.statusCode ?? 500, userInfo: [
                NSLocalizedDescriptionKey: errText
            ])
        }
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let choices = json?["choices"] as? [[String: Any]]
        let message = choices?.first?["message"] as? [String: Any]
        return message?["content"] as? String ?? ""
    }

    private func callAnthropicText(model: String, apiKey: String, systemPrompt: String, userPrompt: String) async throws -> String {
        let url = URL(string: "https://api.anthropic.com/v1/messages")!
        let payload: [String: Any] = [
            "model": model.isEmpty ? "claude-sonnet-4-0" : model,
            "max_tokens": 4096,
            "system": systemPrompt,
            "messages": [["role": "user", "content": userPrompt]]
        ]
        return try await executeAnthropicRequest(url: url, apiKey: apiKey, payload: payload)
    }

    private func callAnthropicVision(model: String, apiKey: String, prompt: String, jpegBase64: String) async throws -> String {
        let url = URL(string: "https://api.anthropic.com/v1/messages")!
        let payload: [String: Any] = [
            "model": model.isEmpty ? "claude-sonnet-4-0" : model,
            "max_tokens": 1024,
            "messages": [
                [
                    "role": "user",
                    "content": [
                        ["type": "image", "source": ["type": "base64", "media_type": "image/jpeg", "data": jpegBase64]],
                        ["type": "text", "text": prompt]
                    ]
                ]
            ]
        ]
        return try await executeAnthropicRequest(url: url, apiKey: apiKey, payload: payload)
    }

    private func executeAnthropicRequest(url: URL, apiKey: String, payload: [String: Any]) async throws -> String {
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        req.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        req.httpBody = try JSONSerialization.data(withJSONObject: payload)
        let (data, response) = try await session.data(for: req)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let errText = String(data: data, encoding: .utf8) ?? "HTTP Error"
            throw NSError(domain: "OmniLive", code: (response as? HTTPURLResponse)?.statusCode ?? 500, userInfo: [
                NSLocalizedDescriptionKey: errText
            ])
        }
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let content = json?["content"] as? [[String: Any]]
        return content?.compactMap { $0["text"] as? String }.joined() ?? ""
    }
}
