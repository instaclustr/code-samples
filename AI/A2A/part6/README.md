# A2A Bridge — SDK, SSE, Push (Part 6)

This module is the **Part 6** implementation: a protocol-faithful step beyond the [Clockwork Agent](../README.md) (hand-rolled JSON-RPC + polling only).

**Blog name:** **Quartz Chronometer Agent** (repo folder remains `bridge/`). **Part 7 Kafka:** **Atomic Timekeeper**. Glossary: [`docs/SERIES-NAMING.md`](../docs/SERIES-NAMING.md).

**Example index:** [`docs/examples/README.md`](../docs/examples/README.md) — why, what's done, what's next.

**Full overview:** [`docs/examples/BRIDGE-OVERVIEW.md`](../docs/examples/BRIDGE-OVERVIEW.md) — what it demonstrates, how it works, limitations.

---

## Why we are doing this

### The problem Clockwork left open

Part 5 ([blog](../docs/blogs/scaling-agents-part-5-a2a-java-demo.md)) deliberately built the smallest possible A2A server: plain Java, three `SendMessage` paths, **one update strategy** (client polls `GetTask`). That taught the protocol contract without SDK or LLM noise.

Parts 3–4 already described **three ways** a client can learn about task progress:

1. **Poll** — `GetTask` in a loop (Clockwork does this).
2. **SSE** — server streams `statusUpdate` / `artifactUpdate` on a long-lived response.
3. **Push** — server POSTs to a client-registered webhook when state changes.

Skipping straight to **Kafka** (Part 7) would make Kafka look like a workaround for polling. Readers need to see what gets published — task state, artifacts — and how that relates to SSE and push on the wire first.

### What the bridge adds

