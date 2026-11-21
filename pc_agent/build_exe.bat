@echo off
setlocal
cd /d "%~dp0"

set "PY_CMD="
where py >nul 2>nul && set "PY_CMD=py"
if "%PY_CMD%"=="" (
    where python >nul 2>nul && set "PY_CMD=python"
)

if "%PY_CMD%"=="" (
    echo [ERROR] Python 3 was not found on this PC.
    echo Please install Python from https://www.python.org/downloads/
    echo Check "Add python.exe to PATH" during installation, then run this file again.
    pause
    exit /b 1
)

echo [1/3] Installing required libraries...
%PY_CMD% -m pip install --upgrade pip
%PY_CMD% -m pip install -r requirements.txt
if errorlevel 1 (
    echo [ERROR] Failed to install dependencies.
    pause
    exit /b 1
)

echo [2/3] Building OmniLivePCAgent.exe (GUI)...
%PY_CMD% -m PyInstaller --noconfirm --clean --onefile --windowed --name OmniLivePCAgent omnilive_pc_agent.py
if errorlevel 1 (
    echo [ERROR] Failed to build OmniLivePCAgent.exe.
    pause
    exit /b 1
)

echo [3/3] Copying OmniLivePCAgent.exe to pc_agent folder...
copy /y "dist\OmniLivePCAgent.exe" "OmniLivePCAgent.exe" >nul

echo.
echo ========================================================
echo Done! OmniLivePCAgent.exe has been created.
echo Double-click OmniLivePCAgent.exe in this folder to launch.
echo ========================================================
pause
