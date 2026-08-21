# A2A Kafka — diagrams

Mermaid sources and PNG/SVG exports for the Kafka transport POC.

## Diagrams

### RR · stream · notify on Kafka (`bench:rr`, `bench:stream`, `bench:notify`)

How the benchmark agent maps the three standard A2A interaction shapes onto **`a2a.requests`** and **`a2a.updates`** (or per-worker reply topics). Kafka **notify** is async consume — not HTTP webhook POST.

| Format | File |
|--------|------|
| PNG (2×) | [a2a-kafka-rr-stream-notify.png](a2a-kafka-rr-stream-notify.png) |
| PNG (4×, Word/print) | [a2a-kafka-rr-stream-notify-word.png](a2a-kafka-rr-stream-notify-word.png) |
| SVG | [a2a-kafka-rr-stream-notify.svg](a2a-kafka-rr-stream-notify.svg) |
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
