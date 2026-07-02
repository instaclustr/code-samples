# Example 1: Synchronous Time Service (Clockwork Agent)

This example demonstrates the simplest A2A interaction: a `SendMessage` request that returns an immediate `Message` result (no task polling required).

## What it teaches

- Basic JSON-RPC style `SendMessage` call
- Immediate message response path
- Minimal `Message` shape (`role` + `parts[]`)

## Client prompt

`What is the current time?`

## Expected flow

1. Client sends `SendMessage`
2. Server matches `"time"` intent
3. Server responds with `result.message`

## Response shape (simplified)

```json
{
  "result": {
    "message": {
      "kind": "message",
      "role": "agent",
      "parts": [
        { "kind": "text", "text": "The current time is 2026-06-17 14:10:48 AEST." }
      ]
    }
  }
}
```

## Code references

- Server: `src/main/java/local/a2a/examples/A2aDemoServer.java`
  - `handleSendMessage(...)`
  - `immediateMessage(...)`
- Client: `src/main/java/local/a2a/examples/A2aDemoClient.java`
  - `runSynchronousTimeExample(...)`

## Why this matters in A2A terms

Not every request should become a long-running task. This path is the fast-response branch where a direct `Message` is enough.
