#!/usr/bin/env python
"""Print vector ANN neighbors for one question or the scripted set."""

from __future__ import annotations

import argparse

from common import ensure_schema, wait_for_cassandra
from retrieval import DEFAULT_QUERIES, vector_search


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Cassandra vector retrieval demo."
    )
    parser.add_argument(
        "query",
        nargs="*",
        help="Question to run. If omitted, runs the three scripted questions.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    session = wait_for_cassandra()
    ensure_schema(session)
    queries = [" ".join(args.query)] if args.query else DEFAULT_QUERIES

    for query in queries:
        print("\n" + "=" * 72)
        print(f"QUERY: {query}")
        for rank, hit in enumerate(vector_search(session, query), 1):
            vehicle = (
                f" [{hit.model or '?'} {hit.model_year or '?'}]"
                if hit.model or hit.model_year
                else ""
            )
            score = f"{hit.score:.4f}" if hit.score is not None else "n/a"
            print(
                f"  {rank}. [{hit.category}] {hit.id}{vehicle} "
                f"{hit.title}  score={score}"
            )


if __name__ == "__main__":
    main()
