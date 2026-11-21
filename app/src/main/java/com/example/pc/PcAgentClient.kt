package com.example.pc

import android.graphics.BitmapFactory
import com.example.ai.ActionExecutionResult
import com.example.ai.PcScreenSnapshot
import com.example.ai.PcStepPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.util.Base64
import java.util.concurrent.TimeUnit

data class PcHealthInfo(
    val platform: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val activeWindow: String
)

class PcAgentClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun checkHealth(baseUrl: String, token: String): Result<PcHealthInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = normalizeBaseUrl(baseUrl) + "/health"
                val reqBuilder = Request.Builder().url(url).get()
                if (token.isNotBlank()) {
                    reqBuilder.header("X-OmniLive-Token", token.trim())
                }
                client.newCall(reqBuilder.build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}: ${body.ifBlank { "接続拒否" }}")
                    }
                    val json = JSONObject(body)
                    PcHealthInfo(
                        platform = json.optString("platform", "PC"),
                        screenWidth = json.optInt("screenWidth", 1920),
                        screenHeight = json.optInt("screenHeight", 1080),
                        activeWindow = json.optString("activeWindow", "")
                    )
                }
            }
        }

    suspend fun captureScreen(baseUrl: String, token: String): Result<PcScreenSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = normalizeBaseUrl(baseUrl) + "/screen"
                val reqBuilder = Request.Builder().url(url).get()
                if (token.isNotBlank()) {
                    reqBuilder.header("X-OmniLive-Token", token.trim())
                }
                client.newCall(reqBuilder.build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}: ${body.ifBlank { "画面取得に失敗" }}")
                    }
                    val json = JSONObject(body)
                    val b64 = json.getString("imageBase64")
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: error("スクリーンショット画像のデコードに失敗しました。")
                    PcScreenSnapshot(
                        screenWidth = json.optInt("screenWidth", bitmap.width),
                        screenHeight = json.optInt("screenHeight", bitmap.height),
                        capturedWidth = json.optInt("capturedWidth", bitmap.width),
                        capturedHeight = json.optInt("capturedHeight", bitmap.height),
                        activeWindow = json.optString("activeWindow", ""),
                        platform = json.optString("platform", "PC"),
                        bitmap = bitmap
                    )
                }
            }
        }

    suspend fun executeAction(
        baseUrl: String,
        token: String,
        plan: PcStepPlan
    ): Result<ActionExecutionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = normalizeBaseUrl(baseUrl) + "/execute"
            val payload = JSONObject().apply {
                put("actionType", plan.actionType.name)
                put("x", plan.x)
                put("y", plan.y)
                put("inputText", plan.inputText)
            }
            val reqBuilder = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody(jsonMediaType))
            if (token.isNotBlank()) {
                reqBuilder.header("X-OmniLive-Token", token.trim())
            }
            client.newCall(reqBuilder.build()).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = runCatching { JSONObject(body) }.getOrNull()
                val ok = response.isSuccessful && (json?.optBoolean("success", true) ?: true)
                val detail = json?.optString("detail").orEmpty().ifBlank {
                    body.ifBlank { "HTTP ${response.code}" }
                }
                ActionExecutionResult(success = ok, detail = detail)
            }
        }
    }

    private fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return "http://192.168.1.10:8765"
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }
}

