"""Vector ANN retrieval shared by the CLI and presenter."""

from __future__ import annotations

from dataclasses import asdict, dataclass

from common import embed_query, vector_literal

DEFAULT_QUERIES = [
    "Is there a recall on the tailgate?",
    "What is recall 24V-113?",
    "How much can the Summit 1500 tow?",
]


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


def vector_search(session, query: str, limit: int = 5) -> list[Hit]:
    """Return semantic neighbors ordered by Cassandra ANN."""
    literal = vector_literal(embed_query(query))
    rows = session.execute(
        f"""
        SELECT id, title, category, body, model, model_year,
               similarity_cosine(embedding, {literal}) AS score
        FROM docs
        ORDER BY embedding ANN OF {literal}
        LIMIT {limit}
        """
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
