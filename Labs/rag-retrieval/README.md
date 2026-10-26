# RAG Retrieval Notebooks

Hands-on Jupyter notebooks for the **Recipe & Dietary Safety Assistant**: five retrieval strategies, plus monitoring and a comparison, running live against a shared, read-only OpenSearch cluster on NetApp Instaclustr.

These notebooks are the query-only version of the [`rag-retrieval` journey](https://github.com/instaclustr/labs/tree/main/rag-retrieval) in `instaclustr/labs`. The journey covers the full build: ingest scripts, containers, tests and the production controls. These notebooks cover only the searching, so you can try each strategy without building anything.

This folder lives at `Labs/rag-retrieval/` in the `instaclustr/code-samples` repository.

> This code is a sample. It is not supported, and it is not dietary-safety advice. Read and understand it before you run it in your own environment.

## What you need

- Python 3.10 or newer (3.12 recommended).
- Network access to the shared cluster (port 9200).
- About 1.5 GB of disk for the Python packages, and a one-time download of two small models (about 100 MB in total).
- Optional: LLM credentials (Amazon Bedrock or an OpenAI-compatible API). Without them everything runs except the generated answers.

## Set up

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m spacy download en_core_web_sm
cp .env.example .env        # only needed for LLM answers
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

## Settings

Both switches are in the first code cell of `00_setup.ipynb`. After you change one, **save the file** and re-run the cell. The other notebooks run the saved copy of `00_setup.ipynb`, so an unsaved change has no effect on them.

- `RUN_LLM` (default `False`): turns answer generation on. With it off you still see all the retrieval. With it on, the notebooks call your LLM provider, which **bills you**. A full pass through notebooks 01 to 05 makes about 10 LLM calls.
- `USE_SHARED_READONLY_CLUSTER` (default `True`): uses the shared workshop cluster. Set it to `False` to use your own cluster, with `OPENSEARCH_*` settings in `.env` (see `.env.example`). Your cluster needs the journey's data loaded first, using the journey's ingest steps.

### The shared read-only account

`00_setup.ipynb` contains the host, user and password of a **shared read-only account**, so you can run the notebooks with no signup. It can only search, and the password is public on purpose, with the cluster owner's agreement. It may be changed or the cluster taken down at any time. **Do not copy this pattern for real credentials.**

The notebooks never write to the cluster.

### LLM settings

Copy `.env.example` to `.env` and fill in either the Amazon Bedrock settings or the OpenAI-compatible settings. `.env` is git-ignored. Never commit it.

## For facilitators

What each notebook should show, with the LLM off:

| Notebook | Expected result |
| --- | --- |
| 00 | `[PASS]` for the cluster, all six indexes, the embedding model and spaCy. The audit-log index holds only a few documents. |
| 01 | 260 recipes (209 hot, 51 long-term, 201 with a Sulfites caution). Vector search does **not** retrieve the `thegratefulgirlcooks.com` recipe for the question that names it. |
| 02 | spaCy tags `fish and chips` (or `chips`). Both Fish and Chips recipes are retrieved, with identical scores. |
| 03 | The pipeline prints `min_max` and `arithmetic_mean` with weights `[0.3, 0.7]`. Both recipes are retrieved. |
| 04 | Without a filter, some of the 5 hits carry a Sulfites caution. With the exclusion, **0** do. |
| 05 | The `womensweeklyfood.com.au` question stays in the hot tier. The `thegratefulgirlcooks.com` question escalates with `named_source_not_in_hot_tier`. With the LLM on, citations are valid (`hallucinated=[]`). |
| 06 | The shared audit log has very few records, so the escalation alert can fire on a tiny sample. It also prints `[WARN]` that no retention policy is attached to the audit index. The comparison table shows only vector search missing the `thegratefulgirlcooks.com` recipe, and only the hard exclusion reaching 0 Sulfites. |

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
