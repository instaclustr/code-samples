# RAG Retrieval Notebooks

Hands-on Jupyter notebooks for the **Recipe & Dietary Safety Assistant**: five retrieval strategies, plus monitoring and a comparison, running live against a shared, read-only OpenSearch cluster on NetApp Instaclustr.

These notebooks are the query-only version of the [`rag-retrieval` journey](https://github.com/instaclustr/labs/tree/main/rag-retrieval) in `instaclustr/labs`. The journey covers the full build: ingest scripts and the production controls. These notebooks cover only the searching, so you can try each strategy without building anything. The shared cluster is read-only and already ingested, so there is nothing to build before you start. To use your own cluster instead, see [Use your own cluster](#use-your-own-cluster).

The cluster holds 260 recipes, stored as 1,823 searchable **chunks** (one section of a recipe each: ingredients, allergen cautions and labels, introduction, method steps, serving notes). Every search returns chunks, and the notebooks fold them back into recipes. Notebook 01 explains this.

This folder lives at `Labs/rag-retrieval/` in the `instaclustr/code-samples` repository.

> This code is a sample. It is not supported, and it is not dietary-safety advice. Read and understand it before you run it in your own environment.

## What you need

- Python 3.10 or newer (tested on 3.10).
- Network access to the shared cluster (port 9200).
- No ingest and no write access: the shared cluster already holds all the data.
- About 1.5 GB of disk for the Python packages, and a one-time download of two small models (about 100 MB in total).
- Optional: LLM credentials (Amazon Bedrock or an OpenAI-compatible API). Without them everything runs except the generated answers.

## Set up

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m spacy download en_core_web_sm
cp .env.example .env        # for LLM answers, or to use your own cluster
jupyter lab
```

Open the notebooks from this folder. Keep all the files together, because every notebook runs `00_setup.ipynb` by its relative path.

**Before a workshop,** run the commands above and open `01_vector_search.ipynb` once, so the embedding model is already downloaded when the session starts. If Hugging Face limits your downloads, set `HF_TOKEN` in `.env`.

## The notebooks

Run them in order, or open any one on its own. Each starts with one cell that runs `00_setup.ipynb`.

| Notebook | What it teaches |
| --- | --- |
| [`00_setup.ipynb`](00_setup.ipynb) | Settings, connection, models, shared helper functions. Run for you by the others. |
| [`01_vector_search.ipynb`](01_vector_search.ipynb) | The data, and the baseline: plain vector search and its gap. |
| [`02_bm25_ner.ipynb`](02_bm25_ner.ipynb) | Keyword search plus entity boosting. |
| [`03_hybrid_search.ipynb`](03_hybrid_search.ipynb) | Native hybrid search with a search pipeline. |
| [`04_ner_grounding_rerank.ipynb`](04_ner_grounding_rerank.ipynb) | Hard filters first, vector ranking second. |
| [`05_governed_retrieval.ipynb`](05_governed_retrieval.ipynb) | Hot and long-term tiers, escalation, citation checks. |
| [`06_monitoring_and_comparison.ipynb`](06_monitoring_and_comparison.ipynb) | Audit statistics, alerts, retention, then all strategies side by side. |

Every code cell has an explanation above it and a note on what to expect.

## Use your own cluster

The shared cluster is read-only and already ingested. The notebooks only read, so they cannot create indexes. If you use your own cluster, ingest the data first:

1. Set up the cluster and the journey as in its [setup page](https://github.com/instaclustr/labs/blob/main/rag-retrieval/tasks/setup-environment.md). The cluster needs the AI Search plugin and an admin account.
2. Use the journey's own virtual environment, as its setup page describes (the journey and these notebooks both use spaCy 3.8.16 and allow NumPy 2, so one environment can also serve both: after the journey's setup, run `pip install -r requirements.txt` from this folder in the same environment, which adds Jupyter and pandas). In it, start the NER service (`python -m src.ner_service` in `scripts/baseline-bm25-rag`) and leave it running, and in a second terminal run `python -m src.ingest` in each of these folders under `scripts/`, in this order: `baseline-vector-rag`, `baseline-bm25-rag`, `hybrid-rag-native`, `hybrid-rag-ner-rerank`, `governance-tiering`. Keep the order: the last one adds the hot and long-term tiers to the indexes the one before it wrote.
3. In `governance-tiering`, run a few questions with `python -m src.query --question "..."`. The ingest creates the audit-log index empty, and each question adds one record to it, which notebook 06 reads. The query needs the NER service running and LLM credentials in the journey's `.env`, and each question is a billed LLM call.
4. In `00_setup.ipynb`, set `USE_SHARED_READONLY_CLUSTER = False` and put your `OPENSEARCH_*` settings in the notebooks' `.env`. Use a read-only account for the notebooks if you can. The notebooks use the journey's default index names (`recipes-*`). If you gave your indexes other names in the journey's `.env`, copy those six `OPENSEARCH_*_INDEX` lines into the notebooks' `.env` too (see `.env.example`).
5. Run `00_setup.ipynb`. Expect `[PASS]` for all six indexes and 1,823 chunks in each content index.

The audit-record counts in the facilitator table (6 records, 3 escalated) apply to the shared cluster only. On yours they depend on how many questions you ran.

## Settings

Both switches are in the first code cell of `00_setup.ipynb`. After you change one, **save the file** and re-run the cell. The other notebooks run the saved copy of `00_setup.ipynb`, so an unsaved change has no effect on them.

- `RUN_LLM` (default `False`): turns answer generation on. With it off you still see all the retrieval. With it on, the notebooks call your LLM provider, which **bills you**. A full pass through notebooks 01 to 05 makes about 10 LLM calls.
- `USE_SHARED_READONLY_CLUSTER` (default `True`): uses the shared workshop cluster. Set it to `False` to use your own cluster, with `OPENSEARCH_*` settings in `.env` (see `.env.example`). Your cluster must be ingested first: see [Use your own cluster](#use-your-own-cluster).

### The shared read-only account

`00_setup.ipynb` contains the host, user and password of a **shared read-only account**, so you can run the notebooks with no signup. It can only search, and the password is public on purpose, with the cluster owner's agreement. It may be changed or the cluster taken down at any time. **Do not copy this pattern for real credentials.**

The notebooks never write to the cluster.

### LLM settings

Copy `.env.example` to `.env` and fill in either the Amazon Bedrock settings or the OpenAI-compatible settings. `.env` is git-ignored. Never commit it.

## For facilitators

What each notebook should show, with the LLM off:

| Notebook | Expected result |
| --- | --- |
| 00 | `[PASS]` for the cluster, all six indexes, the embedding model and spaCy. The five content indexes show 1,823 chunks each, and the audit-log index shows 6 audit records. |
| 01 | 1,823 chunks that make up 260 recipes (209 hot, 51 long-term, 201 with a Sulfites caution). Vector search does **not** retrieve the `thegratefulgirlcooks.com` recipe for the question that names it, even with 10 recipes. |
| 02 | spaCy tags `fish and chips` (or `chips`). Both Fish and Chips recipes are retrieved, with identical scores. |
| 03 | The pipeline prints `min_max` and `arithmetic_mean` with weights `[0.5, 0.5]`. Both recipes are retrieved. |
| 04 | Without a filter, some of the 5 recipes carry a Sulfites caution (2 in a typical run). With the exclusion, **0** do. |
| 05 | The `womensweeklyfood.com.au` question stays in the hot tier. The `thegratefulgirlcooks.com` question escalates with `named_source_not_in_hot_tier`, and a `PINNED:` line shows that recipe was added to the evidence as `[R6]`. With the LLM on, citations are valid (`hallucinated=[]`). |
| 06 | The audit log holds 6 records, 3 escalated (50.0%). The alert fires only above 50%, so none appears, with no margin: one more escalated question would trigger it. It prints `[WARN]` that no retention policy is attached to the audit index. The comparison table shows vector search and notebook 04's NER + rerank missing the `thegratefulgirlcooks.com` recipe (notebook 05 finds it through the pin), and only the hard exclusion reaching 0 Sulfites. |

Scores and answer wording can vary a little between runs. The rankings above should not.

Practical notes:
- All attendees search the same cluster. Check with the cluster owner that it can take the load before a large session.
- Notebooks 04 to 06 repeat a few small functions from earlier notebooks (marked "Recap"), so each one runs on its own. They are copies, not edits.

## Troubleshooting

- **`ModuleNotFoundError`:** Jupyter is not using the virtual environment. Close it, run `source .venv/bin/activate`, and start `jupyter lab` again.
- **A `[FAIL]` on the cluster line:** check your network or VPN, and that port 9200 is reachable.
- **`scipy` fails to load on Python 3.10 with a recent macOS** (`__thread_bss ... zero-fill section`): `requirements.txt` pins a working `scipy` for Python below 3.11. Re-run `pip install -r requirements.txt`.
- **`[FAIL] RUN_LLM is True but these settings are missing`:** add the listed settings to `.env`, then re-run the setup cell.
- **`(LLM call failed: ...)` in an answer:** the provider rejected the call, for example because the model is not enabled for your account. Retrieval is unaffected.

## Before you commit changes

Clear all outputs (in JupyterLab: Edit, then Clear Outputs of All Cells) and never commit `.env`.

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
