# Quartz Chronometer Agent

**Part 6 (new)** — all three [Clockwork Agent](../src/main/java/local/a2a/examples/) examples on the **official A2A JSON-RPC** stack (`a2a-java` + Quarkus reference server).

Replaces the old multi-phase `bridge/` teaching split (poll → SSE → push) with one agent that mirrors Clockwork semantics:

| Example | Prompt | Protocol path |
|---------|--------|---------------|
| **1 — Time** | `What is the current time?` | Immediate **`Message`** (no Task, no SSE) |
| **2 — Countdown** | `Count down 60 seconds` | Long-running **`Task`** + **SSE** updates |
| **3 — Confirm** | `Count down 20 seconds with confirm` | **`input-required`** → follow-up `confirm` on same task → **SSE** countdown |

**Design:** [DESIGN.md](DESIGN.md) · **Diagram:** [`a2a-hero-part6-quartz-chronometer.svg`](../docs/diagrams/a2a-hero-part6-quartz-chronometer.svg) · **Blog name:** [SERIES-NAMING.md](../docs/SERIES-NAMING.md)

## Quick start

```bash
./scripts/run-demo.sh
```

Shorter countdowns for a fast workshop:

```bash
QUARTZ_COUNTDOWN_SECONDS=20 QUARTZ_CONFIRM_COUNTDOWN_SECONDS=10 ./scripts/run-demo.sh
```

## Manual run

**Terminal 1 — agent (:8085)**

```bash
cd quartz-chronometer
mvn -q package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

**Terminal 2 — client**

```bash
cd quartz-chronometer
export QUARTZ_CHRONOMETER_URL=http://localhost:8085
mvn -q exec:java
```

## Protocol

| Item | Value |
|------|-------|
| Agent Card | `GET http://localhost:8085/.well-known/agent-card.json` |
| Transport | **JSON-RPC** (official reference server) |
| Streaming | `capabilities.streaming: true` — used for Task examples |
| Push | **not used** in this module |

## Environment

| Variable | Default |
|----------|---------|
| `QUARTZ_CHRONOMETER_URL` | `http://localhost:8085` |
| `QUARTZ_COUNTDOWN_SECONDS` | `60` |
| `QUARTZ_CONFIRM_COUNTDOWN_SECONDS` | `20` |

Port **8085** avoids collision with Clockwork (`8080`) and legacy `bridge/` (`8081`).

## Tests

```bash
mvn test
```

## Limitations

Educational demo — not production-ready. Full detail: **[DESIGN.md — Limitations and out of scope](DESIGN.md#limitations-and-out-of-scope)**.

- **Out of scope:** webhooks, `GetTask` poll path, Kafka, gRPC/REST, benchmark (see `bridge/`, Part 8)
- **Simplified:** rule-based prompts, 10s countdown ticks, in-memory state, no auth/TLS
- **Undemonstrated:** `CancelTask` (server only), poll fallback if SSE drops
- **Quirks:** Ex 3 uses two SSE sessions; thin unit tests; sequential demo client only

## Relation to other folders

| Folder | Role |
|--------|------|
| `src/main/java/local/a2a/examples/` | Part 5 Clockwork — hand-rolled JSON-RPC, poll only |
| **`quartz-chronometer/`** | Part 6 — same three examples, official SDK + SSE for tasks |
| `bridge/` | Legacy Part 6 experiment (poll/SSE/push phases + benchmark/Kafka) — **not** the blog path going forward |
