# Example 4: Async Countdown (Bridge Agent — SDK + poll)

Phase A of the Part 6 bridge module: same long-running countdown as [Example 2](02-async-countdown.md), but using the official **`a2a-java`** SDK (Quarkus reference server + SDK client).

**Diagram (Phase A — poll lane):** [`a2a-bridge-event-paths.png`](../diagrams/a2a-bridge-event-paths.png) · [Word/print](../diagrams/a2a-bridge-event-paths-word.png)

## What it teaches

- Porting Clockwork countdown semantics to `AgentExecutor` + `AgentEmitter`
- `GetTask` polling with the SDK client (`PollCountdownClient`)
- Agent Card at `/.well-known/agent-card.json` (v1.0 path)

## Run

**Terminal 1 — server**

```bash
cd bridge
mvn -q package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

**Terminal 2 — client**

```bash
cd bridge
mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.PollCountdownClient
```

Use `A2A_BRIDGE_COUNTDOWN_SECONDS=20` for a shorter demo.

## Client prompt

`Count down 60 seconds`

## Expected flow

1. Client resolves Agent Card from `http://localhost:8081`
2. `SendMessage` → `Task` with `TASK_STATE_WORKING` and `Countdown started: Ns remaining.`
3. Client polls `GetTask(taskId)` every 5 seconds
4. Server updates status every 10 seconds (`Countdown update: …`)
5. Server completes and attaches `countdown-result` artifact

## Code references

- Server card: `bridge/src/main/java/local/a2a/bridge/BridgeAgentCardProducer.java`
- Server executor: `bridge/src/main/java/local/a2a/bridge/CountdownAgentExecutorProducer.java`
- Poll client: `bridge/src/main/java/local/a2a/bridge/client/PollCountdownClient.java`

## Captured run (2026-07-07, 20s countdown)

**Agent card** (`GET /.well-known/agent-card.json`):

```json
{
  "name": "Bridge Countdown Agent",
  "description": "Part 6 bridge agent: async countdown via official a2a-java SDK (poll baseline)",
  "version": "1.0.0",
  "capabilities": { "streaming": true, "pushNotifications": false },
  "skills": [{ "id": "countdown", "name": "Countdown Timer" }],
  "supportedInterfaces": [{ "protocolBinding": "JSONRPC", "url": "http://localhost:8081" }]
}
```

**Client console** (`A2A_BRIDGE_COUNTDOWN_SECONDS=20`):

```text
=== Agent Card ===
name: Bridge Countdown Agent
skills: 1

=== Phase A: Async Countdown (poll via GetTask) ===
Task created: 55f89ce0-413b-442e-a033-149e19e2a9f8
state=TASK_STATE_WORKING | Countdown started: 20s remaining.
state=TASK_STATE_WORKING | Countdown update: 10s remaining.
state=TASK_STATE_WORKING | Countdown update: 10s remaining.
state=TASK_STATE_COMPLETED | Countdown completed at 2026-07-07 14:52:45 AEST.
Final artifact: Countdown completed at 2026-07-07 14:52:45 AEST.
```

The duplicate `10s remaining` line is expected: the client polls every 5s while the server updates every 10s.

## Context in the series

Full bridge overview: [`BRIDGE-OVERVIEW.md`](BRIDGE-OVERVIEW.md) (what it demonstrates, how it works, limitations).

## Next (Phase B)

Done — see [05-sse-countdown.md](05-sse-countdown.md). Overview: [BRIDGE-OVERVIEW.md](BRIDGE-OVERVIEW.md).
