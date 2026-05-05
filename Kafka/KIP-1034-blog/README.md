# KIP-1034: Dead letter queue (DLQ) in Kafka Streams

Local notes and a **frozen wiki export** for [KIP-1034](https://cwiki.apache.org/confluence/display/KAFKA/KIP-1034%3A+Dead+letter+queue+in+Kafka+Streams) (Apache Kafka improvement proposal).

## Status (per wiki export)

**Adopted** (wiki snapshot last modified Feb 24, 2026). Confirm current state on the [live KIP page](https://cwiki.apache.org/confluence/display/KAFKA/KIP-1034%3A+Dead+letter+queue+in+Kafka+Streams).

## Full text in this repo

| File | Description |
|------|-------------|
| [`docs/kip-1034-wiki-export.md`](docs/kip-1034-wiki-export.md) | Confluence export (saved from the wiki; includes motivation, API sketches, compatibility, test plan). |

## Summary

- **Problem:** Built-in Streams exception paths are mostly **log-and-continue** or **fail the app**—both are awkward in production compared to a **DLQ topic** (replay, alerting, retention, rich metadata), similar to Kafka Connect.
- **Config:** `errors.dead.letter.queue.topic.name` — when set, default exception handlers can emit DLQ records; when `null`, no DLQ from defaults. **Custom handlers** ignore this setting unless they choose to use it.
- **Mechanism:** Handlers gain a **`Response`** carrying **`Result`** (resume / fail / retry where applicable) plus a list of **`ProducerRecord<byte[], byte[]>`** for the DLQ. Raw key/value bytes come from context (`sourceRawKey` / `sourceRawValue` on **`RecordContext`** and **`ErrorHandlerContext`**).
- **Default DLQ record:** Key/value are the **raw** input message bytes when applicable; **headers** carry exception class, stack trace, message, source topic/partition/offset (`__streams.errors.*`). **Production** path: DLQ records may be **metadata-only** where full source bytes are not available.
- **Topic:** One DLQ topic per app by default; **Streams does not auto-create** it—operators create the topic and retention.
- **Failure mode:** If producing to the DLQ fails, the failure surfaces via **`uncaughtExceptionHandler`** (per KIP).
- **API churn:** Deprecates older `handle` / enum response types in favor of **`handleError`**-style methods returning **`Response`**, with backward-compatible defaults.

---

## DLQ record shape: key, value, metadata, and headers

When the **default** deserialization exception handler emits to the DLQ (with `errors.dead.letter.queue.topic.name` set), each **`ConsumerRecord`** on the DLQ topic is a normal Kafka message. Treat it like any other topic for ACLs, retention, and consumers—except the **payload contract** is defined by Streams.

### Key and value (bytes)

| Field | Meaning |
|--------|--------|
| **`key`** | **`byte[]`** — typically the **raw key bytes** from the **source** consumer record that failed to deserialize (same encoding your producer used; in this repo’s IT, UTF-8 for `key-7`). |
| **`value`** | **`byte[]`** — typically the **raw value bytes** from the **source** record (in the IT, the UTF-8 bytes of `not-an-int` that broke `IntegerDeserializer`). |

If the framework cannot supply full source bytes, the KIP allows **metadata-only** DLQ rows in some cases—design replay and alerting to tolerate missing key/value when you upgrade across versions.

### Record metadata (Kafka fields, not headers)

These are standard `ConsumerRecord` fields (useful in dashboards and replay tools):

| Field | Typical use |
|--------|-------------|
| **`topic`**, **`partition`**, **`offset`** | Where this DLQ row lives (not the same as the **source** offset below). |
| **`timestamp`** / **`timestampType`** | Broker / `CreateTime` or `LogAppendTime` per topic config—not the same thing as **`__streams.errors.offset`**. |
| **`serializedKeySize`**, **`serializedValueSize`**, **`headers()`** | Size and structured error context. |

### Error headers (`__streams.errors.*`)

Header **keys** are stable string constants (see `org.apache.kafka.streams.errors.internals.ExceptionHandlerUtils` in **`kafka-streams`**). Values are **`byte[]`**; in practice they are **UTF-8 text** you can decode for display or assertions.

| Header key | UTF-8 value (typical) |
|-------------|----------------------|
| **`__streams.errors.exception`** | Fully qualified exception class, e.g. `org.apache.kafka.common.errors.SerializationException`. |
| **`__streams.errors.message`** | Short human-readable message, e.g. `Size of data received by IntegerDeserializer is not 4`. |
| **`__streams.errors.stacktrace`** | Full stack trace string (can be long—truncate in UIs). |
| **`__streams.errors.topic`** | Source topic name where deserialization failed (here: `kip1034-dlq-input`). |
| **`__streams.errors.partition`** | Source partition as a string (here: `0` for a single-partition topic). |
| **`__streams.errors.offset`** | Source offset as a string (which input record failed). |

In Java tests you can import the same constant names the broker uses from **`ExceptionHandlerUtils`** and assert on headers, for example:

```java
import static org.apache.kafka.streams.errors.internals.ExceptionHandlerUtils.HEADER_ERRORS_EXCEPTION_NAME;
import static org.apache.kafka.streams.errors.internals.ExceptionHandlerUtils.HEADER_ERRORS_TOPIC_NAME;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;

Header h = dlqRecord.headers().lastHeader(HEADER_ERRORS_EXCEPTION_NAME);
String exceptionClass = new String(h.value(), StandardCharsets.UTF_8);
assertTrue(exceptionClass.contains("SerializationException"));
assertTrue(new String(dlqRecord.headers().lastHeader(HEADER_ERRORS_TOPIC_NAME).value(), StandardCharsets.UTF_8)
        .contains("kip1034-dlq-input"));
```

### Concrete example (this repo’s fault row)

Scenario: index **77** sends key **`key-7`** (5 bytes UTF-8) and value **`not-an-int`** (10 bytes UTF-8). `IntegerDeserializer` throws.

**Key (hex):** `6b65792d37` → UTF-8 **`key-7`**

**Value (hex):** `6e6f742d616e2d696e74` → UTF-8 **`not-an-int`**

**Headers (illustrative):**

- **`__streams.errors.exception`** → contains `SerializationException`
- **`__streams.errors.message`** → contains `IntegerDeserializer` / `not 4` (size mismatch)
- **`__streams.errors.topic`** → `kip1034-dlq-input`
- **`__streams.errors.partition`** → `0`
- **`__streams.errors.offset`** → string form of the **source** record’s offset (depends on how many records were already on the input partition before this failure).

Running `mvn test -Dtest=Kip1034DeserializationDlqIT` prints a **full hex + UTF-8 dump** of the matched DLQ `ConsumerRecord` (including every header) to Surefire stdout—useful when verifying header names or copy-pasting into docs.

---

## Local test bed (Kafka 4.2.0 + Java integration test)

### What it does today

- **`docker-compose.yml`** — single-node **KRaft** broker using **`apache/kafka:4.2.0`** (plaintext on `localhost:9092`), based on the [official compose example](https://github.com/apache/kafka/tree/trunk/docker/examples/docker-compose-files/single-node/plaintext).
- **Maven + JUnit** — `Kip1034DeserializationDlqIT` exercises **deserialization DLQ** plus a small **windowed aggregation** so the topology resembles a realistic app (not a straight passthrough).

**Streams config**

- **`LogAndContinueExceptionHandler`** — one bad payload does not kill the app (the default **`LogAndFailExceptionHandler`** would fail the thread even when a DLQ is configured).
- **`errors.dead.letter.queue.topic.name`** — DLQ topic for default handler–emitted rows.

**Topology (current)**

1. Read **`kip1034-dlq-input`** as `KStream<String, Integer>` (`String` + `Integer` serdes).
2. **`groupByKey`** → **10s tumbling** `TimeWindows.ofSizeWithNoGrace(Duration.ofMillis(10_000))` → **`aggregate`** (running **sum** and **count** in a compact `"sum,count"` string).
3. **`toStream`** → **`mapValues`** to **mean** (`double`) → **`selectKey`** (drop `Windowed` wrapper) → write **`kip1034-dlq-output-windowed`** as `String` key + **`Double`** value.

There is **no** `suppress(untilWindowCloses)` in the IT: the sink emits **on every aggregate update** (running mean within the window). The test waits until the **last** emitted value per logical key matches the expected **batch** mean for that key (tolerant of **>99** sink records under changelog replay / at-least-once).

**Producer (in the test)**

- **100** sends: index `i` uses key **`key-(i % 10)`** (ten keys: `key-0` … `key-9`).
- Valid rows: **4-byte big-endian int** value **`1000 + i`**.
- **One fault:** index **`77`** sends UTF-8 bytes **`not-an-int`** on **`key-7`** so **`IntegerDeserializer`** throws → **one** DLQ row (same raw key/value bytes + `__streams.errors.*` headers). (A bad UTF-8 **string** payload is not used: **`StringDeserializer`** can replace ill-formed sequences instead of throwing, so the fault is a **non–4-byte** value for **`IntegerDeserializer`**.)
- **Event time:** every `ProducerRecord` uses the same **`CreateTime`**:  
  `batchEventTs = (System.currentTimeMillis() / 10_000) * 10_000`  
  so all rows fall in one tumbling window **and** the timestamp is recent enough that the broker does **not** replace it with log-append time (see *Time / timestamps* below).

**Assertions**

- Exactly **one** DLQ record matching **both** the faulty raw key and raw value (avoids matching stale DLQ rows from earlier runs that share the bad value).
- After consumers **seek to end** of DLQ and output topics (then produce), the output consumer eventually sees **final** running means for all **10** keys equal to the analytically computed averages ( **`key-7`** uses **9** ints because index 77 never enters the stream).

**Streams tuning in the test**

- **`cache.max.bytes.buffering = 0`** — flush aggregate updates promptly (easier to reason about in traces).

### Versions / evolution (configurations that worked)

| Version | Topology | Keys / values | Output topic | Consumer / time notes |
|--------|-----------|---------------|--------------|------------------------|
| **v1 — passthrough** | `stream` → `to(output)` | 100 distinct keys `key-000` … `key-099`; values `1000+i` except one bad payload | `kip1034-dlq-output` (4-byte int values) | Assert **99** output rows. Simple; no windows. |
| **v2 — windowed mean (current)** | Tumbling 10s **mean per key**; sink `Double` | 10 rotating keys `key-0` … `key-9`; same fault story | **`kip1034-dlq-output-windowed`** | New topic name so old **int** payloads do not break **`DoubleDeserializer`**. DLQ await matches **key + value**. Input consumers **seek to end** before produce to avoid stale DLQ. **Aligned `CreateTime`** on all producer sends so one window + stable expected means. |

Older clones may still have topic **`kip1034-dlq-output`** on the broker from v1; it is unused by the current test. You can delete it manually if you want a clean broker.

### Traces (`-Dkip1034.trace=true`)

To print a **line-by-line ledger** of everything sent to the input topic and everything read from the output topic:

```bash
export KAFKA_BOOTSTRAP_SERVERS=localhost:9092   # optional; default is localhost:9092

mvn test -Dtest=Kip1034DeserializationDlqIT -Dkip1034.trace=true
```

With tracing enabled, Surefire stdout includes:

1. **`TRACE: produce`** — header with `batchEventTs` and window size; then **100** lines: `i`, slot, key, `CreateTime`, value (int or `FAULT(not-an-int)`).
2. **`TRACE: output`** — one line per **sink** record: sequence number, key, **running mean**, partition, offset, record timestamp and type. Count can exceed **99** (replays / duplicate emissions).
3. **`TRACE: output end`** — sorted **`final`** mean per key as used for assertions.

Filter example:

```bash
mvn -q test -Dtest=Kip1034DeserializationDlqIT -Dkip1034.trace=true 2>&1 | grep -E '^(==========|produce |output  |  final )'
```

### Time / timestamps (why this is easy to get wrong)

- **Too-old `CreateTime`:** If you hard-code a small epoch-ms timestamp (e.g. `1_000_000`), many brokers treat it as invalid and **overwrite** with log-append time. Records then land in **different** windows as real time advances, and the **last** sink mean per key may **never** match a single-batch expectation. **Fix:** use a **recent, window-aligned** timestamp shared by the whole batch (`(now / windowMs) * windowMs`).
- **No explicit timestamp:** Wall-clock spread across slow polls can also **split** the batch across windows. **Fix:** same explicit per-batch `CreateTime` for every send.
- **Sink record timestamps:** Early sink rows may show timestamps that differ from `batchEventTs` (processing / metadata paths); later rows often align with input `CreateTime`. Assertions use **value** (mean), not sink timestamp.

### Prereqs

- Docker / Docker Compose
- JDK **17+** and **Maven 3.9+**

### Run

```bash
cd kip-1034-kafka-streams-dlq   # this repo

docker compose up -d
# wait until the broker accepts connections (often a few seconds)

export KAFKA_BOOTSTRAP_SERVERS=localhost:9092   # default if unset
mvn test
```

Optional trace:

```bash
mvn test -Dtest=Kip1034DeserializationDlqIT -Dkip1034.trace=true
```

Stop the broker when finished:

```bash
docker compose down
```

### Topics

The integration test creates (once, RF=1): **`kip1034-dlq-input`**, **`kip1034-dlq-output-windowed`**, **`kip1034-dlq-dead-letter`**. Re-runs tolerate **`TopicExistsException`**.

---


