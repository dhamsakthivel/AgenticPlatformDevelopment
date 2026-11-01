# QA Agentic Platform — MVP Specification

## 1. Purpose
An agentic platform where specialised AI agents work in an orchestrated workflow to turn a user story into reviewed test assets and test data. The MVP proves the orchestration idea end to end on a synthetic healthcare (EDI 834 member enrollment) domain, built entirely outside any client environment, and designed so that moving to a client environment later is a configuration change.

Target client tool landscape (for later): Azure DevOps (stories, repos, pipelines, bugs), qTest (test cases, cycles, runs), ServiceNow (change requests, incidents, CMDB), Visual Studio, PyCharm.

## 2. MVP scope

### In scope
1. Trigger: an Azure DevOps user story moves to state `Ready` (service hook) or a manual trigger from the UI.
2. Story Analysis Agent: analyses the story with RAG context, posts findings as a comment on the ADO story.
3. Test Design Agent: drafts test cases and writes them to qTest (mock in `local`, trial in `sandbox`) in a draft state.
4. Human approval: a person approves, edits or rejects the drafted test cases in the platform UI.
5. Test Data Agent: for approved test cases, produces a data spec (LLM), generates synthetic data deterministically (EDI 834 files + member records), validates it, provisions it into the system under test, and attaches a data reference to the test case.
6. Knowledge layer: document store, chunking, embeddings (OpenAI `text-embedding-3-small`), hybrid search in Postgres + pgvector.
7. Dashboard: runs list, run detail with step timeline, approval inbox, knowledge upload/search, cost metrics.
8. Observability: every LLM call logged with tokens, cost, latency, prompt version.
9. Evals: golden datasets for Story Analysis and Test Design, run on demand.

### Stretch (Phase 9)
- Execution & Defect Agent: run Playwright tests against the enrollment app; create ADO bugs for failures after approval.

### Out of scope for MVP
Kafka, Kubernetes, marketplace, traceability graph DB, Dynatrace/Splunk, release readiness, multi-tenant auth. (Design must not block them.)

## 3. Architecture

```
React UI ──REST/SSE──▶ platform-app (Spring Boot, modular monolith)
                          ├─ api            (REST, SSE, ADO webhook)
                          ├─ orchestrator   (Temporal workflows + activities)
                          ├─ agents         (story-analysis, test-design, test-data)
                          ├─ knowledge      (ingestion, chunking, embedding, hybrid retrieval)
                          ├─ core           (contracts, ports, schemas, redactor)
                          └─ integrations   (ADO, qTest, mock qTest, ServiceNow, OpenAI, enrollment app)
                                   │
        Postgres+pgvector · Redis · Temporal dev server · mock-qtest · enrollment-app (SUT)
```

### Repository layout
```
qa-agent-platform/
  pom.xml                         (parent)
  platform-core/                  contracts, ports, JSON schemas, PhiRedactor
  platform-knowledge/             documents, chunks, embeddings, retrieval
  platform-agents/                agents + prompts
  platform-integrations/          adapters (ado, qtest, servicenow, openai, enrollment)
  platform-orchestrator/          Temporal workflows, activities, worker config
  platform-api/                   REST controllers, SSE, webhook
  platform-app/                   Spring Boot main, profiles, Flyway migrations
  mock-qtest/                     small Spring Boot app imitating the qTest REST API subset
  enrollment-app/                 system under test (Spring Boot)
  frontend/                       React + Vite + TS
  e2e/                            Playwright (stretch)
  evals/                          golden datasets (JSON)
  knowledge-seed/                 synthetic business rules and sample docs (Markdown)
  scripts/                        seed-ado-stories, ingest-knowledge, run-evals
  infra/docker-compose.yml
  docs/SPEC.md, docs/BUILD_PROMPTS.md
  .env.example, README.md
```

### Tech stack
- Java 21, Spring Boot 3.x, Spring AI 1.x (OpenAI chat + embeddings; Azure OpenAI switchable by config), Temporal Java SDK, Flyway, Testcontainers, Datafaker.
- Postgres 16 with pgvector (image `pgvector/pgvector:pg16`), Redis 7, Temporal dev server + Temporal UI.
- React 18+, TypeScript, Vite, TanStack Query, React Router, Tailwind, shadcn/ui.
- Playwright (TypeScript) for the stretch phase.

