import SwiftUI
import UIKit

struct ChatItem: Identifiable {
    let id = UUID()
    let isUser: Bool
    let text: String
    let codeBlocks: [ParsedCodeBlock]
}

struct ParsedCodeBlock: Identifiable {
    let id = UUID()
    let language: String
    let filename: String
    let code: String

    var isHtml: Bool {
        let lower = filename.lowercased()
        return lower.hasSuffix(".html") || lower.hasSuffix(".htm") || language.lowercased() == "html"
    }
}

private func extractCodeBlocks(from text: String) -> [ParsedCodeBlock] {
    var hintedFilename: String?
    if let fnRegex = try? NSRegularExpression(pattern: "(?im)^\\s*(?:FILENAME|FILE)\\s*:\\s*([A-Za-z0-9._-]+)") {
        let range = NSRange(text.startIndex..., in: text)
        if let match = fnRegex.firstMatch(in: text, range: range),
           let fnRange = Range(match.range(at: 1), in: text) {
            hintedFilename = String(text[fnRange])
        }
    }

    guard let regex = try? NSRegularExpression(pattern: "```([A-Za-z0-9_+.-]*)\\s*\\n([\\s\\S]*?)```") else {
        return []
    }
    let nsRange = NSRange(text.startIndex..., in: text)
    let matches = regex.matches(in: text, range: nsRange)
    return matches.enumerated().compactMap { idx, match in
        guard let codeRange = Range(match.range(at: 2), in: text) else { return nil }
        let lang = Range(match.range(at: 1), in: text).map { String(text[$0]) }.flatMap { $0.isEmpty ? nil : $0 } ?? "txt"
        let ext: String
        switch lang.lowercased() {
        case "html", "htm": ext = "html"
        case "python", "py": ext = "py"
        case "swift": ext = "swift"
        case "javascript", "js": ext = "js"
        default: ext = "txt"
        }
        let filename = (idx == 0 ? hintedFilename : nil) ?? "omnilive_code_\(idx + 1).\(ext)"
        return ParsedCodeBlock(language: lang, filename: filename, code: String(text[codeRange]))
    }
}

struct ContentView: View {
    @AppStorage("omnilive_provider") private var providerRaw = IosAiProvider.gemini.rawValue
    @AppStorage("omnilive_model") private var modelName = "gemini-3.8-flash"
    @AppStorage("omnilive_api_key") private var apiKey = ""
    @AppStorage("omnilive_pc_url") private var pcUrl = "http://192.168.1.10:8765"
    @AppStorage("omnilive_pc_token") private var pcToken = ""

    private let aiService = IosAiService()
    private let pcClient = IosPcAgentClient()

    @State private var selectedMode: IosLiveMode = .codeArchitect
    @State private var promptInput = ""
    @State private var messages: [ChatItem] = []
    @State private var isGenerating = false
    @State private var liveAlertMessage: String?

    @State private var pcGoal = "メモ帳を開く"
    @State private var pcStatus = "未接続（同一Wi-FiのPCアドレスを入力）"
    @State private var pcImage: UIImage?
    @State private var isRunningPc = false

    private var currentProvider: IosAiProvider {
        IosAiProvider(rawValue: providerRaw) ?? .gemini
    }

