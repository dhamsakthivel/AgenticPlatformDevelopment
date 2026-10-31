from mcp.server.mcpserver import MCPServer
from mcp.server.transport_security import TransportSecuritySettings

from app.engine import Engine
from app.schemas import EstimateRequest, GenerateRequest, QueryRequest

# The agent is an internal runtime behind the platform API key. Hostnames vary
# (localhost, docker DNS, tests), so rebinding protection is left to the edge.
_TRANSPORT = TransportSecuritySettings(enable_dns_rebinding_protection=False)


def build_mcp(engine: Engine) -> MCPServer:
    mcp = MCPServer(
        "ake-user-story",
        instructions="Search the AKE knowledge base, draft one user story, or estimate effort.",
    )

    @mcp.tool()
    def search_knowledge_base(query: str, top_k: int = 4) -> str:
        """Search the AKE knowledge base and return grounded excerpts."""
        result = engine.query(QueryRequest(query=query, top_k=max(1, min(top_k, 12))))
        lines = [
            f"{item['title']} / {item['section']} ({item['score']}): {item['excerpt']}"
            for item in result["citations"]
        ]
        return "\n".join(lines) or "No matching excerpts."

    @mcp.tool()
    def draft_user_story(prompt: str) -> str:
        """Draft one user story from the knowledge base for the given prompt."""
        story = engine.generate(GenerateRequest(prompt=prompt))
        criteria = "\n".join(f"- {item}" for item in story["acceptance_criteria"])
        return (
            f"{story['title']}\n{story['description']}\n"
            f"Priority: {story['priority']}\nPoints: {story['prediction']['points']}\n{criteria}"
        )

    @mcp.tool()
    def predict_effort(title: str, description: str, acceptance_criteria: str = "") -> str:
        """Estimate Fibonacci points, confidence, and risk tags for a story."""
        criteria = [line.strip("- ").strip() for line in acceptance_criteria.splitlines() if line.strip()]
        prediction = engine.estimate(
            EstimateRequest(title=title, description=description, acceptance_criteria=criteria)
        )
        risks = ", ".join(prediction["risks"]) or "none"
        return (
            f"{prediction['points']} points, confidence {prediction['confidence']}, risks: {risks}. "
            + " ".join(prediction["rationale"])
        )

    return mcp


def mount_path() -> str:
    return "/"


def transport_security() -> TransportSecuritySettings:
    return _TRANSPORT
