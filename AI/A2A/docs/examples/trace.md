# A2A Demo Trace — Clockwork Agent (Captured Run)

This document captures concrete request/response traces from the **Clockwork Agent** (`A2aDemoServer` / `A2aDemoClient`) for all three examples.

- Example 1: synchronous time service
- Example 2: asynchronous countdown task
- Example 3: input-required + follow-up confirmation

---

## Example 1 — Synchronous time service

### Client -> Server (`SendMessage`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-time-1",
  "method": "SendMessage",
  "params": {
    "message": {
      "messageId": "msg-time-1",
      "role": "user",
      "parts": [{ "kind": "text", "text": "What is the current time?" }]
    }
  }
}
```

### Server -> Client (immediate `Message`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-time-1",
  "result": {
    "message": {
      "kind": "message",
      "messageId": "msg-504a2821-fc67-45da-828a-0cd1490587cc",
      "role": "agent",
      "parts": [{ "kind": "text", "text": "The current time is 2026-06-17 14:21:25 AEST." }]
    }
  }
}
```

---

## Example 2 — Async countdown (30s)

### Client -> Server (`SendMessage`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-cd-1",
  "method": "SendMessage",
  "params": {
    "message": {
      "messageId": "msg-cd-1",
      "role": "user",
      "parts": [{ "kind": "text", "text": "Count down 30 seconds" }]
    }
  }
}
```

### Server -> Client (initial `Task`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-cd-1",
  "result": {
    "task": {
      "kind": "task",
      "id": "task-7efa6fd9-cadc-4059-9e5c-a55c4e8aadbf",
      "contextId": "ctx-task-7efa6fd9-cadc-4059-9e5c-a55c4e8aadbf",
      "status": {
        "state": "working",
        "message": {
          "role": "agent",
          "parts": [{ "kind": "text", "text": "Countdown started: 30s remaining." }]
        },
        "timestamp": "2026-06-17T04:21:49.481063Z"
      }
    }
  }
}
```

### Client -> Server (`GetTask`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-cd-get-1",
  "method": "GetTask",
  "params": { "taskId": "task-7efa6fd9-cadc-4059-9e5c-a55c4e8aadbf" }
}
```

### Server -> Client (progress sample)

```json
{
  "jsonrpc": "2.0",
  "id": "req-cd-get-1",
  "result": {
    "kind": "task",
    "id": "task-7efa6fd9-cadc-4059-9e5c-a55c4e8aadbf",
    "status": {
      "state": "working",
      "message": {
        "role": "agent",
        "parts": [{ "kind": "text", "text": "Countdown update: 20s remaining." }]
      }
    }
  }
}
```

### Server -> Client (final `GetTask`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-final-task-7efa6fd9-cadc-4059-9e5c-a55c4e8aadbf",
  "result": {
    "kind": "task",
    "id": "task-7efa6fd9-cadc-4059-9e5c-a55c4e8aadbf",
    "status": {
      "state": "completed",
      "message": {
        "role": "agent",
        "parts": [{ "kind": "text", "text": "Countdown completed at 2026-06-17 14:22:19 AEST." }]
      }
    },
    "artifacts": [
      {
        "artifactId": "artifact-task-7efa6fd9-cadc-4059-9e5c-a55c4e8aadbf",
        "name": "countdown-result",
        "parts": [{ "kind": "text", "text": "Countdown completed at 2026-06-17 14:22:19 AEST." }]
      }
    ]
  }
}
```

---

## Example 3 — Input-required countdown (20s + confirm)

### Client -> Server (initial `SendMessage`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-ir-1",
  "method": "SendMessage",
  "params": {
    "message": {
      "messageId": "msg-ir-1",
      "role": "user",
      "parts": [{ "kind": "text", "text": "Count down 20 seconds with confirm" }]
    }
  }
}
```

### Server -> Client (`Task` in `input-required`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-ir-1",
  "result": {
    "task": {
      "kind": "task",
      "id": "task-5ae409b7-d404-4db3-ae09-4f68a074f78b",
      "contextId": "ctx-task-5ae409b7-d404-4db3-ae09-4f68a074f78b",
      "status": {
        "state": "input-required",
        "message": {
          "role": "agent",
          "parts": [{ "kind": "text", "text": "Please confirm countdown start for 20 seconds." }]
        },
        "timestamp": "2026-06-17T04:22:01.647731Z"
      }
    }
  }
}
```

### Client -> Server (follow-up `SendMessage` with `taskId`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-ir-confirm-1",
  "method": "SendMessage",
  "params": {
    "taskId": "task-5ae409b7-d404-4db3-ae09-4f68a074f78b",
    "message": {
      "messageId": "msg-ir-2",
      "role": "user",
      "parts": [{ "kind": "text", "text": "confirm" }]
    }
  }
}
```

### Server -> Client (task resumed to `working`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-ir-confirm-1",
  "result": {
    "task": {
      "kind": "task",
      "id": "task-5ae409b7-d404-4db3-ae09-4f68a074f78b",
      "status": {
        "state": "working",
        "message": {
          "role": "agent",
          "parts": [{ "kind": "text", "text": "Countdown started: 20s remaining." }]
        },
        "timestamp": "2026-06-17T04:22:01.691147Z"
      }
    }
  }
}
```

### Server -> Client (final `GetTask`)

```json
{
  "jsonrpc": "2.0",
  "id": "req-final-task-5ae409b7-d404-4db3-ae09-4f68a074f78b",
  "result": {
    "kind": "task",
    "id": "task-5ae409b7-d404-4db3-ae09-4f68a074f78b",
    "status": {
      "state": "completed",
      "message": {
        "role": "agent",
        "parts": [{ "kind": "text", "text": "Countdown completed at 2026-06-17 14:22:21 AEST." }]
      }
    },
    "artifacts": [
      {
        "artifactId": "artifact-task-5ae409b7-d404-4db3-ae09-4f68a074f78b",
        "name": "countdown-result",
        "parts": [{ "kind": "text", "text": "Countdown completed at 2026-06-17 14:22:21 AEST." }]
      }
    ]
  }
}
```

---

## Notes

- Timestamps and UUIDs are runtime-generated and will differ each run.
- This demo uses polling (`GetTask`) rather than streaming/push transport.
