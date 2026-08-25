"""FastAPI presenter for vector ANN retrieval."""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse
from pydantic import BaseModel, Field

from common import ensure_schema, wait_for_cassandra
from retrieval import DEFAULT_QUERIES, MAX_LIMIT, vector_search

PRESENTER_HTML = Path(__file__).resolve().parents[1] / "static" / "presenter.html"
LOGGER = logging.getLogger(__name__)


class QueryRequest(BaseModel):
    query: str = Field(min_length=1, max_length=500)
    limit: int = Field(default=5, ge=1, le=MAX_LIMIT)


@asynccontextmanager
async def lifespan(app: FastAPI):
    session = wait_for_cassandra()
    ensure_schema(session)
    app.state.cassandra = session
    yield
    session.cluster.shutdown()


app = FastAPI(title="Cassandra Vector Search Presenter", lifespan=lifespan)


@app.get("/", include_in_schema=False)
def index() -> FileResponse:
    return FileResponse(PRESENTER_HTML)


@app.get("/api/presets")
def presets() -> dict[str, object]:
    return {"queries": DEFAULT_QUERIES}


@app.post("/api/query")
def query(payload: QueryRequest, request: Request) -> dict[str, object]:
    query_text = payload.query.strip()
    if not query_text:
        raise HTTPException(status_code=422, detail="Query cannot be blank")
    try:
        hits = vector_search(
            request.app.state.cassandra,
            query_text,
            payload.limit,
        )
    except Exception as exc:
        LOGGER.exception("Vector retrieval failed")
        raise HTTPException(
            status_code=503,
            detail="Vector retrieval failed; check the server logs.",
        ) from exc
    return {
        "query": query_text,
        "hits": [hit.to_dict() for hit in hits],
    }
