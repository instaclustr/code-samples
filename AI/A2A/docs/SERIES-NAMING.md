# Series naming — blog vs repo

Public names for the **Scaling Agent Systems** example arc (timekeeping metaphor). **Repo paths and Maven modules stay unchanged** — `bridge/`, `PollCountdownClient`, etc.

**Related:** [examples index](examples/README.md) · [Part 5 blog](blogs/scaling-agents-part-5-a2a-java-demo.md) · [Part 6 blog](blogs/scaling-agents-part-6-a2a-bridge-streaming-push.md) · [Kafka design notes](kafka/README.md)

---

## Progression

| Part | Public name (blog) | Internal / repo | Role |
|------|-------------------|-----------------|------|
| **5** | **Clockwork Agent** | `src/main/java/local/a2a/examples/` | Hand-rolled JSON-RPC; poll only; three protocol paths |
| **6** | **Quartz Chronometer Agent** | [`bridge/`](../bridge/) | Official `a2a-java` SDK; poll → SSE → push (Phases A–C) |
| **7** | **Atomic Timekeeper** (layer / backbone) | Kafka in `PushNotificationReceiver` + consumers | Task lifecycle on a topic; fan-out, audit, replay |

One-line arc:

> **Clockwork** taught the protocol ticks. **Quartz Chronometer** runs them on a spec-faithful SDK with poll, stream, and push. **Atomic Timekeeper** distributes those lifecycle events on Kafka behind the protocol.

---

## Metaphor map (use sparingly in posts)

| Concept | Clockwork | Quartz Chronometer | Atomic Timekeeper |
|---------|-----------|-------------------|-------------------|
| Implementation | Visible gears | Quartz oscillator + certified precision | Authoritative time signal |
| Client updates | Glance at the hands (poll) | Display ticks (SSE); alarm (push) | NIST / cell-tower sync (event backbone) |
| A2A surface | Hand-rolled server | SDK reference server | Unchanged — Kafka is **not** A2A transport |
| Teaching goal | Protocol contract | Production-shaped update paths | Durable, multi-consumer orchestration |

**Part 7 naming:** prefer **Atomic Timekeeper** as the **Kafka layer or backbone**, not a third peer “agent” in the same sense as Clockwork and Quartz — unless a post explicitly personifies the topic pipeline.

---

## Blog vs repo glossary

| Blog / prose | Repo / code | Notes |
|--------------|-------------|-------|
| Clockwork Agent | `A2aDemoServer`, `A2aDemoClient` | Agent Card: “Clockwork Agent” (or similar in Part 5) |
| Quartz Chronometer Agent | `bridge/` module | Agent Card today: `Bridge Countdown Agent` — rename on card when polishing Part 6 for publish (optional) |
| Atomic Timekeeper | `docs/kafka/`, producer in `PushNotificationReceiver` | Topic e.g. `a2a.task.events` |
| Bridge (internal) | `bridge/`, `BridgeAgentCardProducer`, `docs/examples/BRIDGE-OVERVIEW.md` | Fine in READMEs and commit messages; avoid as **headline** public name |

---

## Style

- Title case for agent names: **Clockwork Agent**, **Quartz Chronometer Agent**
- **Atomic Timekeeper** — no “Agent” suffix (infrastructure role)
- First mention in each post: public name + brief “(the `bridge/` module in the repo)” if readers may clone the code

---

## Decision log

| Date | Decision |
|------|----------|
| 2026-07-08 | Adopt timekeeping arc: Clockwork → Quartz Chronometer Agent (Part 6) → Atomic Timekeeper (Part 7 Kafka); keep `bridge/` internal |
