import re

from app.schemas import Prediction

FIBONACCI = (1, 2, 3, 5, 8, 13, 21)
RISK_TERMS = {
    "security": ("auth", "permission", "security", "credential", "privacy"),
    "integration": ("sync", "offline", "notification", "integration", "api"),
    "migration": ("migration", "backfill", "legacy"),
    "payments": ("payment", "billing", "invoice", "refund"),
    "compliance": ("audit", "compliance", "retention"),
}
TOKEN = re.compile(r"[a-z0-9]+")


def predict_effort(
    title: str,
    description: str,
    acceptance_criteria: list[str],
    neighbor_points: list[int] | None = None,
) -> Prediction:
    """Transparent effort model.

    Blends writing size, risk language, acceptance-criteria completeness,
    and the points of similar stories already in the knowledge base.
    """
    corpus = " ".join([title, description, *acceptance_criteria]).lower()
    tokens = TOKEN.findall(corpus)
    words = len(tokens)
    rationale: list[str] = []
    score = 2.0

    if words > 80:
        score += 1
        rationale.append("Description is long enough to hide extra cases.")
    if words > 180:
        score += 2
        rationale.append("Scope reads larger than a single thin slice.")
    if words <= 25:
        score -= 0.5
        rationale.append("Very small write-up; treat the estimate as fragile.")

    criteria = [item.strip() for item in acceptance_criteria if item.strip()]
    if len(criteria) >= 4:
        score += 1
        rationale.append(f"{len(criteria)} acceptance checks suggest more than a trivial change.")
    elif len(criteria) < 2:
        score += 1
        rationale.append("Thin acceptance criteria usually means hidden work.")

    risks: list[str] = []
    for risk, terms in RISK_TERMS.items():
        if any(term in corpus for term in terms):
            risks.append(risk)
    if risks:
        score += min(3, len(risks) + 0.5)
        rationale.append("Risk language: " + ", ".join(risks) + ".")

    neighbors = neighbor_points or []
    if neighbors:
        average = sum(neighbors) / len(neighbors)
        score = (score * 0.65) + (average * 0.35)
        rationale.append(
            f"Blended with {len(neighbors)} similar stories averaging {average:.1f} points."
        )
    else:
        rationale.append("No similar stories yet, so this is a prior from the text alone.")

    points = _nearest_fibonacci(score)
    completeness = 0.15 if len(criteria) >= 3 else 0.0
    neighbor_boost = min(0.25, 0.08 * len(neighbors))
    risk_penalty = 0.06 * max(0, len(risks) - 1)
    confidence = max(0.28, min(0.9, 0.42 + completeness + neighbor_boost - risk_penalty))
    if not rationale:
        rationale.append("Baseline slice with no extra risk signals.")
    return Prediction(
        points=points,
        confidence=round(confidence, 2),
        risks=risks,
        rationale=rationale,
        neighbors=len(neighbors),
    )


def _nearest_fibonacci(score: float) -> int:
    value = max(FIBONACCI[0], min(FIBONACCI[-1], score))
    return min(FIBONACCI, key=lambda point: abs(point - value))
