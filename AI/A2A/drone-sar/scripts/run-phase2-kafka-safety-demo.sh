#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
A2A_DIR="$ROOT/a2a"
MISSION_PATH="${SAR_MISSION_PATH:-$ROOT/missions/test-mission-kafka-safety.json}"
AGENT_PORT="${SAR_A2A_AGENT_PORT:-8083}"
AGENT_URL="http://localhost:${AGENT_PORT}"
KAFKA_BOOTSTRAP="${SAR_KAFKA_BOOTSTRAP:-localhost:9092}"
EVENTS_TOPIC="${SAR_KAFKA_EVENTS_TOPIC:-sar.telemetry.events}"
ASSESSMENTS_TOPIC="${SAR_KAFKA_ASSESSMENTS_TOPIC:-sar.violation-assessments}"

echo "==> Installing Phase 1 sim..."
(cd "$ROOT/sim" && mvn -q install)

echo "==> Installing sar-core + safety-analyst..."
(cd "$A2A_DIR/sar-core" && mvn -q install)
(cd "$A2A_DIR/sar-core" && mvn -q test)
(cd "$A2A_DIR/safety-analyst" && mvn -q package -DskipTests)

if ! command -v kafka-topics >/dev/null 2>&1; then
  echo "WARN: kafka-topics not on PATH — assuming topics exist or auto-create is enabled"
else
  echo "==> Ensuring Kafka topics..."
  kafka-topics --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists --topic "$EVENTS_TOPIC" --partitions 1 --replication-factor 1 || true
  kafka-topics --bootstrap-server "$KAFKA_BOOTSTRAP" --create --if-not-exists --topic "$ASSESSMENTS_TOPIC" --partitions 1 --replication-factor 1 || true
fi

ANALYST_PID=""
AGENT_PID=""
cleanup() {
  if [[ -n "$ANALYST_PID" ]] && kill -0 "$ANALYST_PID" 2>/dev/null; then
    kill "$ANALYST_PID" 2>/dev/null || true
  fi
  if [[ -n "$AGENT_PID" ]] && kill -0 "$AGENT_PID" 2>/dev/null; then
    kill "$AGENT_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT

echo "==> Starting safety analyst consumer..."
export SAR_KAFKA_BOOTSTRAP="$KAFKA_BOOTSTRAP"
export SAR_KAFKA_EVENTS_TOPIC="$EVENTS_TOPIC"
export SAR_KAFKA_ASSESSMENTS_TOPIC="$ASSESSMENTS_TOPIC"
export SAR_OLLAMA_ENABLED="${SAR_OLLAMA_ENABLED:-true}"
export SAR_SAFETY_MAX_ASSESSMENTS="${SAR_SAFETY_MAX_ASSESSMENTS:-8}"
export SAR_SAFETY_IDLE_MS="${SAR_SAFETY_IDLE_MS:-20000}"
export SAR_KAFKA_CONSUMER_GROUP="sar-safety-analyst-$(date +%s)"
java -jar "$A2A_DIR/safety-analyst/target/drone-sar-safety-analyst-0.1.0.jar" > /tmp/sar-safety-analyst.log 2>&1 &
ANALYST_PID=$!
sleep 2

echo "==> Building flock coordinator agent..."
(cd "$A2A_DIR/flock-coordinator" && mvn -q package -DskipTests)

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

echo "==> Running mission client with Kafka safety mission..."
export SAR_A2A_AGENT_URL="${AGENT_URL}"
export SAR_MISSION_PATH="${MISSION_PATH}"
export SAR_MAX_TICKS="${SAR_MAX_TICKS:-600}"
export SAR_VIZ_OUT="${SAR_VIZ_OUT:-$ROOT/viz/out/sar-kafka-safety}"
export SAR_KAFKA_BOOTSTRAP="$KAFKA_BOOTSTRAP"
export SAR_KAFKA_EVENTS_TOPIC="$EVENTS_TOPIC"
(cd "$A2A_DIR/mission-client" && mvn -q compile exec:java)
MISSION_EXIT=$?

VIZ_DIR="${SAR_VIZ_OUT:-$ROOT/viz/out/sar-kafka-safety}"
REPLAY_JSON="$VIZ_DIR/replay.json"
REPLAY_HTML_SRC="$ROOT/viz/replay.html"
REPLAY_HTML_OUT="$VIZ_DIR/replay.html"
if [[ -f "$REPLAY_HTML_SRC" ]]; then
  cp "$REPLAY_HTML_SRC" "$REPLAY_HTML_OUT"
fi

if [[ -f "$REPLAY_JSON" ]]; then
  python3 - <<'PY' "$REPLAY_JSON"
import json, sys
path = sys.argv[1]
data = json.load(open(path))
copilot = data.get("copilot") or {}
violations = copilot.get("violations") or []
if not violations:
    print("ERROR: replay.json missing copilot.violations — rebuild mission-client and re-run.", file=sys.stderr)
    sys.exit(2)
print(f"OK: replay.json copilot bundle has {len(violations)} safety assessment(s)")
PY
  COPILOT_CHECK=$?
else
  echo "WARN: replay.json not found at $REPLAY_JSON" >&2
  COPILOT_CHECK=2
fi

if [[ "$MISSION_EXIT" -ne 0 ]]; then
  echo "WARN: mission client exited $MISSION_EXIT (may be expected for violation demos before fix)" >&2
fi
if [[ "$COPILOT_CHECK" -ne 0 ]]; then
  exit "$COPILOT_CHECK"
fi

echo "==> Waiting for safety analyst to finish..."
wait "$ANALYST_PID" 2>/dev/null || true
ANALYST_PID=""

echo ""
echo "=== Safety analyst output ==="
cat /tmp/sar-safety-analyst.log

echo ""
echo "==> Phase 2 Kafka safety demo complete."
