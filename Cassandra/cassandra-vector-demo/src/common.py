"""Cassandra and embedding helpers for the vector demo."""

from __future__ import annotations

import os
import time
from functools import lru_cache
from pathlib import Path
from typing import Sequence

from cassandra.cluster import Cluster, Session
from cassandra.policies import RoundRobinPolicy

# TLS-inspecting corporate proxies re-sign HTTPS with a root that certifi does not
# carry, and some of those roots omit the critical flag on basicConstraints, which
# OpenSSL 3 rejects outright. Verifying against the OS trust store instead lets the
# embedding model download succeed on those networks.
try:
    import truststore
except ModuleNotFoundError:
    pass
else:
    truststore.inject_into_ssl()

KEYSPACE = "vector_demo"
TABLE = "docs"
EMBED_DIM = 384
MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"
CONTACT_POINTS = [os.getenv("CASSANDRA_HOST", "127.0.0.1")]
PORT = int(os.getenv("CASSANDRA_PORT", "9042"))

PROJECT_ROOT = Path(__file__).resolve().parents[1]
MODEL_CACHE = PROJECT_ROOT / "model_cache"
MODEL_CACHE.mkdir(parents=True, exist_ok=True)
os.environ.setdefault("HF_HOME", str(MODEL_CACHE))
os.environ.setdefault(
    "SENTENCE_TRANSFORMERS_HOME",
    str(MODEL_CACHE / "sentence-transformers"),
)
os.environ.setdefault("HF_HUB_CACHE", str(MODEL_CACHE / "hub"))


def wait_for_cassandra(timeout_sec: int = 180) -> Session:
    deadline = time.time() + timeout_sec
    last_err: Exception | None = None
    while time.time() < deadline:
        cluster: Cluster | None = None
        try:
            cluster = Cluster(
                CONTACT_POINTS,
                port=PORT,
                load_balancing_policy=RoundRobinPolicy(),
                protocol_version=5,
            )
            session = cluster.connect()
            session.execute("SELECT now() FROM system.local")
            return session
        except Exception as exc:  # noqa: BLE001 - startup race is expected
            last_err = exc
            if cluster is not None:
                cluster.shutdown()
            time.sleep(3)
    raise RuntimeError(f"Cassandra not ready after {timeout_sec}s: {last_err}")


def ensure_schema(session: Session) -> None:
    session.execute(
        f"""
        CREATE KEYSPACE IF NOT EXISTS {KEYSPACE}
        WITH replication = {{'class': 'SimpleStrategy', 'replication_factor': 1}}
        """
    )
    session.set_keyspace(KEYSPACE)
    session.execute(
        f"""
        CREATE TABLE IF NOT EXISTS {TABLE} (
            id text PRIMARY KEY,
            title text,
            body text,
            category text,
            model text,
            model_year int,
            embedding vector<float, {EMBED_DIM}>
        )
        """
    )
    session.execute(
        f"""
        CREATE INDEX IF NOT EXISTS docs_embedding_sai
        ON {TABLE} (embedding)
        USING 'sai'
        WITH OPTIONS = {{'similarity_function': 'COSINE'}}
        """
    )


@lru_cache(maxsize=1)
def get_embedder():
    from sentence_transformers import SentenceTransformer

    return SentenceTransformer(MODEL_NAME)


def embed_texts(texts: Sequence[str]) -> list[list[float]]:
    vectors = get_embedder().encode(list(texts), normalize_embeddings=True)
    return [vector.astype(float).tolist() for vector in vectors]


def embed_query(text: str) -> list[float]:
    return embed_texts([text])[0]


def vector_literal(vector: Sequence[float]) -> str:
    return "[" + ", ".join(f"{value:.8f}" for value in vector) + "]"
