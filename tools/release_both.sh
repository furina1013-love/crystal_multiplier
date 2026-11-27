#!/bin/bash
# 一条龙发版（两版合并成一个 Release）：两个工程一起构建 -> 一个 Release 里放 mc1.20.1 与 mc1.21.1 两个 jar
# 用法：tools/release_both.sh 1.8.1
set -euo pipefail

VER="$1"
if [ -z "$VER" ]; then echo "用法: tools/release_both.sh <version>"; exit 1; fi
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OTHER="${CM_1211_DIR:-/root/java-mod-dev/crystal_multiplier_1211}"
TOKEN_FILE="${GH_TOKEN_FILE:-/root/java-mod-dev/_private/tokens/githubtoken.txt}"
REPO="${GH_REPO:-furina1013-love/crystal_multiplier}"
INIT="${CM_GRADLE_INIT:-/tmp/init-mirror.gradle}"

cd "$ROOT"

echo "[1/5] 版本号 -> $VER（两版）"
sed -i "s/^version = '.*'/version = '$VER'/" build.gradle
sed -i "s/^mod_version=.*/mod_version=$VER/" "$OTHER/gradle.properties"

echo "[2/5] 构建 1.20.1"
"$ROOT/tools/build.sh" "$VER"

echo "[3/5] 构建 1.21.1"
GRADARGS="--console=plain"
if [ -f "$INIT" ]; then GRADARGS="$GRADARGS --init-script $INIT"; fi
( cd "$OTHER" && ./gradlew build $GRADARGS | grep -E "BUILD|error:" | tail -2 )
cp -f "build/libs/crystal_multiplier-$VER-mc1.20.1.jar" dist/
cp -f "$OTHER/build/libs/crystal_multiplier-$VER-mc1.21.1.jar" dist/
( cd dist && sha256sum crystal_multiplier-$VER-*.jar > SHA256SUMS.txt )

echo "[4/5] 提交 + 打 tag + 推送"
git add -A
if git diff --cached --quiet; then echo "  （1.20.1 无改动）"; else
  git -c user.name="crystal-multiplier" -c user.email="dev@example.com" commit -q -m "release $VER"
fi
( cd "$OTHER" && git add -A && { if git diff --cached --quiet; then echo "  （1.21.1 无改动）"; else
  git -c user.name="crystal-multiplier" -c user.email="dev@example.com" commit -q -m "release $VER"; fi; } )
git -c user.name="crystal-multiplier" -c user.email="dev@example.com" tag -f -a "v$VER" -m "$VER" 2>/dev/null || git tag -f "v$VER"
GIT_TOKEN=$(tr -d '\r\n' < "$TOKEN_FILE")
URL="https://x-access-token:$GIT_TOKEN@github.com/$REPO.git"
PUSHED=0
for attempt in 1 2; do
  echo "  推送尝试 $attempt"
  if OUT=$(git -c http.lowSpeedLimit=0 -c http.lowSpeedTime=0 push "$URL" "HEAD:main" "refs/tags/v$VER" 2>&1); then
    echo "$OUT" | sed "s/$GIT_TOKEN/***/g"; PUSHED=1; break
  fi
  echo "$OUT" | sed "s/$GIT_TOKEN/***/g" | tail -2; sleep 3
done
if [ "$PUSHED" != "1" ]; then
  echo "  git push 不通，改用 API 推送"
  python3 "$ROOT/tools/api_push.py" "$VER"
fi

echo "[5/5] 建单个 Release（含两版 jar）并上传"
python3 "$ROOT/tools/gh_release.py" "$VER"