object PcAgentCompanionAssets {
    val OMNILIVE_PC_AGENT_PY: String = """
import base64
import ctypes
import io
import json
import os
import platform
import subprocess
import sys
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    import pyautogui
    from PIL import ImageGrab
except ImportError:
    print("Missing dependencies. Run: pip install pyautogui pillow")
    sys.exit(1)

pyautogui.FAILSAFE = True
pyautogui.PAUSE = 0.15

PORT = int(os.environ.get("OMNILIVE_PC_PORT", "8765"))
PAIRING_TOKEN = os.environ.get("OMNILIVE_PC_TOKEN", "").strip()


def get_active_window_title() -> str:
    if platform.system() == "Windows":
        try:
            hwnd = ctypes.windll.user32.GetForegroundWindow()
            length = ctypes.windll.user32.GetWindowTextLengthW(hwnd)
            buf = ctypes.create_unicode_buffer(length + 1)
            ctypes.windll.user32.GetWindowTextW(hwnd, buf, length + 1)
            return buf.value
        except Exception:
            return ""
    return ""


def capture_screen_payload() -> dict:
    img = ImageGrab.grab()
    orig_w, orig_h = img.size
    max_w = 1280
    if orig_w > max_w:
        scale = max_w / float(orig_w)
        resized = img.resize((max_w, max(1, int(orig_h * scale))))
    else:
        resized = img

    buf = io.BytesIO()
    resized.convert("RGB").save(buf, format="JPEG", quality=72)
    b64 = base64.b64encode(buf.getvalue()).decode("ascii")
    return {
        "screenWidth": orig_w,
        "screenHeight": orig_h,
        "capturedWidth": resized.size[0],
        "capturedHeight": resized.size[1],
        "activeWindow": get_active_window_title(),
        "platform": platform.system(),
        "imageBase64": b64,
    }


def execute_pc_action(data: dict) -> tuple[bool, str]:
    action = str(data.get("actionType", "DONE")).upper()
    x = int(data.get("x", -1))
    y = int(data.get("y", -1))
    text = str(data.get("inputText", ""))

    if action == "CLICK":
        if x < 0 or y < 0:
            return False, f"Invalid coordinates ({x}, {y})"
        pyautogui.click(x, y)
        return True, f"Clicked ({x}, {y})"
    if action == "DOUBLE_CLICK":
        if x < 0 or y < 0:
            return False, f"Invalid coordinates ({x}, {y})"
        pyautogui.doubleClick(x, y)
        return True, f"Double-clicked ({x}, {y})"
    if action == "RIGHT_CLICK":
        if x < 0 or y < 0:
            return False, f"Invalid coordinates ({x}, {y})"
        pyautogui.rightClick(x, y)
        return True, f"Right-clicked ({x}, {y})"
    if action == "TYPE_TEXT":
        if x >= 0 and y >= 0:
            pyautogui.click(x, y)
        pyautogui.write(text, interval=0.02)
        return True, f"Typed text ({len(text)} chars)"
    if action == "HOTKEY":
        keys = [k.strip().lower() for k in text.split("+") if k.strip()]
        if not keys:
            return False, "No hotkey specified"
        pyautogui.hotkey(*keys)
        return True, f"Pressed hotkey {'+'.join(keys)}"
    if action == "PRESS_KEY":
        if not text.strip():
            return False, "No key specified"
        pyautogui.press(text.strip().lower())
        return True, f"Pressed key {text.strip()}"
    if action == "SCROLL_DOWN":
        pyautogui.scroll(-500)
        return True, "Scrolled down"
    if action == "SCROLL_UP":
        pyautogui.scroll(500)
        return True, "Scrolled up"
    if action == "OPEN_APP":
        target = text.strip()
        if not target:
            return False, "No app or URL specified"
        if target.startswith("http://") or target.startswith("https://"):
            webbrowser.open(target)
            return True, f"Opened URL {target}"
        if platform.system() == "Windows":
            subprocess.Popen(["cmd", "/c", "start", "", target], shell=False)
            return True, f"Launched {target}"
        if platform.system() == "Darwin":
            subprocess.Popen(["open", "-a", target])
            return True, f"Launched {target}"
        subprocess.Popen([target])
        return True, f"Launched {target}"
    if action in ("WAIT", "DONE"):
        return True, f"Action {action} acknowledged"
    return False, f"Unsupported actionType: {action}"


class AgentHandler(BaseHTTPRequestHandler):
    def _authorized(self) -> bool:
        if not PAIRING_TOKEN:
            return True
        return self.headers.get("X-OmniLive-Token", "") == PAIRING_TOKEN

    def _send_json(self, code: int, payload: dict):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if not self._authorized():
            self._send_json(401, {"error": "Unauthorized token"})
            return
        if self.path == "/health":
            w, h = pyautogui.size()
            self._send_json(200, {
                "status": "ok",
                "platform": platform.system(),
                "screenWidth": w,
                "screenHeight": h,
                "activeWindow": get_active_window_title(),
            })
            return
        if self.path == "/screen":
            try:
                self._send_json(200, capture_screen_payload())
            except Exception as exc:
                self._send_json(500, {"error": str(exc)})
            return
        self._send_json(404, {"error": "Not found"})

    def do_POST(self):
        if not self._authorized():
            self._send_json(401, {"error": "Unauthorized token"})
            return
        if self.path == "/execute":
            length = int(self.headers.get("Content-Length", "0"))
            raw = self.rfile.read(length).decode("utf-8")
            try:
                data = json.loads(raw)
                ok, detail = execute_pc_action(data)
                self._send_json(200 if ok else 400, {"success": ok, "detail": detail})
            except Exception as exc:
                self._send_json(500, {"success": False, "detail": str(exc)})
            return
        self._send_json(404, {"error": "Not found"})

    def log_message(self, format, *args):
        print(f"[OmniLivePCAgent] {format % args}")


if __name__ == "__main__":
    print(f"OmniLive PC Agent listening on http://0.0.0.0:{PORT}")
    print("Move mouse to top-left corner at any time for PyAutoGUI emergency failsafe.")
    server = ThreadingHTTPServer(("0.0.0.0", PORT), AgentHandler)
    server.serve_forever()
    """.trimIndent()

    val BUILD_EXE_COMMANDS: String = """
# 1. Python環境で必要ライブラリとPyInstallerを導入
pip install pyautogui pillow pyinstaller

# 2. Windows用 GUI版単体実行ファイル (.exe) を生成
pyinstaller --onefile --windowed --name OmniLivePCAgent omnilive_pc_agent.py

# 3. dist\OmniLivePCAgent.exe を起動し、PCのIPアドレス (例: http://192.168.1.10:8765) をアプリへ入力
    """.trimIndent()
}
