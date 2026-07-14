# Scenarios — Beyond Clockwork & Bridge

Planning doc for evolving from **protocol lab** examples (Clockwork, bridge Phases A–C) toward **realistic agent systems**: AI inside agents, A2A orchestration, and Kafka-backed workflows.

**Related:** [examples index](../examples/README.md) · [BRIDGE-OVERVIEW.md](../examples/BRIDGE-OVERVIEW.md) · [Part 6 blog](../blogs/scaling-agents-part-6-a2a-bridge-streaming-push.md)

---

## Principle: add a third track, don’t replace the lab

| Track | Purpose | Status |
|-------|---------|--------|
| **Clockwork** (`src/main/java/local/a2a/examples/`) | Hand-rolled protocol; three `SendMessage` paths; poll only | **Done** (Part 5) |
| **Bridge** (`bridge/`) | Official SDK; poll / SSE / push; Kafka in Part 7 | **Phases A–C done** |
| **Live scenarios** (future — e.g. `scenarios/`) | AI agents + orchestration + realistic workflow | **Planned** |

Readers who only need wire format stay on Clockwork/bridge. Readers building production systems follow the live scenario track.

Clockwork and bridge remain **stable references** — do not fold them into one mega-demo.

---

## How to pick a use case

Work **backwards from A2A behaviours** to teach, not from “let’s add an LLM.”

### 1. List orchestration patterns

| Pattern | Why it matters |
|---------|----------------|
| Supervisor delegates to specialists | Core multi-agent A2A (discover Agent Card → `SendMessage`) |
| Long-running `Task` + SSE or push | Bridge already proves transport; scenario adds real work duration |
| `input-required` | Human or orchestrator confirmation gate |
| Parallel subtasks → aggregate artifact | Fan-in orchestration |
| Failure / retry / replay | Kafka justification (Part 7+) |

### 2. Pick a domain where those behaviours are natural

| Scenario | Fit | Instaclustr angle |
|----------|-----|-------------------|
| **A — Incident / alert triage** | Event in → research → recommend → notify; push + Kafka natural | Kafka ingress, replay, fan-out |
| **B — Research brief** | Planner → 2 specialists → synthesizer; closer to a2a-samples LangGraph | Simpler narrative |
| **C — Change review** | Logs/diff in → risk summary → `input-required` approval | Good for `input-required` teaching |

**Recommendation for first live scenario:** **A (incident triage)** — timely, maps cleanly to Kafka + push, distinct from countdown pedagogy.

### 3. Cap complexity

- **First live post:** 2–3 agents + 1 orchestrator (not ten micro-agents).
- **One happy-path demo script** with optional LLM (env-gated, e.g. Ollama) so workshops survive non-determinism.
- **One runnable path** per blog post.

### 4. Map infrastructure deliberately

| Concern | Technology | When to introduce |
|---------|------------|-------------------|
| Task / orchestration events | **Kafka** (managed) | Part 7 — bridge; Part 10 — orchestrator |
| Durable task / thread state | PostgreSQL | After multi-agent path works in-memory |
| Search / observability | OpenSearch | Later — optional |

Kafka is **behind** A2A; it does not replace `SendMessage` or Agent Cards. Part 7+ invariants: [`docs/kafka/README.md`](../kafka/README.md).

---

## Suggested series progression

| Step | Blog / milestone | What you add | A2A lesson |
|------|------------------|--------------|------------|
| **7** | **Atomic Timekeeper** — Kafka on bridge | Producer in `PushNotificationReceiver`; consumer(s) | Events behind protocol; replay, fan-out |
| **8** | Single AI agent | One `a2a-java` agent + local LLM (e.g. Ollama) | Real `Task` + streaming; non-determinism |
| **9** | Two agents + rule orchestrator | Research + action agents; deterministic planner | Card discovery, delegation |
| **10** | AI orchestrator + Kafka | Supervisor reads task events / state; picks next agent | AI in control; Kafka as nervous system |
| **11** | Heterogeneous agent (optional) | e.g. Python LangGraph agent via A2A | Same protocol, different runtime |

