# Drone SAR — Phase 1 simulation (+ Phase 2 A2A)

Grid-based search-and-rescue simulation with in-process flock coordination. **Phase 1** is CLI-only; **Phase 2** adds A2A JSON-RPC + SSE streaming.

| Phase | Doc | Code |
|-------|-----|------|
| **1** | [DRONE-SAR-A2A-DESIGN.md §10](docs/DRONE-SAR-A2A-DESIGN.md#phase-1--grid-sim--flock-logic-no-a2a) | `sim/` |
| **2** | [DRONE-SAR-PHASE2-A2A.md](docs/DRONE-SAR-PHASE2-A2A.md) | `a2a/` |
| **2b** | [phase2b/DESIGN.md](phase2b/DESIGN.md) | `phase2b/` — one A2A agent per drone |
| **2c** | [phase2c/DESIGN.md](phase2c/DESIGN.md) | `phase2c/` — DroneTask lifecycle, patches, safety peer |
| **2d** | [phase2d/DESIGN.md](phase2d/DESIGN.md) | `phase2d/` — NL patches, detection fusion, operator confirm (Step 2) |

## Layout

| Path | Purpose |
|------|---------|
| `sim/` | Java 17 Maven module — grid world, flock library, CLI runner (Phase 1) |
| `a2a/` | Phase 2 — flock coordinator agent, mission client, streaming + viz |
| `phase2b/` | Phase 2b — distributed coordinator + N drone-worker agents |
| `phase2c/` | Phase 2c — autonomous DroneTasks, mission patches, safety agent |
| `phase2d/` | Phase 2d — NL mission patches, low-confidence detection fusion, input-required confirm |
| `viz/replay.html` | HTML canvas replay (load `replay.json`) |
| `viz/out/` | Generated artifacts (`sar-test-fast`, `sar-a2a`, `sar-kafka-safety`, `sar-kafka-safety-2target`, …) |
| `docs/` | Design notes and diagrams (`DRONE-SAR-A2A-DESIGN.md`, `DRONE-SAR-PHASE2-A2A.md`) |
| `missions/` | Mission JSON files |
| `DESIGN-CHECK.md` | Design §10 Phase 1 mapping |
| `a2a/DESIGN.md` | Phase 2 short summary |

## Prerequisites

- Java 17+
- Maven 3.9+

## Build and test

```bash
cd scenarios/drone-sar/sim
mvn test
```

**Pathing:** `SimDronePathingTest` guards against drones freezing in SEARCH when no-fly detours are exhausted (`test-mission-kafka-safety-2target.json`). Fix lives in `sim/.../SimDrone.java` — skip unreachable waypoints after failed detours or 3 blocked ticks.

## Run a mission

From `scenarios/drone-sar/sim`:

```bash
# Quick demo (~500 ticks, sampled telemetry)
mvn -q exec:java -Dexec.args="../missions/test-mission-fast.json 500 --sample"

# Full sample mission (needs ~800+ ticks for RTB)
mvn -q exec:java -Dexec.args="../missions/sample-mission.json 900 --sample"
```

Exit code `0` when Phase 1 success criteria are met: searched cells > 0, all drones RTB and land, no emergency land, no geofence violations.

## Visualization

Generate static snapshot + replay bundle:

```bash
cd scenarios/drone-sar/sim
mvn -q exec:java -Dexec.args="../missions/test-mission-fast.json 500 --viz-out ../viz/out/sar-test-fast"
```

| Output | Description |
|--------|-------------|
| `snapshot.svg` | Search heatmap, no-fly zones, drone paths (end state) |
| `snapshot.png` | Same view rasterized (900×900) |
| `replay.json` | Full tick-by-tick positions for replay |
| `replay.html` | Canvas player (copied into output dir) |

**Static snapshot:** open `snapshot.svg` or `snapshot.png` in any viewer.

**Animated replay:**

```bash
cd scenarios/drone-sar/viz/out/sar-multi   # must match your --viz-out folder
python3 -m http.server 8765
# open http://localhost:8765/replay.html
```

The replay page loads `replay.json` from **the same directory** as the server. If you serve `sar-test-fast` you get one target; serve `sar-multi` for the multi-target run. The status bar shows mission id and target count (e.g. `2 target(s)`).

**Phase 2.5 copilot replay** (`sar-kafka-safety`, `sar-kafka-safety-2target`): sidebar shows separate **Mission narrator** and **Safety analyst** panels (tick-scrubbed LLM/template text). Copilot data is written only by the **mission client** — Phase 1 `MissionRunner --viz-out` produces map/frames only (no `copilot` block). See [a2a/KAFKA-SAFETY.md](a2a/KAFKA-SAFETY.md), [viz/out/sar-kafka-safety/README.md](viz/out/sar-kafka-safety/README.md), [viz/out/sar-kafka-safety-2target/README.md](viz/out/sar-kafka-safety-2target/README.md).

Controls: Play/Pause, tick scrubber, speed (0.25×–4×). Heatmap builds tick-by-tick; drone trails animate over the search area.


Each tick emits one JSON line per drone (design doc §5):

```json
{
  "droneId" : "d-01",
  "tick" : 10,
  "position" : { "x" : 44.0, "y" : 40.0, "altAglM" : 20.0 },
  "headingDeg" : 0.0,
  "batteryPct" : 96.5,
  "mode" : "search",
  "sectorId" : "lane-1"
}
```

When a drone detects its assigned target, telemetry includes a `detection` block with `targetId`. **All drones assigned to that target RTB immediately**; in multi-target missions, other drones keep searching until their target is found.

## Sample output (truncated)

```
=== drone-sar Phase 1 sim ===
missionId=sar-test-fast drones=2 maxTicks=500
--- telemetry (JSON lines, ~1 Hz) ---
{
  "droneId" : "d-01",
  "tick" : 30,
  "position" : { "x" : 50.0, "y" : 52.0, "altAglM" : 30.0 },
  "headingDeg" : 0.0,
  "batteryPct" : 89.5,
  "mode" : "search",
  "sectorId" : "lane-1",
  "detection" : {
    "targetType" : "person",
    "confidence" : 0.91,
    "bboxNorm" : [ 0.2, 0.3, 0.1, 0.2 ]
  },
  "photoRef" : "file://sim-frames/sar-test-fast/d-01/t30.jpg"
}
--- summary ---
ticksRun=198
searchedCells=79
allRtbLanded=true
anyEmergencyLand=false
anyGeofenceViolation=false
targetDetected=true
successCriteriaMet=true
--- final drone states ---
d-01 mode=LANDED batteryPct=31.1 pos=(10.0,10.0)
d-02 mode=LANDED batteryPct=29.1 pos=(10.0,10.0)
```

## Phase 2 — A2A + streaming

```bash
scenarios/drone-sar/a2a/scripts/run-phase2-demo.sh
```

Writes viz to `viz/out/sar-a2a/`. See [DRONE-SAR-PHASE2-A2A.md](docs/DRONE-SAR-PHASE2-A2A.md) for architecture, SSE artifact flow, Agent Card, and env vars.

```bash
cd scenarios/drone-sar/viz/out/sar-a2a
python3 -m http.server 8768
# http://localhost:8768/replay.html
```

## Phase 2.5 — Kafka safety + replay copilot

Deterministic violation detection → Kafka topics → Ollama/template assessments. **Tier 2** embeds safety (and optional narrator) text in `replay.json` for tick-scrubbed HTML panels.

**Single target (default):**

```bash
scenarios/drone-sar/a2a/scripts/run-phase2-kafka-safety-demo.sh
```

**Two targets + narrator + copilot** (Kafka + flock coordinator + mission narrator on `:8084`):

```bash
export SAR_MISSION_PATH=scenarios/drone-sar/missions/test-mission-kafka-safety-2target.json
export SAR_VIZ_OUT=scenarios/drone-sar/viz/out/sar-kafka-safety-2target
export SAR_NARRATOR_URL=http://localhost:8084
# Start mission-narrator agent before client — see a2a/KAFKA-SAFETY.md § Quick start (2-target)
scenarios/drone-sar/a2a/scripts/run-phase2-kafka-safety-demo.sh
```

| Doc | Purpose |
|-----|---------|
| [a2a/KAFKA-SAFETY.md](a2a/KAFKA-SAFETY.md) | Architecture, env vars, 2-target runbook, troubleshooting |
| [viz/out/sar-kafka-safety/README.md](viz/out/sar-kafka-safety/README.md) | Single-target sample run |
| [viz/out/sar-kafka-safety-2target/README.md](viz/out/sar-kafka-safety-2target/README.md) | Two-target sample run (narrator + violations) |

```bash
cd scenarios/drone-sar/viz/out/sar-kafka-safety-2target
python3 -m http.server 8768
# replay.html — narrator + safety panels; scrub ticks 10–17, 16, 59
# snapshot.png — end-state map (no copilot text)
```

**Do not** regenerate copilot replay with `MissionRunner --viz-out` — it overwrites `replay.json` without `copilot.narrations` / `copilot.violations`.

## Next phases

| Phase | Scope |
|-------|--------|
| **2** | ✅ A2A flock coordinator + mission client — [DRONE-SAR-PHASE2-A2A.md](docs/DRONE-SAR-PHASE2-A2A.md) |
| **2.5** | ✅ Kafka safety analyst (violations + Ollama) + Tier 2 replay copilot — [KAFKA-SAFETY.md](a2a/KAFKA-SAFETY.md) · [sample run artifacts](viz/out/sar-kafka-safety/README.md) |
| **3** | Safety agent, mid-mission patches |
| **4** | Kafka hybrid backend + fan-out |

See [DRONE-SAR-A2A-DESIGN.md](docs/DRONE-SAR-A2A-DESIGN.md).
