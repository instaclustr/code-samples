# Phase 1 design check — Drone SAR sim

Maps [DRONE-SAR-A2A-DESIGN.md](../../docs/scenarios/DRONE-SAR-A2A-DESIGN.md) Phase 1 deliverables to this implementation.

| Design §10 deliverable | Status | Implementation |
|------------------------|--------|----------------|
| `scenarios/drone-sar/sim/` grid, heights, geofence, battery, 1 Hz tick | **Done** | `GridWorld`, `SimDrone.tick`, `SimulationEngine` |
| Flock coordinator **library** (patterns, separation, RTB) | **Done (MVP)** | `SectorAllocator` (grid-lane §15), `SimulationEngine` orchestrates RTB |
| CLI mission runner JSON → stdout | **Done** | `MissionRunner`, `MissionLoader` |
| Success: N drones search; RTB before battery; geofence respected | **Done** | `SimulationResult.successCriteriaMet()` |

## Section-by-section

| Design section | Coverage |
|----------------|----------|
| **§5** Task / telemetry JSON | `DroneTelemetry`, `Detection`, `Position`; CLI prints JSON lines |
| **§5** Mission JSON | `Mission` record + `missions/*.json` |
| **§6** Grid layers | Ground + foliage height fields; geofence polygons; rules-driven AGL |
| **§6** Search patterns | `grid-lanes` via `SectorAllocator`; other patterns deferred |
| **§7** Safety (local enforce) | Geofence hard block in `SimDrone`; RTB at `rtbBatteryPct` |
| **§15** Flocking / join-leave | Central coordinator; greedy lane split; no LLM |
| **§16** One agent per drone | **Not in Phase 1** — drones are in-process objects |
| **§17** Kafka hybrid | **Not in Phase 1** |

## Gaps (acceptable for Phase 1)

- Drone–drone separation / repulsion not modeled
- Waypoints do not explicitly route around no-fly zones (movement blocked at boundary)
- Detection is a distance stub on coordinator path, not per-sensor simulation
- **Multi-target (Phase 1b):** drones split across targets in one flock; fleet RTB when all found — not separate flocks yet
- Only `grid-lanes` search pattern; spiral / Voronoi deferred
- Mobile base moves slowly (1 cell per 120 ticks) but does not trigger replan
- Viz is offline snapshot + JSON replay (not live SSE — that is Phase 2)

## Verification

```bash
cd scenarios/drone-sar/sim && mvn test
```

Tests cover polygon math, geofence cell coords, sector count, and a full fast-mission run asserting RTB + no geofence violations.

## Decision log entry (proposed)

| Date | Decision |
|------|----------|
| 2026-08-14 | Phase 1 sim shipped under `scenarios/drone-sar/sim/`; flock as in-process library + CLI |
