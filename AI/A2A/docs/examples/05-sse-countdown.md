# Example 5: Async Countdown (Bridge Agent — SDK + SSE)

Phase B of the Part 6 bridge module: same countdown as [Example 4](04-sdk-countdown-poll.md), but the client receives **streamed events** over SSE instead of polling `GetTask`.

**Diagram (Phase B — SSE lane):** [`a2a-bridge-event-paths.png`](../diagrams/a2a-bridge-event-paths.png) · [Word/print](../diagrams/a2a-bridge-event-paths-word.png)

## What it teaches

- Agent Card with `streaming: true`
- `SendStreamingMessage` / SSE delivery of `TaskStatusUpdateEvent` and `TaskArtifactUpdateEvent`
- No poll loop — updates arrive as the server emits them (~every 10s for this countdown)

## Run

**Terminal 1 — server**

```bash
cd bridge
mvn -q package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

**Terminal 2 — SSE client**

```bash
cd bridge
mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.SseCountdownClient
```

Use `A2A_BRIDGE_COUNTDOWN_SECONDS=20` for a shorter demo.

**Phase A poll client** still works (client forces `setStreaming(false)`):

```bash
mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.PollCountdownClient
```

## Client prompt

`Count down 60 seconds`

## Expected flow

1. Client resolves Agent Card (`streaming: true`)
2. `sendMessage` opens an SSE stream (SDK uses streaming automatically when configured)
3. Server emits `statusUpdate` events as countdown progresses
4. Server emits `artifactUpdate` then final `statusUpdate` with `TASK_STATE_COMPLETED`
5. Client prints each event as it arrives — no `GetTask` calls

## Compare Phase A vs Phase B

| | Phase A (`PollCountdownClient`) | Phase B (`SseCountdownClient`) |
|---|--------------------------------|-------------------------------|
| Client config | `setStreaming(false)`, `setPolling(true)` | `setStreaming(true)`, `setPolling(false)` |
| Updates | Client pulls every 5s via `GetTask` | Server pushes `statusUpdate` on change |
| Duplicate lines | Possible (poll faster than server tick) | One line per server emit |
| Server | Same `CountdownAgentExecutor` | Same |

## Code references

- Agent card (`streaming: true`): `bridge/src/main/java/local/a2a/bridge/BridgeAgentCardProducer.java`
- Server executor (unchanged): `bridge/src/main/java/local/a2a/bridge/CountdownAgentExecutorProducer.java`
- SSE client: `bridge/src/main/java/local/a2a/bridge/client/SseCountdownClient.java`

## Captured run (2026-07-07, 20s countdown)

**Agent card** (`streaming: true`):

```json
{
  "name": "Bridge Countdown Agent",
  "capabilities": { "streaming": true, "pushNotifications": false }
}
```

**Client console** (`A2A_BRIDGE_COUNTDOWN_SECONDS=20`):

```text
=== Agent Card ===
name: Bridge Countdown Agent
streaming: true

=== Phase B: Async Countdown (SSE stream) ===
event=statusUpdate | state=TASK_STATE_WORKING | Countdown started: 20s remaining.
event=statusUpdate | state=TASK_STATE_WORKING | Countdown update: 10s remaining.
event=artifactUpdate | Countdown completed at 2026-07-07 14:58:23 AEST.
event=statusUpdate | state=TASK_STATE_COMPLETED | Countdown completed at 2026-07-07 14:58:23 AEST.
Final artifact: Countdown completed at 2026-07-07 14:58:23 AEST.
```

Note: updates arrive **on the server's 10s tick** — no duplicate `10s remaining` lines unlike the poll client.

## Context in the series

Full bridge overview: [`BRIDGE-OVERVIEW.md`](BRIDGE-OVERVIEW.md).

## Next (Phase C)

Done — see [06-push-webhook.md](06-push-webhook.md). Overview: [BRIDGE-OVERVIEW.md](BRIDGE-OVERVIEW.md).
