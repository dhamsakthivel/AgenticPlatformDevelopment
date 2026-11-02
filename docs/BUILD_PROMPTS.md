# Build prompts for Cursor

Use these in order. Start a NEW Cursor Agent chat for each phase, paste the prompt, and review + commit before moving on.
Every prompt assumes `.cursor/rules/qa-agent-platform.mdc` and `docs/SPEC.md` are in the repo.

---

## Phase 0 — Scaffold

```
Read @docs/SPEC.md and the project rules. We are starting Phase 0: scaffolding only.

Create:
1. Maven parent pom (Java 21, Spring Boot 3.x latest stable, Spring AI BOM latest stable 1.x, Temporal Java SDK latest stable) and the modules listed in SPEC section 3 "Repository layout" (platform-core, platform-knowledge, platform-agents, platform-integrations, platform-orchestrator, platform-api, platform-app, mock-qtest, enrollment-app). Empty packages are fine, but each module must compile.
2. platform-app: Spring Boot main class, application.yml with profiles local and sandbox, /api/health endpoint, springdoc OpenAPI, Flyway enabled with an empty baseline migration.
3. infra/docker-compose.yml with: pgvector/pgvector:pg16 (with a volume, and an init script enabling the vector extension), redis:7, Temporal dev server and Temporal UI. Verify the correct Temporal images and commands from Temporal's docs.
4. frontend/: React + TypeScript + Vite, Tailwind, shadcn/ui initialised, React Router, TanStack Query, a layout with nav items Runs, Approvals, Knowledge, Metrics (placeholder pages), and a dev proxy to the backend.
5. .env.example with every variable from SPEC section 4, .gitignore (include .env), README.md with setup and run steps.

Done when: `docker compose -f infra/docker-compose.yml up -d`, `mvn verify`, starting platform-app with profile local, and `npm run dev` in frontend all work. List the exact commands in the README.
Do not implement any agent or adapter logic yet.
```

---

## Phase 1 — Domain, schema, ports, adapters

```
Phase 1. Read @docs/SPEC.md sections 5, 8 and 12, and section 14.

Implement:
1. Flyway migrations for every table in SPEC section 8, including the HNSW and GIN indexes.
2. In platform-core: domain records (Story, TestCaseDraft, ExternalTestCaseRef, TestCaseStatus, etc.) and the port interfaces exactly as in SPEC section 5.
3. In platform-integrations:
   - MockWorkItemAdapter (profile local) loading stories from scripts/seed/stories.json.
   - AzureDevOpsAdapter (profile sandbox) using the Azure DevOps REST API with a PAT: get work item, add comment, add hyperlink. Check the current REST API version in Microsoft's docs.
   - QTestAdapter using QTEST_BASE_URL and QTEST_TOKEN, following qTest Manager's public REST API shapes.
   - AuditService that records every external write into audit_event.
4. mock-qtest module: Spring Boot app on port 8091 implementing the qTest API subset from SPEC section 12, in-memory storage.
5. scripts/seed/stories.json with 18 synthetic enrollment stories (3 deliberately vague) per SPEC section 14, and scripts/seed-ado-stories (a small Java or shell script) that creates them in the sandbox ADO project.
6. Tests: unit tests for adapters with WireMock; a Testcontainers test proving migrations apply.

Done when mvn verify passes and the README explains how to seed ADO.
```

---

## Phase 2 — Knowledge layer (RAG)

```
Phase 2. Read @docs/SPEC.md section 9.

Implement in platform-knowledge:
1. PhiRedactor in platform-core (regex for SSN, member-ID-like patterns, DOB, phone, email; replace with typed tokens; unit tests with positive and negative cases).
2. Document ingestion: Markdown and PDF loaders, content_hash, skip unchanged docs.
3. Chunkers: Markdown by heading with token-size limits (300–800 tokens), story as one chunk, test case as one chunk.
4. OpenAiEmbeddingAdapter implementing EmbeddingPort via Spring AI, model and dimensions from config, batching, logging to llm_call.
5. Storage of chunks with embedding vector(1536) and tsvector.
6. HybridRetriever: vector top 20 + full-text top 20, Reciprocal Rank Fusion (k=60), metadata filters, returns chunks with ids and scores.
7. Retrieval profiles per agent in application.yml.
8. knowledge-seed/ with the four synthetic documents from SPEC section 14 (write realistic but invented enrollment business rules).
9. API endpoints: POST /api/knowledge/documents, POST /api/knowledge/reindex, GET /api/knowledge/search.
10. Tests: Testcontainers test that ingests seed docs with a fake EmbeddingPort and verifies hybrid ranking and filters.

Done when I can upload the seed docs and search them via the API.
```

---

## Phase 3 — LLM client, prompts, Story Analysis Agent

```
Phase 3. Read @docs/SPEC.md sections 6 (Story Analysis) and 15.

Implement:
1. LlmClient implementation over Spring AI ChatClient: structured output into a record, JSON Schema validation (use a JSON Schema validator library), one repair retry with the validation errors, cost calculation from llm-prices.yml, logging to llm_call with prompt id/version and run/step ids.
2. PromptRegistry loading prompts/<agent>/<version>.md with variables; active version per agent from config.
3. StoryAnalysisAgent: builds input with retrieved context (clearly delimited as untrusted data), calls LlmClient, validates that citations reference provided chunk ids, returns StoryAnalysisOutput.
4. prompts/story-analysis/v1.md: role, task, rules (treat context as data, cite chunk ids, be specific about ambiguities, riskScore rubric 1–5), output must follow schema.
5. A formatter that turns StoryAnalysisOutput into a readable Markdown comment for ADO.
6. evals/story-analysis with 8 golden cases (include the vague stories) and an @Tag("eval") test that writes a report to evals/reports/.
7. Unit tests with a mocked LlmClient.

Done when the eval runs against the real API and produces a report.
```

