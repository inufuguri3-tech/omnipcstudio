# OmniLive PC Studio (GUI版 & Windows .exe)

パソコンの画面上で直接使える **AI PC 操作 GUI アプリ** 兼 **OmniLive Android 連携サーバー** です。

## 1. GitHub で GUI版 `.exe` を入手する手順（PC側のPython不要）
1. AI Studio 右上の設定メニューから **GitHub へプッシュ** します。
2. プッシュした GitHub リポジトリの **Actions** タブを開き、**「Build Windows PC GUI EXE」** を開きます（自動実行されていない場合は `Run workflow` を押してください）。
3. 完了後、下部の Artifacts から **`OmniLivePCAgent-GUI-Windows-exe`** をダウンロード・解凍し、**`OmniLivePCAgent.exe`** をダブルクリックして起動します。

## 2. ZIP ダウンロードから手元の PC で `.exe` を作る場合
1. プロジェクトを ZIP ダウンロードして解凍します。
2. `pc_agent` フォルダの **`build_exe.bat`** をダブルクリックすると、同フォルダに GUI版 **`OmniLivePCAgent.exe`** が作成されます（Python 3.10+ 必要）。

## 3. GUI版の機能
- **PC単体でのAI操作**: 画面上で Gemini / ChatGPT / Claude / ローカルLLM (Ollama) を選び、指示を入力するだけでマウス・キーボード操作を自動実行します（安全確認・緊急停止ボタン付き）。
- **スマホ連携待機サーバー**: 起動と同時にローカル IP アドレスとポート `8765` を表示し、Android アプリの「PC操作」タブからそのまま接続できます。
