# QA Agentic Platform

Agents that turn a user story into reviewed test cases and synthetic test data, on a made-up health-insurance enrollment domain. Nothing here is a real client system.

This repository can load 18 made-up enrollment stories, talk to Azure DevOps or a small stand-in for qTest, search a rule book of made-up enrollment notes, and run one story through the analyst and the test designer. The screens at http://localhost:5173 start that run, show each step, and approve or reject the drafts. Approved cases do not get synthetic enrollment files yet.

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

`./mvnw verify` (or `.\mvnw.cmd verify`) now starts a temporary Postgres database inside Docker, so the Docker engine has to be running for the build as well as for the app. That check proves the new tables and the vector indexes are created.

Copy `.env.example` to `.env` in the project folder when you have an OpenAI key or sandbox credentials. The app reads that file on each model call. A value already set in the app's terminal wins. A terminal opened later does not change an app that is already running. Saving `.env` does: the next review reads it. The `local` profile does not need those keys for search. Postgres from Compose uses database `qa_agent`, user `qa_agent`, password `qa_agent` on port 5432. Redis is on port 6379.

The `client` profile is reserved and refuses to start.

After you pull this phase, stop the app if it is already running and start it again so it picks up the search code.

## The rule book

The rule book is a set of notes the later agents will search before they write anything. The notes are invented for a fake plan called Example Benefit Plan. They are not a real insurer’s rules, and they are not the licensed 834 guide.

Four notes ship in `knowledge-seed/`: who can be enrolled, a short glossary, what an 834 file is in our own words, and how a test case should be written. A reindex also files each local story, and any test case the qTest stand-in currently has.

Personal-looking values are swapped for tokens such as `[EMAIL]` and `[SSN]` before anything is saved or embedded. A business rule such as “a child under 26” or “effective date 2026-01-01” is left as written.

With no `OPENAI_API_KEY`, search still works. The app builds a stand-in vector named `local-token-hash` from the words in the text, so shared words land near each other. Rankings will not match a real OpenAI embedding. When `OPENAI_API_KEY` is in the app's terminal or in `.env`, the next search uses `text-embedding-3-small` (1,536 numbers per chunk). Reindex after the key is present so the notes are stored with that model.

Postgres from Compose has to be running. From the repository folder, with the app already started on port 8080:

```bash
curl -s -X POST http://localhost:8080/api/knowledge/reindex
curl -s "http://localhost:8080/api/knowledge/search?q=spouse%20dependent&module=enrollment"
```

```powershell
curl.exe -s -X POST http://localhost:8080/api/knowledge/reindex
curl.exe -s "http://localhost:8080/api/knowledge/search?q=spouse%20dependent&module=enrollment"
```

The reindex reply counts what was filed, what was skipped because it had not changed, and any warnings. A second reindex should skip the same notes. If the qTest stand-in is not running, reindex still files the notes and the stories, and adds a warning about test cases.

To file one note of your own:

```bash
curl -s -F "file=@knowledge-seed/enrollment-business-rules.md;type=text/markdown" \
  http://localhost:8080/api/knowledge/documents
```

```powershell
curl.exe -s -F "file=@knowledge-seed/enrollment-business-rules.md;type=text/markdown" `
  http://localhost:8080/api/knowledge/documents
