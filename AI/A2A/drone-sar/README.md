# Drone SAR — Phase 2 A2A

Phase 2 wraps the Phase 1 grid sim in the **official A2A JSON-RPC protocol** via `a2a-java` SDK (same stack as `bridge/`).

**Full design + streaming notes:** [docs/scenarios/DRONE-SAR-PHASE2-A2A.md](../../../docs/scenarios/DRONE-SAR-PHASE2-A2A.md)  
**Architecture diagram:** [docs/diagrams/drone-sar-phase2-a2a-narrator.png](../../../docs/diagrams/drone-sar-phase2-a2a-narrator.png)

## Layout

```text
a2a/
  sar-core/            Shared mission parsing + SimulationEngine runner + viz export + narrator types
  flock-coordinator/   Quarkus A2A agent (skill sar:flock-coordinator) :8083
  mission-narrator/    Quarkus A2A agent (skill sar:mission-narrator) :8084 + Ollama
  mission-client/      A2A SDK client (JSON-RPC + SSE streaming)
  safety-analyst/      Kafka consumer — Ollama/template violation assessments
  scripts/run-phase2-demo.sh
  scripts/run-phase2-narrator-demo.sh
  scripts/run-phase2-kafka-safety-demo.sh
  KAFKA-SAFETY.md      Phase 2.5 — Kafka violation pipeline + Tier 2 replay copilot
  DESIGN.md            Short summary (links to docs/)
```

## Quick start

```bash
./scripts/run-phase2-demo.sh
```

### With local LLM narrator (Ollama)

Requires [Ollama](https://ollama.com) with `llama3:latest` (or set `SAR_OLLAMA_MODEL`).

```bash
./scripts/run-phase2-narrator-demo.sh
```

Starts **two A2A agents** — flock coordinator `:8083` and mission narrator `:8084`. The client forwards significant events; the narrator calls Ollama and returns `sar-narrative` artifacts.

Set `SAR_NARRATOR_URL=none` to disable narrator calls. Set `SAR_OLLAMA_ENABLED=false` on the narrator agent for template-only briefings.

## Phase 2.5 — Kafka safety + replay copilot

Requires Kafka at `localhost:9092`. Embeds safety assessments in `replay.json` for the HTML replay GUI.

```bash
./scripts/run-phase2-kafka-safety-demo.sh
```

Output: `../viz/out/sar-kafka-safety/` (`replay.json` with `copilot` block, `snapshot.png`, `replay.html`).

**Two-target demo** (t1 + t2, drone transfer, pathing stress):

```bash
export SAR_MISSION_PATH=../../missions/test-mission-kafka-safety-2target.json
export SAR_VIZ_OUT=../../viz/out/sar-kafka-safety-2target
export SAR_NARRATOR_URL=http://localhost:8084   # start mission-narrator on :8084 first
./scripts/run-phase2-kafka-safety-demo.sh
```

Full write-up: [KAFKA-SAFETY.md](KAFKA-SAFETY.md) · [single-target sample](../viz/out/sar-kafka-safety/README.md) · [2-target sample](../viz/out/sar-kafka-safety-2target/README.md).

```bash
cd ../viz/out/sar-kafka-safety-2target && python3 -m http.server 8768
# http://localhost:8768/replay.html — narrator + safety panels; ticks 10–17, 59
```

Publishes violation events to `sar.telemetry.events`; safety analyst writes assessments to `sar.violation-assessments`. Default mission: `missions/test-mission-kafka-safety.json` (`demoViolations: true`). **Copilot replay requires mission client** — Phase 1 `MissionRunner --viz-out` does not write `copilot` data.

**Not Part 8:** this path does not use `PushNotificationReceiver` or `a2a.task.events`; see [KAFKA-SAFETY.md — Relation to Part 8](KAFKA-SAFETY.md#relation-to-part-8-atomic-timekeeper).

## Protocol

| Component | Transport |
|-----------|-----------|
| Flock coordinator | `GET http://localhost:8083/.well-known/agent-card.json` |
| Mission narrator | `GET http://localhost:8084/.well-known/agent-card.json` |
| Agent server | `a2a-java-sdk-reference-jsonrpc` (**JSON-RPC**) |
| Mission client | `JSONRPCTransport` + **SSE** (`streaming=true`, `polling=false`) |

## Mission message

```text
mission:search-rescue
/path/to/mission.json
maxTicks=500
realtime=false
```

## Narrator message

```text
narrate:event
{"type":"TARGET_FOUND","tick":20,"missionId":"sar-multi-001","targetId":"t1","droneId":"d-02"}
```

## Visuals

Default output: `../viz/out/sar-a2a/` (`replay.json`, `snapshot.png`, `replay.html`).

```bash
cd ../viz/out/sar-a2a && python3 -m http.server 8768
# http://localhost:8768/replay.html
```

Set `SAR_VIZ_OUT=none` to disable. See [§8 Visualization](../../../docs/scenarios/DRONE-SAR-PHASE2-A2A.md#8-visualization).

**Phase 2.5 copilot replay:** separate sidebar panels for **Mission narrator** and **Safety analyst** (tick-scrubbed). Outputs: `../viz/out/sar-kafka-safety/` (1 target), `../viz/out/sar-kafka-safety-2target/` (2 targets) — [KAFKA-SAFETY.md](KAFKA-SAFETY.md).

## Environment

| Variable | Default |
|----------|---------|
| `SAR_A2A_AGENT_URL` | `http://localhost:8083` |
| `SAR_NARRATOR_URL` | unset (off); narrator demo sets `http://localhost:8084` |
| `SAR_NARRATOR_PORT` | `8084` |
| `SAR_OLLAMA_URL` | `http://localhost:11434` (narrator agent) |
| `SAR_OLLAMA_MODEL` | `llama3:latest` |
| `SAR_OLLAMA_ENABLED` | `true` |
| `SAR_MISSION_PATH` | `../../missions/test-mission-fast.json` |
| `SAR_MAX_TICKS` | `500` |
| `SAR_VIZ_OUT` | `../../viz/out/sar-a2a` |
| `SAR_COPILOT` | on — embed narrator + safety text in `replay.json` |
| `SAR_KAFKA_BOOTSTRAP` | unset (off) — see [KAFKA-SAFETY.md](KAFKA-SAFETY.md) |

## Tests

```bash
cd sar-core && mvn test
cd ../mission-client && mvn test
```
