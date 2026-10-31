# AKE — Agentic Knowledge Engine

Cloud 3.0 MVP for one agent: **draft a user story from the document library**.

The workspace is a Next.js app. Spring Boot is the system of record and the security boundary. A FastAPI runtime runs the LangGraph agent, retrieval, and MCP tools.

```text
Next.js  :3000          Spring Boot :8080           FastAPI :8000
library, agent,    -->  API key, audit,       -->   LangGraph story graph
backlog, forecast       Mongo documents,            LangChain chunking
                        Postgres stories            vector search
                                                    MCP /mcp
                                                    LangSmith traces
```

## What the MVP does

- **Library.** Paste a source. It is stored as a document and chunked into the vector index. A Harbor field-service brief is seeded on startup.
- **Story agent.** A prompt runs a four-node graph: retrieve, draft, critique, predict. The draft cites the excerpts it used.
- **Backlog.** Generated stories are saved with status `draft`, `ready`, or `committed`.
- **Forecast.** A transparent effort rubric (size, acceptance checks, risk language) snaps to Fibonacci points and flags a watchlist. This is a planning prior, not a trained model.
- **Control.** Shows the embedding backend, whether LangSmith is on, the MCP path, security controls, and the audit log.
- **Spring AI coach.** With `OPENAI_API_KEY` set, a coach can tighten one saved story. Without a key it stays idle and says so.

Without an API key the agent still runs. Embeddings are a local 256-dimension hash, and the drafter is a rubric over the retrieved text. Set `OPENAI_API_KEY` to switch both drafting and embeddings to the configured models. `text-embedding-3-small` is requested at 256 dimensions so it shares the pgvector column with the local embedder.

## Run it locally (no databases)

Three processes. The platform profile defaults to `local`, which keeps documents and stories in memory.

```bash
python3 -m venv services/agent/.venv
services/agent/.venv/bin/pip install -e "services/agent[dev]"
services/agent/.venv/bin/uvicorn app.main:app --app-dir services/agent --port 8000

# from services/platform — the wrapper downloads Maven on first use
./mvnw spring-boot:run

cp .env.example apps/web/.env.local
npm --prefix apps/web install
npm --prefix apps/web run dev
```

Open `http://localhost:3000`. The dev API key is `dev-ake-key`. The browser does not receive it; Next.js server actions send it to Spring.

## Run it with MongoDB, PostgreSQL, and pgvector

```bash
docker compose up --build
```

That sets `AKE_PROFILE=cloud`. Documents go to MongoDB, stories go to PostgreSQL, and chunk embeddings go to pgvector.

## Configuration

See `.env.example`.

| Variable | Role |
| --- | --- |
| `AKE_API_KEY` | Shared key for Next.js → Spring and Spring → agent |
| `OPENAI_API_KEY` | Optional chat and embedding model |
| `LANGSMITH_API_KEY` | Turns on LangSmith tracing for the graph |
| `DATABASE_URL` | Agent pgvector connection. Empty uses an in-memory index |
| `AKE_PROFILE` | `local` or `cloud` |

## MCP

The agent exposes Streamable HTTP MCP at `http://localhost:8000/mcp` with three tools: `search_knowledge_base`, `draft_user_story`, and `predict_effort`. Callers still send `X-API-Key` on the REST API. The MCP mount is the tool surface for other agents.

## Tests

```bash
services/agent/.venv/bin/pytest -q services/agent/tests
services/platform/mvnw -f services/platform/pom.xml test
```

## Security

- Stateless API key, compared in constant time.
- `nosniff`, `no-store`, and frame denial on platform responses.
- Body size limits and bean validation on document and story writes.
- Audit entries for index, generate, update, estimate, and coach.
- LangSmith export stays off until a key is present.
