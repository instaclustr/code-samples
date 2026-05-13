# JSON and wire formats

This demo uses a few distinct shapes. Types live in [`NewsItem.java`](../src/main/java/local/kip1034/dlq/news/NewsItem.java), [`NewsJson.java`](../src/main/java/local/kip1034/dlq/news/NewsJson.java), [`NewsStreamPayload.java`](../src/main/java/local/kip1034/dlq/news/NewsStreamPayload.java), and [`NewsFreeformTopology.java`](../src/main/java/local/kip1034/dlq/news/freeform/NewsFreeformTopology.java).

---

## 1. `NewsItem` (topic values: `demo-news-ff-raw`, `demo-news-ff-repaired`)

**Role.** After bytes pass through [`JsonNewsItemSerde`](../src/main/java/local/kip1034/dlq/news/freeform/JsonNewsItemSerde.java), the value is a **`NewsItem`**: Jackson maps a JSON **object** to fields `articleId`, `heading`, `article` (strings) and `categories` (array of strings). Extra object keys are **ignored** (`@JsonIgnoreProperties(ignoreUnknown = true)` on `NewsItem`).

**Happy path.** At least one element in `categories` must be non-null and non-blank (`hasAnyCategory()`). Those items are exploded by category and windowed.

### Correct — valid for the serde and the “ok” branch

```json
{
  "articleId": "ff-ok-1",
  "heading": "Local weather",
  "article": "Rain expected.",
  "categories": ["weather", "local"]
}
```

Omitted keys deserialize as `null` / empty list; that usually pushes the record toward the **missing-categories** branch rather than serde failure, as long as the document is still valid JSON object syntax.

### Correct JSON for the serde, but routed to the **application DLQ**

`categories` is missing, null, empty, or contains only blank strings. Deserialization succeeds; the topology writes a **`MISSING_CATEGORIES`** envelope to **`demo-news-ff-app-dlq`**.

```json
{
  "articleId": "ff-missing-1",
  "heading": "Markets rally",
  "article": "Stocks rose on optimism.",
  "categories": []
}
```

```json
{
  "articleId": "x",
  "heading": "h",
  "article": "body",
  "categories": ["", "   "]
}
```

### Incorrect for `JsonNewsItemSerde` — **KIP-1034 Streams DLQ** (`demo-news-ff-streams-dlq`)

The deserializer throws `SerializationException`; the handler stores **raw key/value bytes** (not this JSON schema) plus `__streams.errors.*` headers.

**Not JSON at all (plain prose):**

```
Midtown Science Museum officials said the new wing will open in April.
```

**Malformed JSON** (same idea as [`NewsFreeformFixtureEdgeCases#nonJsonProseOrBadJson`](../src/main/java/local/kip1034/dlq/news/freeform/NewsFreeformFixtureEdgeCases.java)):

```text
{ not json
```

**JSON, but not a single `NewsItem` object** (root must be `{ ... }`, not an array):

```json
[
  {
    "articleId": "a1",
    "heading": "One",
    "article": "Story.",
    "categories": ["news"]
  }
]
```

**Wrong type for `categories`** (expects a JSON array of strings; a single string typically fails deserialization):

```json
{
  "articleId": "bad-cat-type",
  "heading": "h",
  "article": "a",
  "categories": "not-an-array"
}
```

---

## 2. Application DLQ envelope (`demo-news-ff-app-dlq`)

**Role.** String value built by [`NewsJson.dlqEnvelope`](../src/main/java/local/kip1034/dlq/news/NewsJson.java): a JSON object with four **string** fields:

| Field | Meaning |
|--------|--------|
| `reason` | e.g. `MISSING_CATEGORIES` (emitted by [`NewsFreeformTopology`](../src/main/java/local/kip1034/dlq/news/freeform/NewsFreeformTopology.java)). |
| `articleId` | Copy of the item’s `articleId` when known (may be empty in error paths). |
| `rawJson` | Stringified **`NewsItem`** JSON that failed the category rule. |
| `detail` | Human-readable explanation. |

### Example (`MISSING_CATEGORIES`)

Pretty-printed for reading; on the wire it is one line.

```json
{
  "reason": "MISSING_CATEGORIES",
  "articleId": "ff-missing-1",
  "rawJson": "{\"articleId\":\"ff-missing-1\",\"heading\":\"Markets rally\",\"article\":\"Stocks rose on optimism.\",\"categories\":[]}",
  "detail": "categories empty or all blank"
}
```

### Incorrect for [`NewsFreeformAppDlqRepairMain`](../src/main/java/local/kip1034/dlq/news/freeform/NewsFreeformAppDlqRepairMain.java)

- **Missing or blank `rawJson`** — repair returns empty (nothing to parse).
- **Invalid outer JSON** — envelope not parsed.
- **`reason` not handled** — only `MISSING_CATEGORIES` and `BAD_JSON` are implemented; anything else is skipped.

