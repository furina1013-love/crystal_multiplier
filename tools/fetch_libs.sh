#!/bin/bash
# 下载编译期依赖 jar 到工程下的 libs/ 目录（CI 与本机通用）。
# 用法：tools/fetch_libs.sh <1.20.1|1.21.1> [目标 libs 目录]
#   1.20.1 -> 默认 libs   （仓库根目录）
#   1.21.1 -> 默认 versions/1.21.1/libs
# 清单见 tools/libs.txt：set|文件名|URL
set -euo pipefail

SET="$1"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

if [ -z "$SET" ]; then echo "用法: tools/fetch_libs.sh <1.20.1|1.21.1> [dest]"; exit 1; fi

if [ "$#" -ge 2 ]; then
  DEST="$2"
elif [ "$SET" = "1.20.1" ]; then
  DEST="$ROOT/libs"
else
  DEST="$ROOT/versions/$SET/libs"
fi

mkdir -p "$DEST"
while IFS='|' read -r set name url; do
  case "$set" in ''|'#'*) continue ;; esac
  [ "$set" = "$SET" ] || continue
  if [ -f "$DEST/$name" ]; then
    echo "  已存在 $name"
    continue
  fi
  echo "  下载 $name"
  curl -fsSL --retry 3 -o "$DEST/$name.part" "$url"
  mv "$DEST/$name.part" "$DEST/$name"
done < "$ROOT/tools/libs.txt"
echo "依赖就绪: $DEST"
ls -1 "$DEST"