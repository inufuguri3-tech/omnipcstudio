"""
OmniLive PC Studio & Agent (GUI + LAN Server)
Windows / Mac / Linux desktop GUI for AI-powered PC automation,
with built-in LAN HTTP server for OmniLive Android remote control.

Run with GUI (default):
    python omnilive_pc_agent.py

Run headless (CLI server only):
    python omnilive_pc_agent.py --headless
"""

import base64
import ctypes
import io
import json
import os
import platform
import queue
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    import pyautogui
    from PIL import ImageGrab
except ImportError:
    if sys.stdout is not None:
        print("Missing dependencies. Run: pip install pyautogui pillow")
    sys.exit(1)

try:
    import tkinter as tk
    from tkinter import messagebox, scrolledtext, ttk
    HAS_TK = True
except Exception:
    HAS_TK = False

try:
    from PIL import ImageTk
    HAS_IMAGETK = True
except Exception:
    HAS_IMAGETK = False

pyautogui.FAILSAFE = True
pyautogui.PAUSE = 0.15

PORT = int(os.environ.get("OMNILIVE_PC_PORT", "8765"))
PAIRING_TOKEN = os.environ.get("OMNILIVE_PC_TOKEN", "").strip()

LOG_QUEUE: "queue.Queue[str]" = queue.Queue()


def emit_log(msg: str) -> None:
    timestamp = time.strftime("%H:%M:%S")
    line = f"[{timestamp}] {msg}"
    LOG_QUEUE.put(line)
    if sys.stdout is not None:
        try:
            print(line, flush=True)
        except Exception:
            pass


