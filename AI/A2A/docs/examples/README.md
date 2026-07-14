# A2A Examples — Index

Runnable examples for the **Scaling Agent Systems** series (Kafka + A2A). Two code paths:

| Track | Code | Port | Update model |
|-------|------|------|----------------|
| **Clockwork** (Part 5) | `src/main/java/local/a2a/examples/` | 8080 | Poll only (`GetTask`) |
| **Bridge** (Part 6+) | [`bridge/`](../../bridge/) | 8081 | Poll → SSE → push → Kafka |

**Blog names** (public): Clockwork Agent → **Quartz Chronometer Agent** (Part 6) → **Atomic Timekeeper** (Part 7 Kafka). See **[`docs/SERIES-NAMING.md`](../SERIES-NAMING.md)**.

---

## Why the bridge exists

[Part 5](../blogs/scaling-agents-part-5-a2a-java-demo.md) introduced the **Clockwork Agent**: hand-rolled JSON-RPC, three protocol paths, **polling only**. That was the right way to learn `SendMessage`’s fork without an SDK in the way.

It is not where you stop for production-shaped agent systems. [Part 3](../blogs/scaling-agents-part-3-a2a-runtime.md) and [Part 4](../blogs/TODO-part-4-link) already showed **three client update strategies** (poll, SSE, push). Clockwork implemented one.

The **bridge module** closes that gap before [Part 7](../blogs/scaling-agents-part-6-a2a-bridge-streaming-push.md) wires **Kafka**:

1. Move to the official **`a2a-java`** SDK (spec-faithful server + client).
2. Add **SSE** so the client receives `statusUpdate` events instead of a poll loop.
3. Add **push webhooks** so disconnected clients get notified (receiver → Kafka producer).
4. In Part 7, publish task lifecycle events to a **Kafka topic** for fan-out, replay, and orchestration.

Kafka is **not** an A2A transport binding. It sits **behind** the protocol — carrying task events that originate from correct A2A surfaces.

See [`bridge/README.md`](../../bridge/README.md) and the [Part 6 blog draft](../blogs/scaling-agents-part-6-a2a-bridge-streaming-push.md).

---

## What we have so far

### Clockwork Agent (Part 5) — complete

| # | Example | Doc | Trace |
|---|---------|-----|-------|
| 1 | Synchronous time → `Message` | [01-time-service.md](01-time-service.md) | [trace.md](trace.md) |
| 2 | Async countdown → `Task` + poll | [02-async-countdown.md](02-async-countdown.md) | [trace.md](trace.md) |
| 3 | `input-required` + confirm | [03-input-required-countdown.md](03-input-required-countdown.md) | [trace.md](trace.md) |

Plain Java (`HttpServer` / `HttpClient`), no SDK, no LLM.

### Bridge (Part 6) — Phases A–C complete

*Blog name: **Quartz Chronometer Agent** — see [`SERIES-NAMING.md`](../SERIES-NAMING.md).*

| Phase | Status | Example doc | What it adds |
|-------|--------|-------------|--------------|
| **A** | **Done** | [04-sdk-countdown-poll.md](04-sdk-countdown-poll.md) | `a2a-java` Quarkus server + SDK client; `GetTask` poll (parity with Clockwork Ex 2) |
| **B** | **Done** | [05-sse-countdown.md](05-sse-countdown.md) | SSE stream; `streaming: true`; no poll loop |
| **C** | **Done** | [06-push-webhook.md](06-push-webhook.md) | Push webhook; receiver logs stdout (Kafka in Part 7) |
| **D** | Part 7 | — | **Atomic Timekeeper** — Kafka producer/consumer on task events |

**Full overview** (what it demonstrates, how it works, limitations): **[BRIDGE-OVERVIEW.md](BRIDGE-OVERVIEW.md)**

Captured runs: [04](04-sdk-countdown-poll.md) · [05](05-sse-countdown.md) · [06](06-push-webhook.md)

---

## What the next step covers (Part 7 — Kafka)

Phase C (push) is done — see [06-push-webhook.md](06-push-webhook.md).

**Part 7:** `PushNotificationReceiver` gains a Kafka producer; consumers demonstrate fan-out and replay on managed Kafka. **Design notes:** [`docs/kafka/README.md`](../kafka/README.md) (push vs RPC invariants).

**Beyond Part 7:** AI agents and orchestration — **[`docs/scenarios/README.md`](../scenarios/README.md)** (Parts 8–11, incident triage vs research brief, architecture).

---

## Quick run

**Clockwork** (all three examples):

```bash
mvn -q compile exec:java -Dexec.mainClass=local.a2a.examples.A2aDemoServer   # :8080
mvn -q compile exec:java -Dexec.mainClass=local.a2a.examples.A2aDemoClient
```

**Bridge** (agent + all three client paths):

```bash
cd bridge && mvn -q package -DskipTests && java -jar target/quarkus-app/quarkus-run.jar   # :8081
cd bridge && mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.PollCountdownClient    # Phase A
cd bridge && mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.SseCountdownClient     # Phase B
cd bridge && mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.PushCountdownClient    # Phase C
```