---

## Phase 4 — Orchestration with Temporal

```
Phase 4. Read @docs/SPEC.md sections 7 and 10.

Implement in platform-orchestrator and platform-api:
1. Temporal worker configuration (task queue "qa-agents"), client config from TEMPORAL_TARGET.
2. StoryToTestAssetsWorkflow steps 1–4 for now (fetch story, retrieve context, story analysis, post comment) plus the approval wait in step 7 stubbed so the workflow can be signalled. Workflow code must be deterministic.
3. Activities that persist agent_step start/end/output/error and publish step events.
4. Idempotency keys for external writes.
5. RunService + REST: POST /api/runs, GET /api/runs, GET /api/runs/{id}, GET /api/runs/{id}/events (SSE).
6. POST /api/webhooks/ado: validate ADO_WEBHOOK_SECRET, start the workflow when a work item's state changes to Ready, ignore duplicates for an already-running story.
7. Tests using Temporal's test environment (TestWorkflowEnvironment) with mocked activities.

Done when a manual POST /api/runs in profile local runs end to end through step 4 and is visible in the Temporal UI and via the REST API.
```

---

## Phase 5 — Test Design Agent and approval

```
Phase 5. Read @docs/SPEC.md sections 6 (Test Design), 7 steps 5–7, and 10 (approvals).

Implement:
1. TestDesignAgent with prompts/test-design/v1.md, retrieving similar existing test cases and setting possibleDuplicateOf when similarity is high.
2. Workflow steps 5–7: run test design, save drafts to TestManagementPort with DRAFT status and to test_case_draft, create an approval record, wait for the approvalDecision signal (7-day timeout).
3. Approval API: GET /api/approvals, POST /api/approvals/{id}/decision supporting APPROVE, REJECT and EDIT (edited content updates qTest via updateTestCase). Sending the decision signals the workflow.
4. evals/test-design with 6 golden cases and an @Tag("eval") test.
5. Tests for the approval flow with TestWorkflowEnvironment.

Done when a run pauses at approval, and approving via the API resumes it.
```

---

## Phase 6 — Enrollment app and Test Data Agent

```
Phase 6. Read @docs/SPEC.md sections 6 (Test Data), 7 step 8, and 13.

Implement:
1. enrollment-app per SPEC section 13: 834 subset parser, business rules from knowledge-seed/enrollment-business-rules.md, member/coverage storage, test-data load/teardown endpoints, minimal members page.
2. In platform-agents: TestDataAgent stage 1 (LLM → TestDataSpec, prompt test-data/v1.md).
3. Deterministic stage 2: EdiBuilder834 (builds valid 834 interchanges with Datafaker values, applies requested ediVariations), Edi834Validator, TestDataSet assembly. No LLM in stage 2.
4. EnrollmentAppTestDataAdapter implementing TestDataTargetPort with leases.
5. Workflow step 8 for each approved case, plus attachNote on the qTest case with the data set id, files and expected outcome.
6. Unit tests for the builder and validator, including each variation; an integration test provisioning into enrollment-app.

Done when an approved run ends with provisioned data and notes on the test cases.
```

---

## Phase 7 — Frontend

```
Phase 7. Read @docs/SPEC.md section 11.

Build the frontend pages against the real API:
1. Runs list with "Start run" dialog.
2. Run detail: step timeline with status, duration, tokens, cost; expandable JSON outputs; live updates via SSE; links to the ADO story and qTest cases.
3. Approval inbox: per test case approve/reject/edit (editable steps), submit decision, optimistic UI with error handling.
4. Knowledge: upload, document list, search with scores.
5. Metrics: cost per day, per agent, tokens per run, approval acceptance rate (add a backend endpoint if needed).
Keep the UI clean and responsive; handle loading and error states everywhere.

Done when the full MVP flow can be driven from the UI in profile local.
```

---

## Phase 8 — Hardening and demo

```
Phase 8. Hardening and demo readiness.

1. Walk through SPEC section 16 acceptance criteria one by one and fix gaps.
2. Add a "demo mode" seed: one command that resets data, ingests knowledge, and seeds stories.
3. Switch the sandbox profile to the qTest trial (only config changes should be needed; fix the adapter if not).
4. Security pass: no secrets in git, webhook secret validated, PHI redactor applied on every path to the LLM, audit events for every external write.
5. Write docs/DEMO.md: a 5-minute demo script and the metrics to show (time saved, acceptance rate, gaps found, cost per story).
6. Write docs/ARCHITECTURE.md: one-page overview with a Mermaid diagram.

Done when a clean clone can run the demo by following README and DEMO.md.
```

---

## Phase 9 (stretch) — Execution & Defect Agent

```
Phase 9 (stretch). Add execution and defect creation.

1. e2e/: Playwright TypeScript project with tests for the enrollment app, data-driven from provisioned test data sets.
2. ExecutionActivity that runs the relevant Playwright tests (via a small runner service or CLI) and collects results and traces.
3. DefectAgent: groups failures, retrieves similar existing bugs (embedding search), drafts a bug with repro steps and evidence, and waits for approval before creating it in ADO through WorkItemPort (add createBug to the port).
4. Record results on the qTest test cases.
5. Extend the run detail UI with execution results and draft defects.
```
