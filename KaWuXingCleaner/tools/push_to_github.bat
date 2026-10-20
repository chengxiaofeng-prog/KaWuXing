@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

REM ============================================================
REM  悟空清理 - 一键上传到 GitHub（不需要 PAT，首次会弹浏览器登录）
REM  双击即可；也可带仓库名：push_to_github.bat KaWuXing
REM ============================================================

set REPO_NAME=%~1
if "%REPO_NAME%"=="" set REPO_NAME=KaWuXing
set OWNER=chengxiaofeng-prog
set BRANCH=main
set PROJECT_DIR=%~dp0..
set REMOTE=https://github.com/%OWNER%/%REPO_NAME%.git
set SUBDIR=KaWuXingCleaner

cd /d "%PROJECT_DIR%"

where git >nul 2>&1
if errorlevel 1 (
  echo [x] 没有检测到 git，请先安装 Git for Windows。
  pause
  exit /b 1
)

echo ============================================================
echo   仓库： %REMOTE%
echo   分支： %BRANCH%
echo ============================================================
echo.

if not exist ".git" (
  echo [1/5] 初始化本地仓库...
  git init -q
  git branch -M %BRANCH%
) else (
  echo [1/5] 本地仓库已存在
)

git config user.name >nul 2>&1
if errorlevel 1 git config user.name "%OWNER%"
git config user.email >nul 2>&1
if errorlevel 1 git config user.email "%OWNER%@users.noreply.github.com"

echo [2/5] 提交本地改动...
git add -A
git commit -q -m "feat: 悟空清理 1.0.0 - Android 垃圾清理" 2>nul

git remote remove origin >nul 2>&1
git remote add origin %REMOTE%

echo [3/5] 检查远程仓库状态（首次会弹浏览器登录 GitHub）...
git ls-remote --heads origin > "%TEMP%\kwx_heads.txt" 2>nul
set HEADSIZE=0
for %%A in ("%TEMP%\kwx_heads.txt") do set HEADSIZE=%%~zA

if "%HEADSIZE%"=="0" goto PUSH_DIRECT
goto PUSH_SUBDIR

:PUSH_DIRECT
echo [4/5] 远程是空仓库，直接推送工程到根目录...
git push -u origin %BRANCH%
if errorlevel 1 goto FAILED
goto DONE

:PUSH_SUBDIR
echo [4/5] 远程已有内容，本工程会被放进 %SUBDIR%\ 子目录，不覆盖原有代码...
set TMPREPO=%TEMP%\kwx_push_%RANDOM%
git clone -q %REMOTE% "%TMPREPO%"
if errorlevel 1 goto FAILED

echo       复制工程文件（robocopy，已排除 .git / build）...
robocopy "%PROJECT_DIR%" "%TMPREPO%\%SUBDIR%" /E /XD .git .gradle build .idea /NFL /NDL /NJH /NJS >nul

if not exist "%TMPREPO%\.github\workflows" mkdir "%TMPREPO%\.github\workflows"
copy /Y "%PROJECT_DIR%\tools\subdir-workflow.yml" "%TMPREPO%\.github\workflows\kawuxing-cleaner-android.yml" >nul

cd /d "%TMPREPO%"
git add -A
git commit -q -m "feat(cleaner): 悟空清理 1.0.0 - Android 垃圾清理" 2>nul
git push origin HEAD:%BRANCH%
if errorlevel 1 goto FAILED
cd /d "%PROJECT_DIR%"
rmdir /s /q "%TMPREPO%" >nul 2>&1
goto DONE

:DONE
echo.
echo [5/5] 推送完成。
echo.
echo   Actions： https://github.com/%OWNER%/%REPO_NAME%/actions
echo   下载：    https://github.com/%OWNER%/%REPO_NAME%/releases/download/latest/app-debug.apk
echo.
echo   5-15 分钟后 Actions 里会出现一次构建；成功后上面的下载地址直接可用。
echo   （第一次构建要给 Actions 一点时间下载 Gradle 与依赖）
start "" "https://github.com/%OWNER%/%REPO_NAME%/actions"
pause
exit /b 0

:FAILED
echo.
echo [x] 推送失败。
echo     1) 如果提示 Authentication failed：确认浏览器里登录的是 %OWNER% 这个账号；
echo     2) 如果提示仓库不存在：先在 https://github.com/new 建一个名为 %REPO_NAME% 的仓库（不要勾 README）；
echo     3) 如果提示网络错误：打开代理后重试，或右键管理器运行 tools\fix_github_hosts.bat。
echo     4) 也可以用 PAT 方式：bash push-to-github.sh %REPO_NAME%
echo.
pause
exit /b 1
