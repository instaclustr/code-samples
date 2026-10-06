#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
A2A_DIR="$ROOT/a2a"
MISSION_PATH="${SAR_MISSION_PATH:-$ROOT/missions/test-mission-multi-target.json}"
COORDINATOR_PORT="${SAR_A2A_AGENT_PORT:-8083}"
NARRATOR_PORT="${SAR_NARRATOR_PORT:-8084}"
COORDINATOR_URL="http://localhost:${COORDINATOR_PORT}"
NARRATOR_URL="http://localhost:${NARRATOR_PORT}"

echo "==> Installing Phase 1 sim..."
(cd "$ROOT/sim" && mvn -q install)

echo "==> Installing sar-core + tests..."
(cd "$A2A_DIR/sar-core" && mvn -q install)

echo "==> Building flock coordinator (:${COORDINATOR_PORT})..."
(cd "$A2A_DIR/flock-coordinator" && mvn -q package -DskipTests)

echo "==> Building mission narrator (:${NARRATOR_PORT})..."
(cd "$A2A_DIR/mission-narrator" && mvn -q package -DskipTests)

COORDINATOR_PID=""
NARRATOR_PID=""
cleanup() {
  for pid in "$NARRATOR_PID" "$COORDINATOR_PID"; do
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      for _ in $(seq 1 10); do
        kill -0 "$pid" 2>/dev/null || break
        sleep 0.5
      done
      kill -9 "$pid" 2>/dev/null || true
    fi
  done
}
trap cleanup EXIT

wait_for_agent() {
  local url="$1"
  local name="$2"
  for _ in $(seq 1 40); do
    if curl -sf "${url}/.well-known/agent-card.json" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  echo "${name} did not become ready on ${url}" >&2
  exit 1
}

echo "==> Starting flock coordinator on ${COORDINATOR_URL} ..."
(cd "$A2A_DIR/flock-coordinator" && java -jar target/quarkus-app/quarkus-run.jar) &
COORDINATOR_PID=$!
wait_for_agent "${COORDINATOR_URL}" "Flock coordinator"

echo "==> Starting mission narrator on ${NARRATOR_URL} ..."
(cd "$A2A_DIR/mission-narrator" && java -jar target/quarkus-app/quarkus-run.jar) &
NARRATOR_PID=$!
wait_for_agent "${NARRATOR_URL}" "Mission narrator"

echo "==> Agent cards:"
echo "  coordinator: $(curl -s "${COORDINATOR_URL}/.well-known/agent-card.json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')"
echo "  narrator:    $(curl -s "${NARRATOR_URL}/.well-known/agent-card.json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["name"])')"

echo "==> Running mission client with LLM narrator (Ollama ${SAR_OLLAMA_MODEL:-llama3:latest})..."
export SAR_A2A_AGENT_URL="${COORDINATOR_URL}"
export SAR_NARRATOR_URL="${NARRATOR_URL}"
export SAR_MISSION_PATH="${MISSION_PATH}"
export SAR_VIZ_OUT="${SAR_VIZ_OUT:-$ROOT/viz/out/sar-a2a-narrator}"
(cd "$A2A_DIR/mission-client" && mvn -q test exec:java)

echo "==> Phase 2 narrator demo complete."
