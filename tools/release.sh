#!/bin/bash
# 一条龙发版：构建 -> 提交 -> 打 tag -> 推送源码与 tag -> 建 GitHub Release 并上传产物
# 用法：tools/release.sh 1.7.0
# token：默认读 /root/java-mod-dev/githubtoken.txt，可用 GH_TOKEN_FILE 覆盖；仓库可用 GH_REPO 覆盖。
set -euo pipefail

VER="${1:?用法: tools/release.sh <version>}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TOKEN_FILE="${GH_TOKEN_FILE:-/root/java-mod-dev/githubtoken.txt}"
REPO="${GH_REPO:-furina1013-love/crystal_multiplier}"

cd "$ROOT"

echo "[1/5] build.gradle 版本 -> $VER"
sed -i "s/^version = '.*'/version = '$VER'/" build.gradle

echo "[2/5] 构建"
"$ROOT/tools/build.sh" "$VER"

echo "[3/5] 产物入 dist + 校验和"
cp -f "build/libs/crystal_multiplier-$VER-mc1.20.1.jar" dist/
( cd dist && sha256sum crystal_multiplier-*.jar > SHA256SUMS.txt )

echo "[4/5] 提交 + 打 tag + 推送"
git add -A
if git diff --cached --quiet; then
  echo "  （没有需要提交的改动）"
else
  git -c user.name="crystal-multiplier" -c user.email="dev@example.com" commit -q -m "release $VER"
fi
git -c user.name="crystal-multiplier" -c user.email="dev@example.com" tag -f -a "v$VER" -m "$VER" 2>/dev/null || git tag -f "v$VER"
GIT_TOKEN=$(tr -d '\r\n' < "$TOKEN_FILE")
URL="https://x-access-token:${GIT_TOKEN}@github.com/${REPO}.git"
# github.com:443 时通时断：先试两次 git push，不行就退回 api.github.com
PUSHED=0
for attempt in 1 2; do
  echo "  推送尝试 $attempt"
  if OUT=$(git -c http.lowSpeedLimit=0 -c http.lowSpeedTime=0 push "$URL" "HEAD:main" "refs/tags/v$VER" 2>&1); then
    echo "$OUT" | sed "s/${GIT_TOKEN}/***/g"
    PUSHED=1
    break
  fi
  echo "$OUT" | sed "s/${GIT_TOKEN}/***/g" | tail -2
  sleep 3
done
if [ "$PUSHED" != "1" ]; then
  echo "  git push 不通，改用 API 推送"
  python3 "$ROOT/tools/api_push.py" "$VER"
fi

echo "[5/5] 建 Release 并上传"
python3 "$ROOT/tools/gh_release.py" "$VER"