def get_lan_ip() -> str:
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(0.5)
        sock.connect(("8.8.8.8", 80))
        ip = sock.getsockname()[0]
        sock.close()
        return ip
    except Exception:
        try:
            return socket.gethostbyname(socket.gethostname())
        except Exception:
            return "127.0.0.1"


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
    resized.convert("RGB").save(buf, format="JPEG", quality=75)
    b64 = base64.b64encode(buf.getvalue()).decode("ascii")
    return {
        "screenWidth": orig_w,
        "screenHeight": orig_h,
        "capturedWidth": resized.size[0],
        "capturedHeight": resized.size[1],
        "activeWindow": get_active_window_title(),
        "platform": platform.system(),
        "imageBase64": b64,
        "imageObj": resized,
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
        if action == "WAIT":
            time.sleep(1.0)
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
            self._send_json(
                200,
                {
                    "status": "ok",
                    "platform": platform.system(),
                    "screenWidth": w,
                    "screenHeight": h,
                    "activeWindow": get_active_window_title(),
                },
            )
            return
        if self.path == "/screen":
            try:
                payload = capture_screen_payload()
                payload.pop("imageObj", None)
                self._send_json(200, payload)
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
                emit_log(f"Remote execute: {data.get('actionType')} -> {detail}")
                self._send_json(200 if ok else 400, {"success": ok, "detail": detail})
            except Exception as exc:
                self._send_json(500, {"success": False, "detail": str(exc)})
            return
        self._send_json(404, {"error": "Not found"})

    def log_message(self, format, *args):
        emit_log(f"HTTP {format % args}")


def extract_json_object(raw: str) -> dict:
    text = raw.strip()
    start = text.find("{")
    end = text.rfind("}")
    if start >= 0 and end > start:
        text = text[start : end + 1]
    return json.loads(text)


def request_ai_pc_plan(
    provider: str,
    model_name: str,
    api_key: str,
    local_url: str,
    goal: str,
    snap: dict,
    history: list[str],
    step_idx: int,
) -> dict:
    hist_text = "None" if not history else " | ".join(history[-5:])
    prompt = f"""Desktop GUI Vision AI Agent ({snap['platform']}). Inspect the attached PC desktop screenshot and pick the single best next action.
Goal: "{goal}"
Step: {step_idx}
Active Window: "{snap['activeWindow'] or 'Unknown'}"
Native Resolution: {snap['screenWidth']}x{snap['screenHeight']}
Previous Steps: {hist_text}

Return ONLY valid JSON:
{{"actionType":"CLICK|DOUBLE_CLICK|RIGHT_CLICK|TYPE_TEXT|HOTKEY|PRESS_KEY|SCROLL_DOWN|SCROLL_UP|OPEN_APP|WAIT|DONE","x":500,"y":500,"inputText":"","reasoning":"日本語で簡潔な理由","requiresConfirmation":false,"isComplete":false}}

Rules:
- x and y MUST be normalized integers 0..1000 (0=left/top, 500=center, 1000=right/bottom), or -1 if unused.
- Use OPEN_APP (inputText="notepad", "calc", "chrome", or "https://...") to launch apps/URLs directly.
- Use CLICK / DOUBLE_CLICK / RIGHT_CLICK with normalized x,y (0..1000).
- Use TYPE_TEXT (optional x,y + inputText), HOTKEY (e.g. "ctrl+c", "alt+tab"), PRESS_KEY (e.g. "enter"), SCROLL_DOWN, SCROLL_UP.
- Set actionType="DONE" and isComplete=true once completed."""

    b64 = snap["imageBase64"]

    if provider == "Gemini":
        key = api_key.strip() or os.environ.get("GEMINI_API_KEY", "").strip()
        if not key:
            raise ValueError("Gemini APIキーを入力するか環境変数 GEMINI_API_KEY を設定してください。")
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{model_name}:generateContent?key={key}"
        body = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt},
                        {"inline_data": {"mime_type": "image/jpeg", "data": b64}},
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "responseMimeType": "application/json",
            },
        }
        req = urllib.request.Request(
            url,
            data=json.dumps(body).encode("utf-8"),
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=45) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        raw_text = data["candidates"][0]["content"]["parts"][0]["text"]
        return extract_json_object(raw_text)

    if provider in ("ChatGPT (OpenAI)", "ローカルLLM (Ollama)"):
        if provider == "ChatGPT (OpenAI)":
            key = api_key.strip() or os.environ.get("OPENAI_API_KEY", "").strip()
            if not key:
                raise ValueError("OpenAI APIキーを入力するか環境変数 OPENAI_API_KEY を設定してください。")
            endpoint = "https://api.openai.com/v1/chat/completions"
            headers = {"Content-Type": "application/json", "Authorization": f"Bearer {key}"}
        else:
            endpoint = local_url.strip() or "http://127.0.0.1:11434/v1/chat/completions"
            headers = {"Content-Type": "application/json"}

        body = {
            "model": model_name,
            "temperature": 0.2,
            "max_tokens": 512,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": prompt},
                        {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}},
                    ],
                }
            ],
        }
        if provider == "ChatGPT (OpenAI)":
            body["response_format"] = {"type": "json_object"}

        req = urllib.request.Request(
            endpoint,
            data=json.dumps(body).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=45) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        raw_text = data["choices"][0]["message"]["content"]
        return extract_json_object(raw_text)

    if provider == "Claude (Anthropic)":
        key = api_key.strip() or os.environ.get("ANTHROPIC_API_KEY", "").strip()
        if not key:
            raise ValueError("Anthropic APIキーを入力するか環境変数 ANTHROPIC_API_KEY を設定してください。")
        body = {
            "model": model_name,
            "max_tokens": 512,
            "temperature": 0.2,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image",
                            "source": {
                                "type": "base64",
                                "media_type": "image/jpeg",
                                "data": b64,
                            },
                        },
                        {"type": "text", "text": prompt},
                    ],
                }
            ],
        }
        req = urllib.request.Request(
            "https://api.anthropic.com/v1/messages",
            data=json.dumps(body).encode("utf-8"),
            headers={
                "Content-Type": "application/json",
                "x-api-key": key,
                "anthropic-version": "2023-06-01",
            },
            method="POST",
        )
        with urllib.request.urlopen(req, timeout=45) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        raw_text = data["content"][0]["text"]
        return extract_json_object(raw_text)

    raise ValueError(f"未対応のプロバイダー: {provider}")


