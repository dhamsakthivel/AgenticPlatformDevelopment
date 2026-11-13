from dataclasses import dataclass

import pandas as pd

from nse_swing.config import Config


@dataclass(frozen=True)
class Trade:
    symbol: str
    signal_date: pd.Timestamp
    entry_date: pd.Timestamp
    exit_date: pd.Timestamp
    shares: int
    entry_price: float
    exit_price: float
    pnl: float
    r_multiple: float
    exit_reason: str
    bars_held: int


@dataclass(frozen=True)
class OpenPosition:
    symbol: str
    shares: int
    entry_date: pd.Timestamp
    entry_price: float
    stop: float
    last_close: float
    bars_held: int
    sector: str


@dataclass(frozen=True)
class OrderTicket:
    symbol: str
    side: str
    shares: int
    order_type: str
    limit_price: float
    trigger_price: float | None
    stop: float | None
    reason: str
    product: str = "CNC"


@dataclass(frozen=True)
class BacktestResult:
    daily: pd.DataFrame
    trades: tuple[Trade, ...]
    final_cash: float
    final_equity: float
    open_positions: tuple[OpenPosition, ...]
    next_orders: tuple[OrderTicket, ...]
    halted: bool
    peak_equity: float
    mode: str
    universe_name: str
    trade_start: pd.Timestamp | None
    skipped: tuple[str, ...]
    config: Config
