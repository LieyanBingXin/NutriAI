#!/usr/bin/env bash
# NutriAI — 更新版本（先备份，再重建 app，数据库不动）
set -euo pipefail
cd "$(dirname "$0")"

./backup.sh
echo "==> 拉取代码并重建应用容器 ..."
# 若使用 Git 部署：
# git -C ../../ pull --ff-only
docker compose up -d --build app
docker image prune -f >/dev/null || true
echo "✅ 更新完成；查看日志： docker compose logs -f app"
