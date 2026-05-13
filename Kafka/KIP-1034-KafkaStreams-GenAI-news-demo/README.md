# Free-form news + KIP-1034 DLQ (Kafka Streams)

Self-contained demo: **`demo-news-ff-raw`** accepts **plain UTF-8 prose** or **`NewsItem` JSON**. The topic edge uses **`JsonNewsItemSerde`** (strict Jackson). Non-JSON values fail deserialization → **`LogAndContinueExceptionHandler`** + **`errors.dead.letter.queue.topic.name`** → **`demo-news-ff-streams-dlq`** (KIP-1034 style: raw bytes + `__streams.errors.*` headers). Valid JSON with **empty categories** is deserialized then routed to **`demo-news-ff-app-dlq`** as JSON envelopes. Two small repair mains republish to **`demo-news-ff-repaired`**, which merges back into the topology.

**Background:** [KIP-1034: Dead letter queue in Kafka Streams](https://cwiki.apache.org/confluence/display/KAFKA/KIP-1034%3A+Dead+letter+queue+in+Kafka+Streams).

## Streams application flow

`NewsFreeformStreamsApplication` builds **`NewsFreeformTopology`** on top of two input topics—**`demo-news-ff-raw`** and **`demo-news-ff-repaired`**—both consumed as **`KStream<String, NewsItem>`** using the same **`JsonNewsItemSerde`**. Everything below assumes a record **survived** deserialization on that edge.

1. **Deserialization (per topic, before the graph)**  
   For each byte value, the serde either produces a **`NewsItem`** or throws. On throw, **`LogAndContinueExceptionHandler`** (configured on the app) writes a row to **`demo-news-ff-streams-dlq`** (KIP-1034: raw key/value bytes plus `__streams.errors.*` headers) and **skips** that offset—so prose never enters a `KStream` operator as a value.

2. **Merge**  
   Valid items from **raw** and **repaired** are **`merge`**d into one stream. Repaired records are how out-of-band repair jobs close the loop after handling DLQs.

3. **Split (application logic)**  
   The merged stream is **`split`** with named branches: if `categories` is missing or all blank, the record is mapped to a **JSON envelope** (`reason`, `articleId`, `rawJson`, `detail`) and written to **`demo-news-ff-app-dlq`**. Otherwise it is the **“ok”** branch.

4. **Happy path → sink**  
   **Ok** records are **`flatMap`**ped to one key-value per non-blank category (key = category), **`groupByKey`**, **one-minute tumbling window**, **`aggregate`** (join payloads with `|`), then mapped to **`windowStart:windowEnd:payload...`** strings on **`demo-news-ff-by-category-minute`**.

5. **Outside the topology (repair loops)**  
   **`NewsFreeformKip1034RepairMain`** reads **`demo-news-ff-streams-dlq`** as bytes, turns prose into **`NewsItem` JSON** (Ollama when available, else deterministic tags), and produces to **`demo-news-ff-repaired`**. **`NewsFreeformAppDlqRepairMain`** reads **`demo-news-ff-app-dlq`**, fills categories for **`MISSING_CATEGORIES`** envelopes, and produces to **`demo-news-ff-repaired`**. The Streams app consumes **`repaired`** with the same strict serde, so those rows re-enter the flow at step 2.

For diagrams (including serde vs merge), see **[docs/topology.md](docs/topology.md)**.

## Two failure modes (why both examples)

The producer sends **two “bad” rows** on purpose so you can see **where** Kafka Streams can still fail a record, and **who** owns recovery.

| | **Free-form prose** (museum text) | **JSON with empty `categories`** |
|---|-----------------------------------|----------------------------------|
| **What breaks** | The bytes are **not JSON** at all, so they cannot become a `NewsItem`. | Jackson succeeds: the payload is valid **`NewsItem` JSON**, but **`hasAnyCategory()`** is false. |
| **Where it is detected** | In **`JsonNewsItemSerde`** during **deserialization**, before any `KStream` transform runs. | After deserialization, in **`NewsFreeformTopology`** on the merged **`NewsItem`** stream (`split` branch). |
| **How it is “caught”** | The deserializer **throws** `SerializationException`. Streams invokes **`LogAndContinueExceptionHandler`**, which (with **`errors.dead.letter.queue.topic.name`**) **produces** to **`demo-news-ff-streams-dlq`**. The read offset still advances, so the app keeps running. | No throw at the edge: the record is a normal **`NewsItem`**. The topology **`mapValues`** the row into a **DLQ envelope** string and **`to`s** **`demo-news-ff-app-dlq`**. |
| **What lands on the DLQ** | **Raw bytes** (same as on the wire) plus **`__streams.errors.*`** headers (exception class, source topic/partition/offset, etc.). | **Application-defined JSON** (`reason`, `articleId`, `rawJson`, `detail`)—easy for a dedicated consumer to parse. |
| **Who repairs it** | **`NewsFreeformKip1034RepairMain`**: `ByteArrayDeserializer`, interpret UTF-8, **`structurePlainTextToNewsItemJson`** (or fallbacks), produce to **`demo-news-ff-repaired`**. | **`NewsFreeformAppDlqRepairMain`**: parse envelope, **`inferCategoriesJson`** (or heading-token fallback), produce to **`demo-news-ff-repaired`**. |
| **Teaching point** | **KIP-1034 / framework DLQ**: serde and wire-format problems are handled **outside** your `map`/`branch` code; you need a consumer that understands **bytes + error headers**. | **Application DLQ**: your **business rules** after a successful parse; same Kafka machinery for produce/consume, but **you** chose the envelope schema and the policy. |

A third producer record (**valid JSON with real categories**) exercises only the happy path and the **windowed** sink.

## Quick start

```bash
docker compose up -d
mvn -q test
./scripts/demo.sh
# or: ./scripts/demo.sh --skip-ollama
```

**Optional — LLM-fed traffic** (requires Ollama; publishes valid `NewsItem` JSON to `demo-news-ff-raw`):

```bash
mvn -q exec:java -Dexec.mainClass=local.kip1034.dlq.news.freeform.NewsFreeformRandomLlmProducerMain
# NEWS_RANDOM_COUNT=5 NEWS_GEN_THEME="space and astronomy" ...
# NEWS_APPEND_FIXTURE_EDGE_CASES=true  # append missing-categories + serde-failure fixtures after LLM rows
mvn -q exec:java -Dexec.mainClass=local.kip1034.dlq.news.freeform.NewsFreeformStreamingLlmProducerMain
# NEWS_LLM_BATCH_SIZE=4 NEWS_LLM_STREAM_INTERVAL_SEC=30 NEWS_LLM_STREAM_MAX_BATCHES=5 ...
```

Broker: `localhost:9092` (see `docker-compose.yml`).

## Topics

| Topic | Role |
|--------|------|
| `demo-news-ff-raw` | Prose or `NewsItem` JSON (bytes). |
| `demo-news-ff-repaired` | Valid JSON; merge source. |
| `demo-news-ff-streams-dlq` | **KIP-1034** DLQ (serde failures). |
| `demo-news-ff-app-dlq` | **Application** envelopes (`MISSING_CATEGORIES`). |
| `demo-news-ff-by-category-minute` | Windowed sink. |

## JSON on the wire

**`NewsItem`** (values on **`demo-news-ff-raw`** and **`demo-news-ff-repaired`** after deserialization) is a JSON **object** with string fields **`articleId`**, **`heading`**, **`article`**, and a **`categories`** array of strings. Jackson ignores unknown keys. **`JsonNewsItemSerde`** throws on invalid JSON or on shapes that do not deserialize to `NewsItem` (for example a JSON **array** at the root, or `"categories"` as a string instead of an array). Plain UTF-8 prose is not JSON and hits the **KIP-1034** DLQ.

If deserialization succeeds but every category is missing, null, empty, or only whitespace, the topology emits an **application DLQ envelope** to **`demo-news-ff-app-dlq`** (`reason`, `articleId`, `rawJson`, `detail` — see code-linked doc below).

**Happy path example** (from `NewsFreeformProducerMain`):

```json
{
  "articleId": "ff-ok-1",
  "heading": "Local weather",
  "article": "Rain expected.",
  "categories": ["weather", "local"]
}
```

**Valid `NewsItem` JSON, but not the happy path** (empty tags → app DLQ):

```json
{
  "articleId": "ff-missing-1",
  "heading": "Markets rally",
  "article": "Stocks rose on optimism.",
  "categories": []
}
```

**Serde failure examples** (bytes on raw topic; land on **`demo-news-ff-streams-dlq`** with error headers, not as `NewsItem`):

- Any non-JSON body, e.g. museum prose from the fixed producer.
- Malformed JSON such as `{ not json` (see `NewsFreeformFixtureEdgeCases`).

**Windowed topic** **`demo-news-ff-by-category-minute`** does not store a single JSON document: values look like **`{startMillis}:{endMillis}:{NewsStreamPayload}|{NewsStreamPayload}|…`** where each payload uses the key **`category`** (singular), not `categories`.

Full spec, more incorrect cases, and envelope / repair notes: **[docs/json-formats.md](docs/json-formats.md)**.

## Main classes

| Class | Purpose |
|--------|---------|
| `NewsFreeformStreamsApplication` | Kafka Streams app + DLQ config. |
| `NewsFreeformTopology` | Merge, split, app DLQ, windowed aggregate. |
| `JsonNewsItemSerde` | Strict `NewsItem` serde at the edge. |
| `NewsFreeformKip1034RepairMain` | KIP-1034 DLQ → Ollama / deterministic → `repaired`. |
| `NewsFreeformAppDlqRepairMain` | App DLQ envelopes → Ollama / fallback → `repaired`. |
| `NewsFreeformProducerMain` | Fixed sample: museum prose + JSON (missing categories + OK). |
| `NewsFreeformRandomLlmProducerMain` | Ollama: **`generateSyntheticArticles`** → `demo-news-ff-raw` (burst; optional DLQ fixtures). |
| `NewsFreeformStreamingLlmProducerMain` | Ollama: same prompt in a timed loop → `demo-news-ff-raw`. |
| `NewsFreeformFixtureEdgeCases` | Deterministic missing-categories + bad-bytes for `NEWS_APPEND_FIXTURE_EDGE_CASES`. |
| `NewsFreeformBootstrap` | Creates topics via AdminClient. |

## Environment

| Variable | Default | Used by |
|----------|---------|---------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | All |
| `NEWS_FF_STREAMS_APPLICATION_ID` | `demo-news-freeform-v1` | Streams only |
| `OLLAMA_URL` | `http://localhost:11434` | Repair mains; LLM producers |
| `OLLAMA_MODEL` | `llama3:latest` | Repair mains; LLM producers |
| `NEWS_RANDOM_COUNT` | `10` | `NewsFreeformRandomLlmProducerMain` |
| `NEWS_GEN_THEME` | (empty) | Random / streaming LLM producers (theme hint) |
| `NEWS_APPEND_FIXTURE_EDGE_CASES` | `false` | Random LLM producer: append app + KIP-1034 fixture rows |
| `NEWS_LLM_BATCH_SIZE` | `5` | Streaming LLM producer (max 40) |
| `NEWS_LLM_STREAM_INTERVAL_SEC` | `60` | Streaming LLM producer |
| `NEWS_LLM_STREAM_MAX_BATCHES` | `0` | Streaming LLM producer (`0` = until Ctrl+C) |

## Test and demo output (example)

Example run: **`mvn test`**, **`NewsFreeformRandomLlmProducerMain`** against **`localhost:9092`**, and **`./scripts/demo.sh --skip-ollama --skip-model-pull`** with Docker broker **`news-freeform-kafka-broker`** already up. (LLM-generated headings and tags will differ on each run; repair behavior depends on whether **`ollama serve`** is reachable at **`OLLAMA_URL`**—`--skip-ollama` only skips *starting* Ollama in the script.)

### Unit tests

```
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0  -- in NewsFreeformTopologyIT
BUILD SUCCESS
```

### Random LLM producer (burst)

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092
mvn compile exec:java -Dexec.mainClass=local.kip1034.dlq.news.freeform.NewsFreeformRandomLlmProducerMain -Dexec.args="2"
```

Example log lines:

```
LLM round 1: requesting 2 article(s), 0 total so far / 2
Sent key=ARTICLE-00001 heading=EU Agrees on New Trade Policy Framework (1 of 2)
Sent key=ARTICLE-00002 heading=Indian Tech Startup Revolutionizes Healthcare Delivery (2 of 2)
Published 2 record(s) to demo-news-ff-raw (2 from LLM)
```

### End-to-end script (`scripts/demo.sh`)

The script deletes **`demo-news-ff*`** topics, starts Streams + both repair mains + **`NewsFreeformProducerMain`**, waits for windows, then **`kafka-console-consumer`** dumps samples. Per-run logs live under **`/tmp/news-freeform-demo-<pid>/`** (for example `streams.log`, `kip-repair.log`, `app-repair.log`, `producer.log`).

**Streams** reaches **`RUNNING`** and logs the five topic names. **Serde** rejects the museum prose on **`demo-news-ff-raw`** (expected):

```
WARN ... LogAndContinueExceptionHandler - Exception caught during Deserialization, ... topic: demo-news-ff-raw ...
SerializationException: Failed to deserialize NewsItem: Midtown Science Museum officials said...
Caused by: JsonParseException: Unrecognized token 'Midtown'...
```

**Repairs** (when Ollama answers the repair mains), example:

```
KIP-1034 DLQ offset=0 key=ff-prose-1 preview=Midtown Science Museum officials said...
KIP-1034 repair source=OLLAMA model=llama3:latest key=ff-prose-1 categories=[Robotics, Accidents, Science Museums]
Repaired (KIP-1034 path) -> demo-news-ff-repaired key=ff-prose-1
Repaired (app DLQ) -> demo-news-ff-repaired key=ff-missing-1
```

**Topic dumps** at the end of the script: **`demo-news-ff-raw`** (museum text + `ff-missing-1` / `ff-ok-1` JSON), **`demo-news-ff-streams-dlq`** (raw prose bytes for the museum record), **`demo-news-ff-app-dlq`** (`MISSING_CATEGORIES` envelope), **`demo-news-ff-repaired`** (merged JSON from both repair paths), **`demo-news-ff-by-category-minute`** (windowed aggregates, e.g. categories `weather`, `local`, `markets`, …).

## Documentation

- **[docs/json-formats.md](docs/json-formats.md)** — Expected **`NewsItem`** JSON, **application DLQ** envelope shape, **windowed** value layout; correct vs incorrect examples; **§5** semantics (seek-to-end repairs, keys vs `articleId`, merge, `LogAndContinue`, test scope).
- **[docs/topology.md](docs/topology.md)** — Mermaid diagrams; **§1** documents all **producers** (fixed sample vs LLM random/streaming). **§2** reproduces the **verbatim** Ollama prompt strings from **`NewsLlmClient.java`** (same three prompts as below).

### LLM prompts (`NewsLlmClient`)

This repo builds **exactly three** user-facing prompts (all via `POST {OLLAMA_URL}/api/generate`). The **full text** of each is in **`docs/topology.md` §2**; the Java source of truth is **`NewsLlmClient.java`**.

| # | Method | Typical callers | Ollama read timeout | Verbatim prompt in docs |
|---|--------|-----------------|---------------------|-------------------------|
| 1 | **`inferCategoriesJson`** | `NewsFreeformAppDlqRepairMain` (`MISSING_CATEGORIES`, `BAD_JSON` path) | 120s | **[§2.1](docs/topology.md#llm-prompt-infer-categories)** |
| 2 | **`structurePlainTextToNewsItemJson`** | `NewsFreeformKip1034RepairMain` (KIP-1034 DLQ bytes as UTF-8) | 240s | **[§2.2](docs/topology.md#llm-prompt-structure-plaintext)** |
| 3 | **`generateSyntheticArticles`** | `NewsFreeformRandomLlmProducerMain`, `NewsFreeformStreamingLlmProducerMain` | 420s | **[§2.3](docs/topology.md#llm-prompt-synthetic-articles)** |

There are **no other** LLM prompt strings in this repository (fixtures and the fixed producer do not call Ollama).

### Producers and prompts

- **`NewsFreeformProducerMain`** — no LLM; museum text from **`museum-demo-incident-sample.txt`** plus two inline JSON rows (**§1** in `docs/topology.md`).
- **`NewsFreeformRandomLlmProducerMain`** / **`NewsFreeformStreamingLlmProducerMain`** — **`generateSyntheticArticles`** (**§2.3**). Optional **`NEWS_APPEND_FIXTURE_EDGE_CASES`** on the random main appends **`NewsFreeformFixtureEdgeCases`** for DLQ drills (deterministic payloads, not LLM prompts).
- **Repair mains** — **`inferCategoriesJson`** / **`structurePlainTextToNewsItemJson`** (**§2.1–2.2**).

Originally part of the broader **`kip-1034-kafka-streams-dlq`** project (sibling under `Applications/Experiments/`). This repo keeps only the free-form news path.
