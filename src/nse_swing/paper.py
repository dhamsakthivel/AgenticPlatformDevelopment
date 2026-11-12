import json
from dataclasses import dataclass
from pathlib import Path

import pandas as pd


@dataclass(frozen=True)
class PaperSpec:
    capital: float
    goal: float
    trade_start: str
    universe: str


def resolve_account(
    path: Path,
    *,
    capital: float,
    goal: float,
    universe: str,
    last_date: pd.Timestamp,
    reset: bool,
) -> tuple[PaperSpec, str]:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and not reset:
        raw = json.loads(path.read_text())
        spec = PaperSpec(
            capital=float(raw["capital"]),
            goal=float(raw["goal"]),
            trade_start=str(raw["trade_start"]),
            universe=str(raw["universe"]),
        )
        if spec.universe != universe:
            raise ValueError(
                f"{path} belongs to the {spec.universe} book. Pass --reset or another --state path."
            )
        note = f"Continuing the model portfolio started {spec.trade_start}."
        if abs(capital - spec.capital) > 0.01 or abs(goal - spec.goal) > 0.01:
            note += " Keeping the saved capital and goal. Pass --reset to change them."
        return spec, note
    spec = PaperSpec(
        capital=float(capital),
        goal=float(goal),
        trade_start=pd.Timestamp(last_date).strftime("%Y-%m-%d"),
        universe=universe,
    )
    path.write_text(json.dumps(spec.__dict__, indent=2) + "\n")
    note = (
        f"Started a model portfolio on {spec.trade_start}. "
        "From the next session it assumes every order is taken."
    )
    return spec, note
