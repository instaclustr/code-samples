# Bridge Module — What It Demonstrates

Overview of the **Part 6 bridge** (`bridge/`): what the three phases prove, how the system works, and what is intentionally missing or simplified.

**Blog name:** **Quartz Chronometer Agent** ([`SERIES-NAMING.md`](../SERIES-NAMING.md)). Part 7 Kafka: **Atomic Timekeeper**.

**Related:** [examples index](README.md) · [bridge/README.md](../../bridge/README.md) · [Part 6 blog](../blogs/scaling-agents-part-6-a2a-bridge-streaming-push.md) · [hero diagram](../diagrams/a2a-bridge-event-paths.svg) · [PNG](../diagrams/a2a-bridge-event-paths.png) · [Word PNG](../diagrams/a2a-bridge-event-paths-word.png)

---

## What it demonstrates

The bridge is the step from Clockwork’s hand-rolled, **poll-only** demo to a **production-shaped A2A surface** using the official [`a2a-java`](https://github.com/a2aproject/a2a-java) SDK.

Same teaching story as Clockwork Example 2 — a deterministic **countdown task** — but three ways a client can learn about progress:

| Phase | Example doc | What it proves |
|-------|-------------|----------------|
| **A** | [04-sdk-countdown-poll.md](04-sdk-countdown-poll.md) | SDK server + client; `GetTask` poll (Clockwork Ex 2 parity) |
| **B** | [05-sse-countdown.md](05-sse-countdown.md) | Server **pushes** `statusUpdate` / `artifactUpdate` over SSE |
| **C** | [06-push-webhook.md](06-push-webhook.md) | **Disconnected** client; server POSTs to your webhook URL |

Together they implement the fork from [Part 3](../blogs/scaling-agents-part-3-a2a-runtime.md) / [Part 4](../blogs/TODO-part-4-link): **poll, stream, push** — and show where **Kafka** fits next (behind the protocol, not replacing `SendMessage`).

### One-line summary

> The bridge runs the **same async task lifecycle** on a **real SDK stack**, delivered three ways (pull, stream, push), so Part 7’s Kafka layer is an **event backbone behind correct A2A** — not a shortcut around the protocol.

---

## Why Quarkus? (and what we didn’t use)

Quarkus is here because the **official `a2a-java` reference server is built on it** — not because we chose Quarkus over Spring (or similar) as a separate architecture decision.

Part 6’s goal was to move from hand-rolled Clockwork to the **official Java SDK** with poll, SSE, and push working without reimplementing those transports. The bridge depends on Maven artifact `a2a-java-sdk-reference-jsonrpc`, whose description is *“A2A JSONRPC Reference Server (based on Quarkus)”*. It pulls in `a2a-java-sdk-reference-common`, which wires HTTP, CDI, and MicroProfile config via `quarkus-vertx-http` and `quarkus-arc`.

Our custom server code is two small CDI producers:

- `BridgeAgentCardProducer` — `@Produces @PublicAgentCard AgentCard`
- `CountdownAgentExecutorProducer` — `@Produces AgentExecutor`

Quarkus boots the app, serves `/.well-known/agent-card.json`, handles JSON-RPC, SSE, and push — we only plug in card + executor.

**Trade-off:** heavier build (`bridge/target/` is large; do not commit it) in exchange for a **spec-faithful** server with streaming and push aligned with how the A2A project ships Java servers today.

### What is *not* Quarkus

| Piece | Stack |
|-------|--------|
| **A2A agent server** (`:8081`) | Quarkus + `a2a-java` reference |
| **SDK clients** (`PollCountdownClient`, `SseCountdownClient`, `PushCountdownClient`) | Plain Java + `a2a-java-sdk-client` |
| **Push webhook receiver** (`:8082`) | JDK `com.sun.net.httpserver.HttpServer` — deliberately minimal |

Quarkus is the **agent host**, not the whole bridge module.

### Alternatives considered

| Option | Pros | Why we didn’t use it for bridge |
|--------|------|----------------------------------|
| **Keep extending Clockwork** (`HttpServer`, hand-rolled JSON) | Zero deps; you own every line | No official SDK; would reimplement SSE + push; drifts from spec |
| **`a2a-java-sdk-server-common` + transport modules only** | Lighter than a full Quarkus app | No turnkey reference server — you wire HTTP, routing, streaming yourself |
| **Quarkus + `reference-jsonrpc`** (what we did) | Official pattern; SSE/push “for free” | Heavier build; Quarkus-specific |
| **Another Java framework (Spring Boot, etc.)** | Familiar to many teams | No official A2A reference server for Spring in SDK 1.0.0 |
| **Python / LangGraph** (`a2a-samples`) | Great for LLM agents | Breaks the Java thread from Parts 5–7 |
| **gRPC or REST binding** | Other official transports exist in the BOM | Bridge targets JSON-RPC parity with Clockwork; only `reference-jsonrpc` exists as a ready-made server |

A lighter deploy later is possible via lower-level SDK modules (`server-common` + `transport-jsonrpc`) with your own HTTP stack — essentially “Clockwork, but SDK-backed.” That is more work and was not the Part 6 teaching goal.

---

## How it works

### One agent, three clients

All phases share the **same server**:

| Piece | Role |
|-------|------|
| **Quarkus** + `a2a-java` reference JSON-RPC | Agent on **port 8081** |
| `CountdownAgentExecutorProducer` | Parses `"Count down N seconds"`; drives lifecycle via `AgentEmitter` |
| `BridgeAgentCardProducer` | Agent Card with `streaming: true`, `pushNotifications: true` |

**Server behaviour** (all phases):

1. `execute()` parses countdown seconds from user text.
2. `startWork()` — first state `TASK_STATE_WORKING`, `"Countdown started: Ns remaining."`
3. Loop: sleep 10s → `updateStatus()` until done.
4. `addArtifact()` + `complete()` with completion timestamp.

The SDK requires `AgentExecutor.execute()` to **finish before the event queue closes** (see [a2a-java#221](https://github.com/a2aproject/a2a-java/issues/221)), so the countdown **blocks inside `execute()`** — not a background thread that outlives the method.

```text
                    Bridge Countdown Agent (:8081)
                              |
         +--------------------+--------------------+
         |                    |                    |
    Phase A              Phase B              Phase C
  PollCountdownClient  SseCountdownClient   PushCountdownClient
  GetTask every 5s     SSE stream open      push URL on SendMessage
         |                    |                    |
    client pulls         events pushed        server POSTs
                              |
                    PushNotificationReceiver (:8082)   ← Phase C only
```

### Phase A — poll

- Client: `setStreaming(false)`, `setPolling(true)`.
- Loop: `GetTask(taskId)` every 5 seconds until terminal state.
- **Teaching point:** Baseline that always works; may show **duplicate** status lines when poll interval (5s) is faster than server tick (10s).

### Phase B — SSE

- Client: `setStreaming(true)`, `setPolling(false)`.
- `sendMessage` opens an SSE stream; SDK delivers `TaskUpdateEvent` (`statusUpdate`, `artifactUpdate`).
- **Teaching point:** One event per server emit — no duplicate poll lines.

### Phase C — push webhook

- Client: streaming and polling **off**; `MessageSendConfiguration.taskPushNotificationConfig` points at `http://localhost:8082/a2a/webhook`.
- SDK `BasePushNotificationSender` POSTs JSON on each state change (CDI beans in `a2a-java` server-common).
- `PushNotificationReceiver` logs the body; Part 7 adds `kafkaProducer.send(...)`.
- **Teaching point:** A2A push is **server → client**; your receiver is an **HTTP ingress adapter** (producer), not a Kafka consumer wrapped as A2A.

### Ports

| Service | Port |
|---------|------|
| Clockwork Agent | 8080 |
| Bridge Agent | 8081 |
| Push Notification Service (webhook) | 8082 |

---

## What’s missing or simplified

### Intentionally out of scope

| Area | What we skipped |
|------|-----------------|
| **LLM / real agent** | Pattern-matched countdown only — no model, tools, or MCP |
| **Clockwork scenarios** | No time (Ex 1) or `input-required` confirm (Ex 3) on the bridge yet |
| **Other transports** | JSON-RPC only — no gRPC or REST |
| **Auth** | No `auth-required`, webhook JWT, or `TaskAuthorizationProvider` |
| **Persistence** | In-memory tasks and push config — lost on restart |
| **Kafka** | Receiver logs to stdout; no topic, consumers, or replay (**Part 7**) |

### Simplifications that matter for readers

1. **`sendMessage` can block for the full countdown** (~60s+) even with `returnImmediately(true)` and push configured — because `execute()` must complete. Phase C webhooks arrive *during* that blocking call; the client is not fully “fire-and-forget” yet.

2. **Push receiver is minimal** — no signature verification, retries, idempotency keys, or durable queue; plain `HttpServer` + stdout.

3. **Single countdown skill** on the Agent Card vs Clockwork’s three examples.

4. **Localhost only** — no TLS, ingress, or managed deployment (Instaclustr Kafka comes in Part 7).

5. **Webhook delivery guarantees** — demo does not exercise failure, retry, or replay; that is the motivation for Kafka in Part 7.

### SDK notes (Phase C)

- `TaskPushNotificationConfig` in **1.0.0.Final** uses a flat builder (`id`, `url`, …) — not the nested `PushNotificationConfig` shown in some older docs.
- Push config **`id`** is required when building `TaskPushNotificationConfig`.
- Pass **consumers** into `sendMessage` if you need to capture `TaskEvent` while the call is in flight.

---

## Example traces

| Phase | Captured run |
|-------|----------------|
| A | [04-sdk-countdown-poll.md#captured-run](04-sdk-countdown-poll.md) |
| B | [05-sse-countdown.md#captured-run](05-sse-countdown.md) |
| C | [06-push-webhook.md#captured-run](06-push-webhook.md) |

---

## Next: Part 7

Replace stdout logging in `PushNotificationReceiver` with a Kafka producer on a task-events topic; add consumers for fan-out, audit, and replay — on managed Kafka (e.g. Instaclustr).

**Design invariants (push vs RPC, producer adapter pattern):** [`docs/kafka/README.md`](../kafka/README.md).

**Then (Parts 8+):** AI agents and orchestration — [`docs/scenarios/README.md`](../scenarios/README.md).
