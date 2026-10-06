#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
A2A_DIR="$ROOT/a2a"
MISSION_PATH="${SAR_MISSION_PATH:-$ROOT/missions/test-mission-fast.json}"
AGENT_PORT="${SAR_A2A_AGENT_PORT:-8083}"
AGENT_URL="http://localhost:${AGENT_PORT}"

echo "==> Installing Phase 1 sim..."
(cd "$ROOT/sim" && mvn -q install)

echo "==> Installing sar-core..."
(cd "$A2A_DIR/sar-core" && mvn -q install)

echo "==> Running sar-core tests..."
(cd "$A2A_DIR/sar-core" && mvn -q test)

echo "==> Building flock coordinator agent..."
(cd "$A2A_DIR/flock-coordinator" && mvn -q package -DskipTests)

AGENT_PID=""
cleanup() {
  if [[ -n "$AGENT_PID" ]] && kill -0 "$AGENT_PID" 2>/dev/null; then
    kill "$AGENT_PID" 2>/dev/null || true
    for _ in $(seq 1 10); do
      kill -0 "$AGENT_PID" 2>/dev/null || break
      sleep 0.5
    done
    kill -9 "$AGENT_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "==> Starting flock coordinator on ${AGENT_URL} ..."
(cd "$A2A_DIR/flock-coordinator" && java -jar target/quarkus-app/quarkus-run.jar) &
AGENT_PID=$!

for i in $(seq 1 30); do
  if curl -sf "${AGENT_URL}/.well-known/agent-card.json" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! curl -sf "${AGENT_URL}/.well-known/agent-card.json" >/dev/null 2>&1; then
  echo "Agent did not become ready on ${AGENT_URL}" >&2
  exit 1
fi

echo "==> Agent card:"
curl -s "${AGENT_URL}/.well-known/agent-card.json" | head -c 400
echo -e "\n..."

echo "==> Running mission client (JSON-RPC + SSE)..."
export SAR_A2A_AGENT_URL="${AGENT_URL}"
export SAR_MISSION_PATH="${MISSION_PATH}"
export SAR_VIZ_OUT="${SAR_VIZ_OUT:-$ROOT/viz/out/sar-a2a}"
(cd "$A2A_DIR/mission-client" && mvn -q test exec:java)

echo "==> Phase 2 demo complete."
