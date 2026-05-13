#!/usr/bin/env bash
# Free-form news demo: strict NewsItem JSON serde on the wire -> KIP-1034 DLQ for prose;
# app DLQ for missing categories; two repair loops + sample producer.
#
# Prerequisites: Docker (Kafka), Ollama optional but recommended for KIP-1034 path (plain text -> JSON).
# Usage:
#   ./scripts/demo.sh
#   OLLAMA_MODEL=llama3:latest ./scripts/demo.sh --skip-model-pull
#   ./scripts/demo.sh --skip-ollama   # Kafka only; repair falls back to deterministic tags

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

NO_MODEL_PULL=0
START_OLLAMA=1
OLLAMA_STARTED_BY_SCRIPT=0
while [ $# -gt 0 ]; do
  case "$1" in
    --skip-model-pull) NO_MODEL_PULL=1 ;;
    --skip-ollama) START_OLLAMA=0 ;;
    -h|--help)
      sed -n '1,15p' "$0" | tail -n +2
      exit 0
      ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
MODEL="${OLLAMA_MODEL:-llama3:latest}"
OLLAMA_URL="${OLLAMA_URL:-http://localhost:11434}"
APP_ID="demo-news-ff-$(date +%s)"

require_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "ERROR: missing $1" >&2; exit 1; }; }

ensure_kafka() {
  require_cmd docker
  docker compose -f "${ROOT}/docker-compose.yml" up -d
  local i=0
  while [ "$i" -lt 60 ]; do
    if docker exec news-freeform-kafka-broker /opt/kafka/bin/kafka-broker-api-versions.sh \
      --bootstrap-server localhost:9092 >/dev/null 2>&1; then
      echo "Kafka is up."
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
  echo "ERROR: Kafka not ready" >&2
  exit 1
}

delete_ff_topics() {
  echo "Deleting topics matching ^demo-news-ff"
  docker exec news-freeform-kafka-broker sh -c '
    for t in $(/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep "^demo-news-ff" || true); do
      /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --delete --topic "$t" || true
    done
  ' || true
  sleep 2
}

maven_classpath() {
  mvn -q -f "${ROOT}/pom.xml" compile
  mvn -q -f "${ROOT}/pom.xml" dependency:build-classpath -DincludeScope=compile -Dmdep.outputFile="${ROOT}/target/news-ff-demo.cp.txt"
  printf '%s:%s' "${ROOT}/target/classes" "$(cat "${ROOT}/target/news-ff-demo.cp.txt")"
}

run_java() {
  local main="$1"
  shift
  java -cp "${CP}" "${main}" "$@"
}

ensure_ollama() {
  if [ "$START_OLLAMA" -eq 0 ]; then
    curl -sf "${OLLAMA_URL}/api/tags" >/dev/null || {
      echo "WARN: Ollama not at ${OLLAMA_URL}; KIP-1034 path will use deterministic tags unless you start Ollama."
    }
    return 0
  fi
  if ! command -v ollama >/dev/null 2>&1; then
    echo "ERROR: 'ollama' not on PATH. Install from https://ollama.com or use --skip-ollama." >&2
    exit 1
  fi
  if ! curl -sf "${OLLAMA_URL}/api/tags" >/dev/null 2>&1; then
    echo "Starting 'ollama serve' in the background..."
    ollama serve >>/tmp/ollama-serve-news-freeform-demo.log 2>&1 &
    OLLAMA_STARTED_BY_SCRIPT=1
    i=0
    while [ "$i" -lt 45 ]; do
      if curl -sf "${OLLAMA_URL}/api/tags" >/dev/null 2>&1; then
        echo "Ollama is up (${OLLAMA_URL})."
        break
      fi
      i=$((i + 1))
      sleep 1
    done
    if ! curl -sf "${OLLAMA_URL}/api/tags" >/dev/null 2>&1; then
      echo "ERROR: Ollama did not become ready. See /tmp/ollama-serve-news-freeform-demo.log" >&2
      exit 1
    fi
  else
    echo "Ollama already responding at ${OLLAMA_URL}"
  fi
  if [ "$NO_MODEL_PULL" -eq 1 ]; then
    echo "Skipping ollama pull (--skip-model-pull)."
    return 0
  fi
  echo "Ensuring model '${MODEL}' (ollama pull; skips quickly if already present)..."
  if ! ollama pull "${MODEL}"; then
    echo "WARN: ollama pull failed (e.g. network). Continuing if '${MODEL}' is already installed — check: ollama list"
  fi
  sleep 3
}

