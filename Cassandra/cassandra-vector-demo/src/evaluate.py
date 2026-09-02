#!/usr/bin/env python
"""Measure how often ANN ranks the exactly-matching recall first.

Every recall document is worded from the same template, so the recall ID is the
only thing that distinguishes them. That makes this a clean test of whether
embeddings carry enough signal to resolve an identifier.
"""

from __future__ import annotations

import json
import re
from pathlib import Path

from common import ensure_schema, wait_for_cassandra
from retrieval import MAX_LIMIT, vector_search

CORPUS_PATH = Path(__file__).resolve().parents[1] / "data" / "corpus.json"
RECALL_ID = re.compile(r"\b(\d{2}V-\d{3})\b")


def recall_ids() -> list[tuple[str, str]]:
    """Return (doc_id, recall_id) for every recall document in the corpus."""
    pairs = []
    for doc in json.loads(CORPUS_PATH.read_text()):
        match = RECALL_ID.search(doc["title"])
        if match:
            pairs.append((doc["id"], match.group(1)))
    return pairs


def main() -> None:
    session = wait_for_cassandra()
    ensure_schema(session)

    pairs = recall_ids()
    if not pairs:
        print(f"No recall documents found in {CORPUS_PATH}")
        return

    total = len(pairs)
    at_1 = at_3 = 0
    ranks: list[int] = []

    print(f"Asking for {total} recall IDs by number, LIMIT {MAX_LIMIT}\n")
    for doc_id, rid in pairs:
        hits = vector_search(session, f"What is recall {rid}?", limit=MAX_LIMIT)
        ids = [hit.id for hit in hits]
        rank = ids.index(doc_id) + 1 if doc_id in ids else None

        if rank is not None:
            ranks.append(rank)
            at_1 += rank == 1
            at_3 += rank <= 3
            where = f"rank {rank}"
        else:
            where = f"outside top {MAX_LIMIT}"

        note = "" if rank == 1 else f"   top hit was {hits[0].id}"
        print(f"  {rid}  ->  {where}{note}")

    mean_rank = f"{sum(ranks) / len(ranks):.2f}" if ranks else "n/a"
    missed = total - len(ranks)
    print(
        f"\nrecall@1 = {at_1}/{total} ({at_1 / total:.0%})   "
        f"recall@3 = {at_3}/{total} ({at_3 / total:.0%})   "
        f"mean rank = {mean_rank}"
    )
    if missed:
        print(
            f"{missed} document(s) never appeared in the top {MAX_LIMIT}, "
            "so the mean rank above is optimistic."
        )


if __name__ == "__main__":
    main()
