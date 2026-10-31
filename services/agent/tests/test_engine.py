from fastapi.testclient import TestClient

from app.config import Settings
from app.engine import Engine
from app.predict import predict_effort
from app.schemas import EstimateRequest, GenerateRequest, IngestRequest, QueryRequest


HARBOR = """
# Harbor Field Service
## Same-day window changes
When a promised window changes by more than 30 minutes, Harbor notifies the assigned technician and the customer.
The technician notification includes the new window, the site address, and whether parts are still reserved.
The customer notification does not include technician personal phone numbers.
If the technician has not acknowledged within 10 minutes, the dispatcher sees an unacknowledged badge.
## Offline job completion
Technicians mark complete while offline. A completion submitted offline must not create a second visit record when it syncs.
"""


def test_ingest_and_query_finds_the_notification_rule():
    engine = Engine(Settings(api_key="test-key", database_url=""))
    engine.ingest(IngestRequest(id="doc-1", title="Harbor brief", text=HARBOR))
    result = engine.query(
        QueryRequest(query="notify technician when the arrival window changes", top_k=3)
    )
    assert result["citations"]
    assert "30 minutes" in result["citations"][0]["excerpt"] or any(
        "30 minutes" in item["excerpt"] for item in result["citations"]
    )


def test_generate_story_is_grounded_and_testable():
    engine = Engine(Settings(api_key="test-key", database_url=""))
    engine.ingest(IngestRequest(id="doc-1", title="Harbor brief", text=HARBOR))
    story = engine.generate(
        GenerateRequest(prompt="Write a story about notifying the technician when a job window changes")
    )
    assert story["description"].startswith("As a ")
    assert len(story["acceptance_criteria"]) >= 3
    assert story["citations"]
    assert story["prediction"]["points"] in {1, 2, 3, 5, 8, 13, 21}
    assert "integration" in story["prediction"]["risks"]


def test_prediction_flags_security_and_snaps_to_fibonacci():
    prediction = predict_effort(
        "Migrate billing credentials",
        "Move payment tokens and invoice history with an audit trail for compliance.",
        ["Given a legacy invoice, when the migration runs, then the token is stored."],
    )
    assert prediction.points in {1, 2, 3, 5, 8, 13, 21}
    assert "payments" in prediction.risks
    assert "security" in prediction.risks or "compliance" in prediction.risks


def _client():
    from app.main import create_app

    application = create_app(Settings(api_key="test-key", database_url="", seed_path=""))
    return TestClient(application)


def test_mcp_lists_user_story_tools():
    with _client() as client:
        response = client.post(
            "/mcp",
            headers={
                "Accept": "application/json, text/event-stream",
                "Content-Type": "application/json",
            },
            json={
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2025-06-18",
                    "capabilities": {},
                    "clientInfo": {"name": "ake-test", "version": "0.1.0"},
                },
            },
        )
        assert response.status_code == 200, response.text
        body = response.json()
        assert body["result"]["serverInfo"]["name"] == "ake-user-story"
        listed = client.post(
            "/mcp",
            headers={
                "Accept": "application/json, text/event-stream",
                "Content-Type": "application/json",
            },
            json={"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}},
        )
        assert listed.status_code == 200, listed.text
        names = {tool["name"] for tool in listed.json()["result"]["tools"]}
        assert names == {"search_knowledge_base", "draft_user_story", "predict_effort"}


def test_api_rejects_missing_key_and_serves_a_story():
    with _client() as client:
        denied = client.post("/v1/stories/estimate", json={"title": "A", "description": "B"})
        assert denied.status_code == 401
        health = client.get("/health")
        assert health.status_code == 200
        ingested = client.post(
            "/v1/documents/ingest",
            headers={"X-API-Key": "test-key"},
            json={"id": "doc-1", "title": "Harbor brief", "text": HARBOR},
        )
        assert ingested.status_code == 200
        generated = client.post(
            "/v1/stories/generate",
            headers={"X-API-Key": "test-key"},
            json={"prompt": "Draft a story for offline job completion without duplicate visits"},
        )
        assert generated.status_code == 200
        body = generated.json()
        assert "offline" in body["description"].lower() or body["citations"]
        estimate = client.post(
            "/v1/stories/estimate",
            headers={"X-API-Key": "test-key"},
            json={
                "title": "Offline completion",
                "description": "sync conflict",
                "acceptance_criteria": ["a", "b", "c"],
            },
        )
        assert estimate.status_code == 200
        assert estimate.json()["points"] >= 1
