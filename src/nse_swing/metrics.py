from dataclasses import dataclass

import numpy as np
import pandas as pd

from nse_swing.models import Trade


@dataclass(frozen=True)
class Performance:
    total_return: float
    years: float
    cagr: float | None
    max_drawdown: float
    drawdown_sessions: int
    sharpe: float | None
    sortino: float | None
    trade_count: int
    win_rate: float | None
    average_r: float | None
    profit_factor: float | None
    average_exposure: float


def performance(
    daily: pd.DataFrame,
    trades: tuple[Trade, ...],
    initial_capital: float,
    risk_free_rate: float,
) -> Performance:
    equity = daily["equity"].astype(float)
    final_equity = float(equity.iloc[-1])
    total_return = final_equity / initial_capital - 1
    start, end = equity.index[0], equity.index[-1]
    years = max((end - start).days, 1) / 365.25
    cagr = None
    if years >= 1 and final_equity > 0 and initial_capital > 0:
        cagr = (final_equity / initial_capital) ** (1 / years) - 1
    peak = equity.cummax()
    drawdown = equity / peak - 1
    max_drawdown = float(-drawdown.min()) if len(drawdown) else 0.0
    longest = current = 0
    for underwater in equity.to_numpy() < peak.to_numpy():
        if underwater:
            current += 1
            longest = max(longest, current)
        else:
            current = 0
    returns = equity.pct_change().dropna()
    sharpe = _sharpe(returns, risk_free_rate)
    sortino = _sortino(returns, risk_free_rate)
    winners = [trade.pnl for trade in trades if trade.pnl > 0]
    losers = [trade.pnl for trade in trades if trade.pnl < 0]
    win_rate = None if not trades else len(winners) / len(trades)
    average_r = None if not trades else float(np.mean([trade.r_multiple for trade in trades]))
    profit_factor = None
    if losers:
        profit_factor = sum(winners) / -sum(losers) if winners else 0.0
    elif winners:
        profit_factor = math_inf()
    exposure = float(daily["invested_frac"].mean()) if "invested_frac" in daily else 0.0
    return Performance(
        total_return=total_return,
        years=years,
        cagr=cagr,
        max_drawdown=max_drawdown,
        drawdown_sessions=longest,
        sharpe=sharpe,
        sortino=sortino,
        trade_count=len(trades),
        win_rate=win_rate,
        average_r=average_r,
        profit_factor=profit_factor,
        average_exposure=exposure,
    )


def math_inf() -> float:
    return float("inf")


def _sharpe(returns: pd.Series, risk_free_rate: float) -> float | None:
    if len(returns) < 30 or float(returns.std(ddof=1)) == 0:
        return None
    excess = returns - risk_free_rate / 252
    return float(np.sqrt(252) * excess.mean() / excess.std(ddof=1))


def _sortino(returns: pd.Series, risk_free_rate: float) -> float | None:
    if len(returns) < 30:
        return None
    excess = returns - risk_free_rate / 252
    downside = excess[excess < 0]
    if len(downside) == 0:
        return None
    deviation = float(np.sqrt((downside.to_numpy() ** 2).mean()))
    if deviation == 0:
        return None
    return float(np.sqrt(252) * excess.mean() / deviation)
