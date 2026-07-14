# Scaling Agent Systems with Kafka and A2A

This repo includes practical A2A protocol materials:

- Intro/blog drafts and diagrams in `docs/`
- A minimal **Clockwork Agent** demo (plain Java A2A) in `src/main/java/local/a2a/examples/`
- **Part 6 bridge** — blog: **Quartz Chronometer Agent**; Part 7: **Atomic Timekeeper** ([`docs/SERIES-NAMING.md`](docs/SERIES-NAMING.md)) — in [`bridge/`](bridge/)

## The Clockwork Agent — three examples

1. **Synchronous time service**  
   Client sends `SendMessage("What is the current time?")` and gets an immediate `Message` result.

2. **Asynchronous countdown timer**  
   Client sends `SendMessage("Count down 60 seconds")`, gets a `Task`, polls with `GetTask`, receives status updates every 10 seconds, and a final artifact with completion time.

3. **Input-required countdown flow**  
   Client sends `SendMessage("Count down 20 seconds with confirm")`, receives a `Task` in `input-required`, sends follow-up confirmation tied to the same task, then observes normal async progress to completion.

Detailed docs:

- `docs/SERIES-NAMING.md` — **blog names**: Clockwork → Quartz Chronometer Agent → Atomic Timekeeper
- `docs/examples/README.md` — **index**: why bridge, what's done, what's next
- `docs/examples/BRIDGE-OVERVIEW.md` — **bridge overview**: demonstrates, how it works, limitations
- `docs/examples/01-time-service.md`
- `docs/examples/02-async-countdown.md`
- `docs/examples/03-input-required-countdown.md`
- `docs/examples/04-sdk-countdown-poll.md` — bridge Phase A (SDK + poll)
- `docs/examples/05-sse-countdown.md` — bridge Phase B (SDK + SSE)
- `docs/examples/06-push-webhook.md` — bridge Phase C (push webhook)
- `docs/scenarios/README.md` — **roadmap**: AI agents, orchestration, live scenarios (Parts 8+)
- `docs/examples/trace.md` — Clockwork captured traces

## Run

Use two terminals.

### 1) Start the Clockwork Agent

```bash
cd /Users/pbrebner/Applications/Experiments/scaling-agent-systems-kafka-a2a
mvn -q compile exec:java -Dexec.mainClass=local.a2a.examples.A2aDemoServer
```

### 2) Run the demo client

```bash
cd /Users/pbrebner/Applications/Experiments/scaling-agent-systems-kafka-a2a
mvn -q compile exec:java -Dexec.mainClass=local.a2a.examples.A2aDemoClient
```

The client will:

- fetch and print the Agent Card,
- run the synchronous time example,
- run the async countdown example and print task status until terminal state,
- run an `input-required` example and then continue the same task with a confirmation message.

## Bridge agent (Part 6 — Phases A–C)

Official `a2a-java` SDK on port **8081**: countdown task with **poll**, **SSE**, and **push webhook** clients. See [`docs/examples/BRIDGE-OVERVIEW.md`](docs/examples/BRIDGE-OVERVIEW.md).

```bash
cd bridge && mvn -q package -DskipTests && java -jar target/quarkus-app/quarkus-run.jar
cd bridge && mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.PollCountdownClient    # A
cd bridge && mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.SseCountdownClient     # B
cd bridge && mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.PushCountdownClient    # C
```

**Next:** Part 7 — Kafka task-event topic. See [`docs/examples/README.md`](docs/examples/README.md).

## Notes

- This is intentionally minimal and educational (not a full production A2A stack).
- Transport is HTTP + JSON-RPC style request envelopes.
- The async path demonstrates task semantics (`submitted`/`working`/`completed`) and artifact delivery.
- The third example demonstrates `input-required` and multi-turn continuation on the same task (`taskId` in `SendMessage` params).

## How server-to-client updates work in this demo

Important: this demo currently uses **polling**, not true server push.

- Server-side task state changes are real (countdown updates every 10s).
- Client learns about changes by calling `GetTask(taskId)` repeatedly.
- So "updates" are delivered via request/response, not SSE/webhook streams.

In other words, it simulates the long-running behavior while keeping transport simple.

## How long-running tasks keep running on the agent side

The server separates request handling from background execution:

1. `HttpServer` keeps the JVM process running and accepts RPC requests.
2. Tasks are stored in-memory in `TASKS` (`ConcurrentHashMap`).
3. `CountdownTask.start()` schedules background work with `ScheduledExecutorService`.
4. Background timer updates task fields (`state`, `statusMessage`, `remainingSeconds`, final artifact).
5. `GetTask` reads the current task snapshot and returns it to the client.

## Current limitations

**Clockwork:** in-memory tasks, polling only, hand-rolled JSON-RPC.

**Bridge:** Phases A–C done (poll, SSE, push); in-memory state; no Kafka yet (Part 7). See [`docs/examples/BRIDGE-OVERVIEW.md`](docs/examples/BRIDGE-OVERVIEW.md#whats-missing-or-simplified).

## Roadmap (bridge → Kafka)

1. ~~Phase A: SDK + poll baseline~~ **done**
2. ~~Phase B: SSE streaming client~~ **done**
3. ~~Phase C: push webhook receiver~~ **done**
4. Part 7: Kafka task-event topic + consumers — [`docs/kafka/README.md`](docs/kafka/README.md)
5. Parts 8+: AI agents & orchestration — [`docs/scenarios/README.md`](docs/scenarios/README.md)