## 4. Configuration
Environment variables (see `.env.example`):
```
SPRING_PROFILES_ACTIVE=local|sandbox
OPENAI_API_KEY=
OPENAI_CHAT_MODEL=                 # set by user, e.g. a current GPT model
OPENAI_CHAT_MODEL_SMALL=           # cheaper model for classification steps
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_DIMENSIONS=1536
ADO_ORG_URL=https://dev.azure.com/<org>
ADO_PROJECT=
ADO_PAT=
ADO_WEBHOOK_SECRET=
QTEST_BASE_URL=http://localhost:8091   # mock by default
QTEST_TOKEN=
QTEST_PROJECT_ID=
SERVICENOW_INSTANCE_URL=
SERVICENOW_USER=
SERVICENOW_PASSWORD=
ENROLLMENT_APP_URL=http://localhost:8092
TEMPORAL_TARGET=localhost:7233
LLM_PRICE_TABLE=classpath:llm-prices.yml
```

Profile behaviour:
| Port | local | sandbox |
|---|---|---|
| WorkItemPort | InMemory/Mock ADO (seeded stories) | AzureDevOpsAdapter (REST + PAT) |
| TestManagementPort | QTestAdapter → mock-qtest | QTestAdapter → qTest trial |
| ItsmPort | Mock | ServiceNowAdapter (PDI) |
| LlmClient / EmbeddingPort | OpenAI | OpenAI |
| TestDataTargetPort | enrollment-app | enrollment-app |

## 5. Ports (platform-core)
```java
interface WorkItemPort {
  Story getStory(String storyId);
  void addComment(String storyId, String markdown);
  void addLink(String storyId, String url, String label);
}
interface TestManagementPort {
  ExternalTestCaseRef createDraftTestCase(String storyId, TestCaseDraft draft);
  void updateTestCase(ExternalTestCaseRef ref, TestCaseDraft draft);
  void setStatus(ExternalTestCaseRef ref, TestCaseStatus status); // DRAFT, APPROVED, REJECTED
  void attachNote(ExternalTestCaseRef ref, String text);
  List<ExternalTestCase> findByModule(String module);
}
interface ItsmPort { /* Phase 2: change requests, incidents */ }
interface EmbeddingPort { List<float[]> embed(List<String> texts); String modelName(); }
interface LlmClient { <T> LlmResult<T> structured(LlmRequest request, Class<T> type, String schemaId); }
interface TestDataTargetPort { ProvisionResult provision(TestDataSet set); void release(String dataSetId); }
```

## 6. Agent contracts
JSON Schemas live in `platform-core/src/main/resources/schemas/`. Java records mirror them.

### Story Analysis
Input: `StoryAnalysisInput { storyId, title, description, acceptanceCriteria[], retrievedContext[] }`
Output:
```json
{
  "storyId": "string",
  "summary": "string",
  "criteria": [{ "id": "AC1", "text": "string", "testable": true, "issue": "string|null" }],
  "ambiguities": [{ "question": "string", "severity": "LOW|MEDIUM|HIGH" }],
  "missingScenarios": ["string"],
  "riskScore": 1,
  "riskReasons": ["string"],
  "citations": [{ "chunkId": "uuid", "source": "string" }]
}
```
riskScore is 1–5. Citations must reference chunk ids that were actually provided in the context.

### Test Design
Input: story + Story Analysis output + similar existing test cases (retrieved).
Output:
```json
{
  "storyId": "string",
  "testCases": [{
    "key": "TC1",
    "title": "string",
    "type": "POSITIVE|NEGATIVE|BOUNDARY",
    "priority": "P1|P2|P3",
    "preconditions": ["string"],
    "steps": [{ "action": "string", "expected": "string" }],
    "dataRequirements": ["string"],
    "linkedCriteria": ["AC1"],
    "possibleDuplicateOf": "string|null"
  }]
}
```