```

Search accepts `q`, and optional `source`, `app`, `module`, `profile`, and `limit`. Profiles named `story-analysis`, `test-design`, and `test-data` live in `platform-app/src/main/resources/application.yml`. They choose which kinds of notes each future agent should see. On the local profile, stories are the `ENR-00x` ids from `scripts/seed/stories.json`. A sandbox Azure DevOps project uses numeric work item ids, so a reindex there warns that `ENR-001` was not found unless you point the stories file at those numeric ids.

## The story analyst

The analyst reads one story plus the notes search finds, and writes a short review: a summary, which acceptance criteria a tester could actually check, what is still unclear, a risk score from 1 to 5, and the ids of the notes it used. Text from the notes is treated as data. An instruction hidden in a note does not change the analyst's job. Personal-looking values are swapped for tokens before the text is sent.

The wording lives in `platform-agents/src/main/resources/prompts/story-analysis/v1.md`. The shape of the answer is `platform-core/src/main/resources/schemas/story-analysis-output.json`. If the answer does not match that shape, the analyst asks once more with the errors, then stops. If it cites a note that was not provided, it asks once more, then stops.

The app still starts when `OPENAI_API_KEY` is empty. Each review reads `OPENAI_API_KEY` and `OPENAI_CHAT_MODEL` at the moment of the call: the app's terminal, then `.env`, then the values seen at startup. If either one is still empty, that step fails and names the one to set. A priced example is `gpt-5-mini`. That model rejects every temperature except 1, so the client sends 1 for it and for the o-series models. Other models still receive 0.2. Prices for a few model names are in `platform-integrations/src/main/resources/llm-prices.yml`, taken from OpenAI's published token prices. Add a row if you choose a model that is not listed. Cost is recorded on each call.

`./mvnw verify` does not call OpenAI. It uses a fake model. The real check is eight stories in `evals/story-analysis/`, including the three vague ones. It writes a report under `evals/reports/` and expects at least 7 of the 8 stories to pass.

```bash
export OPENAI_API_KEY="your-key"
export OPENAI_CHAT_MODEL="gpt-5-mini"
./scripts/run-evals.sh
```

```powershell
$env:OPENAI_API_KEY = 'your-key'
$env:OPENAI_CHAT_MODEL = 'gpt-5-mini'
.\scripts\run-evals.ps1
```

Do not paste the key into a chat. The report lists pass or fail, the risk score, tokens, and cost for each story.

## Drive a run from the screen

Start Docker, the app, and the qTest stand-in the same way as below. Put `OPENAI_API_KEY` and `OPENAI_CHAT_MODEL` in that app's terminal, or in `.env` in the project folder. Open http://localhost:5173 on the same computer. A preview address hosted on another machine uses that machine's terminal and `.env`. In another window:

```powershell
cd $HOME\AgenticPlatformDevelopment\frontend
npm install
npm run dev
```

Open http://localhost:5173.

1. **Runs** explains the path: the story analyst writes a review, the test designer drafts cases, then the run waits for you.
2. **Start a run** and pick a story. `ENR-002` is a clear story. `ENR-016` is vague on purpose, so the review should find gaps. Stay on the run page. The steps update as the agents finish. A model call usually takes about half a minute.
3. When the status is **Waiting for you**, open **Approvals**. Approve, reject, or edit every draft, then send the decision. The run then completes.
4. **Knowledge** searches the rule book the agents read. **Metrics** shows token cost and how many drafts were accepted.

A story that already has a running or waiting run cannot be started again. Open that run from the message on the start dialog.

## Start a run

Docker Desktop has to be running, including Postgres and the Temporal dev server. Start the app on the local profile, with `OPENAI_API_KEY` and `OPENAI_CHAT_MODEL` set if you want the analyst to call the model. Then ask for one story:

```bash
curl -s -X POST http://localhost:8080/api/runs \
  -H 'Content-Type: application/json' \
  -d '{"storyId":"ENR-002","triggeredBy":"sam"}'
```

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/runs -ContentType "application/json" -Body '{"storyId":"ENR-002","triggeredBy":"sam"}'
```

The reply is the run id. `GET /api/runs/{id}` returns the steps. After the drafts are saved the status is `WAITING_FOR_APPROVAL` for up to 7 days. The comment text is on the `postStoryComment` step. The drafted cases are on the run and in the local qTest stand-in. On the local profile the comment is stored in `audit_event`. On the sandbox profile it is also posted on the Azure DevOps work item.