Each step should **reuse** bridge transport patterns (SSE or push), not reintroduce hand-rolled JSON-RPC.

Keep **countdown** as the protocol unit test; add `scenarios/<name>/` for product-shaped demos.

---

## Target architecture (end state)

```text
User / API
    │
    ▼
Orchestrator (AI or rules)     ← in control; typically NOT an A2A server
    │  discovers Agent Cards
    ├── A2A ──► Research Agent (LLM + tools)
    ├── A2A ──► Action Agent (LLM or deterministic)
    │
    └── publish / consume ──► Kafka
              ▲                    (task lifecycle, orchestration commands)
              │
              └── push webhook / agent-side publish
```

- **Orchestrator** uses A2A as a **client** to remote agents.
- **Agents** expose A2A surfaces (`a2a-java` or other SDK).
- **Kafka** carries orchestration events, audit, replay — not agent-to-agent RPC.

See [bridge event-path diagram](../diagrams/a2a-bridge-event-paths.svg).

---

## Scenario A — Incident triage (draft)

**Trigger:** Synthetic alert JSON (or webhook) — e.g. museum-demo / ops style incident.

**Agents (max 3):**

| Agent | Role | A2A shape |
|-------|------|-----------|
| **Triage** | Classify severity, extract entities | `Message` or short `Task` |
| **Research** | Enrich from context (LLM + optional tool) | Long `Task`, SSE |
| **Recommend** | Proposed actions, summary artifact | `Task` → `completed` + artifact |

**Orchestrator (Part 9–10):**

- Part 9: Rule-based — if severity > X, call research, then recommend.
- Part 10: LLM planner — reads Kafka task events, decides next `SendMessage`.

**Why it works for the series:** Push suits disconnected orchestrator; Kafka suits alert ingress and audit; `input-required` optional for “approve runbook.”

---

## Scenario B — Research brief (alternative)

**Trigger:** User question — “Summarize X with sources.”

**Flow:** Planner agent (or orchestrator) → specialist A + B in parallel → synthesizer merges artifacts.

Simpler story than incident triage; good if you want parity with [a2a-samples LangGraph](https://github.com/a2aproject/a2a-samples).

---

## What to avoid early

| Pitfall | Why |
|---------|-----|
| Orchestrator that only wraps Kafka, never Agent Cards | Hides A2A; looks like Kafka RPC |
| Many agents before two work reliably | Debug and blog surface explode |
| One demo that replaces Clockwork + bridge | Loses teaching clarity |
| Auth, persistence, multi-region before one streamed multi-agent path | Workshop friction |
| LLM-only demos with no scripted fallback | Non-deterministic live talks |

---

## Concrete next actions

| # | Action | Owner / when |
|---|--------|----------------|
| 1 | **Part 7** — Kafka producer in `PushNotificationReceiver`; simple consumer | Next implementation milestone |
| 2 | **Spike** — single `a2a-java` agent + Ollama, streaming short answer | Validates LLM + SSE before multi-agent |
| 3 | **Choose scenario** — A (incident) vs B (research); record decision here | Product / narrative |
| 4 | **Scaffold** `scenarios/incident/` (or chosen name) when Part 8 starts | New module, same repo |
| 5 | **Demo script** — env vars, sample payloads, expected Agent Card skills | Per scenario README |

---

## Decision log

| Date | Decision |
|------|----------|
| 2026-07-07 | Document scenario strategy; prefer **incident triage** as first live scenario after Part 7; keep Clockwork + bridge as permanent lab tracks |

_Update this table when scenario choice or progression changes._

---

## References

- [Bridge overview](../examples/BRIDGE-OVERVIEW.md) — what bridge proves today
- [A2A GitHub reference](../A2A_GITHUB_REFERENCE.md) — official SDKs, samples (LangGraph, etc.)
- [a2a-samples — LangGraph](https://github.com/a2aproject/a2a-samples/tree/main/samples/python/agents/langgraph) — streaming + push reference
- [Part 3 runtime](../blogs/scaling-agents-part-3-a2a-runtime.md) — poll, SSE, push fork
