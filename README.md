# QA Agentic Platform

Agents that turn a user story into reviewed test cases and synthetic test data, on a made-up health-insurance enrollment domain. Nothing here is a real client system.

This repository is at **Phase 0**: the empty frame. The app starts, the database tools are described, and the screen has four placeholder pages. No agent reads a story yet.

The full specification is [docs/SPEC.md](docs/SPEC.md). Build steps are in [docs/BUILD_PROMPTS.md](docs/BUILD_PROMPTS.md).

## What you need

- Java 21
- Maven 3.9+ (or the included `./mvnw`)
- Node.js 22+
- Docker, for Postgres, Redis, and the Temporal dev server

## Run it

Open a terminal **inside this repository**, not in your home folder. The prompt should end with the project name, for example `AgenticPlatformDevelopment`. If you do not have the code yet:

```bash
git clone https://github.com/dhamsakthivel/AgenticPlatformDevelopment.git
cd AgenticPlatformDevelopment
git checkout cursor/qa-agent-platform-phase-0-192c
```

### Mac or Linux

```bash
docker compose -f infra/docker-compose.yml up -d
./mvnw verify
./mvnw -pl platform-app -am spring-boot:run -Dspring-boot.run.profiles=local
```

In a second terminal, from the same repository:

```bash
cd frontend
npm install
npm run dev
```

### Windows PowerShell

PowerShell does not run `./mvnw`. Use `.\mvnw.cmd`, and quote the `-D` setting so PowerShell does not eat it. Docker Desktop must be running.

Start the Docker Desktop app and wait until it says the engine is running. `npipe://./pipe/dockerDesktopLinuxEngine` and `The system cannot find the file specified` mean the Docker program is installed but the engine is switched off, so Postgres never starts and the app then reports `Connection to localhost:5432 refused`.

```powershell
cd $HOME\AgenticPlatformDevelopment
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps
.\mvnw.cmd verify
.\mvnw.cmd -pl platform-app -am spring-boot:run "-Dspring-boot.run.profiles=local"
```

`docker compose ps` should show the postgres service as `healthy` before you start the app.

In a second PowerShell window:

```powershell
cd $HOME\AgenticPlatformDevelopment\frontend
npm install
npm run dev
```

If Maven stops with `release version 21 not supported`, the PC is using an older Java. Install Java 21, then open a **new** PowerShell window and run the commands again:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

Close PowerShell, open it again, and check that both lines say version 21:

```powershell
java -version
javac -version
```

Then open:

- App: http://localhost:5173
- API health: http://localhost:8080/api/health
- API docs: http://localhost:8080/swagger-ui.html
- Temporal UI: http://localhost:8233 (bundled in the Temporal dev server; gRPC is `localhost:7233`)

`npm run build` in `frontend/` typechecks and produces the production bundle.

Copy `.env.example` to `.env` when you have an OpenAI key or sandbox credentials. The `local` profile does not need those keys yet, because no agent calls a model in this phase. Postgres from Compose uses database `qa_agent`, user `qa_agent`, password `qa_agent` on port 5432. Redis is on port 6379.

The `client` profile is reserved and refuses to start.

## Versions pinned for this phase

| Piece | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.16 |
| Spring AI BOM | 1.1.8 (declared, not called yet) |
| Temporal Java SDK | 1.40.0 |
| springdoc OpenAPI | 2.10.0 |
| Postgres image | `pgvector/pgvector:pg16` |
| Redis image | `redis:7.4.7` |
| Temporal dev server | `temporalio/temporal:1.9.1` (`server start-dev --ip 0.0.0.0`, UI on 8233) |
| React | 19 |
| Vite | 8 |

## Still to build

1. Story records, database tables, and the Azure DevOps / qTest connections (mocked on a laptop).
2. Document upload and search.
3. The agent that reads a story.
4. The agent that drafts test cases.
5. The sample enrollment app and the agent that creates test data.
6. The workflow that runs those steps, and stops for a person to approve the test cases.
7. Live screens for runs, approvals, knowledge, and cost.
8. Checks that score the agents.
9. Optional: run the tests in a browser and file defects.
