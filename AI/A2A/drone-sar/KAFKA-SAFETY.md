# Drone SAR — Phase 2.5 Kafka safety + Tier 2 replay copilot

Phase **2.5** adds a thin **Kafka slice** on top of Phase 2 A2A: deterministic violation detection in the mission client, events on **`sar.telemetry.events`**, and local **Ollama** (or template) assessments on **`sar.violation-assessments`**.

Phase **2.5 Tier 2** embeds the same narrator + safety-analyst text in **`replay.json`** so the HTML replay GUI can show tick-scrubbed **copilot panels** alongside the map.

**Parent:** [DRONE-SAR-PHASE2-A2A.md](../../../docs/scenarios/DRONE-SAR-PHASE2-A2A.md) · [DRONE-SAR-A2A-DESIGN.md](../../../docs/scenarios/DRONE-SAR-A2A-DESIGN.md) §7 · **Sample artifacts:** [viz/out/sar-kafka-safety/README.md](../viz/out/sar-kafka-safety/README.md)

---

## Architecture

```text
Flock coordinator (A2A SSE :8083)
        │
Mission client ──detect safety events──► sar.telemetry.events (Kafka)
        │        │
        │        └── in-process SarSafetyAnalystService ──► copilot in replay.json
        │                                         │
        │                                         ▼
        │                              Safety analyst consumer (JAR)
        │                              (Ollama llama3 or templates)
        │                                         │
        └────────────────────────────────────────► sar.violation-assessments
```

| Component | Path | Role |
|-----------|------|------|
| Event detector | `sar-core` `SarSignificantEventDetector` | `GEOFENCE_VIOLATION`, `LOW_BATTERY`, `EMERGENCY_LAND`, `MISSION_SAFETY_SUMMARY` |
| Kafka producer | `sar-core` `SarKafkaEventPublisher` | Publishes when `SAR_KAFKA_BOOTSTRAP` set |
| Safety analyst (Kafka) | `a2a/safety-analyst/` | Consumes events topic, writes assessments topic |
| Copilot recorder | `sar-core` `SarCopilotRecorder` | Collects narrations + assessments for replay |
| In-process analyst | `sar-core` `SarSafetyAnalystService` | Same template/Ollama logic as Kafka consumer |
| Replay GUI | `viz/replay.html` | Separate **Mission narrator** and **Safety analyst** panels |
| Demo mission (1 target) | `missions/test-mission-kafka-safety.json` | `demoViolations: true` — guaranteed multi-type violations |
| Demo mission (2 targets) | `missions/test-mission-kafka-safety-2target.json` | t1 + t2, drone transfer on find, pathing stress test |
| Sim pathing | `sim/.../SimDrone.java` | Skips unreachable waypoints when no-fly detours fail — see `SimDronePathingTest` |

**Principle:** Java detects facts; Kafka carries them for downstream systems; LLM (or templates) explains and recommends actions — **not** in the flock control loop.

---

## Relation to Part 8 (Atomic Timekeeper)

Phase 2.5 uses the **same design rule** as Part 8 — Kafka is **behind** A2A, not on the wire — but it is **not** a copy of the Part 8 bridge pipeline.

| | **Part 8** (`part8/`, `bridge/`) | **Drone SAR Phase 2.5** (this doc) |
|---|--------------------------------|-------------------------------------|
| **Shared rule** | Kafka records facts; does not replace `SendMessage` or Agent Cards | Same |
| **A2A control plane** | HTTP JSON-RPC + **push webhooks** | HTTP JSON-RPC + **SSE** (flock coordinator `:8083`) |
| **What triggers Kafka** | Agent POST → `PushNotificationReceiver` → produce | Mission client **detects** safety facts from telemetry / SSE |
| **Topics** | `a2a.task.events` | `sar.telemetry.events`, `sar.violation-assessments` |
| **Producer** | `TaskEventPublisher` in webhook receiver | `SarKafkaEventPublisher` in mission client |
| **Push notifications** | Yes — spec `CreateTaskPushNotificationConfig` + webhook | **No** — `pushNotifications: false` on SAR agents |
| **Kafka consumers** | Audit / replay (`TaskEventAuditConsumer`) | Safety analyst JAR (not an A2A client) |
| **Later phases** | — | Phase **2c/2d** add per-drone A2A workers and AI coordinator; **no** Part 8 webhook wiring |

**Do not conflate them in talks:** Part 8 teaches **task lifecycle mirrored from A2A push**. Drone SAR teaches **domain safety events** published beside an SSE mission stream. Both are valid “Kafka behind the protocol” — different hook points and schemas.

