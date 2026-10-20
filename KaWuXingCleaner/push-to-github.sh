#!/usr/bin/env bash
# 一键：检查/创建仓库 -> 推送 main -> 触发 GitHub Actions 编译
#
# 用法：
#   export GITHUB_TOKEN=ghp_xxxxxxxx     # 勾选 repo 权限的 Personal Access Token
#   bash push-to-github.sh KaWuXing      # 参数为仓库名，默认 KaWuXing
#
# 可选环境变量：
#   GH_OWNER    指定归属账号/组织（默认用 token 对应的账号）
#   GH_PRIVATE=1 建私有仓库（默认公开）
#
# 仓库已存在且非空时，本工程会被放进 KaWuXingCleaner/ 子目录，
# 并在仓库根目录生成对应的 workflow（GitHub 只读根目录的 .github/workflows），不会覆盖你原有的代码。
set -euo pipefail

REPO_NAME="${1:-KaWuXing}"
SUBDIR="KaWuXingCleaner"
API="https://api.github.com"
SRC_DIR="$(cd "$(dirname "$0")" && pwd)"
TOKEN="${GITHUB_TOKEN:-}"

if [[ -z "$TOKEN" ]]; then
  echo "缺少 GITHUB_TOKEN。先执行："
  echo "  export GITHUB_TOKEN=你的PAT      # https://github.com/settings/tokens 勾选 repo"
  echo "或者直接双击 tools\\push_to_github.bat，用浏览器登录推送，不需要 PAT。"
  exit 1
fi

