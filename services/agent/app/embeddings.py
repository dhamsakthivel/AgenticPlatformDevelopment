import hashlib
import math
import re

from langchain_core.embeddings import Embeddings

TOKEN = re.compile(r"[a-z0-9]+")


class HashEmbeddings(Embeddings):
    """Lexical feature hashing so RAG runs with no external model.

    Production swaps this for an OpenAI-compatible embeddings model at the
    same dimension (text-embedding-3-small supports reduced dimensions).
    """

    def __init__(self, dim: int = 256) -> None:
        self.dim = dim

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return [self.embed_query(text) for text in texts]

    def embed_query(self, text: str) -> list[float]:
        vector = [0.0] * self.dim
        tokens = TOKEN.findall(text.lower())
        if not tokens:
            return vector
        for token in tokens:
            digest = hashlib.sha256(token.encode()).digest()
            index = int.from_bytes(digest[:4], "little") % self.dim
            sign = 1.0 if digest[4] % 2 == 0 else -1.0
            vector[index] += sign
        norm = math.sqrt(sum(value * value for value in vector)) or 1.0
        return [value / norm for value in vector]


def build_embeddings(settings) -> Embeddings:
    if not settings.llm_enabled():
        return HashEmbeddings(settings.embed_dim)
    from langchain_openai import OpenAIEmbeddings

    kwargs = {
        "model": settings.embed_model,
        "api_key": settings.openai_api_key,
        "dimensions": settings.embed_dim,
    }
    if settings.openai_base_url:
        kwargs["base_url"] = settings.openai_base_url
    return OpenAIEmbeddings(**kwargs)