    var body: some View {
        TabView {
            liveProTab
                .tabItem {
                    Label("Live / コード", systemImage: "sparkles")
                }

            pcOperatorTab
                .tabItem {
                    Label("PC操作", systemImage: "desktopcomputer")
                }

            settingsTab
                .tabItem {
                    Label("設定・App Store", systemImage: "gearshape.fill")
                }
        }
        .tint(Color(red: 0.13, green: 0.83, blue: 0.93))
        .alert("OmniLive", isPresented: Binding(
            get: { liveAlertMessage != nil },
            set: { if !$0 { liveAlertMessage = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(liveAlertMessage ?? "")
        }
    }

    private var liveProTab: some View {
        NavigationStack {
            VStack(spacing: 10) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(IosLiveMode.allCases) { mode in
                            Button {
                                selectedMode = mode
                            } label: {
                                Text(mode.rawValue)
                                    .font(.caption.bold())
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(selectedMode == mode ? Color.cyan.opacity(0.25) : Color(.secondarySystemBackground))
                                    .foregroundStyle(selectedMode == mode ? Color.cyan : Color.primary)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                    .padding(.horizontal)
                }

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        if messages.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("\(selectedMode.rawValue) モード")
                                    .font(.headline)
                                Text("AIが完全なHTMLゲームやPythonスクリプトを生成し、1タップでPCに保存・ブラウザ起動できます。")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                Button("サンプル: ネオン色のHTMLシューティングゲームを作って") {
                                    sendLivePrompt("ブラウザでそのまま遊べるネオン色のシューティングHTMLゲームを1ファイルで作って")
                                }
                                .buttonStyle(.bordered)
                            }
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                        }

                        ForEach(messages) { item in
                            VStack(alignment: .leading, spacing: 8) {
                                Text(item.isUser ? "あなた" : "OmniLive AI")
                                    .font(.caption.bold())
                                    .foregroundStyle(item.isUser ? .cyan : .indigo)
                                Text(item.text)
                                    .font(.body)
                                    .textSelection(.enabled)

                                ForEach(item.codeBlocks) { block in
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text("\(block.filename) (\(block.language.uppercased()))")
                                            .font(.caption.monospaced())
                                            .foregroundStyle(.cyan)
                                        ScrollView(.horizontal) {
                                            Text(block.code)
                                                .font(.system(.caption, design: .monospaced))
                                                .padding(8)
                                        }
                                        .background(Color.black.opacity(0.6))
                                        .clipShape(RoundedRectangle(cornerRadius: 8))

                                        HStack {
                                            Button("コピー") {
                                                UIPasteboard.general.string = block.code
                                                liveAlertMessage = "コードをコピーしました"
                                            }
                                            .buttonStyle(.bordered)

                                            Button("PCへ保存") {
                                                saveCodeToPc(block: block, openInBrowser: false)
                                            }
                                            .buttonStyle(.bordered)

                                            if block.isHtml {
                                                Button("PCで開く") {
                                                    saveCodeToPc(block: block, openInBrowser: true)
                                                }
                                                .buttonStyle(.borderedProminent)
                                            }
                                        }
                                        .font(.caption)
                                    }
                                    .padding(10)
                                    .background(Color(.tertiarySystemBackground))
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                }
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(item.isUser ? Color.cyan.opacity(0.15) : Color(.secondarySystemBackground))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                    }
                    .padding(.horizontal)
                }

                HStack {
                    TextField("作りたいアプリや質問を入力...", text: $promptInput)
                        .textFieldStyle(.roundedBorder)
                    Button {
                        let txt = promptInput
                        promptInput = ""
                        sendLivePrompt(txt)
                    } label: {
                        if isGenerating {
                            ProgressView()
                        } else {
                            Image(systemName: "paperplane.fill")
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(promptInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isGenerating)
                }
                .padding()
            }
            .navigationTitle("OmniLive iOS")
        }
    }

    private var pcOperatorTab: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text(pcStatus)
                        .font(.subheadline)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 12))

                    HStack {
                        Button("PC接続テスト") { testPcConnection() }
                            .buttonStyle(.bordered)
                        Button("画面を取得") { capturePcScreen() }
                            .buttonStyle(.bordered)
                    }