# ---------- 0. 网络预检：直连失败就扫本机常见代理端口 ----------
can_reach() { curl -s -o /dev/null --max-time 8 https://api.github.com; }

echo "==> 检查 GitHub API 连通性…"
if ! can_reach; then
  echo "    直连失败，扫描本机代理端口…"
  for p in 7890 7891 10809 10808 1080 2080 20171 33210 8080 8888 26561; do
    if curl -s -o /dev/null --max-time 2 -x "http://127.0.0.1:$p" https://api.github.com; then
      export https_proxy="http://127.0.0.1:$p"
      export http_proxy="http://127.0.0.1:$p"
      echo "    命中代理：127.0.0.1:$p"
      break
    fi
  done
fi

if ! can_reach; then
  echo "仍然连不上 api.github.com。请："
  echo "  1) 打开代理/VPN（模式设为「全局」或「TUN」），或"
  echo "  2) export https_proxy=http://127.0.0.1:你的端口 后重跑"
  exit 1
fi
echo "    网络正常"

# ---------- 1. 确认账号 ----------
AUTH=(-H "Authorization: Bearer $TOKEN" -H "Accept: application/vnd.github+json")

ME=$(curl -s "${AUTH[@]}" "$API/user")
LOGIN=$(echo "$ME" | sed -n 's/.*"login": *"\([^"]*\)".*/\1/p' | head -1)
if [[ -z "$LOGIN" ]]; then
  echo "token 无效或没有读取账号的权限：$ME"
  exit 1
fi
OWNER="${GH_OWNER:-$LOGIN}"
echo "==> 账号：$LOGIN  归属：$OWNER  仓库：$REPO_NAME"

# ---------- 2. 仓库不存在则创建 ----------
CODE=$(curl -s -o /dev/null -w "%{http_code}" "${AUTH[@]}" "$API/repos/$OWNER/$REPO_NAME")
if [[ "$CODE" == "404" ]]; then
  echo "==> 仓库不存在，正在创建…"
  PRIVATE="false"
  [[ "${GH_PRIVATE:-0}" == "1" ]] && PRIVATE="true"
  RESP=$(curl -s "${AUTH[@]}" -X POST "$API/user/repos" \
    -d "{\"name\":\"$REPO_NAME\",\"private\":$PRIVATE,\"description\":\"KaWuXing Cleaner - Android 垃圾清理\"}")
  echo "$RESP" | grep -q '"full_name"' || { echo "创建失败：$RESP"; exit 1; }
elif [[ "$CODE" == "200" ]]; then
  echo "==> 仓库已存在"
else
  echo "查询仓库失败，HTTP $CODE"; exit 1
fi

REMOTE_URL="https://github.com/$OWNER/$REPO_NAME.git"
AUTH_URL="https://x-access-token:$TOKEN@github.com/$OWNER/$REPO_NAME.git"

# ---------- 3. 判断是否空仓库 ----------
CODE=$(curl -s -o /dev/null -w "%{http_code}" "${AUTH[@]}" "$API/repos/$OWNER/$REPO_NAME/contents/")

if [[ "$CODE" == "404" ]]; then
  # ---------- 3a. 空仓库：直接推本地 commit ----------
  echo "==> 空仓库，直接推送本工程到根目录"
  cd "$SRC_DIR"
  if [[ ! -d .git ]]; then
    git init -q
    git config user.name >/dev/null 2>&1 || git config user.name "$LOGIN"
    git config user.email >/dev/null 2>&1 || git config user.email "$LOGIN@users.noreply.github.com"
    git add -A
    git commit -q -m "feat: 悟空清理 1.0.0 - Android 垃圾清理（扫描/清理/存储分析/内存加速）"
  fi
  git branch -M main
  git remote remove origin >/dev/null 2>&1 || true
  git remote add origin "$AUTH_URL"
  git push -u origin main
  git remote set-url origin "$REMOTE_URL"
else
  # ---------- 3b. 非空仓库：clone 后放进子目录 ----------
  echo "==> 仓库已有内容，本工程放入 $SUBDIR/ 子目录"
  TMP=$(mktemp -d)
  trap 'rm -rf "$TMP"' EXIT
  git clone -q "$AUTH_URL" "$TMP/repo"
  cd "$TMP/repo"
  mkdir -p "$SUBDIR"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --delete --exclude '.git' "$SRC_DIR"/ "$SUBDIR"/
  else
    rm -rf "${SUBDIR:?}"/*
    cp -R "$SRC_DIR"/. "$SUBDIR"/
    rm -rf "$SUBDIR/.git"
  fi

  # 根目录 workflow：GitHub 只认仓库根的 .github/workflows
  mkdir -p .github/workflows
  cat > .github/workflows/kawuxing-cleaner-android.yml <<'YAML'
name: Build KaWuXing Cleaner APK

on:
  push:
    branches: [ main, master ]
    paths:
      - 'KaWuXingCleaner/**'
      - '.github/workflows/kawuxing-cleaner-android.yml'
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-24.04
    timeout-minutes: 40
    defaults:
      run:
        working-directory: KaWuXingCleaner
    env:
      ANDROID_HOME: /usr/local/lib/android/sdk
      ANDROID_SDK_ROOT: /usr/local/lib/android/sdk
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - name: Prepare Android SDK
        run: |
          SM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
          if [ -x "$SM" ]; then
            yes | "$SM" --licenses > /dev/null 2>&1 || true
            "$SM" --install "platforms;android-34" "build-tools;34.0.0" > /dev/null 2>&1 || true
          fi
      - uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.7'
      - name: Build debug APK
        run: gradle :app:assembleDebug --no-daemon --stacktrace
      - name: Upload APK
        if: success()
        uses: actions/upload-artifact@v4
        with:
          name: KaWuXingCleaner-debug-apk
          path: KaWuXingCleaner/app/build/outputs/apk/debug/*.apk
          if-no-files-found: error
      - name: Publish to latest release
        if: success() && github.event_name == 'push'
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        run: |
          set -e
          APK=$(ls app/build/outputs/apk/debug/*.apk | head -1)
          gh release view latest >/dev/null 2>&1 || \
            gh release create latest --title "最新构建" --notes "自动构建 ${GITHUB_SHA:0:7}" --latest
          gh release upload latest "$APK" --clobber
YAML

  git config user.name >/dev/null 2>&1 || git config user.name "$LOGIN"
  git config user.email >/dev/null 2>&1 || git config user.email "$LOGIN@users.noreply.github.com"
  git add -A
  if git diff --cached --quiet; then
    echo "==> 内容无变化，跳过提交"
  else
    git commit -q -m "feat(cleaner): 悟空清理 1.0.0 - Android 垃圾清理"
    git push origin HEAD:main
  fi
  cd "$SRC_DIR"
fi

echo ""
echo "=================================================="
echo " 仓库：   https://github.com/$OWNER/$REPO_NAME"
echo " Actions：https://github.com/$OWNER/$REPO_NAME/actions"
echo " 下载：   https://github.com/$OWNER/$REPO_NAME/releases/download/latest/app-debug.apk"
echo "=================================================="
echo "推送完成。5–15 分钟后到 Actions 页面看构建结果，"
echo "构建成功会自动覆盖 latest release，上面的下载地址直接可用。"
