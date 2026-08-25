"""Vector ANN retrieval shared by the CLI and presenter."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from weakref import WeakKeyDictionary

from common import TABLE, embed_query

DEFAULT_QUERIES = [
    "Is there a recall on the tailgate?",
    "What is recall 24V-113?",
    "How much can the Summit 1500 tow?",
]

MAX_LIMIT = 10

# Preparing costs a round trip, so keep one statement per session without
# keeping the session itself alive.
_ANN_STATEMENTS: WeakKeyDictionary = WeakKeyDictionary()


@dataclass
class Hit:
    id: str
    title: str
    category: str
    body: str
    model: str | None = None
    model_year: int | None = None
    score: float | None = None

    def to_dict(self) -> dict[str, object]:
        return asdict(self)


def _ann_statement(session):
    statement = _ANN_STATEMENTS.get(session)
    if statement is None:
        statement = session.prepare(
            f"""
            SELECT id, title, category, body, model, model_year,
                   similarity_cosine(embedding, ?) AS score
            FROM {TABLE}
            ORDER BY embedding ANN OF ?
            LIMIT ?
            """
        )
        _ANN_STATEMENTS[session] = statement
    return statement


def vector_search(session, query: str, limit: int = 5) -> list[Hit]:
    """Return semantic neighbors ordered by Cassandra ANN."""
    bounded_limit = max(1, min(int(limit), MAX_LIMIT))
    embedding = embed_query(query)
    rows = session.execute(
        _ann_statement(session),
        (embedding, embedding, bounded_limit),
    )
    return [
        Hit(
            id=row.id,
            title=row.title,
            category=row.category,
            body=row.body,
            model=getattr(row, "model", None),
            model_year=getattr(row, "model_year", None),
            score=float(row.score) if row.score is not None else None,
        )
        for row in rows
    ]
