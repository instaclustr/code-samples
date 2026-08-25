# Cassandra Vector Search — ANN Retrieval Sample

A local **Apache Cassandra 5** demo that stores document embeddings next to the text they describe and retrieves nearest neighbors with **JVector ANN** (`ORDER BY embedding ANN OF`). It is retrieval-only: no keyword lane, no metadata filter, no fusion, and no language model.

This is the precursor sample for the vector-search write-up. The hybrid RAG sample adds SAI keyword grounding and answer generation on the same corpus.

## What it demonstrates

You own a **2024 Summit 1500**. Ask a support question and inspect the chunks Cassandra ranks first:

- **Paraphrase works.** *"Is there a recall on the tailgate?"* finds the tailgate-latch recall by meaning.
- **Identifiers blur.** *"What is recall 24V-113?"* can rank the similar `23V-088` backup-camera recall above the exact identifier.
- **Missing metadata stays ambiguous.** *"How much can the Summit 1500 tow?"* returns plausible chunks with conflicting model years, trucks, and limits.

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
- **Python 3.10+** (Python 3.14 may fail installing the embedding stack)
- About **4GB** RAM and **2GB** free disk for Cassandra, Python packages, and the first-run model download
- Internet access the first time `seed.py` downloads `sentence-transformers/all-MiniLM-L6-v2`



## Running

```bash
cd cassandra-vector-demo
docker compose up -d
docker compose ps          # wait until cassandra is healthy (1–2 min on first boot)

python3 -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate
pip install -r requirements.txt

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
python src/demo.py "What is recall 24V-113?"
python src/demo.py "How much can the Summit 1500 tow?"
```

- *"Is there a recall on the tailgate?"* → semantic match; the tailgate latch recall (`recall-24v-113`) should sit near the top.
- *"What is recall 24V-113?"* → embeddings treat nearby recall codes as neighbors, so `recall-23v-088` can rank first.
- *"How much can the Summit 1500 tow?"* → several confident chunks, several different numbers (2024 1500, 2500, 2023, payload).

The app stops at the ranking so you can judge the list before an LLM ever sees it.

## Project structure

```
cassandra-vector-demo/
├── src/
│   ├── common.py          # Cassandra connect, schema, embeddings
│   ├── setup_schema.py    # Keyspace + SAI ANN index
│   ├── seed.py            # Embed + insert corpus
│   ├── retrieval.py       # ORDER BY embedding ANN OF
│   ├── demo.py            # CLI
│   └── presenter.py       # FastAPI presenter
├── static/
│   └── presenter.html     # Single-column ANN UI
├── data/
│   └── corpus.json        # Fictional Summit truck knowledge base
├── tests/
│   └── test_app.py
├── docker-compose.yml     # Cassandra 5.0.8 on localhost:9042
├── requirements.txt
└── README.md
```



## Additional materials

- [Storage-Attached Indexing (SAI)](https://cassandra.apache.org/doc/latest/cassandra/developing/cql/indexing/sai/sai-concepts.html) — how Cassandra attaches ANN to the table
- Prefer Cassandra **5.0.7+** (this sample uses **5.0.8**) for vector correctness and latency fixes

