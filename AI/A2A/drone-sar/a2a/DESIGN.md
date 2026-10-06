# Phase 2 — A2A flock coordinator (summary)

**Full documentation:** [DRONE-SAR-PHASE2-A2A.md](../../../docs/scenarios/DRONE-SAR-PHASE2-A2A.md)  
**Parent design:** [DRONE-SAR-A2A-DESIGN.md](../../../docs/scenarios/DRONE-SAR-A2A-DESIGN.md) §10 Phase 2  
**Runbook:** [README.md](README.md)

## Status

Implemented in `scenarios/drone-sar/a2a/` — flock coordinator agent, mission client, shared `sar-core`, viz export.

## Architecture (one line)

Mission client → **A2A JSON-RPC + SSE** → flock coordinator agent → `SimulationEngine` (in-process) → telemetry artifacts → post-mission viz.

## Streaming (short)

- Agent Card: `streaming: true`, protocol **JSONRPC**, port **8083**
- Client: `JSONRPCTransport`, `streaming=true`, `polling=false`
- Agent emits **`sar-telemetry`** (append) + **`sar-mission-summary`** via `AgentEmitter`
- Client buffers SSE `TaskArtifactUpdateEvent` JSON; no live browser map yet

See [§6 A2A streaming](../../../docs/scenarios/DRONE-SAR-PHASE2-A2A.md#6-a2a-streaming) for event flow, artifact shapes, and what stays off the wire.

## Success criteria

- [x] Client starts mission via A2A `SendMessage`
- [x] Agent streams telemetry artifacts during sim
- [x] Task completes with mission summary JSON
- [x] Official JSON-RPC A2A protocol on agent + client
- [x] Phase 1-equivalent viz (`replay.json`, `snapshot.png`, `replay.html`)
- [x] Tier 2 replay copilot (narrator + safety analyst panels in `replay.json`)
- [x] Kafka safety events + in-process violation assessments (Phase 2.5)
- [x] Unit tests for core, viz export, client parsing, copilot recorder

## Mission narrator agent (Option A)

Second A2A agent **`sar:mission-narrator`** on port **8084** — mission copilot, not pilot (design §18).

**Diagrams:** [component view](../../../docs/diagrams/drone-sar-phase2-a2a-narrator.png) · [runtime sequence](../../../docs/diagrams/drone-sar-phase2-narrator-sequence.png) · [Phase 2 doc](../../../docs/scenarios/DRONE-SAR-PHASE2-A2A.md#3-architecture)

```text
Mission client ──SSE──► flock coordinator (:8083)
       │
       └── narrate:event ──► mission narrator (:8084) ──► Ollama (llama3)
                                    └── sar-narrative artifact (text)
```

- Significant events detected client-side: `MISSION_START`, `TARGET_FOUND`, `DRONE_TRANSFER`
- Post-mission `narrate:summary` for after-action briefing
- Template fallback when `SAR_OLLAMA_ENABLED=false` or Ollama unreachable

```bash
./scripts/run-phase2-narrator-demo.sh
```

## Phase 2.5 — Kafka safety + replay copilot

Kafka violation pipeline + Tier 2 embed of narrator/safety text in `replay.json`. See [KAFKA-SAFETY.md](KAFKA-SAFETY.md).

```bash
./scripts/run-phase2-kafka-safety-demo.sh
# viz: ../viz/out/sar-kafka-safety/ — replay.html has Safety analyst panel
# 2-target: SAR_MISSION_PATH=../../missions/test-mission-kafka-safety-2target.json SAR_VIZ_OUT=../../viz/out/sar-kafka-safety-2target
```

Sim pathing fix (2026-08-27): drones skip unreachable no-fly legs instead of freezing — `SimDronePathingTest` in `sim/`.
