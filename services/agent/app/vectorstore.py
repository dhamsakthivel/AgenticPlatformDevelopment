from dataclasses import dataclass, field
import math
from typing import Protocol

from langchain_core.embeddings import Embeddings


@dataclass
class Chunk:
    id: str
    document_id: str
    title: str
    section: str
    content: str
    embedding: list[float] = field(default_factory=list)

    def excerpt(self, limit: int = 280) -> str:
        text = " ".join(self.content.split())
        if len(text) <= limit:
            return text
        return text[: limit - 1].rstrip() + "…"


class VectorStore(Protocol):
    def upsert(self, chunks: list[Chunk]) -> None: ...

    def delete_document(self, document_id: str) -> None: ...

    def search(
        self, vector: list[float], top_k: int, document_ids: list[str] | None = None
    ) -> list[tuple[Chunk, float]]: ...

    def count(self) -> int: ...


def cosine(left: list[float], right: list[float]) -> float:
    return sum(a * b for a, b in zip(left, right))


class MemoryVectorStore:
    def __init__(self) -> None:
        self._chunks: dict[str, Chunk] = {}

    def upsert(self, chunks: list[Chunk]) -> None:
        for chunk in chunks:
            self._chunks[chunk.id] = chunk

    def delete_document(self, document_id: str) -> None:
        self._chunks = {
            key: chunk
            for key, chunk in self._chunks.items()
            if chunk.document_id != document_id
        }

    def search(
        self, vector: list[float], top_k: int, document_ids: list[str] | None = None
    ) -> list[tuple[Chunk, float]]:
        allowed = set(document_ids or [])
        scored: list[tuple[Chunk, float]] = []
        for chunk in self._chunks.values():
            if allowed and chunk.document_id not in allowed:
                continue
            score = cosine(vector, chunk.embedding)
            if math.isnan(score):
                continue
            scored.append((chunk, score))
        scored.sort(key=lambda item: item[1], reverse=True)
        return scored[:top_k]

    def count(self) -> int:
        return len(self._chunks)


class PgVectorStore:
    """pgvector-backed store. Used when DATABASE_URL is set and the extension exists."""

    def __init__(self, database_url: str, dim: int) -> None:
        import psycopg

        self._dim = dim
        self._conn = psycopg.connect(database_url, autocommit=True)
        self._prepare()

    def _prepare(self) -> None:
        with self._conn.cursor() as cur:
            cur.execute("CREATE EXTENSION IF NOT EXISTS vector")
            cur.execute(
                f"""
                CREATE TABLE IF NOT EXISTS kb_chunks (
                    id TEXT PRIMARY KEY,
                    document_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    section TEXT NOT NULL,
                    content TEXT NOT NULL,
                    embedding vector({self._dim}) NOT NULL
                )
                """
            )
            cur.execute(
                "CREATE INDEX IF NOT EXISTS kb_chunks_document_idx ON kb_chunks (document_id)"
            )

    def upsert(self, chunks: list[Chunk]) -> None:
        with self._conn.cursor() as cur:
            for chunk in chunks:
                literal = "[" + ",".join(f"{value:.6f}" for value in chunk.embedding) + "]"
                cur.execute(
                    """
                    INSERT INTO kb_chunks (id, document_id, title, section, content, embedding)
                    VALUES (%s, %s, %s, %s, %s, %s::vector)
                    ON CONFLICT (id) DO UPDATE SET
                      title = EXCLUDED.title,
                      section = EXCLUDED.section,
                      content = EXCLUDED.content,
                      embedding = EXCLUDED.embedding
                    """,
                    (chunk.id, chunk.document_id, chunk.title, chunk.section, chunk.content, literal),
                )

    def delete_document(self, document_id: str) -> None:
        with self._conn.cursor() as cur:
            cur.execute("DELETE FROM kb_chunks WHERE document_id = %s", (document_id,))

    def search(
        self, vector: list[float], top_k: int, document_ids: list[str] | None = None
    ) -> list[tuple[Chunk, float]]:
        literal = "[" + ",".join(f"{value:.6f}" for value in vector) + "]"
        filters = ""
        params: list[object] = [literal]
        if document_ids:
            filters = "WHERE document_id = ANY(%s)"
            params.append(document_ids)
        params.extend([literal, top_k])
        sql = f"""
            SELECT id, document_id, title, section, content,
                   1 - (embedding <=> %s::vector) AS score
            FROM kb_chunks
            {filters}
            ORDER BY embedding <=> %s::vector
            LIMIT %s
        """
        with self._conn.cursor() as cur:
            cur.execute(sql, params)
            rows = cur.fetchall()
        hits: list[tuple[Chunk, float]] = []
        for row in rows:
            chunk = Chunk(
                id=row[0],
                document_id=row[1],
                title=row[2],
                section=row[3],
                content=row[4],
                embedding=[],
            )
            hits.append((chunk, float(row[5])))
        return hits

    def count(self) -> int:
        with self._conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM kb_chunks")
            row = cur.fetchone()
        return int(row[0]) if row else 0


def open_store(database_url: str, dim: int) -> tuple[VectorStore, str]:
    if not database_url:
        return MemoryVectorStore(), "memory"
    try:
        return PgVectorStore(database_url, dim), "pgvector"
    except Exception:
        return MemoryVectorStore(), "memory"


def embed_chunks(embedder: Embeddings, chunks: list[Chunk]) -> list[Chunk]:
    vectors = embedder.embed_documents([chunk.content for chunk in chunks])
    for chunk, vector in zip(chunks, vectors):
        chunk.embedding = vector
    return chunks
