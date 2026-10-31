import json
import re

from langchain_core.language_models.chat_models import BaseChatModel

from app.schemas import Citation, StoryDraft

PERSONAS = (
    ("field technician", ("technician", "crew", "offline")),
    ("dispatcher", ("dispatcher", "board", "assign")),
    ("customer", ("customer", "arrival", "window")),
    ("supervisor", ("supervisor", "conflict", "missed")),
)
PRIORITY_TERMS = {
    "urgent": ("must", "cannot", "security", "privacy", "conflict"),
    "high": ("notify", "notification", "offline", "permission"),
    "medium": ("show", "record", "review"),
}


def draft_story(
    prompt: str,
    citations: list[Citation],
    llm: BaseChatModel | None,
) -> tuple[dict, list[str], str]:
    if llm is not None:
        drafted, notes = _llm_draft(prompt, citations, llm)
        model = "openai"
    else:
        drafted, notes = _offline_draft(prompt, citations)
        model = "local-rubric"
    return drafted, notes, model


def _llm_draft(prompt: str, citations: list[Citation], llm: BaseChatModel) -> tuple[dict, list[str]]:
    context = "\n\n".join(
        f"[{item.title} / {item.section}] {item.excerpt}" for item in citations
    ) or "No knowledge-base excerpts were retrieved."
    message = llm.invoke(
        [
            {
                "role": "system",
                "content": (
                    "You write one agile user story grounded only in the excerpts. "
                    "Return JSON with keys title, persona, action, benefit, "
                    "acceptance_criteria (3 to 5 strings), priority "
                    "(low, medium, high, or urgent). Do not invent product facts."
                ),
            },
            {
                "role": "user",
                "content": f"Request:\n{prompt}\n\nExcerpts:\n{context}",
            },
        ]
    )
    content = message.content if isinstance(message.content, str) else str(message.content)
    parsed = _parse_json(content)
    notes = ["Drafted with the configured chat model."]
    if not parsed:
        notes.append("Model output was not valid JSON; used the local rubric instead.")
        return _offline_draft(prompt, citations)
    return parsed, notes


def _offline_draft(prompt: str, citations: list[Citation]) -> tuple[dict, list[str]]:
    corpus = " ".join([prompt, *[item.excerpt for item in citations]]).lower()
    persona = "product user"
    for name, hints in PERSONAS:
        if any(hint in corpus for hint in hints):
            persona = name
            break
    action = _action_from_prompt(prompt)
    benefit = _benefit(citations, persona)
    priority = "medium"
    for level, terms in PRIORITY_TERMS.items():
        if any(term in corpus for term in terms):
            priority = level
            break
    criteria = _criteria(action, citations)
    title = _title(action)
    return (
        {
            "title": title,
            "persona": persona,
            "action": action,
            "benefit": benefit,
            "acceptance_criteria": criteria,
            "priority": priority,
        },
        ["Drafted from the knowledge base with the local rubric (no chat model configured)."],
    )


def critique(draft: dict, citations: list[Citation]) -> tuple[dict, list[str]]:
    notes: list[str] = []
    persona = str(draft.get("persona") or "product user").strip()
    action = str(draft.get("action") or "complete the requested workflow").strip().rstrip(".")
    benefit = str(draft.get("benefit") or "the outcome is explicit and testable").strip().rstrip(".")
    criteria = [str(item).strip() for item in draft.get("acceptance_criteria") or [] if str(item).strip()]
    while len(criteria) < 3:
        criteria.append(_fallback_criterion(len(criteria), action))
        notes.append("Added a missing acceptance check so the story is testable.")
    criteria = criteria[:5]
    priority = str(draft.get("priority") or "medium").lower()
    if priority not in {"low", "medium", "high", "urgent"}:
        priority = "medium"
        notes.append("Priority was normalized to medium.")
    title = str(draft.get("title") or _title(action)).strip()
    if citations:
        notes.append(f"Grounded in {len(citations)} knowledge-base excerpts.")
    else:
        notes.append("No excerpts matched. The story is a hypothesis, not a sourced requirement.")
    phrase = _want_phrase(action)
    description = f"As a {persona}, I want {phrase}, so that {benefit}."
    return (
        {
            "title": title,
            "persona": persona,
            "action": action,
            "benefit": benefit,
            "description": description,
            "acceptance_criteria": criteria,
            "priority": priority,
        },
        notes,
    )


def _action_from_prompt(prompt: str) -> str:
    cleaned = prompt.strip().rstrip(".")
    cleaned = re.sub(
        r"^(please\s+)?(write|draft|create|generate)\s+(a\s+)?(user\s+)?story\s+(about|for|to)\s+",
        "",
        cleaned,
        flags=re.IGNORECASE,
    )
    cleaned = re.sub(r"^(i want to|i want|we need to|we need)\s+", "", cleaned, flags=re.IGNORECASE)
    if not cleaned:
        cleaned = "complete the workflow described in the knowledge base"
    if len(cleaned) > 180:
        cleaned = cleaned[:177].rstrip() + "…"
    return cleaned[0].lower() + cleaned[1:] if cleaned else cleaned


def _benefit(citations: list[Citation], persona: str) -> str:
    for citation in citations:
        sentence = _first_sentence(citation.excerpt)
        if sentence and len(sentence) < 180:
            return sentence[0].lower() + sentence[1:].rstrip(".")
    return f"a {persona} can finish the work without guessing the rule"


def _criteria(action: str, citations: list[Citation]) -> list[str]:
    primary = (
        f"Given the current product rules, when the user does this — {action} — "
        "then the result matches the knowledge base and is visible to the right role."
    )
    sourced = []
    for citation in citations[:2]:
        sentence = _first_sentence(citation.excerpt)
        if sentence:
            sourced.append(f"Rule from {citation.section}: {sentence}")
    empty = "If the required context is missing, the product explains what is missing instead of saving a partial result."
    return [primary, *sourced, empty][:5]


def _fallback_criterion(index: int, action: str) -> str:
    options = [
        f"The main outcome of “{action}” is visible after a successful action.",
        "An unauthorized role cannot perform the action.",
        "A failed save leaves the previous data unchanged and shows the reason.",
    ]
    return options[index % len(options)]


def _want_phrase(action: str) -> str:
    """Turn a leading gerund into an infinitive so the story reads as 'I want to…'."""
    text = action.strip()
    if text.startswith("to "):
        return text
    match = re.match(r"^([a-z]+ing)\b(.*)$", text)
    if match:
        return f"to {match.group(1)[:-3]}{match.group(2)}"
    return text


def _title(action: str) -> str:
    words = action.split()
    short = " ".join(words[:10]).strip(" .")
    if not short:
        return "New user story"
    return short[0].upper() + short[1:]


def _first_sentence(text: str) -> str:
    parts = re.split(r"(?<=[.])\s+", text.strip())
    return parts[0].strip() if parts else ""


def _parse_json(content: str) -> dict | None:
    match = re.search(r"\{.*\}", content, re.DOTALL)
    if not match:
        return None
    try:
        data = json.loads(match.group(0))
    except json.JSONDecodeError:
        return None
    if not isinstance(data, dict):
        return None
    return data


def to_story(draft: dict, citations: list[Citation], notes: list[str], prediction, model: str) -> StoryDraft:
    return StoryDraft(
        title=draft["title"],
        persona=draft["persona"],
        action=draft["action"],
        benefit=draft["benefit"],
        description=draft["description"],
        acceptance_criteria=draft["acceptance_criteria"],
        priority=draft["priority"],
        citations=citations,
        notes=notes,
        prediction=prediction,
        model=model,
    )
