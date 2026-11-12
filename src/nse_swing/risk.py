import math
from dataclasses import dataclass

from nse_swing.config import Config


@dataclass(frozen=True)
class BuyPlan:
    shares: int
    stop: float
    limit_price: float
    worst_fill: float
    reserved_cash: float
    planned_risk: float


def buy_debit(shares: int, fill: float, cost_rate: float) -> float:
    gross = round(shares * fill, 2)
    fee = round(gross * cost_rate, 2)
    return round(gross + fee, 2)


def sell_credit(shares: int, fill: float, cost_rate: float, dp_charge: float = 0.0) -> float:
    gross = round(shares * fill, 2)
    fee = round(gross * cost_rate, 2)
    return round(gross - fee - dp_charge, 2)


def drawdown_halt(was_halted: bool, drawdown: float, config: Config) -> bool:
    if drawdown >= config.halt_new_entries_dd:
        return True
    if was_halted and drawdown > config.resume_dd:
        return True
    return False


def risk_multiplier(drawdown: float, halted: bool, config: Config) -> float:
    if halted:
        return 0.0
    if drawdown >= config.heat_reduction_dd:
        return 0.5
    return 1.0


def plan_buy(
    *,
    close: float,
    raw_stop: float,
    equity: float,
    cash: float,
    open_risk: float,
    config: Config,
    risk_multiplier_value: float,
) -> BuyPlan | None:
    """Size the buy off the limit, so a fill at the top of the gap still risks the budget."""
    if (
        risk_multiplier_value <= 0
        or equity <= 0
        or cash <= 0
        or close <= 0
        or not math.isfinite(close)
        or not math.isfinite(raw_stop)
        or raw_stop >= close
    ):
        return None
    distance_pct = (close - raw_stop) / close
    if distance_pct < config.min_stop_pct or distance_pct > config.max_stop_pct:
        return None
    limit_price = close * (1 + config.max_entry_gap)
    worst_fill = limit_price * (1 + config.slippage_pct)
    if raw_stop >= worst_fill:
        return None
    per_share = worst_fill - raw_stop
    risk_cap = equity * config.risk_per_trade * risk_multiplier_value
    heat_room = equity * config.max_portfolio_heat - open_risk
    budget = min(risk_cap, heat_room)
    if budget <= 0 or per_share <= 0:
        return None
    shares = math.floor(budget / per_share + 1e-9)
    shares = min(shares, math.floor(equity * config.max_position_pct / worst_fill + 1e-9))
    while shares > 0:
        debit = buy_debit(shares, worst_fill, config.buy_cost_rate)
        risk = shares * per_share
        if debit <= cash + 1e-6 and risk <= budget + 1e-6:
            return BuyPlan(
                shares=shares,
                stop=raw_stop,
                limit_price=limit_price,
                worst_fill=worst_fill,
                reserved_cash=debit,
                planned_risk=risk,
            )
        shares -= 1
    return None
