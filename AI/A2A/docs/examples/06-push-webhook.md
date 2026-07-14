# Example 6: Async Countdown (Bridge Agent — push webhook)

Phase C of the Part 6 bridge module: same countdown as Examples 4–5, but a **disconnected client** registers a push webhook URL and receives task updates via **server POST** (no SSE stream, no `GetTask` poll).

**Diagram (Phase C — push + Kafka on-ramp):** [`a2a-bridge-event-paths.png`](../diagrams/a2a-bridge-event-paths.png) · [Word/print](../diagrams/a2a-bridge-event-paths-word.png)

## What it teaches

- Agent Card with `pushNotifications: true`
- `MessageSendConfiguration.taskPushNotificationConfig` — webhook URL on `SendMessage`
- **Push Notification Service** — your HTTP receiver (`PushNotificationReceiver`) logs payloads (Part 7: `kafkaProducer.send(...)`)
- A2A push is **server → client**; the receiver is a **producer adapter**, not a Kafka consumer wrapper

## Architecture

```text
PushCountdownClient  --SendMessage+push config-->  Bridge Agent (8081)
                                                        |
                        PushNotificationReceiver (8082) <-- POST webhook
```

## Run (single command — embeds receiver)

**Terminal 1 — agent**

```bash
cd bridge
mvn -q package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar
```

**Terminal 2 — push client + embedded receiver on 8082**

```bash
cd bridge
mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.PushCountdownClient
```

Use `A2A_BRIDGE_COUNTDOWN_SECONDS=20` for a shorter demo.

### Three-terminal layout (optional)

```bash
# Terminal 2 — receiver only
mvn exec:java -Dexec.mainClass=local.a2a.bridge.push.PushNotificationReceiver

# Terminal 3 — client (external webhook)
A2A_BRIDGE_WEBHOOK_URL=http://localhost:8082/a2a/webhook \
  mvn exec:java -Dexec.mainClass=local.a2a.bridge.client.PushCountdownClient
```

## Client prompt

`Count down 60 seconds`

## Expected flow

1. Client resolves Agent Card (`pushNotifications: true`)
2. Embedded (or external) receiver listens on `http://localhost:8082/a2a/webhook`
3. `SendMessage` includes `taskPushNotificationConfig` with that URL
4. Agent runs countdown; SDK `BasePushNotificationSender` POSTs `statusUpdate` / `artifactUpdate` JSON on each change
5. Receiver logs each webhook; exits after terminal `TASK_STATE_COMPLETED`

## Compare update paths

| | Phase A (poll) | Phase B (SSE) | Phase C (push) |
|---|----------------|---------------|----------------|
| Client connected? | Yes (poll loop) | Yes (open stream) | **No** (webhook only) |
| Direction | Client pulls | Server streams | **Server POSTs** |
| Receiver | — | — | `PushNotificationReceiver` |

## Code references

- Agent card: `bridge/src/main/java/local/a2a/bridge/BridgeAgentCardProducer.java`
- Webhook receiver: `bridge/src/main/java/local/a2a/bridge/push/PushNotificationReceiver.java`
- Push client: `bridge/src/main/java/local/a2a/bridge/client/PushCountdownClient.java`

## Captured run (2026-07-07, 20s countdown)

**Agent card:**

```json
{
  "name": "Bridge Countdown Agent",
  "capabilities": { "streaming": true, "pushNotifications": true }
}
```

**Client + receiver console** (abbreviated):

```text
=== Agent Card ===
name: Bridge Countdown Agent
pushNotifications: true

=== Phase C: Async Countdown (push webhook) ===
Webhook URL: http://localhost:8082/a2a/webhook
(No SSE/poll — updates arrive via server POST to webhook)
Task created: d0bb3251-995a-4b0f-bad2-9f7455791690

=== webhook #1 @ 2026-07-07T05:12:17Z ===
{"statusUpdate":{"taskId":"d0bb3251-...","status":{"state":"TASK_STATE_WORKING","message":{..."text":"Countdown started: 20s remaining."}...}}}

=== webhook #2 @ 2026-07-07T05:12:27Z ===
{"statusUpdate":{..."text":"Countdown update: 10s remaining."}...}

=== webhook #3 @ 2026-07-07T05:12:37Z ===
{"statusUpdate":{"status":{"state":"TASK_STATE_COMPLETED",...}}}

=== webhook #4 @ 2026-07-07T05:12:37Z ===
{"artifactUpdate":{"artifact":{"artifactId":"countdown-result","parts":[{"text":"Countdown completed at ..."}]}}}

=== Push summary ===
notifications received: 4
```

Payload shape: v1.0 `statusUpdate` / `artifactUpdate` wrappers (what Part 7 will publish to Kafka).

## Context in the series

Full bridge overview: [`BRIDGE-OVERVIEW.md`](BRIDGE-OVERVIEW.md).

## Next (Part 7)

Replace stdout logging in `PushNotificationReceiver` with a Kafka producer on topic `a2a.task.events`.

See [`docs/examples/README.md`](README.md). **Roadmap (Parts 8+):** [`docs/scenarios/README.md`](../scenarios/README.md).