### Test Data (two stages)
Stage 1 (LLM) — `TestDataSpec`:
```json
{
  "testCaseKey": "TC1",
  "scenario": "string",
  "subscriber": { "ageRange": [18, 64], "gender": "M|F|U", "state": "string" },
  "dependents": [{ "relationship": "SPOUSE|CHILD", "ageRange": [0, 25], "invalidRelationshipCode": false }],
  "coverage": { "planType": "HMO|PPO", "maintenanceType": "ADD|CHANGE|TERM", "effectiveDateRule": "string" },
  "ediVariations": ["MISSING_REF_SEGMENT", "INVALID_DATE_FORMAT"],
  "expectedOutcome": "ACCEPTED|REJECTED"
}
```
Stage 2 (deterministic code, no LLM): `EdiBuilder834` + Datafaker produce `TestDataSet { id, testCaseKey, files[], members[], expectedOutcome }`, `Edi834Validator` checks structure and rules, `TestDataTargetPort.provision()` loads it and returns a lease (`leaseExpiresAt`).

## 7. Workflow (Temporal)
`StoryToTestAssetsWorkflow(String storyId, String triggeredBy)`:
1. `fetchStory` (activity)
2. `retrieveContextForStory` (activity, knowledge)
3. `runStoryAnalysis` (activity, agent)
4. `postStoryComment` (activity; configurable `platform.auto-post-story-comment`, default true)
5. `runTestDesign` (activity, agent)
6. `saveDraftTestCases` (activity → TestManagementPort, status DRAFT)
7. `createApprovalRequest` (activity) then wait for signal `approvalDecision(ApprovalDecision)` with a 7-day timeout
8. For each approved test case: `runTestDataSpec` → `generateTestData` → `validateTestData` → `provisionTestData` → `attachDataReference`
9. Mark rejected cases REJECTED; complete with a summary

Activity retry policy: max 3 attempts, exponential backoff; non-retryable for schema validation failures after the agent's own retry. All external writes use idempotency keys (`runId + stepName + itemKey`). Each step writes start/end/status/output to `agent_step` and publishes an SSE event.

## 8. Data model (Flyway)
```
workflow_run(id uuid pk, story_id, status, triggered_by, temporal_workflow_id, started_at, completed_at, summary jsonb)
agent_step(id uuid pk, run_id fk, step_name, agent, status, input jsonb, output jsonb, error text, started_at, completed_at)
approval(id uuid pk, run_id fk, status, requested_at, decided_at, decided_by, decision jsonb)
test_case_draft(id uuid pk, run_id fk, key, external_ref, status, content jsonb)
test_data_set(id uuid pk, run_id fk, test_case_key, spec jsonb, files jsonb, validation jsonb, provisioned boolean, lease_expires_at)
documents(id uuid pk, source, uri, title, app, module, version, content_hash, phi_scrubbed boolean, ingested_at)
chunks(id uuid pk, document_id fk, chunk_index, text, embedding vector(1536), tsv tsvector, embedding_model, metadata jsonb)
llm_call(id uuid pk, run_id, step_id, purpose, model, prompt_id, prompt_version, input_tokens, output_tokens, latency_ms, cost_usd numeric, success, created_at)
audit_event(id uuid pk, run_id, actor, action, target_system, target_ref, details jsonb, created_at)
```
Indexes: HNSW on `chunks.embedding` (`vector_cosine_ops`), GIN on `chunks.tsv`, btree on foreign keys and `workflow_run.story_id`.

## 9. Knowledge layer
- Ingest from: `knowledge-seed/*.md` (synthetic business rules, glossary, sample companion-guide style notes written by us), ADO stories (via WorkItemPort), existing test cases (via TestManagementPort).
- Pipeline: load → `PhiRedactor` (regex for SSN, member ID patterns, DOB, phone, email; replace with tokens) → chunk (Markdown by heading, stories as one chunk, test cases as one chunk; target 300–800 tokens) → embed (`text-embedding-3-small`, batch) → upsert. Skip re-embedding when `content_hash` is unchanged.
- Retrieval: hybrid search = top 20 by cosine similarity + top 20 by `ts_rank` full-text, merged with Reciprocal Rank Fusion (k=60), filtered by metadata (app/module/source), return top N with chunk ids for citations.
- Each agent has a retrieval profile in config: sources, filters, topK.

