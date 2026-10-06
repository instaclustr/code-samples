# Kafka safety demo — 2-target viz artifact bundle

Generated from mission `missions/test-mission-kafka-safety-2target.json` via the **Phase 2 mission client** (not Phase 1 `MissionRunner`). Includes **Tier 2 copilot** — mission narrator + safety analyst panels in `replay.html`.

**Regenerate (Kafka + narrator + copilot):**

```bash
# From repo root — requires Kafka :9092, builds agents, starts narrator :8084
export SAR_MISSION_PATH=scenarios/drone-sar/missions/test-mission-kafka-safety-2target.json
export SAR_VIZ_OUT=scenarios/drone-sar/viz/out/sar-kafka-safety-2target
export SAR_NARRATOR_URL=http://localhost:8084

# Start narrator separately if not using the all-in-one flow below:
# scenarios/drone-sar/a2a/scripts/run-phase2-narrator-demo.sh  # coordinator + narrator only

scenarios/drone-sar/a2a/scripts/run-phase2-kafka-safety-demo.sh
# Then start mission narrator on :8084 before mission client, or use the manual steps in KAFKA-SAFETY.md
```

**Important:** Phase 1 `MissionRunner --viz-out` writes frames only — **no `copilot` block**. Always use `mission-client` (or the demo scripts) for narrator/safety replay panels.

---

## Files

| File | Description |
|------|-------------|
| `replay.json` | Tick replay + summary + **`copilot`** (11 narrations, 6 violations in sample run) |
| `replay.html` | Canvas player — **Events**, **Mission narrator**, **Safety analyst** panels |
| `snapshot.png` | End-state map (900×900) — no copilot text |
| `snapshot.svg` | Vector end-state map |

---

## View replay

```bash
cd scenarios/drone-sar/viz/out/sar-kafka-safety-2target
python3 -m http.server 8768
# http://localhost:8768/replay.html
```

Status bar shows `· copilot` when `replay.json` includes the copilot block. Scrub to **ticks 1, 10–17, 16, 59** for narrator + safety cards.

---

## Sample run (2026-08-27, template mode)

Mission **`sar-kafka-safety-2target-demo`**: targets **t1** (upper-left) + **t2** (lower-right); d-01/d-02 → t1, d-03 → t2 (45% battery for violation demo). Two no-fly zones stress detour pathing (fixed in sim — drones skip unreachable legs instead of freezing).

### Mission summary

```json
{
  "ticksRun": 74,
  "searchedCells": 117,
  "allTargetsFound": true,
  "allRtbLanded": false,
  "anyEmergencyLand": true,
  "anyGeofenceViolation": true,
  "fleetRtbTick": 59,
  "foundTargetIds": ["t1", "t2"],
  "successCriteriaMet": false
}
```

`successCriteriaMet=false` is **expected** (`demoViolations: true`). Mission client exits **0** for demo violation missions.

### Highlights

| Tick | Event |
|------|-------|
| 10 | d-03 `LOW_BATTERY` |
| 12 | d-03 `EMERGENCY_LAND` (injected) |
| 15–17 | Geofence violations (d-03, d-02, d-01) |
| 16 | t1 found; d-01/d-02 transferred to help search t2 |
| 59 | t2 found; fleet RTB |

### Copilot bundle

| Panel | Count (sample) |
|-------|----------------|
| `copilot.narrations` | 11 (requires `SAR_NARRATOR_URL` during run) |
| `copilot.violations` | 6 (in-process safety analyst; default with `SAR_COPILOT`) |

Example narration:

```text
Tick 16: d-01 located target t1.
Tick 16: d-01 reassigned from t1 to help search t2.
Tick 59: d-01 located target t2.
```

Example violation:

```json
{
  "tick": 10,
  "severity": "MEDIUM",
  "ruleId": "airspace-v1.battery.rtb",
  "summary": "Drone d-03 reached RTB battery threshold while still searching at tick 10.",
  "recommendedAction": "ORDER_RTB",
  "sourceEventType": "LOW_BATTERY",
  "droneId": "d-03"
}
```

---

## Related docs

- [KAFKA-SAFETY.md](../../a2a/KAFKA-SAFETY.md) — architecture, env vars, 2-target runbook
- [sar-kafka-safety/README.md](../sar-kafka-safety/README.md) — single-target sample run
- [scenarios/drone-sar/README.md](../../README.md) — Phase 1 sim pathing + overview