require_cmd mvn
require_cmd java
ensure_kafka
delete_ff_topics
ensure_ollama

export KAFKA_BOOTSTRAP_SERVERS="${BOOTSTRAP}"
export OLLAMA_MODEL="${MODEL}"
export NEWS_FF_STREAMS_APPLICATION_ID="${APP_ID}"

CP="$(maven_classpath)"
LOGD="/tmp/news-freeform-demo-$$"
mkdir -p "${LOGD}"

cleanup() {
  for pid in "${STREAMS_PID:-}" "${APP_REP_PID:-}" "${KIP_REP_PID:-}"; do
    if [ -n "${pid}" ]; then kill "${pid}" 2>/dev/null || true; wait "${pid}" 2>/dev/null || true; fi
  done
}
trap cleanup EXIT INT TERM

echo "==> Streams (${LOGD}/streams.log)"
run_java local.kip1034.dlq.news.freeform.NewsFreeformStreamsApplication >"${LOGD}/streams.log" 2>&1 &
STREAMS_PID=$!
sleep 14

echo "==> App DLQ repair"
run_java local.kip1034.dlq.news.freeform.NewsFreeformAppDlqRepairMain >"${LOGD}/app-repair.log" 2>&1 &
APP_REP_PID=$!
sleep 4

echo "==> KIP-1034 DLQ repair"
run_java local.kip1034.dlq.news.freeform.NewsFreeformKip1034RepairMain >"${LOGD}/kip-repair.log" 2>&1 &
KIP_REP_PID=$!
sleep 4

echo "==> Producer"
run_java local.kip1034.dlq.news.freeform.NewsFreeformProducerMain >"${LOGD}/producer.log" 2>&1

echo "==> Wait 70s for windows + repair"
sleep 70

cleanup
STREAMS_PID=""
APP_REP_PID=""
KIP_REP_PID=""
trap - EXIT INT TERM

echo ""
echo "---- streams (grep) ----"
grep -E 'RUNNING|free-form|ERROR|Exception' "${LOGD}/streams.log" 2>/dev/null | tail -20 || true

echo ""
echo "---- KIP-1034 repair ----"
grep -E 'KIP-1034|Repaired|Could not|structure' "${LOGD}/kip-repair.log" 2>/dev/null | tail -15 || true

echo ""
echo "---- App DLQ repair ----"
grep -E 'Repaired|Could not' "${LOGD}/app-repair.log" 2>/dev/null | tail -15 || true

dump() {
  local title="$1" topic="$2" max="${3:-30}"
  echo ""
  echo "======== ${title} (${topic}) ========"
  docker exec news-freeform-kafka-broker /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic "${topic}" --from-beginning --max-messages "${max}" --timeout-ms 25000 \
    2>/dev/null || true
}

dump "RAW" "demo-news-ff-raw" 10
dump "STREAMS_DLQ (KIP-1034)" "demo-news-ff-streams-dlq" 5
dump "APP_DLQ" "demo-news-ff-app-dlq" 5
dump "REPAIRED" "demo-news-ff-repaired" 15
dump "WINDOWED" "demo-news-ff-by-category-minute" 25

echo ""
echo "Done. Logs: ${LOGD}/"
if [ "${OLLAMA_STARTED_BY_SCRIPT:-0}" -eq 1 ]; then
  echo "Note: this script started 'ollama serve' (log: /tmp/ollama-serve-news-freeform-demo.log); process left running."
fi