A second start for a story that is already running returns 409. The Temporal UI at http://localhost:8233 shows the same run. A run started before the test designer was added stays on the comment step. Start a different story, such as `ENR-002`, to draft test cases.

The local qTest stand-in has to be running before the drafts can be saved:

```powershell
.\mvnw.cmd -pl mock-qtest spring-boot:run
```

When the run status is `WAITING_FOR_APPROVAL`, list the drafts and send a decision for every key. `APPROVE` and `REJECT` need no extra text. `EDIT` includes the replacement case. The run then finishes. The drafts stay in qTest. Approved ones are marked `APPROVED`. Rejected ones are marked `REJECTED`.

```powershell
$pending = Invoke-RestMethod "http://localhost:8080/api/approvals?status=PENDING"
$pending | ConvertTo-Json -Depth 6
$body = @{
  decidedBy = "sam"
  decisions = @(
    @{ key = "TC1"; action = "APPROVE" },
    @{ key = "TC2"; action = "REJECT" }
  )
} | ConvertTo-Json -Depth 6
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/approvals/$($pending[0].id)/decision" -ContentType "application/json" -Body $body
```

To start from Azure DevOps, set `ADO_WEBHOOK_SECRET` and send that value in the `X-ADO-Webhook-Secret` header to `POST /api/webhooks/ado`. The hook starts a run only when the work item state is Ready. A story that is already running is left alone.

## The qTest stand-in

`mock-qtest` is a tiny program that pretends to be qTest. It keeps test cases in memory and forgets them when you stop it. Start it in its own terminal, from the repository folder, before anything tries to save a test case:

```bash
./mvnw -pl mock-qtest spring-boot:run
```

```powershell
.\mvnw.cmd -pl mock-qtest spring-boot:run
```

It listens on http://localhost:8091. The local app already points `QTEST_BASE_URL` there, and it uses project `1` and module `1` unless you set `QTEST_PROJECT_ID` and `QTEST_MODULE_ID`. Leave `QTEST_TOKEN` empty for the stand-in. A real qTest trial needs the token from that project's API page, and `QTEST_MODULE_ID` must be the numeric module id.

## Seed stories into Azure DevOps

The same 18 stories live in `scripts/seed/stories.json`. On a laptop, profile `local` reads that file. It does not call Azure DevOps.

To copy them into a sandbox Azure DevOps project, create a project that uses the **Agile** process. That process has a work item type named User Story. Set three values in the terminal, then run the script from the repository folder. A story whose title is already in the project is left alone, so you can run the script again.

```bash
export ADO_ORG_URL="https://dev.azure.com/<org>"
export ADO_PROJECT="<project>"
export ADO_PAT="<personal-access-token>"
./scripts/seed-ado-stories.sh
```

```powershell
$env:ADO_ORG_URL = "https://dev.azure.com/<org>"
$env:ADO_PROJECT = "<project>"
$env:ADO_PAT = "<personal-access-token>"
.\scripts\seed-ado-stories.ps1
```

The personal access token needs permission to read and write work items. The script compiles the integrations module, then creates the stories. Each line printed is the story id and the Azure DevOps work item id. Switch the app to profile `sandbox` only when those three values are set. Profile `local` keeps using the file.

## Versions pinned for this phase

| Piece | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.5.16 |
| Spring AI BOM | 1.1.8 (embeddings and chat) |
| PDFBox | 3.0.8 |
| JSON Schema validator | 1.5.6 |
| Temporal Java SDK | 1.40.0 |
| springdoc OpenAPI | 2.10.0 |
| Postgres image | `pgvector/pgvector:pg16` |
| Redis image | `redis:7.4.7` |
| Temporal dev server | `temporalio/temporal:1.9.1` (`server start-dev --ip 0.0.0.0`, UI on 8233) |
| React | 19 |
| Vite | 8 |

## Still to build

1. The sample enrollment app and the agent that creates synthetic 834 files for approved cases.
2. Optional: run the tests in a browser and file defects.