`BAD_JSON` is **not** produced by the topology in this repository; the repair main supports it for manual or future producers. Shape is the same four keys; `rawJson` holds unstructured text the repair wraps in a stub `NewsItem` before calling `inferCategoriesJson`.

---

## 3. Windowed sink (`demo-news-ff-by-category-minute`)

**Role.** Value is **`{windowStartMillis}:{windowEndMillis}:{aggregated}`** where `aggregated` is one or more **`NewsStreamPayload`** JSON objects concatenated with **`|`** (see [`NewsFreeformTopology`](../src/main/java/local/kip1034/dlq/news/freeform/NewsFreeformTopology.java)).

Each payload object has **`articleId`**, **`heading`**, and **`category`** (singular — the bucket key after lowercasing).

### Example value (illustrative timestamps)

```text
1704110400000:1704110460000:{"articleId":"ff-ok-1","heading":"Local weather","category":"weather"}|{"articleId":"ff-ok-1","heading":"Local weather","category":"local"}
```

### Incorrect expectations

- Treating the whole value as a single JSON document — it is a **custom** `start:end:json|json|...` string.
- Expecting `categories` on this inner object — the field name is **`category`**.

---

## 4. LLM return shapes (Ollama)

Repair and producer paths expect model output described in [`docs/topology.md`](topology.md) §2 (verbatim prompts). Parsing is strict enough that stray prose or markdown fences around JSON often causes fallback or empty results; those behaviors are implemented in [`NewsLlmClient`](../src/main/java/local/kip1034/dlq/news/NewsLlmClient.java), not as Kafka topic schemas.

---

## 5. Semantics and sharp edges

These are easy to miss when reading only the README or the JSON tables above.

**`LogAndContinueExceptionHandler` + KIP-1034 topic** — Configured in [`NewsFreeformStreamsApplication`](../src/main/java/local/kip1034/dlq/news/freeform/NewsFreeformStreamsApplication.java). A bad value on **`demo-news-ff-raw`** / **`demo-news-ff-repaired`** is **not** retried at the serde: the handler emits to **`demo-news-ff-streams-dlq`** and processing **continues** (the read advances for `AT_LEAST_ONCE`). That is why museum prose never appears as a `NewsItem` in the graph.

**Repair consumers seek to the log end** — Both repair mains subscribe, then **seek each assigned partition to its end offset** (`awaitAssignedAndSeekToEnd`). They only consume DLQ records that arrive **after** they start. For scripted demos, start Streams and both repair loops **before** the producer burst (as `demo.sh` does). If you start repairs late, earlier DLQ rows are easy to “lose” from the repair process’s point of view unless you change offsets or the consumer group.

**Kafka record key vs `articleId`** — Producers may use a key that differs from the JSON `articleId` (e.g. prose keyed `ff-prose-1`). Repairs **publish to `demo-news-ff-repaired` with key = `item.articleId`** after repair. On the KIP-1034 **deterministic** path, when the model is unavailable, [`NewsFreeformKip1034RepairMain`](../src/main/java/local/kip1034/dlq/news/freeform/NewsFreeformKip1034RepairMain.java) may set `articleId` from the **DLQ record key** when building the stub.

**KIP-1034 repair fallback chain** — `structurePlainTextToNewsItemJson` first; if the returned text is not usable `NewsItem` JSON with categories, the repair tries **`NewsJson.parse(rawText)`** on the original bytes (handles the odd case where DLQ bytes were already valid categorized JSON); if that still fails, a **deterministic** stub is built from words in the text (demo always gets *some* categories).

**`merge` does not deduplicate** — Raw and repaired are concatenated. The same logical article could contribute twice if you publish overlapping content on both inputs.

**Grouping keys are lowercased** — In [`NewsFreeformTopology`](../src/main/java/local/kip1034/dlq/news/freeform/NewsFreeformTopology.java), `flatMap` uses `cat.trim().toLowerCase(Locale.ROOT)` for the stream key before `groupByKey`.

**Streams props worth knowing** — `PROCESSING_GUARANTEE_CONFIG` is **`AT_LEAST_ONCE`**; `CACHE_MAX_BYTES_BUFFERING_CONFIG` is **`0`** (fewer cache-related surprises when stepping through a demo). Changelog state lives under **`java.io.tmpdir`** with a path derived from **`NEWS_FF_STREAMS_APPLICATION_ID`** (default `demo-news-freeform-v1`).

**`TopologyTestDriver` vs production** — [`NewsFreeformTopologyIT`](../src/test/java/local/kip1034/dlq/news/freeform/NewsFreeformTopologyIT.java) exercises serde + split + windowing **without** the broker, **without** `LogAndContinueExceptionHandler` wiring, and **without** real `__streams.errors.*` headers on the KIP-1034 DLQ topic.