                    if let pcImage {
                        Image(uiImage: pcImage)
                            .resizable()
                            .scaledToFit()
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    TextField("PCにさせたい操作 (例: メモ帳を開く)", text: $pcGoal)
                        .textFieldStyle(.roundedBorder)

                    Button(isRunningPc ? "実行中..." : "AIでPCを自動操作") {
                        runPcAutomation()
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(isRunningPc)
                }
                .padding()
            }
            .navigationTitle("PC操作エージェント")
        }
    }

    private var settingsTab: some View {
        NavigationStack {
            Form {
                Section("AI プロバイダー設定") {
                    Picker("AIエンジン", selection: $providerRaw) {
                        ForEach(IosAiProvider.allCases) { p in
                            Text(p.rawValue).tag(p.rawValue)
                        }
                    }
                    .onChange(of: providerRaw) { _, newValue in
                        if let p = IosAiProvider(rawValue: newValue) {
                            modelName = p.defaultModel
                        }
                    }
                    TextField("モデルID", text: $modelName)
                        .autocorrectionDisabled()
                    SecureField("APIキー", text: $apiKey)
                        .autocorrectionDisabled()
                }

                Section("PC LAN 接続") {
                    TextField("PC URL (例: http://192.168.1.10:8765)", text: $pcUrl)
                        .keyboardType(.URL)
                        .autocorrectionDisabled()
                    SecureField("ペアリングトークン (任意)", text: $pcToken)
                }

                Section("App Store 公開 (.ipa) について") {
                    Text("iOSではAppleの仕様上、他アプリの画面を自動操作する機能は制限されています。そのためiOS版ではAIチャット・コード生成・PC遠隔操作を提供します。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    Text("App Store公開には、GitHub Actions (macOS) または Xcode で署名のうえ Apple Developer Program (年間$99) からアップロードする必要があります。")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("設定")
        }
    }

    private func sendLivePrompt(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        messages.append(ChatItem(isUser: true, text: trimmed, codeBlocks: []))
        isGenerating = true
        Task {
            do {
                let answer = try await aiService.generateText(
                    provider: currentProvider,
                    model: modelName,
                    apiKey: apiKey,
                    mode: selectedMode,
                    prompt: trimmed
                )
                let blocks = extractCodeBlocks(from: answer)
                messages.append(ChatItem(isUser: false, text: answer, codeBlocks: blocks))
            } catch {
                liveAlertMessage = error.localizedDescription
            }
            isGenerating = false
        }
    }

    private func saveCodeToPc(block: ParsedCodeBlock, openInBrowser: Bool) {
        Task {
            do {
                let res = try await pcClient.saveGeneratedFile(
                    baseUrl: pcUrl,
                    token: pcToken,
                    filename: block.filename,
                    content: block.code,
                    openInBrowser: openInBrowser
                )
                liveAlertMessage = res.detail ?? "PCへ保存しました"
            } catch {
                liveAlertMessage = "PC接続エラー: \(error.localizedDescription)"
            }
        }
    }

    private func testPcConnection() {
        Task {
            do {
                let health = try await pcClient.checkHealth(baseUrl: pcUrl, token: pcToken)
                pcStatus = "接続成功: \(health.platform) (\(health.screenWidth)x\(health.screenHeight))"
            } catch {
                pcStatus = "接続失敗: \(error.localizedDescription)"
            }
        }
    }

    private func capturePcScreen() {
        Task {
            do {
                let screen = try await pcClient.captureScreen(baseUrl: pcUrl, token: pcToken)
                if let data = Data(base64Encoded: screen.imageBase64), let img = UIImage(data: data) {
                    pcImage = img
                }
                pcStatus = "画面取得成功: \(screen.screenWidth)x\(screen.screenHeight)"
            } catch {
                pcStatus = "画面取得失敗: \(error.localizedDescription)"
            }
        }
    }

    private func runPcAutomation() {
        let goal = pcGoal.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !goal.isEmpty else { return }
        isRunningPc = true
        Task {
            var history: [String] = []
            do {
                for step in 1...6 {
                    pcStatus = "ステップ \(step)/6: PC画面を解析中..."
                    let snap = try await pcClient.captureScreen(baseUrl: pcUrl, token: pcToken)
                    if let data = Data(base64Encoded: snap.imageBase64), let img = UIImage(data: data) {
                        pcImage = img
                    }
                    let plan = try await aiService.planPcAction(
                        provider: currentProvider,
                        model: modelName,
                        apiKey: apiKey,
                        goal: goal,
                        screenshotBase64: snap.imageBase64,
                        history: history,
                        step: step
                    )
                    if plan.actionType.uppercased() == "DONE" || (plan.isComplete ?? false) {
                        pcStatus = "完了: \(plan.reasoning ?? "終了しました")"
                        break
                    }
                    let px = Int((Double(plan.x ?? -1) / 1000.0) * Double(snap.screenWidth))
                    let py = Int((Double(plan.y ?? -1) / 1000.0) * Double(snap.screenHeight))
                    let res = try await pcClient.executeAction(
                        baseUrl: pcUrl,
                        token: pcToken,
                        actionType: plan.actionType,
                        x: px,
                        y: py,
                        inputText: plan.inputText ?? ""
                    )
                    history.append("Step \(step): \(plan.actionType) => \(res.detail ?? "")")
                    pcStatus = "ステップ \(step): \(res.detail ?? plan.actionType)"
                    try await Task.sleep(nanoseconds: 1_000_000_000)
                }
            } catch {
                pcStatus = "エラー: \(error.localizedDescription)"
            }
            isRunningPc = false
        }
    }
}
