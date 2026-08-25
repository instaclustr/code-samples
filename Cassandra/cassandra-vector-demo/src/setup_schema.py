#!/usr/bin/env python
"""Create the vector demo keyspace, table, and ANN index."""

from common import ensure_schema, wait_for_cassandra


def main() -> None:
    print("Waiting for Cassandra...")
    session = wait_for_cassandra()
    print("Applying vector schema and ANN index...")
    ensure_schema(session)
    print("Done. Keyspace: vector_demo  Table: docs")


if __name__ == "__main__":
    main()
