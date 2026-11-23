#!/usr/bin/env bash
# NutriAI - lightweight health check + optional alert webhook
# Cron example (every 5 minutes):
#   */5 * * * * /opt/nutriai/deploy/linux/healthcheck.sh >> /var/log/nutriai-health.log 2>&1
set -u
cd "$(dirname "$0")"

HEALTH_URL="${HEALTH_URL:-http://127.0.0.1/api/health}"
ALERT_WEBHOOK="${ALERT_WEBHOOK:-}"
AUTO_RESTART="${AUTO_RESTART:-true}"
LOG_FILE="${LOG_FILE:-./healthcheck.log}"
STAMP="$(date '+%Y-%m-%d %H:%M:%S')"

log() {
  echo "[$STAMP] $*" | tee -a "$LOG_FILE" >/dev/null
}

code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$HEALTH_URL" || echo 000)"
if [ "$code" = "200" ]; then
  log "OK $HEALTH_URL"
  exit 0
fi

log "FAIL $HEALTH_URL status=$code"

if [ "$AUTO_RESTART" = "true" ]; then
  log "restarting app container"
  docker compose restart app >/dev/null 2>&1 || true
  sleep 20
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$HEALTH_URL" || echo 000)"
  if [ "$code" = "200" ]; then
    log "RECOVERED after restart"
    exit 0
  fi
  log "STILL FAILING status=$code"
fi

if [ -n "$ALERT_WEBHOOK" ]; then
  payload="{\"text\":\"NutriAI health check failed: status=$code at $STAMP\",\"content\":\"NutriAI health check failed: status=$code at $STAMP\"}"
  curl -s -X POST -H 'Content-Type: application/json' -d "$payload" --max-time 10 "$ALERT_WEBHOOK" >/dev/null 2>&1 || true
  log "alert webhook sent"
fi

exit 1