| Layer | Clockwork (Part 5) | Bridge (Part 6) |
|-------|-------------------|-----------------|
| Server | Hand-rolled `HttpServer` | Official [`a2a-java`](https://github.com/a2aproject/a2a-java) (Quarkus JSON-RPC) |
| Client updates | `GetTask` poll only | Poll → **SSE** → **push webhook** |
| Event backbone | — | Webhook receiver **produces** to Kafka (Part 7) |

Kafka remains **behind** the protocol: topics carry task lifecycle events; A2A still owns agent hand-offs. See [`docs/kafka/README.md`](../docs/kafka/README.md) for Part 7+ design invariants (push vs RPC).

### Why a separate folder (not a new git repo)

- **Clockwork** stays minimal and stable for teaching.
- **Bridge** adds Quarkus, SDK deps, SSE, and push without bloating the original demo.
- **Part 7** extends the same module with Kafka producers/consumers.
- One repo keeps blogs, diagrams, and traces together.

### Why Quarkus?

The bridge uses Quarkus because **`a2a-java-sdk-reference-jsonrpc` is a Quarkus-based reference server** — not as an independent framework choice. We add two CDI producers (`BridgeAgentCardProducer`, `CountdownAgentExecutorProducer`); Quarkus + the SDK handle JSON-RPC, SSE, and push.

Clients and the push webhook receiver are **not** Quarkus (plain Java SDK client + JDK `HttpServer` on `:8082`).

Full rationale and alternatives table: [`docs/examples/BRIDGE-OVERVIEW.md`](../docs/examples/BRIDGE-OVERVIEW.md#why-quarkus-and-what-we-didnt-use).

---

## What we have so far

### Series + repo artifacts

| Part | Topic | Status |
|------|-------|--------|
| 1–4 | Scaling, A2A nouns, runtime, diagrams | Blog drafts in `docs/blogs/` |
| **5** | Clockwork Agent — three paths in plain Java | **Complete** — `A2aDemoServer` / `A2aDemoClient`, [trace](../docs/examples/trace.md) |
| **6** | Bridge — SDK, SSE, push | **Phases A–C done**; Part 7 Kafka next |
| **7** | Kafka task-event backbone | Planned |

**Future (Parts 8+):** Live scenarios with AI agents and orchestration — [`docs/scenarios/README.md`](../docs/scenarios/README.md).

### Bridge implementation status

| Phase | Status | Client | Server change |
|-------|--------|--------|---------------|
| **A — Poll baseline** | **Done** | `PollCountdownClient` | `BridgeAgentCardProducer`, `CountdownAgentExecutorProducer` |
| **B — SSE** | **Done** | `SseCountdownClient` | `streaming: true` on Agent Card |
| **C — Push** | **Done** | `PushCountdownClient` + `PushNotificationReceiver` | `pushNotifications: true` |
| **D — Kafka** | Part 7 | Consumers | Producer in webhook + task manager |

**Phase A trace:** [`docs/examples/04-sdk-countdown-poll.md`](../docs/examples/04-sdk-countdown-poll.md) (captured 2026-07-07).

**Phase B trace:** [`docs/examples/05-sse-countdown.md`](../docs/examples/05-sse-countdown.md) (captured 2026-07-07).

**Blog draft:** [`docs/blogs/scaling-agents-part-6-a2a-bridge-streaming-push.md`](../docs/blogs/scaling-agents-part-6-a2a-bridge-streaming-push.md)

**Diagram:** [`docs/diagrams/a2a-bridge-event-paths.svg`](../docs/diagrams/a2a-bridge-event-paths.svg)

**Phase C trace:** [`docs/examples/06-push-webhook.md`](../docs/examples/06-push-webhook.md) (captured 2026-07-07).

---

## What it demonstrates (summary)

One **countdown agent** on the official SDK, three **client update paths**:

| Phase | Client | Delivery |
|-------|--------|----------|
| A | `PollCountdownClient` | Client pulls via `GetTask` |
| B | `SseCountdownClient` | Server streams SSE events |
| C | `PushCountdownClient` + `PushNotificationReceiver` | Server POSTs to webhook |

Details: [`docs/examples/BRIDGE-OVERVIEW.md`](../docs/examples/BRIDGE-OVERVIEW.md).

---

## How it works (summary)

Shared `CountdownAgentExecutor` blocks in `execute()`, calling `AgentEmitter.startWork()` → `updateStatus()` every 10s → `addArtifact()` + `complete()`. Phases differ only in **how the client observes** those emissions (poll, SSE, or push). Agent Card: `streaming: true`, `pushNotifications: true`.

---

## Limitations (summary)

Teaching harness only: no LLM, no Clockwork Ex 1/3 on bridge yet, JSON-RPC only, in-memory state, no auth, localhost, push receiver logs stdout (Kafka in Part 7). `sendMessage` may block for the full countdown even with push configured.

Full list: [`docs/examples/BRIDGE-OVERVIEW.md`](../docs/examples/BRIDGE-OVERVIEW.md#whats-missing-or-simplified).

**After Part 7:** [`docs/scenarios/README.md`](../docs/scenarios/README.md) — AI agents, orchestration, live use cases (Parts 8–11).

---

## What the next step covers (Part 7)

Kafka producer in `PushNotificationReceiver` — see Part 7 blog plan.

**Scenario roadmap (AI agents + orchestration):** [`docs/scenarios/README.md`](../docs/scenarios/README.md).

## Target architecture

```
Client  →  a2a-java server (countdown agent)
              ├─ SSE stream (SendStreamingMessage / SubscribeToTask)     ← Phase B
              ├─ optional push webhook → Push Notification Service     ← Phase C
              │       → Kafka producer                                 ← Part 7
              └─ poll (GetTask) still supported as baseline            ← Phase A ✓
```

---

## Components

| Component | Purpose |
|-----------|---------|
| `CountdownAgentExecutorProducer` | Countdown via `AgentExecutor` + `AgentEmitter` — **Phase A** |
| `PollCountdownClient` | SDK client; `GetTask` poll loop — **Phase A** |
| `SseCountdownClient` | SSE stream, no poll — **Phase B** |
| `PushNotificationReceiver` | Webhook receiver → stdout — **Phase C** (Kafka in Part 7) |
| `PushCountdownClient` | Disconnected client; registers push URL — **Phase C** |

---

## Build and run

```bash
cd bridge
mvn -q package -DskipTests
```

### Server

```bash
java -jar target/quarkus-app/quarkus-run.jar
# Agent card: http://localhost:8081/.well-known/agent-card.json
```

Or: `mvn quarkus:dev`

### Poll client (Phase A)

```bash
mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.PollCountdownClient
```

### SSE client (Phase B)

```bash
mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.SseCountdownClient
```

### Push client (Phase C)

```bash
mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.PushCountdownClient
# Receiver embedded on :8082 by default
# Or: mvn exec:java -Dexec.mainClass=local.a2a.bridge.push.PushNotificationReceiver  (separate terminal)
```

Ports: agent **8081**, webhook receiver **8082**, Clockwork **8080**.

---

## Dependencies

- `org.a2aproject.sdk:a2a-java-sdk-bom` 1.0.0.Final (see `pom.xml`)
- `a2a-java-sdk-reference-jsonrpc` — brings Quarkus 3.36.1 transitively ([why Quarkus?](../docs/examples/BRIDGE-OVERVIEW.md#why-quarkus-and-what-we-didnt-use))
- Part 7: `kafka-clients` or Testcontainers

**Do not commit `bridge/target/`** — Maven/Quarkus build output (~100+ files). Add `target/` to `.gitignore` before pushing to GitHub.
