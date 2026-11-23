# NutriAI 上线部署手册

本目录提供两套可直接使用的上线方案：

| 场景 | 方案 | 入口 |
|------|------|------|
| 自己的 Windows 电脑长期运行 | Java 进程 + 计划任务开机自启 + 防火墙 + 定时备份 | `deploy/windows/` |
| 租用云服务器（推荐 Ubuntu 22.04/24.04） | Docker Compose + MySQL + Nginx + HTTPS | `deploy/linux/` |

> 应用本身只需要 **Java 21 + MySQL 8**；Nginx/HTTPS 负责对外访问与安全加固。

---

## 一、本机 Windows 部署

适用：电脑长期开机、局域网访问、或通过路由器端口映射/内网穿透对外提供访问。

### 1. 准备

- 安装 **JDK 21**（本项目默认 `D:\java\21`）
- 安装 **MySQL 8**，服务已启动，数据库 `nutriai` 可自动创建
- 打包一次：

```bat
cd backend
mvn clean package
```

### 2. 配置

```powershell
cd deploy\windows
copy config.example.ps1 config.ps1
notepad config.ps1
```

至少确认：`ProjectRoot`、`JavaHome`、`JarPath`、`MysqlPassword`、`MysqlDump`。
`config.ps1` 已在 `.gitignore` 中，不会提交。

### 3. 安装为开机自启服务

以 **管理员身份** 打开 PowerShell：

```powershell
cd deploy\windows
powershell -ExecutionPolicy Bypass -File .\install-service.ps1
```

脚本会：

1. 创建计划任务 `NutriAI`（开机 30 秒后以 SYSTEM 运行，失败自动重启）
2. 放行防火墙 8080 端口（仅专用/域网络）
3. 立即启动并等待 `/api/health` 通过

访问 <http://localhost:8080>。

### 4. 日常运维

```powershell
# 查看状态
Invoke-RestMethod http://127.0.0.1:8080/api/health

# 查看日志
Get-Content ..\..\logs\nutriai.log -Tail 100 -Wait

# 手动备份数据库
powershell -ExecutionPolicy Bypass -File .\backup-db.ps1

# 停止 / 卸载
powershell -ExecutionPolicy Bypass -File .\uninstall-service.ps1
```

**定时备份**：任务计划程序 → 创建任务 → 每天 03:00 运行
`powershell.exe -NoProfile -ExecutionPolicy Bypass -File "D:\Web_JavaWork\NutriAI\deploy\windows\backup-db.ps1"`。

### 5. 局域网 / 公网访问

- 局域网：`http://<电脑IP>:8080`，确保防火墙规则已添加。
- 公网：路由器做 8080 端口映射，或使用 Cloudflare Tunnel / frp（更安全，无需开放入站端口）。
- 若使用域名 + HTTPS，建议在前端加一层 Caddy/Nginx，并把 `BaseUrl` 改成 `https://你的域名`。

---

## 二、Linux 云服务器部署（Docker Compose，推荐）

### 1. 服务器建议

- 2 核 2G 起步（4G 更稳），Ubuntu 22.04/24.04
- 安全组开放 **22、80、443**；**不要**对公网开放 3306
- 一个已解析到服务器公网 IP 的域名（A 记录）

### 2. 安装 Docker

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
docker compose version
```

### 3. 上传项目并配置

```bash
sudo mkdir -p /opt/nutriai && sudo chown $USER:$USER /opt/nutriai
# 任选其一：git clone 到 /opt/nutriai，或 scp 整个项目目录
cd /opt/nutriai
cp deploy/.env.example deploy/.env
nano deploy/.env
```

必须修改：

- `APP_BASE_URL` / `DOMAIN`：你的域名
- `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`：强密码
- `AI_API_KEY`：DeepSeek Key（不填则离线 Mock）
- `DEMO_DATA=false`（生产不建议自动创建演示账号）
- 首次用 HTTP 验证时保持 `SESSION_COOKIE_SECURE=false`；HTTPS 配好后改成 `true` 并重启 app

### 4. 启动

```bash
cd /opt/nutriai/deploy/linux
chmod +x *.sh
./deploy.sh
```

完成后访问 `http://服务器IP/`。查看日志：

```bash
docker compose ps
docker compose logs -f app
docker compose logs -f nginx
```

### 5. 配置 HTTPS（Let's Encrypt）

先确保域名已解析、80 端口可访问，然后：

```bash
cd /opt/nutriai/deploy/linux
mkdir -p certbot/conf certbot/www
docker run --rm \
  -v "$PWD/certbot/conf:/etc/letsencrypt" \
  -v "$PWD/certbot/www:/var/www/certbot" \
  certbot/certbot certonly --webroot -w /var/www/certbot \
  -d 你的域名 --email 你的邮箱 --agree-tos --no-eff-email
```

编辑 `nginx/nutriai.conf`：

1. 取消底部 HTTPS `server {}` 注释
2. 替换 `nutri.example.com` 为你的域名
3. 在 80 端口 server 中加入跳转：
   `return 301 https://$host$request_uri;`（保留 acme-challenge location）

重载：

```bash
docker compose exec nginx nginx -t
docker compose exec nginx nginx -s reload
```

HTTPS 生效后，把 `deploy/.env` 中的 `SESSION_COOKIE_SECURE` 改为 `true`，然后重启应用：

