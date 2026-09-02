from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from presenter import QueryRequest, app  # noqa: E402
from retrieval import DEFAULT_QUERIES, Hit  # noqa: E402


class VectorDemoTests(unittest.TestCase):
    def test_scripted_queries_cover_three_vector_failure_modes(self) -> None:
        self.assertEqual(len(DEFAULT_QUERIES), 3)
        self.assertIn("tailgate", DEFAULT_QUERIES[0])
        self.assertIn("24V-330", DEFAULT_QUERIES[1])
        self.assertIn("tow", DEFAULT_QUERIES[2])

    def test_hit_serializes_for_the_presenter(self) -> None:
        hit = Hit(
            "recall-24v-113",
            "Recall 24V-113",
            "recalls",
            "Tailgate latch",
            "1500",
            2024,
            0.78,
        )
        self.assertEqual(hit.to_dict()["model_year"], 2024)

    def test_presenter_exposes_only_retrieval_routes(self) -> None:
        paths = {route.path for route in app.routes}
        self.assertIn("/api/query", paths)
        self.assertNotIn("/api/generate", paths)

    def test_query_request_rejects_blank_input(self) -> None:
        with self.assertRaises(ValueError):
            QueryRequest(query="")

    def test_corpus_has_unique_documents(self) -> None:
        docs = json.loads((ROOT / "data" / "corpus.json").read_text())
        ids = [doc["id"] for doc in docs]
        self.assertEqual(len(ids), len(set(ids)))

    def test_corpus_has_enough_recalls_to_show_the_failure(self) -> None:
        """Below roughly eight similar recalls, ANN ranks identifiers correctly
        every time and the demo's central point disappears."""
        docs = json.loads((ROOT / "data" / "corpus.json").read_text())
        recalls = [doc for doc in docs if doc["category"] == "recalls"]
        self.assertGreaterEqual(len(recalls), 8)


if __name__ == "__main__":
    unittest.main()
