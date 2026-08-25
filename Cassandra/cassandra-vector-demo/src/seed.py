#!/usr/bin/env python3
"""Embed the sample corpus and load it into the vector demo table."""

from __future__ import annotations

import json
from pathlib import Path

from cassandra.query import SimpleStatement

from common import TABLE, embed_texts, ensure_schema, wait_for_cassandra

ROOT = Path(__file__).resolve().parents[1]
CORPUS_PATH = ROOT / "data" / "corpus.json"


def main() -> None:
    docs = json.loads(CORPUS_PATH.read_text())
    print(f"Loaded {len(docs)} docs from {CORPUS_PATH}")

    session = wait_for_cassandra()
    ensure_schema(session)

    texts = [f"{doc['title']}\n{doc['body']}" for doc in docs]
    print("Embedding with sentence-transformers (first run downloads the model)...")
    vectors = embed_texts(texts)

    session.execute(f"TRUNCATE {TABLE}")
    insert = session.prepare(
        f"""
        INSERT INTO {TABLE} (
            id, title, body, category, model, model_year, embedding
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """
    )
    for doc, vector in zip(docs, vectors):
        session.execute(
            insert,
            (
                doc["id"],
                doc["title"],
                doc["body"],
                doc["category"],
                doc.get("model"),
                doc.get("model_year"),
                vector,
            ),
        )
        print(f"  inserted {doc['id']}")

    count = session.execute(SimpleStatement(f"SELECT COUNT(*) FROM {TABLE}")).one()[0]
    print(f"Seed complete. Row count: {count}")


if __name__ == "__main__":
    main()
