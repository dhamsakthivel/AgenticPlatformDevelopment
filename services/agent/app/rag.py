import re
import uuid

from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.schemas import Citation
from app.vectorstore import Chunk, VectorStore, embed_chunks

HEADING = re.compile(r"^#{1,3} ", re.MULTILINE)


def split_document(document_id: str, title: str, text: str) -> list[Chunk]:
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=700,
        chunk_overlap=80,
        separators=["\n\n", "\n", ". ", " "],
    )
    sections = _sections(text)
    chunks: list[Chunk] = []
    for section, body in sections:
        for piece in splitter.split_text(body.strip()):
            if not piece.strip():
                continue
            chunks.append(
                Chunk(
                    id=f"{document_id}:{uuid.uuid4().hex[:10]}",
                    document_id=document_id,
                    title=title,
                    section=section,
                    content=f"{section}\n{piece.strip()}",
                )
            )
    if not chunks:
        chunks.append(
            Chunk(
                id=f"{document_id}:{uuid.uuid4().hex[:10]}",
                document_id=document_id,
                title=title,
                section=title,
                content=text.strip(),
            )
        )
    return chunks


def _sections(text: str) -> list[tuple[str, str]]:
    matches = list(HEADING.finditer(text))
    if not matches:
        return [("Document", text)]
    sections: list[tuple[str, str]] = []
    if matches[0].start() > 0:
        preamble = text[: matches[0].start()].strip()
        if preamble:
            sections.append(("Overview", preamble))
    for index, match in enumerate(matches):
        start = match.end()
        end = matches[index + 1].start() if index + 1 < len(matches) else len(text)
        heading = match.group(0).lstrip("#").strip()
        body = text[start:end]
        sections.append((heading, body))
    return sections


def index_document(store: VectorStore, embedder, document_id: str, title: str, text: str) -> int:
    store.delete_document(document_id)
    chunks = embed_chunks(embedder, split_document(document_id, title, text))
    store.upsert(chunks)
    return len(chunks)


def search(store: VectorStore, embedder, query: str, top_k: int, document_ids: list[str]) -> list[Citation]:
    vector = embedder.embed_query(query)
    hits = store.search(vector, top_k, document_ids)
    citations: list[Citation] = []
    for chunk, score in hits:
        if score < 0.05:
            continue
        citations.append(
            Citation(
                document_id=chunk.document_id,
                title=chunk.title,
                section=chunk.section,
                excerpt=chunk.excerpt(),
                score=round(float(score), 4),
            )
        )
    return citations
