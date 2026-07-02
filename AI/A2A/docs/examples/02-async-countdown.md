# Example 2: Async Countdown Timer (Clockwork Agent)

This example demonstrates the long-running A2A pattern:

- `SendMessage` returns a `Task`
- client tracks progress via repeated `GetTask`
- server transitions to terminal state and returns a final artifact

## What it teaches

- Task-oriented workflow (`submitted`/`working`/`completed`)
- Polling-based progress tracking
- Final artifact delivery

## Client prompt

`Count down 60 seconds`

## Expected flow

1. Client sends `SendMessage`
2. Server creates a countdown task and returns `result.task`
3. Client polls `GetTask(taskId)` every few seconds
4. Server updates status message every 10 seconds
5. Server completes task and attaches final artifact

## Typical status updates

- `Countdown started: 60s remaining.`
- `Countdown update: 50s remaining.`
- `Countdown update: 40s remaining.`
- ...
- `Countdown completed at <timestamp>.`

## Response shapes (simplified)

Initial `SendMessage` result:

```json
{
  "result": {
    "task": {
      "id": "task-...",
      "status": { "state": "working" }
    }
  }
}
```

Final `GetTask` result:

```json
{
  "result": {
    "task": {
      "status": { "state": "completed" },
      "artifacts": [
        {
          "name": "countdown-result",
          "parts": [
            { "kind": "text", "text": "Countdown completed at ..." }
          ]
        }
      ]
    }
  }
}
```

## Code references

- Server: `src/main/java/local/a2a/examples/A2aDemoServer.java`
  - `createCountdownTask(...)`
  - `handleGetTask(...)`
  - `CountdownTask.start()`
- Client: `src/main/java/local/a2a/examples/A2aDemoClient.java`
  - `runAsyncCountdownExample(...)`
  - `getTask(...)`

## Why this matters in A2A terms

This is the core asynchronous model behind many real agent workloads: the initial call returns quickly while task state and outputs evolve over time.