```bash
docker compose up -d app
```

这样会话 Cookie 只会在 HTTPS 下发送。

自动续期（每天检查一次）：

```bash
(crontab -l 2>/dev/null; echo "0 3 * * * cd /opt/nutriai/deploy/linux && docker run --rm -v \$PWD/certbot/conf:/etc/letsencrypt -v \$PWD/certbot/www:/var/www/certbot certbot/certbot renew --quiet && docker compose exec nginx nginx -s reload") | crontab -
```

### 6. 备份

```bash
cd /opt/nutriai/deploy/linux
./backup.sh
# 每日 03:00
(crontab -l 2>/dev/null; echo "0 3 * * * /opt/nutriai/deploy/linux/backup.sh >> /var/log/nutriai-backup.log 2>&1") | crontab -
```

备份文件在 `deploy/linux/backups/`，默认保留 14 天。恢复示例：

```bash
gunzip -c backups/nutriai-20261117-030000.sql.gz | \
  docker compose exec -T db mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"
```

### 7. 更新版本

```bash
cd /opt/nutriai
git pull
cd deploy/linux
./update.sh     # 先备份，再重建 app 容器，数据库卷不动
```

### 8. 不使用 Docker（systemd 方式）

```bash
# 1) 安装 Java 21、MySQL 8、Nginx
# 2) 上传 jar 到 /opt/nutriai/backend/
#    注意：.env 中 MYSQL_HOST 要改成 127.0.0.1（非 Docker）
sudo useradd -r -s /sbin/nologin nutriai
sudo mkdir -p /opt/nutriai/backend /opt/nutriai/logs
sudo chown -R nutriai:nutriai /opt/nutriai
# 3) 复制 deploy/linux/systemd/nutriai.service 与 deploy/.env 到 /opt/nutriai/
sudo cp deploy/linux/systemd/nutriai.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now nutriai
# 4) Nginx：复制 nginx/nutriai.conf，把 upstream 的 app:8080 改成 127.0.0.1:8080
```

### 9. 监控与告警

应用暴露 `GET /api/health`（无需登录，返回 200 即存活）。除云厂商监控外，仓库自带轻量脚本：

**Linux（每 5 分钟检查，失败自动重启 app 并可发 Webhook）**

```bash
cd /opt/nutriai/deploy/linux
chmod +x healthcheck.sh
# 可选：企业微信/飞书/Slack/Discord 的 Webhook
export ALERT_WEBHOOK="https://..."
./healthcheck.sh

# crontab
(crontab -l 2>/dev/null; echo "*/5 * * * * cd /opt/nutriai/deploy/linux && ALERT_WEBHOOK='https://...' ./healthcheck.sh >> /var/log/nutriai-health.log 2>&1") | crontab -
```

脚本行为：连续失败 → `docker compose restart app` → 20 秒后复检 → 仍失败则发 Webhook 并以非 0 退出。

**Windows（任务计划程序每 5 分钟）**

```powershell
# 先手动验证
powershell -NoProfile -ExecutionPolicy Bypass -File deploy\windows\healthcheck.ps1

# 任务计划程序 → 创建任务 → 每 5 分钟
# 程序：powershell.exe
# 参数：-NoProfile -ExecutionPolicy Bypass -File "D:\Web_JavaWork\NutriAI\deploy\windows\healthcheck.ps1"
# 可选：加 -RestartTask 让失败时重启 NutriAI 计划任务；-Webhook "https://..."
```

也可直接把 `https://你的域名/api/health` 接入 UptimeRobot、BetterStack 或云监控，做外部可用性探测。

---

## 三、上线检查清单

- [ ] `MYSQL_PASSWORD` / `MYSQL_ROOT_PASSWORD` 已换成强密码，`.env` 未提交
- [ ] `SESSION_COOKIE_SECURE=true`，站点使用 HTTPS
- [ ] `DEMO_DATA=false`，删除或禁用演示账号
- [ ] 3306 未对公网开放
- [ ] 80/443 已开放，22 端口建议限制来源 IP
- [ ] 已配置每日数据库备份，并实际演练一次恢复
- [ ] 已配置 `/api/health` 监控（`healthcheck.sh` / `healthcheck.ps1` 或 UptimeRobot）
- [ ] 已确认 AI Key 有额度，且未写进前端
- [ ] 修改默认管理员密码（如保留 demo 账号）
- [ ] 日志目录有磁盘余量，Nginx 日志已轮转

## 四、故障排查

| 现象 | 排查 |
|------|------|
| 502 Bad Gateway | `docker compose ps` 看 app 是否 healthy；`docker compose logs app` |
| 连不上 MySQL | 检查 `.env` 密码、`db` 容器健康状态；不要改 `MYSQL_HOST=db` |
| 登录后立刻掉线 | 检查 `SESSION_COOKIE_SECURE` 与 HTTPS 是否一致；反向代理是否传 `X-Forwarded-Proto` |
| 流式回复中断 | Nginx 已对 `/api/` 关闭 `proxy_buffering` 并设置 300s 超时 |
| 静态资源旧版本 | `/sw.js` 已设为 no-cache；强刷或清理 Service Worker |
| 磁盘满 | 清理 `docker system prune`、旧备份与 `logs/` |
