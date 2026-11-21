import Foundation

struct IosPcHealth: Decodable {
    let platform: String
    let screenWidth: Int
    let screenHeight: Int
    let activeWindow: String?
}

struct IosPcScreen: Decodable {
    let screenWidth: Int
    let screenHeight: Int
    let activeWindow: String?
    let platform: String?
    let imageBase64: String
}

struct IosPcResult: Decodable {
    let success: Bool
    let detail: String?
}

final class IosPcAgentClient {
    private let session: URLSession = {
        let cfg = URLSessionConfiguration.default
        cfg.timeoutIntervalForRequest = 12
        return URLSession(configuration: cfg)
    }()

    private func normalizedUrl(_ raw: String) -> String {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if trimmed.isEmpty { return "http://192.168.1.10:8765" }
        if trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://") {
            return trimmed
        }
        return "http://\(trimmed)"
    }

    private func makeRequest(url: URL, method: String, token: String, body: [String: Any]? = nil) throws -> URLRequest {
        var req = URLRequest(url: url)
        req.httpMethod = method
        let cleanToken = token.trimmingCharacters(in: .whitespacesAndNewlines)
        if !cleanToken.isEmpty {
            req.setValue(cleanToken, forHTTPHeaderField: "X-OmniLive-Token")
        }
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        return req
    }

    func checkHealth(baseUrl: String, token: String) async throws -> IosPcHealth {
        guard let url = URL(string: normalizedUrl(baseUrl) + "/health") else { throw URLError(.badURL) }
        let req = try makeRequest(url: url, method: "GET", token: token)
        let (data, _) = try await session.data(for: req)
        return try JSONDecoder().decode(IosPcHealth.self, from: data)
    }

    func captureScreen(baseUrl: String, token: String) async throws -> IosPcScreen {
        guard let url = URL(string: normalizedUrl(baseUrl) + "/screen") else { throw URLError(.badURL) }
        let req = try makeRequest(url: url, method: "GET", token: token)
        let (data, _) = try await session.data(for: req)
        return try JSONDecoder().decode(IosPcScreen.self, from: data)
    }

    func executeAction(baseUrl: String, token: String, actionType: String, x: Int, y: Int, inputText: String) async throws -> IosPcResult {
        guard let url = URL(string: normalizedUrl(baseUrl) + "/execute") else { throw URLError(.badURL) }
        let req = try makeRequest(
            url: url,
            method: "POST",
            token: token,
            body: ["actionType": actionType, "x": x, "y": y, "inputText": inputText]
        )
        let (data, _) = try await session.data(for: req)
        return try JSONDecoder().decode(IosPcResult.self, from: data)
    }

    func saveGeneratedFile(baseUrl: String, token: String, filename: String, content: String, openInBrowser: Bool) async throws -> IosPcResult {
        guard let url = URL(string: normalizedUrl(baseUrl) + "/save-file") else { throw URLError(.badURL) }
        let req = try makeRequest(
            url: url,
            method: "POST",
            token: token,
            body: ["filename": filename, "content": content, "openInBrowser": openInBrowser]
        )
        let (data, _) = try await session.data(for: req)
        return try JSONDecoder().decode(IosPcResult.self, from: data)
    }
}
