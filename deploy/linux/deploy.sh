#!/usr/bin/env bash
# NutriAI — Linux 云服务器一键部署（Docker Compose）
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] 未安装 Docker，请先安装 Docker Engine 与 Compose 插件。"
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "[ERROR] 缺少 docker compose 插件。"
  exit 1
fi
if [ ! -f ../.env ]; then
  echo "[ERROR] 缺少 ../.env。请执行：cp ../.env.example ../.env 并填写。"
  exit 1
fi

mkdir -p certbot/conf certbot/www backups
chmod 700 certbot/conf

echo "==> 构建并启动 NutriAI ..."
docker compose up -d --build

echo "==> 等待服务就绪 ..."
ok=0
for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1/api/health >/dev/null 2>&1; then ok=1; break; fi
  sleep 3
done

if [ "$ok" != "1" ]; then
  echo "[ERROR] 健康检查失败，输出最近日志："
  docker compose logs --tail=120 app nginx
  exit 1
fi

echo
echo "✅ 部署完成"
echo "   本机验证： http://127.0.0.1/api/health"
echo "   对外访问： http://<服务器IP>/"
echo
echo "下一步："
echo "  1. 域名 A 记录指向本机公网 IP"
echo "  2. 申请证书：见 deploy/README.md 的 HTTPS 章节"
echo "  3. 配置每日备份：crontab -e 加入"
echo "     0 3 * * * /opt/nutriai/deploy/linux/backup.sh >> /var/log/nutriai-backup.log 2>&1"
