# Cassandra Vector Search — ANN Retrieval Sample

A local **Apache Cassandra 5** demo that stores document embeddings next to the text they describe and retrieves nearest neighbors with **JVector ANN** (`ORDER BY embedding ANN OF`). It is retrieval-only: no keyword lane, no metadata filter, no fusion, and no language model.

This is the precursor sample for the vector-search write-up. The hybrid RAG sample adds SAI keyword grounding and answer generation on the same dataset.

## What it demonstrates

You own a **2024 Summit 1500**. Ask a support question and inspect the chunks Cassandra ranks first:

- **Paraphrase works.** *"Is there a recall on the tailgate?"* finds the tailgate-latch recall by meaning, ahead of eleven other recalls.
- **Identifiers are weak.** *"What is recall 24V-330?"* ranks the matching recall 9th of 12. Across all twelve recall IDs, ANN puts the right document first only 17% of the time (`src/evaluate.py`).
- **Missing metadata stays ambiguous.** *"How much can the Summit 1500 tow?"* returns plausible chunks with conflicting model years, trucks, and limits.

The twelve recall documents are written from one template, varying only in the recall number, the affected component and its symptom, the remedy, and the model and year. That is deliberate: it leaves the identifier as the signal a lookup-by-number has to rely on.

```
Question
   │  embed with all-MiniLM-L6-v2 (384-dim, local)
   ▼
Cassandra 5  ── SAI / JVector ──▶  ORDER BY embedding ANN OF ?
                                      LIMIT 5
                                      similarity_cosine(...)
```



## Prerequisites

- **Docker / Docker Compose**
- **Python 3.10+** (Python 3.14 may fail installing the embedding stack). The `python3` shipped with macOS command line tools is 3.9 and is too old — `truststore` has no 3.9 wheel, so `pip install` fails with `No matching distribution found`. Name a newer interpreter explicitly, e.g. `python3.13 -m venv .venv`.
- About **4GB** RAM and **2GB** free disk for Cassandra, Python packages, and the first-run model download
- Internet access the first time `seed.py` downloads `sentence-transformers/all-MiniLM-L6-v2` (a Hugging Face Hub token reminder is optional; set `HF_TOKEN` only if you want higher Hub rate limits)



## Running

```bash
cd cassandra-vector-demo
docker compose up -d
docker compose ps          # wait until cassandra is healthy (1–2 min on first boot)

python3.13 -m venv .venv    # any 3.10-3.13 interpreter works
source .venv/bin/activate   # Windows: .venv\Scripts\activate
python -m pip install -r requirements.txt

python src/setup_schema.py
python src/seed.py
python src/demo.py
```

If `seed.py` fails with `CERTIFICATE_VERIFY_FAILED` on a TLS-inspecting corporate network, reinstall from `requirements.txt` so `truststore` can use the operating system certificate store.

`python src/demo.py` with no arguments runs all three scripted questions.

Presenter UI at [http://127.0.0.1:8000](http://127.0.0.1:8000):

```bash
uvicorn presenter:app --app-dir src --host 127.0.0.1 --port 8000
```

Stop / reset:

```bash
docker compose down          # keep data volume
docker compose down -v       # wipe Cassandra data
```



## Try these questions

```bash
python src/demo.py "Is there a recall on the tailgate?"
python src/demo.py "What is recall 24V-330?"
python src/demo.py "How much can the Summit 1500 tow?"
```

- *"Is there a recall on the tailgate?"* → semantic match; the tailgate latch recall (`recall-24v-113`) ranks first at 0.7767.
- *"What is recall 24V-330?"* → the matching recall does not make the top five. It sits at rank 9 of 12, behind eight unrelated recalls.
- *"How much can the Summit 1500 tow?"* → four confident chunks with four different numbers (2024 1500, 2023 1500, 2500, payload), all within 0.08.

The app stops at the ranking so you can judge the list before an LLM ever sees it.

Scores are Cassandra's `similarity_cosine`, which returns `(1 + cos θ) / 2`. A score of 0.5 means orthogonal, so treat roughly 0.7 and above as the meaningful range.

## Measure it

```bash
python src/evaluate.py
```

Asks `What is recall X?` for all twelve recall IDs and reports where the matching document ranked:

```text
recall@1 = 2/12 (17%)   recall@3 = 8/12 (67%)   mean rank = 3.58
```

Worth trying: trim `data/corpus.json` down to two or four recalls, re-seed, and re-run. recall@1 goes to 100%. The failure only appears once enough similar documents exist to compete, which is a good reason to distrust retrieval demos built on a handful of rows.

## Project structure

```
cassandra-vector-demo/
├── src/
│   ├── common.py          # Cassandra connect, schema, embeddings
│   ├── setup_schema.py    # Keyspace + SAI ANN index
│   ├── seed.py            # Embed + insert corpus
│   ├── retrieval.py       # ORDER BY embedding ANN OF
│   ├── demo.py            # CLI
│   ├── evaluate.py        # recall@1 over every recall ID
│   └── presenter.py       # FastAPI presenter
├── static/
│   └── presenter.html     # Single-column ANN UI
├── data/
│   └── corpus.json        # Fictional Summit truck knowledge base
├── tests/
│   └── test_app.py
├── docker-compose.yml     # Cassandra 5.0.9 on localhost:9042
├── requirements.txt
└── README.md
```



## Additional materials

- [Storage-Attached Indexing (SAI)](https://cassandra.apache.org/doc/latest/cassandra/developing/cql/indexing/sai/sai-concepts.html) — how Cassandra attaches ANN to the table
- Prefer Cassandra **5.0.7+** (this sample uses **5.0.9**) for vector correctness and latency fixes — see [CASSANDRA-20086](https://issues.apache.org/jira/browse/CASSANDRA-20086)