class OmniLivePcGuiApp:
    PROVIDER_DEFAULTS = {
        "Gemini": ("gemini-2.5-flash", "GEMINI_API_KEY"),
        "ChatGPT (OpenAI)": ("gpt-4o-mini", "OPENAI_API_KEY"),
        "Claude (Anthropic)": ("claude-3-5-haiku-latest", "ANTHROPIC_API_KEY"),
        "ローカルLLM (Ollama)": ("gemma3", ""),
    }

    def __init__(self, root: "tk.Tk", server: ThreadingHTTPServer):
        self.root = root
        self.server = server
        self.root.title("OmniLive PC Studio — AI 自律デスクトップ操作 GUI")
        self.root.geometry("960x720")
        self.root.minsize(780, 580)
        self.root.configure(bg="#090E1A")

        self.stop_event = threading.Event()
        self.confirm_event = threading.Event()
        self.confirm_approved = False
        self.worker_thread: threading.Thread | None = None
        self.preview_photo = None

        self._build_ui()
        self._poll_queues()

    def _build_ui(self) -> None:
        style = ttk.Style()
        try:
            style.theme_use("clam")
        except Exception:
            pass
        style.configure("TFrame", background="#090E1A")
        style.configure("Card.TFrame", background="#111827")
        style.configure("TLabel", background="#090E1A", foreground="#F8FAFC", font=("Segoe UI", 10))
        style.configure("Card.TLabel", background="#111827", foreground="#F8FAFC", font=("Segoe UI", 10))
        style.configure("Muted.TLabel", background="#111827", foreground="#94A3B8", font=("Segoe UI", 9))
        style.configure("Header.TLabel", background="#090E1A", foreground="#22D3EE", font=("Segoe UI", 16, "bold"))

        container = ttk.Frame(self.root, padding=14)
        container.pack(fill="both", expand=True)

        # Header & LAN Connection Banner
        header = ttk.Frame(container)
        header.pack(fill="x", pady=(0, 10))
        ttk.Label(header, text="OmniLive PC Studio", style="Header.TLabel").pack(side="left")

        lan_ip = get_lan_ip()
        self.server_badge = tk.Label(
            header,
            text=f"スマホ連携待機中: http://{lan_ip}:{PORT}",
            bg="#064E3B",
            fg="#34D399",
            font=("Consolas", 10, "bold"),
            padx=10,
            pady=4,
        )
        self.server_badge.pack(side="right")

        # Main split layout: Left controls / Right preview & logs
        body = ttk.Frame(container)
        body.pack(fill="both", expand=True)

        left_col = ttk.Frame(body, style="Card.TFrame", padding=12)
        left_col.pack(side="left", fill="both", expand=True, padx=(0, 8))

        right_col = ttk.Frame(body, style="Card.TFrame", padding=12)
        right_col.pack(side="right", fill="both", expand=True, padx=(8, 0))

        # --- Left Column: AI Settings & Command ---
        ttk.Label(left_col, text="AI モデル設定", style="Card.TLabel", font=("Segoe UI", 11, "bold")).pack(anchor="w")

        self.provider_var = tk.StringVar(value="Gemini")
        provider_box = ttk.Combobox(
            left_col,
            textvariable=self.provider_var,
            values=list(self.PROVIDER_DEFAULTS.keys()),
            state="readonly",
        )
        provider_box.pack(fill="x", pady=(4, 8))
        provider_box.bind("<<ComboboxSelected>>", self._on_provider_changed)

        ttk.Label(left_col, text="モデル名", style="Muted.TLabel").pack(anchor="w")
        self.model_var = tk.StringVar(value="gemini-2.5-flash")
        self.model_entry = tk.Entry(left_col, textvariable=self.model_var, bg="#090E1A", fg="#F8FAFC", insertbackground="#22D3EE")
        self.model_entry.pack(fill="x", pady=(2, 8))

        ttk.Label(left_col, text="APIキー (未入力時は環境変数を使用 / Ollamaは不要)", style="Muted.TLabel").pack(anchor="w")
        self.apikey_var = tk.StringVar(value=os.environ.get("GEMINI_API_KEY", ""))
        self.apikey_entry = tk.Entry(left_col, textvariable=self.apikey_var, show="*", bg="#090E1A", fg="#F8FAFC", insertbackground="#22D3EE")
        self.apikey_entry.pack(fill="x", pady=(2, 8))

        ttk.Label(left_col, text="ローカルLLM エンドポイント URL", style="Muted.TLabel").pack(anchor="w")
        self.local_url_var = tk.StringVar(value="http://127.0.0.1:11434/v1/chat/completions")
        tk.Entry(left_col, textvariable=self.local_url_var, bg="#090E1A", fg="#F8FAFC", insertbackground="#22D3EE").pack(fill="x", pady=(2, 10))

        self.confirm_var = tk.BooleanVar(value=True)
        tk.Checkbutton(
            left_col,
            text="各操作の前に承認確認を表示する（安全モード）",
            variable=self.confirm_var,
            bg="#111827",
            fg="#F8FAFC",
            selectcolor="#090E1A",
            activebackground="#111827",
            activeforeground="#22D3EE",
        ).pack(anchor="w", pady=(0, 8))

        self.minimize_var = tk.BooleanVar(value=True)
        tk.Checkbutton(
            left_col,
            text="画面撮影時にこのウィンドウを一時最小化する",
            variable=self.minimize_var,
            bg="#111827",
            fg="#F8FAFC",
            selectcolor="#090E1A",
            activebackground="#111827",
            activeforeground="#22D3EE",
        ).pack(anchor="w", pady=(0, 10))

        ttk.Label(left_col, text="パソコンへの操作指示", style="Card.TLabel", font=("Segoe UI", 11, "bold")).pack(anchor="w")
        self.goal_entry = tk.Entry(left_col, bg="#090E1A", fg="#F8FAFC", font=("Segoe UI", 11), insertbackground="#22D3EE")
        self.goal_entry.pack(fill="x", ipady=6, pady=(4, 8))
        self.goal_entry.insert(0, "メモ帳を開く")

        presets_frame = ttk.Frame(left_col, style="Card.TFrame")
        presets_frame.pack(fill="x", pady=(0, 10))
        for label in ("メモ帳を開く", "ChromeでGoogleを開く", "電卓を開く", "下にスクロール"):
            tk.Button(
                presets_frame,
                text=label,
                command=lambda t=label: self._set_goal(t),
                bg="#1E293B",
                fg="#E2E8F0",
                relief="flat",
                padx=8,
                pady=3,
                font=("Segoe UI", 8),
            ).pack(side="left", padx=(0, 4))

        btn_row = ttk.Frame(left_col, style="Card.TFrame")
        btn_row.pack(fill="x", pady=(4, 8))
        self.start_btn = tk.Button(
            btn_row,
            text="▶ AI PC操作を開始",
            command=self._start_task,
            bg="#22D3EE",
            fg="#090E1A",
            font=("Segoe UI", 10, "bold"),
            relief="flat",
            padx=12,
            pady=8,
        )
        self.start_btn.pack(side="left", fill="x", expand=True, padx=(0, 6))

        self.stop_btn = tk.Button(
            btn_row,
            text="■ 緊急停止",
            command=self._stop_task,
            bg="#EF4444",
            fg="#FFFFFF",
            font=("Segoe UI", 10, "bold"),
            relief="flat",
            padx=12,
            pady=8,
        )
        self.stop_btn.pack(side="right")

        self.status_label = tk.Label(
            left_col,
            text="待機中（コマンドを入力して開始してください）",
            bg="#1E293B",
            fg="#22D3EE",
            anchor="w",
            padx=10,
            pady=8,
            wraplength=360,
            justify="left",
        )
        self.status_label.pack(fill="x", pady=(6, 6))

        # Confirmation Panel (hidden until needed)
        self.confirm_frame = tk.Frame(left_col, bg="#1E293B", highlightbackground="#F59E0B", highlightthickness=2, padx=10, pady=8)
        self.confirm_text = tk.Label(self.confirm_frame, text="", bg="#1E293B", fg="#FDE68A", wraplength=340, justify="left")
        self.confirm_text.pack(anchor="w", pady=(0, 6))
        c_btns = tk.Frame(self.confirm_frame, bg="#1E293B")
        c_btns.pack(fill="x")
        tk.Button(
            c_btns,
            text="承認して実行",
            command=lambda: self._resolve_confirm(True),
            bg="#10B981",
            fg="#090E1A",
            font=("Segoe UI", 9, "bold"),
            relief="flat",
            padx=10,
            pady=5,
        ).pack(side="left", fill="x", expand=True, padx=(0, 4))
        tk.Button(
            c_btns,
            text="中止",
            command=lambda: self._resolve_confirm(False),
            bg="#475569",
            fg="#FFFFFF",
            relief="flat",
            padx=10,
            pady=5,
        ).pack(side="right")

        # --- Right Column: Screen Preview & Logs ---
        preview_hdr = ttk.Frame(right_col, style="Card.TFrame")
        preview_hdr.pack(fill="x", pady=(0, 6))
        ttk.Label(preview_hdr, text="デスクトップ・プレビュー", style="Card.TLabel", font=("Segoe UI", 11, "bold")).pack(side="left")
        tk.Button(
            preview_hdr,
            text="画面テスト撮影",
            command=self._capture_preview_only,
            bg="#1E293B",
            fg="#22D3EE",
            relief="flat",
            padx=8,
            pady=3,
        ).pack(side="right")

        self.preview_label = tk.Label(
            right_col,
            text="ここに PC 画面のキャプチャが表示されます",
            bg="#090E1A",
            fg="#64748B",
            height=13,
        )
        self.preview_label.pack(fill="both", expand=True, pady=(0, 10))

        ttk.Label(right_col, text="実行ログ (PC GUI / スマホ連携)", style="Card.TLabel", font=("Segoe UI", 10, "bold")).pack(anchor="w")
        self.log_box = scrolledtext.ScrolledText(
            right_col,
            height=12,
            bg="#090E1A",
            fg="#CBD5E1",
            font=("Consolas", 9),
            state="disabled",
        )
        self.log_box.pack(fill="both", expand=True, pady=(4, 0))

    def _on_provider_changed(self, _event=None) -> None:
        prov = self.provider_var.get()
        default_model, env_name = self.PROVIDER_DEFAULTS.get(prov, ("", ""))
        self.model_var.set(default_model)
        if env_name:
            self.apikey_var.set(os.environ.get(env_name, ""))

    def _set_goal(self, text: str) -> None:
        self.goal_entry.delete(0, "end")
        self.goal_entry.insert(0, text)

    def _capture_preview_only(self) -> None:
        def job():
            snap = self._capture_with_optional_minimize()
            self.root.after(0, lambda: self._update_preview(snap))
            emit_log(f"画面撮影成功 ({snap['screenWidth']}x{snap['screenHeight']})")

        threading.Thread(target=job, daemon=True).start()

    def _capture_with_optional_minimize(self) -> dict:
        if self.minimize_var.get():
            self.root.after(0, self.root.iconify)
            time.sleep(0.35)
        snap = capture_screen_payload()
        if self.minimize_var.get():
            self.root.after(0, self.root.deiconify)
        return snap

    def _update_preview(self, snap: dict) -> None:
        if not HAS_IMAGETK:
            self.preview_label.config(text=f"画面取得済み: {snap['screenWidth']}x{snap['screenHeight']}")
            return
        img = snap.get("imageObj")
        if img is None:
            return
        preview = img.copy()
        preview.thumbnail((440, 260))
        self.preview_photo = ImageTk.PhotoImage(preview)
        self.preview_label.config(image=self.preview_photo, text="")

    def _start_task(self) -> None:
        goal = self.goal_entry.get().strip()
        if not goal:
            messagebox.showwarning("入力エラー", "実行したい指示を入力してください。")
            return
        if self.worker_thread and self.worker_thread.is_alive():
            messagebox.showinfo("実行中", "現在別の操作を実行中です。停止してから再実行してください。")
            return

        self.stop_event.clear()
        self.confirm_frame.pack_forget()
        provider = self.provider_var.get()
        model = self.model_var.get().strip()
        api_key = self.apikey_var.get().strip()
        local_url = self.local_url_var.get().strip()

        def worker():
            history: list[str] = []
            max_steps = 8
            try:
                for step in range(1, max_steps + 1):
                    if self.stop_event.is_set():
                        self._set_status("操作を停止しました。")
                        return
                    self._set_status(f"ステップ {step}/{max_steps}: PC画面を撮影中...")
                    snap = self._capture_with_optional_minimize()
                    self.root.after(0, lambda s=snap: self._update_preview(s))

                    self._set_status(f"ステップ {step}/{max_steps}: {provider} が次の操作を計画中...")
                    plan = request_ai_pc_plan(
                        provider=provider,
                        model_name=model,
                        api_key=api_key,
                        local_url=local_url,
                        goal=goal,
                        snap=snap,
                        history=history,
                        step_idx=step,
                    )
                    action = str(plan.get("actionType", "DONE")).upper()
                    reasoning = str(plan.get("reasoning", ""))
                    is_done = bool(plan.get("isComplete", action == "DONE"))
                    emit_log(f"Step {step} Plan: {action} | {reasoning}")

                    if action == "DONE" or is_done:
                        self._set_status(f"完了: {reasoning}")
                        return

                    norm_x = int(plan.get("x", -1))
                    norm_y = int(plan.get("y", -1))
                    px = int((norm_x / 1000.0) * snap["screenWidth"]) if 0 <= norm_x <= 1000 else -1
                    py = int((norm_y / 1000.0) * snap["screenHeight"]) if 0 <= norm_y <= 1000 else -1
                    exec_payload = {
                        "actionType": action,
                        "x": px,
                        "y": py,
                        "inputText": str(plan.get("inputText", "")),
                    }

                    if self.confirm_var.get() or bool(plan.get("requiresConfirmation", False)):
                        self.confirm_event.clear()
                        self.confirm_approved = False
                        summary = f"Step {step}: {action} ({px}, {py}) {exec_payload['inputText']}\n理由: {reasoning}"
                        self.root.after(0, lambda txt=summary: self._show_confirm(txt))
                        while not self.confirm_event.is_set():
                            if self.stop_event.is_set():
                                self.root.after(0, self.confirm_frame.pack_forget)
                                self._set_status("操作が中止されました。")
                                return
                            time.sleep(0.1)
                        self.root.after(0, self.confirm_frame.pack_forget)
                        if not self.confirm_approved:
                            self._set_status("ユーザーにより操作がキャンセルされました。")
                            return

                    self._set_status(f"ステップ {step}: {action} を実行中...")
                    ok, detail = execute_pc_action(exec_payload)
                    emit_log(f"Step {step} Result: {detail}")
                    history.append(f"Step {step}: {action} => {detail}")
                    if not ok:
                        self._set_status(f"エラー: {detail}")
                        return
                    time.sleep(1.0)

                self._set_status("最大ステップ数に達したため終了しました。")
            except urllib.error.HTTPError as exc:
                err_body = exc.read().decode("utf-8", errors="ignore")
                emit_log(f"HTTP Error {exc.code}: {err_body[:200]}")
                self._set_status(f"AI APIエラー (HTTP {exc.code}): {err_body[:120]}")
            except Exception as exc:
                emit_log(f"Error: {exc}")
                self._set_status(f"エラー: {exc}")

        self.worker_thread = threading.Thread(target=worker, daemon=True)
        self.worker_thread.start()

    def _show_confirm(self, summary: str) -> None:
        self.confirm_text.config(text=summary)
        self.confirm_frame.pack(fill="x", pady=(6, 0))
        self.root.deiconify()

    def _resolve_confirm(self, approved: bool) -> None:
        self.confirm_approved = approved
        self.confirm_event.set()

    def _stop_task(self) -> None:
        self.stop_event.set()
        self.confirm_approved = False
        self.confirm_event.set()
        self._set_status("緊急停止しました。")
        emit_log("緊急停止ボタンが押されました。")

    def _set_status(self, text: str) -> None:
        self.root.after(0, lambda: self.status_label.config(text=text))

    def _poll_queues(self) -> None:
        while not LOG_QUEUE.empty():
            try:
                line = LOG_QUEUE.get_nowait()
                self.log_box.config(state="normal")
                self.log_box.insert("end", line + "\n")
                self.log_box.see("end")
                self.log_box.config(state="disabled")
            except queue.Empty:
                break
        self.root.after(150, self._poll_queues)


def start_server() -> ThreadingHTTPServer:
    server = ThreadingHTTPServer(("0.0.0.0", PORT), AgentHandler)
    emit_log(f"OmniLive LAN Server listening on http://{get_lan_ip()}:{PORT}")
    return server


if __name__ == "__main__":
    headless = "--headless" in sys.argv or not HAS_TK
    http_server = start_server()

    if headless:
        emit_log("Running in headless CLI mode. Press Ctrl+C to stop.")
        http_server.serve_forever()
    else:
        server_thread = threading.Thread(target=http_server.serve_forever, daemon=True)
        server_thread.start()
        tk_root = tk.Tk()
        app = OmniLivePcGuiApp(tk_root, http_server)
        try:
            tk_root.mainloop()
        finally:
            http_server.shutdown()
