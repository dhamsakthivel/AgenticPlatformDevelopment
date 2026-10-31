from pathlib import Path

from langchain_core.language_models.chat_models import BaseChatModel

from app.config import Settings
from app.embeddings import build_embeddings
from app.graph import StoryGraph
from app.predict import predict_effort
from app.rag import index_document, search
from app.schemas import EstimateRequest, GenerateRequest, IngestRequest, QueryRequest
from app.vectorstore import open_store


class Engine:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        self.embedder = build_embeddings(settings)
        self.store, self.vector_backend = open_store(settings.database_url, settings.embed_dim)
        self.llm = _chat_model(settings)
        self.graph = StoryGraph(self.store, self.embedder, self.llm)

    def ingest(self, request: IngestRequest) -> dict:
        count = index_document(self.store, self.embedder, request.id, request.title, request.text)
        return {"id": request.id, "chunks": count, "backend": self.vector_backend}

    def delete(self, document_id: str) -> None:
        self.store.delete_document(document_id)

    def query(self, request: QueryRequest) -> dict:
        citations = search(
            self.store,
            self.embedder,
            request.query,
            request.top_k,
            request.document_ids,
        )
        return {"citations": [item.model_dump() for item in citations], "backend": self.vector_backend}

    def generate(self, request: GenerateRequest) -> dict:
        story = self.graph.invoke(request.prompt, request.document_ids)
        return story.model_dump()

    def estimate(self, request: EstimateRequest) -> dict:
        prediction = predict_effort(request.title, request.description, request.acceptance_criteria)
        return prediction.model_dump()

    def status(self) -> dict:
        return {
            "llm": self.settings.chat_model if self.settings.llm_enabled() else "local-rubric",
            "embeddings": self.settings.embed_model if self.settings.llm_enabled() else "hash-256",
            "vector": self.vector_backend,
            "chunks": self.store.count(),
            "langsmith": self.settings.langsmith_enabled(),
            "langsmithProject": self.settings.langsmith_project if self.settings.langsmith_enabled() else "",
        }

    def seed(self, path: str) -> dict | None:
        if not path:
            return None
        file = Path(path)
        if not file.is_file():
            return None
        text = file.read_text(encoding="utf-8")
        return self.ingest(
            IngestRequest(id="seed-harbor", title="Harbor Field Service brief", text=text, source="seed", tags=["prd"])
        )


def _chat_model(settings: Settings) -> BaseChatModel | None:
    if not settings.llm_enabled():
        return None
    from langchain_openai import ChatOpenAI

    kwargs = {
        "model": settings.chat_model,
        "api_key": settings.openai_api_key,
        "temperature": 0.2,
    }
    if settings.openai_base_url:
        kwargs["base_url"] = settings.openai_base_url
    return ChatOpenAI(**kwargs)


def configure_langsmith(settings: Settings) -> None:
    import os

    if not settings.langsmith_enabled():
        os.environ.pop("LANGSMITH_TRACING", None)
        os.environ["LANGCHAIN_TRACING_V2"] = "false"
        return
    os.environ["LANGSMITH_API_KEY"] = settings.langsmith_api_key
    os.environ["LANGCHAIN_API_KEY"] = settings.langsmith_api_key
    os.environ["LANGSMITH_TRACING"] = "true"
    os.environ["LANGCHAIN_TRACING_V2"] = "true"
    os.environ["LANGSMITH_PROJECT"] = settings.langsmith_project
    os.environ["LANGCHAIN_PROJECT"] = settings.langsmith_project
    if settings.langsmith_endpoint:
        os.environ["LANGSMITH_ENDPOINT"] = settings.langsmith_endpoint
