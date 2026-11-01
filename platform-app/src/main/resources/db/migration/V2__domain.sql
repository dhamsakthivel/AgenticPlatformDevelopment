CREATE TABLE workflow_run (
    id uuid PRIMARY KEY,
    story_id text NOT NULL,
    status text NOT NULL,
    triggered_by text NOT NULL,
    temporal_workflow_id text,
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    summary jsonb
);

CREATE INDEX workflow_run_story_id_idx ON workflow_run (story_id);

CREATE TABLE agent_step (
    id uuid PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES workflow_run (id),
    step_name text NOT NULL,
    agent text,
    status text NOT NULL,
    input jsonb,
    output jsonb,
    error text,
    started_at timestamptz,
    completed_at timestamptz
);

CREATE INDEX agent_step_run_id_idx ON agent_step (run_id);

CREATE TABLE approval (
    id uuid PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES workflow_run (id),
    status text NOT NULL,
    requested_at timestamptz NOT NULL,
    decided_at timestamptz,
    decided_by text,
    decision jsonb
);

CREATE INDEX approval_run_id_idx ON approval (run_id);

CREATE TABLE test_case_draft (
    id uuid PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES workflow_run (id),
    key text NOT NULL,
    external_ref text,
    status text NOT NULL,
    content jsonb NOT NULL
);

CREATE INDEX test_case_draft_run_id_idx ON test_case_draft (run_id);

CREATE TABLE test_data_set (
    id uuid PRIMARY KEY,
    run_id uuid NOT NULL REFERENCES workflow_run (id),
    test_case_key text NOT NULL,
    spec jsonb,
    files jsonb,
    validation jsonb,
    provisioned boolean NOT NULL DEFAULT false,
    lease_expires_at timestamptz
);

CREATE INDEX test_data_set_run_id_idx ON test_data_set (run_id);

CREATE TABLE documents (
    id uuid PRIMARY KEY,
    source text NOT NULL,
    uri text,
    title text,
    app text,
    module text,
    version text,
    content_hash text,
    phi_scrubbed boolean NOT NULL DEFAULT false,
    ingested_at timestamptz
);

CREATE TABLE chunks (
    id uuid PRIMARY KEY,
    document_id uuid NOT NULL REFERENCES documents (id),
    chunk_index integer NOT NULL,
    text text NOT NULL,
    embedding vector(1536),
    tsv tsvector,
    embedding_model text,
    metadata jsonb
);

CREATE INDEX chunks_document_id_idx ON chunks (document_id);
CREATE INDEX chunks_embedding_hnsw_idx ON chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX chunks_tsv_gin_idx ON chunks USING gin (tsv);

CREATE TABLE llm_call (
    id uuid PRIMARY KEY,
    run_id uuid,
    step_id uuid,
    purpose text,
    model text,
    prompt_id text,
    prompt_version text,
    input_tokens integer,
    output_tokens integer,
    latency_ms integer,
    cost_usd numeric,
    success boolean,
    created_at timestamptz NOT NULL
);

CREATE INDEX llm_call_run_id_idx ON llm_call (run_id);
CREATE INDEX llm_call_step_id_idx ON llm_call (step_id);

CREATE TABLE audit_event (
    id uuid PRIMARY KEY,
    run_id uuid,
    actor text NOT NULL,
    action text NOT NULL,
    target_system text NOT NULL,
    target_ref text,
    details jsonb,
    created_at timestamptz NOT NULL
);

CREATE INDEX audit_event_run_id_idx ON audit_event (run_id);
