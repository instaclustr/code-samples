# Cassandra vector demo notes

Presenter: http://127.0.0.1:8000

## First-time setup

This app has its own virtual environment. Create it once before the first demo:

```bash
cd cassandra-vector-demo
python3.13 -m venv .venv   # macOS system python3 is 3.9 and is too old
source .venv/bin/activate
python -m pip install -r requirements.txt
```

## Before the demo

```bash
cd cassandra-vector-demo
docker compose up -d
docker compose ps
source .venv/bin/activate
python src/seed.py
uvicorn presenter:app --app-dir src --host 127.0.0.1 --port 8000
```

## Story

1. **Meaning works:** “Is there a recall on the tailgate?” should retrieve the
   tailgate-latch recall near the top.
2. **Identifiers blur:** “What is recall 24V-113?” can rank the semantically
   similar `23V-088` recall above the exact identifier.
3. **Missing metadata stays ambiguous:** “How much can the Summit 1500 tow?”
   returns plausible but conflicting chunks across model years, models, and
   payload versus towing.

The app intentionally shows only ANN neighbors and cosine scores. Do not imply
that it calls an LLM, performs keyword matching, or filters by model metadata.

CLI fallback:

```bash
python src/demo.py
python src/demo.py "What is recall 24V-113?"
```