## 10. REST API (platform-api)
```
POST /api/webhooks/ado                 ADO service hook (validates shared secret; starts workflow when state becomes Ready)
POST /api/runs                         { storyId } manual trigger
GET  /api/runs                         list with status filters
GET  /api/runs/{id}                    run + steps + drafts + data sets
GET  /api/runs/{id}/events             SSE stream of step events
GET  /api/approvals?status=PENDING
POST /api/approvals/{id}/decision      { decisions: [{ key, action: APPROVE|REJECT|EDIT, editedCase? }], decidedBy }
POST /api/knowledge/documents          multipart upload
POST /api/knowledge/reindex
GET  /api/knowledge/search?q=&source=  debug search with scores
GET  /api/metrics/costs?from=&to=      cost and tokens by agent/model/day
GET  /api/health
```
Expose OpenAPI via springdoc.

## 11. Frontend screens
1. Runs: table (story, status, started, duration, cost), "Start run" button with story id.
2. Run detail: step timeline (status chips, duration, tokens, cost), expandable JSON output per step, live updates via SSE, links to ADO story and qTest cases.
3. Approval inbox: list of pending approvals; per test case approve/reject/edit inline (steps editable); submit decision.
4. Knowledge: upload Markdown/PDF, list documents, search box showing ranked chunks with scores.
5. Metrics: cost per day, cost per agent, average tokens per run, approval acceptance rate.

## 12. mock-qtest
Small Spring Boot app on port 8091 imitating a subset of the qTest Manager REST API shape: projects, modules, test cases (create, update, get, list by module), a custom status field, and notes. In-memory or H2 storage. The real `QTestAdapter` must work against both this mock and the qTest trial; keep the mock's request/response shapes aligned with qTest's public API docs.

## 13. enrollment-app (system under test)
Spring Boot app on port 8092 with its own schema (separate DB or schema in the same Postgres):
- `POST /api/enrollments/834` accepts an 834 file, parses a subset (ISA, GS, ST, BGN, INS, REF, DTP, NM1, DMG, HD, SE, GE, IEA), applies business rules from `knowledge-seed/enrollment-business-rules.md`, stores members/coverages, returns accepted/rejected with reasons.
- `GET /api/members`, `GET /api/members/{id}`
- `POST /api/test-data/load` and `DELETE /api/test-data/{dataSetId}` for provisioning and teardown (only enabled in non-prod profiles).
- Minimal UI page listing members (for Playwright in the stretch phase).

Use only publicly available, general knowledge of the 834 structure and our own synthetic business rules. No licensed standard text copied into the repo.

## 14. Seed data
- `scripts/seed-ado-stories`: creates 15–20 synthetic enrollment user stories with acceptance criteria in the sandbox ADO project (add dependent, terminate coverage, retro effective date, invalid relationship code, duplicate subscriber, address change, plan change mid-year, etc.). Include 3 deliberately vague stories so the Story Analysis agent has gaps to find.
- `knowledge-seed/`: `enrollment-business-rules.md`, `glossary.md`, `834-segment-notes.md`, `testing-standards.md` (all synthetic).
- In `local` profile, the mock WorkItemPort loads the same stories from `scripts/seed/stories.json`.

## 15. Evals
- `evals/story-analysis/*.json`: { story, expected: { mustFindAmbiguitiesContaining[], riskScoreRange, minCriteria } }
- `evals/test-design/*.json`: { story, analysis, expected: { minCases, mustIncludeTypes[], mustCoverCriteria[] } }
- `@Tag("eval")` JUnit tests run real LLM calls, check schema validity and expectations, and write a report to `evals/reports/<timestamp>.md` with pass rate, tokens and cost.
- `scripts/run-evals` runs `mvn -Peval test`.

## 16. Acceptance criteria for the MVP demo
1. `docker compose up` + starting `platform-app`, `mock-qtest`, `enrollment-app` and `frontend` works from a clean clone following the README.
2. Moving a seeded story to Ready in ADO (sandbox) or clicking "Start run" (local) starts a workflow visible in the UI within seconds.
3. The ADO story receives an analysis comment with ambiguities and a risk score.
4. Draft test cases appear in qTest (mock or trial) with DRAFT status.
5. Approving cases in the UI resumes the workflow; synthetic 834 data is generated, validated, provisioned to the enrollment app, and referenced on the test case.
6. Run detail shows every step, tokens and cost; the metrics page shows totals.
7. Eval run produces a report with at least 80% pass rate on the golden sets.
8. No secrets in git; no real member data anywhere.
