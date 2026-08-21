# A2A Kafka — diagrams

Mermaid sources and PNG/SVG exports for the Kafka transport POC.

## Diagrams

### RR · stream · notify on Kafka

How the three standard A2A interaction shapes map onto **`a2a.requests`** and **`a2a.updates`**. Kafka **notifications** are async consume — not HTTP webhook POST.

**Recommended — three patterns, one diagram (stacked compare blocks):**

Same Client · Broker · Agent lifelines. Three shaded sections — pick **one pattern per task**, not concurrent.

| Format | File |
|--------|------|
| PNG (2×) | [a2a-kafka-rr-stream-notify-par.png](a2a-kafka-rr-stream-notify-par.png) |
| PNG (4×, Word/print) | [a2a-kafka-rr-stream-notify-par-word.png](a2a-kafka-rr-stream-notify-par-word.png) |
| SVG | [a2a-kafka-rr-stream-notify-par.svg](a2a-kafka-rr-stream-notify-par.svg) |
| Mermaid source | [a2a-kafka-rr-stream-notify-par.mmd](a2a-kafka-rr-stream-notify-par.mmd) |

**Timeline / swimlane — three rows × time →:**

| Format | File |
|--------|------|
| PNG (2×) | [a2a-kafka-rr-stream-notify-timeline.png](a2a-kafka-rr-stream-notify-timeline.png) |
| PNG (4×, Word/print) | [a2a-kafka-rr-stream-notify-timeline-word.png](a2a-kafka-rr-stream-notify-timeline-word.png) |
| SVG | [a2a-kafka-rr-stream-notify-timeline.svg](a2a-kafka-rr-stream-notify-timeline.svg) |
| Mermaid source | [a2a-kafka-rr-stream-notify-timeline.mmd](a2a-kafka-rr-stream-notify-timeline.mmd) |

**Slide cards — three mini sequence diagrams:**

| Pattern | PNG | Source |
|---------|-----|--------|
| 1 — Request/response | [card-1-rr.png](a2a-kafka-rr-stream-notify-card-1-rr.png) | [`.mmd`](a2a-kafka-rr-stream-notify-card-1-rr.mmd) |
| 2 — Streaming | [card-2-stream.png](a2a-kafka-rr-stream-notify-card-2-stream.png) | [`.mmd`](a2a-kafka-rr-stream-notify-card-2-stream.mmd) |
| 3 — Notifications | [card-3-notify.png](a2a-kafka-rr-stream-notify-card-3-notify.png) | [`.mmd`](a2a-kafka-rr-stream-notify-card-3-notify.mmd) |

**Alternate — three-column flowchart:**

| Format | File |
|--------|------|
| PNG (2×) | [a2a-kafka-rr-stream-notify.png](a2a-kafka-rr-stream-notify.png) |
| Mermaid source | [a2a-kafka-rr-stream-notify.mmd](a2a-kafka-rr-stream-notify.mmd) |

### Kafka transport POC (countdown sequence)

Client and agent communicate **only via Kafka topics** — `message/send` on `a2a.requests`, lifecycle events on `a2a.updates`, optional audit consumer.

| Format | File |
|--------|------|
| PNG (2×) | [a2a-kafka-transport-poc.png](a2a-kafka-transport-poc.png) |
| PNG (4×, Word/print) | [a2a-kafka-transport-poc-word.png](a2a-kafka-transport-poc-word.png) |
| SVG | [a2a-kafka-transport-poc.svg](a2a-kafka-transport-poc.svg) |
| Mermaid source | [a2a-kafka-transport-poc.mmd](a2a-kafka-transport-poc.mmd) |

### Kafka transport vs Part 8 backbone

Side-by-side: **this POC** (Kafka replaces HTTP) vs **Atomic Timekeeper** (HTTP A2A + Kafka behind push webhook).

| Format | File |
|--------|------|
| PNG (2×) | [a2a-kafka-transport-vs-backbone.png](a2a-kafka-transport-vs-backbone.png) |
| PNG (4×, Word/print) | [a2a-kafka-transport-vs-backbone-word.png](a2a-kafka-transport-vs-backbone-word.png) |
| SVG | [a2a-kafka-transport-vs-backbone.svg](a2a-kafka-transport-vs-backbone.svg) |
| Mermaid source | [a2a-kafka-transport-vs-backbone.mmd](a2a-kafka-transport-vs-backbone.mmd) |

## Regenerate

Requires Node.js (`npx`):

```bash
cd docs/diagrams
./export-png.sh
```

## Mirror in series repo

Canonical copies also live under `scaling-agent-systems-kafka-a2a/docs/diagrams/` (same filenames).

Back to [project README](../README.md).
