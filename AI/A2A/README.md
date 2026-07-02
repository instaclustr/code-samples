# Scaling Agent Systems with Kafka and A2A

This repo includes practical A2A protocol materials:

- Intro/blog drafts and diagrams in `docs/`
- A minimal **Clockwork Agent** demo (plain Java A2A) in `src/main/java/local/a2a/examples/`

## The Clockwork Agent — three examples

1. **Synchronous time service**  
   Client sends `SendMessage("What is the current time?")` and gets an immediate `Message` result.

2. **Asynchronous countdown timer**  
   Client sends `SendMessage("Count down 60 seconds")`, gets a `Task`, polls with `GetTask`, receives status updates every 10 seconds, and a final artifact with completion time.

3. **Input-required countdown flow**  
   Client sends `SendMessage("Count down 20 seconds with confirm")`, receives a `Task` in `input-required`, sends follow-up confirmation tied to the same task, then observes normal async progress to completion.

Detailed docs:

- `docs/examples/01-time-service.md`
- `docs/examples/02-async-countdown.md`
- `docs/examples/03-input-required-countdown.md`
- `docs/examples/trace.md` (captured request/response traces)

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

## Current limitations (intentional for intro/demo)

- In-memory task state only (lost on restart).
- No persistent queue/scheduler recovery.
- Single-node process model (no shared task store).
- Polling only (no `SendStreamingMessage`/SSE/webhook push path yet).

## Resume checklist (next steps)

When you pick this project up again, likely next improvements are:

1. Add true server->client update transport (SSE or webhook push).
2. Add durable task persistence (DB/KV) and restart recovery.
3. Add cleanup/retention for completed tasks.
4. Add stricter protocol envelope validation and richer error mapping.
5. Add a second client mode that uses streaming updates instead of polling.
