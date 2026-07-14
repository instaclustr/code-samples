# Kafka Extensions — Design Notes (Part 7+)

Planning and **invariants** for wiring Kafka behind the bridge and live scenarios. Read this before implementing producers, consumers, or orchestration topics.

**Blog name for Part 7:** **Atomic Timekeeper** (Kafka layer behind the **Quartz Chronometer Agent**). Naming glossary: [`SERIES-NAMING.md`](../SERIES-NAMING.md).

**Related:** [Part 3 — push direction](../blogs/scaling-agents-part-3-a2a-runtime.md#3-push-notifications) · [BRIDGE-OVERVIEW](../examples/BRIDGE-OVERVIEW.md) · [Part 6 blog](../blogs/scaling-agents-part-6-a2a-bridge-streaming-push.md) · [scenarios roadmap](../scenarios/README.md)

---

## Core invariant (from Part 3 — keep for all Kafka work)

A2A separates **two interaction styles**. Do not blur them when adding Kafka.

| Direction | Mechanism | Who starts it |
|-----------|-----------|---------------|
| **Client → agent** | Normal A2A API (`SendMessage`, `CreateTaskPushNotificationConfig`, `GetTask`, …) | Client sends a request; agent responds |
| **Agent → client** (“push” in the spec) | Server **POSTs** to a client-registered webhook when task state changes | Agent initiates |

```text
Client  --RPC-->  Agent     (work, register webhook, polls)
Client  <--POST--  Agent     (A2A push: task status notification)
```

**Kafka is neither of the above on the wire.** It is **your** infrastructure behind those surfaces.

### What this means for Kafka extensions

| Do | Don’t |
|----|--------|
| Treat Kafka as an **event backbone** for task lifecycle, audit, fan-out, replay | Treat Kafka as an **A2A transport binding** or replacement for `SendMessage` |
| **Produce** when you observe A2A-side events (webhook POST in, or agent task-manager hook) | Model a **symmetric “client push”** topic where clients publish work instead of calling `SendMessage` |
| Have orchestrators **consume** Kafka for situational awareness, then **call agents via A2A** to delegate | Have orchestrators **only** produce/consume Kafka and skip Agent Cards + A2A |
| Mirror **server → client** notifications into durable records | Wrap a Kafka consumer and pretend it is the A2A Push Notification Service facing the agent |

Registering a webhook URL is still **client → agent RPC**, not “client push.” Part 7’s webhook receiver is **agent → your service HTTP POST** → **your** `kafkaProducer.send(...)`.

---

## Intended Part 7 shape (Atomic Timekeeper on bridge)

```text
Bridge agent (:8081)
    │
    ├─ A2A push POST ──► PushNotificationReceiver (:8082)
    │                           │
    │                           └─ produce ──► topic: a2a.task.events
    │
    └─ (optional later) task-manager hook ──► same topic

Consumers (separate processes):
    orchestrator-audit, replay-demo, fan-out subscriber
```

| Component | Role | A2A relationship |
|-----------|------|------------------|
| `PushNotificationReceiver` | HTTP ingress adapter | Consumes **A2A push** (server → client direction on HTTP); **produces** to Kafka |
| `a2a.task.events` consumers | Observers / orchestration input | **Outside** A2A; read your event log |
| Orchestrator (Part 10) | Decides next step | **Consumes** Kafka for awareness; **initiates** work via `SendMessage` (RPC) |

---

## Event model (sketch)

Records should reflect **task lifecycle facts** already visible on the A2A surface, e.g.:

- `taskId`, `contextId`, `state`, `timestamp`
- notification payload or normalized subset from push webhook
- `source`: `push-webhook` | `agent-internal` (if dual-publish later)

Key by `taskId` (or composite with partition strategy for fan-out demos). Idempotency and schema versioning are out of scope for the first bridge slice — note in blog, add when scenarios need it.

---

## Anti-patterns (reviewer-tested)

These confused early readers; avoid them in code, diagrams, and blog prose:

1. **“Kafka implements A2A push”** — Kafka implements **your** durability/fan-out **after** A2A push (or after internal task events).
2. **“Bidirectional push over Kafka”** — Client work stays on RPC; only agent-originated status flows are “push” in the spec sense.
3. **“Consumer wraps Kafka as webhook”** — The agent POSTs to **your** HTTPS URL; that handler produces. You do not subscribe to Kafka and call that “receiving A2A push.”
4. **“Orchestrator publishes SendMessage to a topic”** — That hides A2A. Command topics are fine as **your** pattern, but delegation to remote agents should still surface as A2A client calls in the teaching path.

---

## Series checkpoints

| Part | Kafka role | A2A rule to preserve |
|------|------------|----------------------|
| **7** | Producer in webhook receiver; demo consumer(s) | Push ingress → produce only |
| **10** | Orchestrator reads task events | Orchestrator still discovers cards + `SendMessage` |
| **11+** | Audit, replay, multi-subscriber | Agents remain A2A servers; Kafka never replaces Agent Card |

---

## Decision log

| Date | Decision |
|------|----------|
| 2026-07-08 | Adopt timekeeping arc: Clockwork → Quartz Chronometer Agent (Part 6) → Atomic Timekeeper (Part 7 Kafka); keep `bridge/` internal |
| 2026-07-08 | Document Part 3 push/RPC distinction as invariant for all Kafka extensions |

_Update when topic names, schemas, or consumer roles are fixed._
