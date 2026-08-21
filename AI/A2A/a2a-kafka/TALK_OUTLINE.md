# 10-Minute Talk: A2A + Kafka — Agent-to-Agent over Event Streaming

## 1. **Intro (≈1 min)**  
**"Why put AI agents on Kafka?"**

- Agents need to talk to each other (orchestration, delegation, tool use).
- HTTP is fine for request/response; for async, scale, and replay we want a bus.
- This talk: **Agent2Agent (A2A) protocol** + **Kafka** using the `a2a-kafka` project.

---

## 2. **What is A2A? (≈1.5 min)**  
**"A common language for agents."**

- **A2A** = open protocol so agents from different vendors/frameworks can interoperate.
- Core ideas (one sentence each):
  - **Agent Card** – who the agent is, what it can do, how to call it.
  - **Task** – one unit of work with a clear lifecycle (submitted → working → completed/failed).
  - **Message** – a turn in the conversation (user or agent), made of **Parts** (text, file, structured data).
  - **Artifact** – something the agent produces (document, image, JSON).
- Normally: JSON-RPC 2.0 over HTTP (and SSE for streaming). We keep the same *semantics* but move the *transport* to Kafka.

---

## 3. **Why Kafka? (≈1 min)**  
**"Async, durable, multi-consumer."**

- **Async** – agents don't have to be up at the same time; tasks can be processed when capacity is free.
- **Durable** – every message/send and status update is stored; replay and auditing are possible.
- **Multi-consumer** – many agents (or clients) can subscribe to the same topics (e.g. discovery, routing).
- **Scale** – partition by `taskId` or `sessionId` for ordering and parallelism.

---

## 4. **Mapping A2A onto Kafka (≈1.5 min)**  
**"Three topics and one envelope."**

- **Topics** (from `A2AKafkaConfig`):
  - `a2a.requests` – client → agent (e.g. `message/send`). Key = `taskId` or `sessionId`.
  - `a2a.updates` – agent → client (status updates, artifacts, full Task). Key = `taskId`.
  - `a2a.agent-cards` – Agent Card announcements for discovery. Key = agent URL or id.
- **Envelope** – every record is a JSON "JSON-RPC style" envelope: `method`, `params` or `result`, `id`, `timestamp`. Same shape as A2A over HTTP, different transport.
- **Flow**: Client produces to `a2a.requests` → Agent consumes, does work → Agent produces to `a2a.updates` (and optionally `a2a.agent-cards`). Clients consume `a2a.updates` (and discovery from `a2a.agent-cards`).

---

## 5. **Code Walkthrough (≈2.5 min)**  
**"Same protocol, different transport."**

- **Models** (`model/`): Show **Task**, **Message**, **Part** (e.g. `TextPart`), **AgentCard**. "These are the A2A types; we serialize them to JSON and put them in the envelope."
- **A2AEnvelope**: "One Kafka value = one envelope: `method` (e.g. `message/send`, `status-update`), `params` or `result`, `id`, `timestamp`."
- **A2AKafkaProducer**: "We don't call HTTP; we call `sendMessage()`, `sendStatusUpdate()`, `publishAgentCard()` — each builds an envelope and produces to the right topic."
- **A2AKafkaConsumer**: "We subscribe to the topics, deserialize the envelope, and dispatch to a handler: `onMessageSend`, `onStatusUpdate`, `onAgentCard`. So the agent (or client) logic is the same; only the transport is Kafka."

---

## 6. **Live Demo (≈2 min)**  
**"End-to-end in two terminals."**

- **Terminal 1**: Start consumer  
  `mvn exec:java@consumer`  
  "Subscribes to all three topics and prints every A2A message."
- **Terminal 2**: Run producer  
  `mvn exec:java@demo`  
  "Publishes one Agent Card, one message/send, one status update."
- **Back to Terminal 1**: Point out the three printed blocks:
  - **AGENT CARD** – name, URL, capabilities.
  - **MESSAGE/SEND** – role, messageId, text: "Hello from A2A Kafka demo".
  - **STATUS UPDATE** – taskId, state `submitted`, timestamp.  
  "Same A2A protocol; the bus is Kafka."

*(If live demo is risky, use a short pre-recorded clip or screenshots of the two terminals.)*

---

## 7. **Wrap-up (≈0.5 min)**  
**"What to take away."**

- A2A gives agents a **common language**; Kafka gives them a **durable, async bus**.
- This repo is a **transport layer**: same POJOs and envelope; swap HTTP for Kafka.
- You can extend it: more A2A methods, schema registry, or multi-tenant topics.

**One liner:**  
"A2A defines *what* agents say; Kafka defines *where* it's said — and that's the outline we just walked through, with this code as the reference."

---

## One-Slide Summary (for slides)

| Section        | Time  | Key point |
|----------------|-------|-----------|
| Intro          | 1 min | Agents need a bus; we use A2A + Kafka. |
| What is A2A?   | 1.5   | Agent Card, Task, Message, Artifact. |
| Why Kafka?     | 1     | Async, durable, multi-consumer, scalable. |
| Mapping        | 1.5   | 3 topics + envelope; key = taskId/sessionId. |
| Code           | 2.5   | Models, envelope, producer, consumer. |
| Demo           | 2     | Consumer then Demo; show 3 message types. |
| Wrap-up        | 0.5   | Same protocol, different transport; extend as needed. |
