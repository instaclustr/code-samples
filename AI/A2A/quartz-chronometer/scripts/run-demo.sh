#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="${QUARTZ_CHRONOMETER_PORT:-8085}"
AGENT_URL="http://localhost:${PORT}"

export QUARTZ_COUNTDOWN_SECONDS="${QUARTZ_COUNTDOWN_SECONDS:-20}"
export QUARTZ_CONFIRM_COUNTDOWN_SECONDS="${QUARTZ_CONFIRM_COUNTDOWN_SECONDS:-10}"

echo "==> Building Quartz Chronometer agent..."
(cd "$ROOT" && mvn -q test package -DskipTests)

AGENT_PID=""
cleanup() {
  if [[ -n "$AGENT_PID" ]] && kill -0 "$AGENT_PID" 2>/dev/null; then
    kill "$AGENT_PID" 2>/dev/null || true
    sleep 1
    kill -9 "$AGENT_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "==> Starting agent on ${AGENT_URL} ..."
(cd "$ROOT" && java -jar target/quarkus-app/quarkus-run.jar) &
AGENT_PID=$!

for _ in $(seq 1 30); do
  if curl -sf "${AGENT_URL}/.well-known/agent-card.json" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! curl -sf "${AGENT_URL}/.well-known/agent-card.json" >/dev/null 2>&1; then
  echo "Agent did not become ready on ${AGENT_URL}" >&2
  exit 1
fi

echo "==> Agent: $(curl -s "${AGENT_URL}/.well-known/agent-card.json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')"
echo "==> Running QuartzDemoClient (countdown=${QUARTZ_COUNTDOWN_SECONDS}s confirm=${QUARTZ_CONFIRM_COUNTDOWN_SECONDS}s)..."
export QUARTZ_CHRONOMETER_URL="${AGENT_URL}"
(cd "$ROOT" && mvn -q exec:java)

echo "==> Demo complete."
