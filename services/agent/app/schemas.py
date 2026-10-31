from pydantic import BaseModel, Field


class IngestRequest(BaseModel):
    id: str = Field(min_length=1, max_length=80)
    title: str = Field(min_length=1, max_length=200)
    text: str = Field(min_length=1, max_length=200_000)
    source: str = "upload"
    tags: list[str] = Field(default_factory=list)


class QueryRequest(BaseModel):
    query: str = Field(min_length=1, max_length=8_000)
    top_k: int = Field(default=4, ge=1, le=12)
    document_ids: list[str] = Field(default_factory=list)


class Citation(BaseModel):
    document_id: str
    title: str
    section: str
    excerpt: str
    score: float


class GenerateRequest(BaseModel):
    prompt: str = Field(min_length=3, max_length=8_000)
    document_ids: list[str] = Field(default_factory=list)


class EstimateRequest(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: str = Field(default="", max_length=8_000)
    acceptance_criteria: list[str] = Field(default_factory=list)


class Prediction(BaseModel):
    points: int
    confidence: float
    risks: list[str]
    rationale: list[str]
    neighbors: int


class StoryDraft(BaseModel):
    title: str
    persona: str
    action: str
    benefit: str
    description: str
    acceptance_criteria: list[str]
    priority: str
    citations: list[Citation]
    notes: list[str]
    prediction: Prediction
    model: str
