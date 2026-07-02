# Example 3: Input-Required Countdown (Clockwork Agent)

This example demonstrates a multi-turn task that first returns `input-required`, then resumes when the client sends follow-up input tied to the same task.

## What it teaches

- `input-required` state semantics
- Continuing an existing task with `taskId` in `SendMessage` params
- Transition from `input-required` -> `working` -> terminal state

## Client prompts

1. Initial: `Count down 20 seconds with confirm`
2. Follow-up: `confirm` (sent with the same `taskId`)

## Expected flow

1. Client sends initial `SendMessage`
2. Server creates a task in `input-required` with a prompt
3. Client sends second `SendMessage` including `taskId` + confirmation text
4. Server resumes task and starts countdown
5. Client polls `GetTask` until completion

## Initial response shape (simplified)

```json
{
  "result": {
    "task": {
      "id": "task-...",
      "status": {
        "state": "input-required",
        "message": {
          "parts": [
            { "kind": "text", "text": "Please confirm countdown start for 20 seconds." }
          ]
        }
      }
    }
  }
}
```

## Follow-up request shape (simplified)

```json
{
  "method": "SendMessage",
  "params": {
    "taskId": "task-...",
    "message": {
      "role": "user",
      "parts": [{ "kind": "text", "text": "confirm" }]
    }
  }
}
```

## Code references

- Server: `src/main/java/local/a2a/examples/A2aDemoServer.java`
  - `createConfirmRequiredCountdownTask(...)`
  - `handleSendMessage(...)` (task continuation branch)
  - `CountdownTask.confirmAndStart()`
- Client: `src/main/java/local/a2a/examples/A2aDemoClient.java`
  - `runInputRequiredCountdownExample(...)`
  - `sendMessageForTask(...)`

## Why this matters in A2A terms

Many real tasks need more input before execution can continue. This example shows how to model that cleanly and continue the same task context across turns.
