@echo off
setlocal
cd /d "%~dp0"

set "PY_CMD="
where py >nul 2>nul && set "PY_CMD=py"
if "%PY_CMD%"=="" (
    where python >nul 2>nul && set "PY_CMD=python"
)

if "%PY_CMD%"=="" (
    echo [ERROR] Python 3 が見つかりませんでした。
    echo https://www.python.org/downloads/ から Python をインストールし、「Add python.exe to PATH」にチェックを入れて再度実行してください。
    pause
    exit /b 1
)

echo [1/3] 必要ライブラリをインストールしています...
%PY_CMD% -m pip install --upgrade pip
%PY_CMD% -m pip install -r requirements.txt
if errorlevel 1 (
    echo [ERROR] ライブラリのインストールに失敗しました。
    pause
    exit /b 1
)

echo [2/3] OmniLivePCAgent.exe (GUI版) をビルドしています...
%PY_CMD% -m PyInstaller --noconfirm --clean --onefile --windowed --name OmniLivePCAgent omnilive_pc_agent.py
if errorlevel 1 (
    echo [ERROR] .exe のビルドに失敗しました。
    pause
    exit /b 1
)

echo [3/3] 実行ファイルを配置しています...
copy /y "dist\OmniLivePCAgent.exe" "OmniLivePCAgent.exe" >nul

echo.
echo ========================================================
echo 完了: GUI版 OmniLivePCAgent.exe を作成しました！
echo このフォルダの OmniLivePCAgent.exe をダブルクリックして起動できます。
echo ========================================================
pause
