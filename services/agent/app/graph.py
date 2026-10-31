from typing import TypedDict

from langgraph.graph import END, StateGraph
from langsmith import traceable

from app.drafting import critique, draft_story, to_story
from app.predict import predict_effort
from app.rag import search
from app.schemas import StoryDraft


class StoryState(TypedDict, total=False):
    prompt: str
    document_ids: list[str]
    citations: list
    draft: dict
    notes: list[str]
    model: str
    prediction: dict
    story: dict


class StoryGraph:
    def __init__(self, store, embedder, llm) -> None:
        self.store = store
        self.embedder = embedder
        self.llm = llm
        graph = StateGraph(StoryState)
        graph.add_node("retrieve", self._retrieve)
        graph.add_node("draft", self._draft)
        graph.add_node("critique", self._critique)
        graph.add_node("predict", self._predict)
        graph.set_entry_point("retrieve")
        graph.add_edge("retrieve", "draft")
        graph.add_edge("draft", "critique")
        graph.add_edge("critique", "predict")
        graph.add_edge("predict", END)
        self.compiled = graph.compile()

    def invoke(self, prompt: str, document_ids: list[str]) -> StoryDraft:
        result = self.compiled.invoke({"prompt": prompt, "document_ids": document_ids})
        return StoryDraft.model_validate(result["story"])

    @traceable(name="retrieve_knowledge", run_type="retriever")
    def _retrieve(self, state: StoryState) -> StoryState:
        citations = search(
            self.store,
            self.embedder,
            state["prompt"],
            top_k=4,
            document_ids=state.get("document_ids") or [],
        )
        return {"citations": [item.model_dump() for item in citations]}

    @traceable(name="draft_user_story", run_type="chain")
    def _draft(self, state: StoryState) -> StoryState:
        from app.schemas import Citation

        citations = [Citation.model_validate(item) for item in state.get("citations") or []]
        draft, notes, model = draft_story(state["prompt"], citations, self.llm)
        return {"draft": draft, "notes": notes, "model": model}

    @traceable(name="critique_story", run_type="chain")
    def _critique(self, state: StoryState) -> StoryState:
        from app.schemas import Citation

        citations = [Citation.model_validate(item) for item in state.get("citations") or []]
        draft, extra = critique(state.get("draft") or {}, citations)
        notes = list(state.get("notes") or [])
        notes.extend(extra)
        return {"draft": draft, "notes": notes}

    @traceable(name="predict_effort", run_type="chain")
    def _predict(self, state: StoryState) -> StoryState:
        from app.schemas import Citation

        draft = state["draft"]
        citations = [Citation.model_validate(item) for item in state.get("citations") or []]
        neighbor_points = _neighbor_points(self.store, citations)
        prediction = predict_effort(
            draft["title"],
            draft["description"],
            draft["acceptance_criteria"],
            neighbor_points,
        )
        story = to_story(draft, citations, list(state.get("notes") or []), prediction, state.get("model") or "local-rubric")
        return {"prediction": prediction.model_dump(), "story": story.model_dump()}


def _neighbor_points(store, citations) -> list[int]:
    """Similar chunks do not carry story points yet; the portfolio blend happens in Spring.

    The hook stays here so a later point index can feed the same predictor.
    """
    del store, citations
    return []