**Three-approach overview:** [bindings-and-compliance.md — Three approaches](../../../docs/kafka/bindings-and-compliance.md#three-approaches-to-a2a--kafka-repo-overview) · Part 8 runbook: [part8/README.md](../../../part8/README.md) · [08-kafka-task-events.md](../../../docs/examples/08-kafka-task-events.md)

---

## Safety event types

| Event | Trigger |
|-------|---------|
| `GEOFENCE_VIOLATION` | Telemetry `geofenceViolation: true` (no-fly escape failed) |
| `LOW_BATTERY` | `mode=search` and battery ≤ RTB threshold (once per drone) |
| `EMERGENCY_LAND` | `mode=emergency_land` (once per drone) |
| `MISSION_SAFETY_SUMMARY` | Post-mission summary JSON |

---

## Quick start

**Prerequisites:** Java 17+, Maven, Kafka at `localhost:9092`. Optional: Ollama (`SAR_OLLAMA_ENABLED=true`).

### Single target (default)

```bash
scenarios/drone-sar/a2a/scripts/run-phase2-kafka-safety-demo.sh
```

Default viz output: `scenarios/drone-sar/viz/out/sar-kafka-safety/`.

### Two targets + narrator + copilot

Requires **mission narrator** on `:8084` in addition to flock coordinator (`:8083`) and Kafka:

```bash
# Terminal A — after building (see demo script): flock coordinator :8083 + safety analyst consumer
# Terminal B — mission narrator
cd scenarios/drone-sar/a2a/mission-narrator
java -jar target/quarkus-app/quarkus-run.jar   # :8084

# Terminal C — mission client via demo script env
export SAR_MISSION_PATH=scenarios/drone-sar/missions/test-mission-kafka-safety-2target.json
export SAR_VIZ_OUT=scenarios/drone-sar/viz/out/sar-kafka-safety-2target
export SAR_NARRATOR_URL=http://localhost:8084
export SAR_OLLAMA_ENABLED=false
scenarios/drone-sar/a2a/scripts/run-phase2-kafka-safety-demo.sh
```

Sample artifacts: [viz/out/sar-kafka-safety-2target/README.md](../viz/out/sar-kafka-safety-2target/README.md).

```bash
cd scenarios/drone-sar/viz/out/sar-kafka-safety-2target
python3 -m http.server 8768
# http://localhost:8768/replay.html
```

The demo script:

1. Installs `sim/` + `sar-core/`, runs unit tests
2. Starts Kafka safety analyst consumer (unique consumer group per run)
3. Starts flock coordinator on `:8083`
4. Runs mission client with Kafka + copilot enabled
5. Validates `replay.json` contains `copilot.violations`

---

## Tier 2 — replay copilot panels

When **`SAR_COPILOT`** is enabled (default), the mission client writes a `copilot` block into `replay.json`:

```json
{
  "copilot": {
    "narrations": [ { "tick": 20, "type": "TARGET_FOUND", "text": "...", "source": "narrator" } ],
    "violations": [ { "tick": 15, "severity": "HIGH", "ruleId": "...", "summary": "...", "recommendedAction": "FREEZE_SECTOR", "sourceEventType": "GEOFENCE_VIOLATION", "droneId": "d-03" } ]
  }
}
```

### UI layout (`replay.html`)

Three **separate** sidebar sections below Drones — not one combined panel:

| Section | Data source | When visible |
|---------|-------------|--------------|
| **Events** | `summary.events` | Always (sim milestones) |
| **Mission narrator** | `copilot.narrations` | When `SAR_NARRATOR_URL` was set during the run |
| **Safety analyst** | `copilot.violations` | When copilot enabled (default) |

All three filter by current tick (`tick <= frame.tick`). Safety cards show severity badges and recommended actions.

Set `SAR_COPILOT=false` to omit the `copilot` block entirely.

### Data path

```text
Telemetry SSE artifact
  → SarSignificantEventDetector
  → (optional) SarMissionNarratorClient → copilot.narrations
  → SarSafetyAnalystService.assessEvent() → copilot.violations
  → SarCopilotRecorder.toReplayCopilot()
  → SarVizExporter → ReplayExporter.build(..., copilot)
  → replay.json
```

In-process assessments use the **same** `SarSafetyAnalystService` as the Kafka consumer — copilot works even without Kafka.

---

## Artifacts

| Output | Path (default) | Notes |
|--------|----------------|-------|
| `replay.json` | `viz/out/sar-kafka-safety/` | Frames + summary + **copilot** |
| `replay.html` | same dir | Copied from `viz/replay.html` at export time |
| `snapshot.png` | same dir | 900×900 end-state map (no copilot text) |
| `snapshot.svg` | same dir | Vector end-state map |

See [viz/out/sar-kafka-safety/README.md](../viz/out/sar-kafka-safety/README.md) for a captured single-target run. Two-target + narrator sample: [viz/out/sar-kafka-safety-2target/README.md](../viz/out/sar-kafka-safety-2target/README.md).

---

## Sample run output

Mission **`sar-kafka-safety-demo`** (template mode, `SAR_OLLAMA_ENABLED=false`):

**Mission client (excerpt):**

```text
copilot=enabled replay panels=narrator,safety-analyst
kafka=published LOW_BATTERY tick=1 drone=d-03
kafka=published EMERGENCY_LAND tick=12 drone=d-03
kafka=published GEOFENCE_VIOLATION tick=15 drone=d-03
kafka=published GEOFENCE_VIOLATION tick=16 drone=d-02
kafka=published GEOFENCE_VIOLATION tick=17 drone=d-01
kafka=published MISSION_SAFETY_SUMMARY
copilot=safety MEDIUM tick=1 Drone d-03 reached RTB battery threshold...
copilot=safety HIGH tick=15 Drone d-03 breached a no-fly zone...
...
Note: successCriteriaMet=false is expected for demo violation missions.
```

**Summary JSON:**

```json
{
  "ticksRun": 76,
  "searchedCellCount": 80,
  "allTargetsFound": true,
  "allRtbLanded": false,
  "anyEmergencyLand": true,
  "anyGeofenceViolation": true,
  "successCriteriaMet": false
}
```

**Kafka consumer assessments (excerpt):**

```text
assessment #1 severity=MEDIUM rule=airspace-v1.battery.rtb
  Drone d-03 reached RTB battery threshold while still searching at tick 1.
  action=ORDER_RTB
assessment #3 severity=HIGH rule=airspace-v1.geofence
  Drone d-03 breached a no-fly zone at tick 15.
  action=FREEZE_SECTOR
```

---

## Environment

| Variable | Default | Purpose |
|----------|---------|---------|
| `SAR_KAFKA_BOOTSTRAP` | unset (off) | Enables Kafka producer in mission client |
| `SAR_KAFKA_EVENTS_TOPIC` | `sar.telemetry.events` | Ingress topic |
| `SAR_KAFKA_ASSESSMENTS_TOPIC` | `sar.violation-assessments` | Egress topic |
| `SAR_OLLAMA_URL` | `http://localhost:11434` | Analyst LLM |
| `SAR_OLLAMA_MODEL` | `llama3:latest` | Model name |
| `SAR_OLLAMA_ENABLED` | `true` | `false` → template assessments only |
| `SAR_SAFETY_MAX_ASSESSMENTS` | `10` | Analyst stop condition |
| `SAR_SAFETY_IDLE_MS` | `15000` | Idle timeout |
| `SAR_COPILOT` | on | Embed copilot in `replay.json`; `false`/`off` to disable |
| `SAR_NARRATOR_URL` | unset | Enables narrator agent + `copilot.narrations` |
| `SAR_VIZ_OUT` | `viz/out/sar-kafka-safety` | Viz output directory |
| `SAR_MISSION_PATH` | `test-mission-kafka-safety.json` | Demo mission path (`test-mission-kafka-safety-2target.json` for 2-target run) |

---

## Manual run

```bash
# Terminal 1 — analyst
export SAR_KAFKA_BOOTSTRAP=localhost:9092
export SAR_OLLAMA_ENABLED=false
java -jar scenarios/drone-sar/a2a/safety-analyst/target/drone-sar-safety-analyst-0.1.0.jar

# Terminal 2 — flock coordinator (see run-phase2-demo.sh)

# Terminal 3 — mission client
export SAR_A2A_AGENT_URL=http://localhost:8083
export SAR_KAFKA_BOOTSTRAP=localhost:9092
export SAR_MISSION_PATH=scenarios/drone-sar/missions/test-mission-kafka-safety.json
export SAR_VIZ_OUT=scenarios/drone-sar/viz/out/sar-kafka-safety
cd scenarios/drone-sar/a2a/mission-client && mvn -q compile exec:java
```

---

## Tests

```bash
cd scenarios/drone-sar/a2a/sar-core && mvn test
cd ../../sim && mvn test   # includes SimDronePathingTest (2-target no-freeze guard)
```

| Test | Covers |
|------|--------|
| `SarCopilotRecorderTest` | Narration + violation collection |
| `SarVizExporterTest` | `copilot` block written to `replay.json` |
| `SarSignificantEventDetectorTest` | Safety event detection + analyst templates |
| `SimDronePathingTest` | 2-target mission — no drone stuck 10+ ticks in SEARCH |

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `replay.json` has no `copilot` block | Use **mission client** or demo script — not Phase 1 `MissionRunner --viz-out`. Rebuild: `cd a2a/mission-client && mvn compile`; ensure `SAR_COPILOT` not disabled |
| Narrator / Safety panels empty but map works | Same as above — `MissionRunner` overwrites replay without copilot. Re-run via `run-phase2-kafka-safety-demo.sh` with `SAR_NARRATOR_URL` for narrations |
| Drones freeze in search (2-target replay) | Update `sim/` — `SimDrone.advanceAlongPath` skips unreachable no-fly legs. Run `SimDronePathingTest` |
| Replay page shows no Safety analyst panel | Serve the **output dir** that contains the matching `replay.json`; hard-refresh browser |
| Mission client exit code 1 on violation demo | Fixed for `demoViolations: true` missions — update `SarMissionClient` if on older code |
| Duplicate Kafka assessments | Use unique `SAR_KAFKA_CONSUMER_GROUP` or recreate topics (demo script uses timestamp suffix) |
| `snapshot.png` has no LLM text | Expected — copilot text is in `replay.html` only |

---

## Next

Phase 3 — safety agent, mid-mission patches. Phase 4 — full hybrid HTTP edge + Kafka fleet backend — [DRONE-SAR-A2A-DESIGN.md](../../../docs/scenarios/DRONE-SAR-A2A-DESIGN.md) §10 Phase 4.
