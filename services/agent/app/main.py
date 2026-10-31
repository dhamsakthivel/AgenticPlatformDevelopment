from contextlib import asynccontextmanager

import secrets

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse
from starlette.routing import Route

from app.config import Settings
from app.engine import Engine, configure_langsmith
from app.mcp_server import build_mcp, transport_security
from app.schemas import EstimateRequest, GenerateRequest, IngestRequest, QueryRequest


def create_app(settings: Settings | None = None) -> FastAPI:
    settings = settings or Settings()
    configure_langsmith(settings)
    engine = Engine(settings)
    mcp = build_mcp(engine)
    mcp.streamable_http_app(
        streamable_http_path="/",
        json_response=True,
        stateless_http=True,
        transport_security=transport_security(),
    )

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        app.state.seed = engine.seed(settings.seed_path)
        async with mcp.session_manager.run():
            yield

    app = FastAPI(
        title="AKE User Story Agent",
        version="0.1.0",
        lifespan=lifespan,
    )
    app.state.engine = engine
    app.state.settings = settings

    # Exact path, so clients are not bounced through a slash redirect that drops POST bodies.
    app.router.routes.append(
        Route("/mcp", endpoint=mcp.session_manager.asgi_app, methods=["GET", "POST", "DELETE"])
    )

    def require_key(x_api_key: str = Header(default="")) -> None:
        provided = x_api_key.encode()
        expected = settings.api_key.encode()
        if len(provided) != len(expected) or not secrets.compare_digest(provided, expected):
            raise HTTPException(status_code=401, detail="A valid X-API-Key header is required.")

    @app.get("/health")
    def health() -> dict:
        return {"status": "up", "service": "ake-agent"}

    @app.get("/v1/system")
    def system(_: None = Depends(require_key)) -> dict:
        return {"status": "up", **engine.status(), "mcp": ["/mcp"]}

    @app.post("/v1/documents/ingest")
    def ingest(body: IngestRequest, _: None = Depends(require_key)) -> dict:
        return engine.ingest(body)

    @app.delete("/v1/documents/{document_id}")
    def delete_document(document_id: str, _: None = Depends(require_key)) -> JSONResponse:
        engine.delete(document_id)
        return JSONResponse({"deleted": document_id})

    @app.post("/v1/rag/query")
    def query(body: QueryRequest, _: None = Depends(require_key)) -> dict:
        return engine.query(body)

    @app.post("/v1/stories/generate")
    def generate(body: GenerateRequest, _: None = Depends(require_key)) -> dict:
        return engine.generate(body)

    @app.post("/v1/stories/estimate")
    def estimate(body: EstimateRequest, _: None = Depends(require_key)) -> dict:
        return engine.estimate(body)

    return app


app = create_app()
