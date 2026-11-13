import math
from dataclasses import dataclass

import numpy as np


def required_cagr(start: float, goal: float, years: float) -> float:
    if start <= 0 or goal <= 0 or years <= 0:
        raise ValueError("start, goal, and years must be positive")
    return (goal / start) ** (1 / years) - 1


def years_to_goal(start: float, goal: float, cagr: float) -> float:
    if start <= 0 or goal <= 0:
        raise ValueError("start and goal must be positive")
    if goal <= start:
        return 0.0
    if cagr <= 0:
        return math.inf
    return math.log(goal / start) / math.log(1 + cagr)


def gain_to_recover(drawdown: float) -> float:
    if not 0 <= drawdown < 1:
        raise ValueError("drawdown must be in [0, 1)")
    return (1 / (1 - drawdown)) - 1


@dataclass(frozen=True)
class PathSummary:
    years: int
    paths: int
    median_terminal: float
    low_terminal: float
    high_terminal: float
    hit_target_frac: float
    ruin_frac: float
    median_max_drawdown: float


def simulate_paths(
    daily_returns: np.ndarray,
    *,
    years: int,
    n_paths: int,
    block: int,
    target_multiple: float,
    ruin_drawdown: float,
    seed: int,
) -> PathSummary | None:
    returns = np.asarray(daily_returns, dtype=float)
    returns = returns[np.isfinite(returns)]
    if len(returns) < block or years < 1 or n_paths < 1:
        return None
    safe = np.clip(returns, -0.99, 1.0)
    n_days = years * 252
    n_blocks = math.ceil(n_days / block)
    rng = np.random.default_rng(seed)
    starts = rng.integers(0, len(safe) - block + 1, size=(n_paths, n_blocks))
    offsets = np.arange(block)
    sampled = safe[starts[..., None] + offsets].reshape(n_paths, n_blocks * block)[:, :n_days]
    equity = np.cumprod(1 + sampled, axis=1)
    peak = np.maximum.accumulate(equity, axis=1)
    drawdown = equity / peak - 1
    terminal = equity[:, -1]
    return PathSummary(
        years=years,
        paths=n_paths,
        median_terminal=float(np.median(terminal)),
        low_terminal=float(np.quantile(terminal, 0.05)),
        high_terminal=float(np.quantile(terminal, 0.95)),
        hit_target_frac=float((equity >= target_multiple).any(axis=1).mean()),
        ruin_frac=float((drawdown <= -ruin_drawdown).any(axis=1).mean()),
        median_max_drawdown=float(np.median(-drawdown.min(axis=1))),
    )
