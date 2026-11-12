from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    """Safety limits for a ₹1,00,000 cash account.

    Statutory cost defaults follow NSE equity delivery levies around April 2026:
    STT 0.1% on both sides, stamp duty 0.015% on the buy, and small exchange,
    SEBI, and GST charges. They are a model, not a tax opinion. Slippage is separate.
    """

    initial_capital: float = 100_000.0
    goal_capital: float = 10_000_000.0
    risk_per_trade: float = 0.008
    max_position_pct: float = 0.18
    max_positions: int = 5
    max_per_sector: int = 2
    max_portfolio_heat: float = 0.04
    ema_fast: int = 20
    ema_mid: int = 50
    ema_slow: int = 200
    atr_period: int = 14
    atr_stop_mult: float = 2.0
    atr_trail_mult: float = 2.5
    breakeven_r: float = 1.5
    max_hold_days: int = 20
    min_stop_pct: float = 0.012
    max_stop_pct: float = 0.07
    max_entry_gap: float = 0.04
    slippage_pct: float = 0.0005
    buy_cost_rate: float = 0.001187
    sell_cost_rate: float = 0.001037
    dp_charge_inr: float = 15.0
    rsi_low: float = 45.0
    rsi_high: float = 68.0
    max_extension: float = 0.08
    min_atr_pct: float = 0.012
    max_atr_pct: float = 0.06
    rs_lookback: int = 60
    halt_new_entries_dd: float = 0.25
    heat_reduction_dd: float = 0.15
    resume_dd: float = 0.10
    min_history: int = 220
    risk_free_rate: float = 0.065
    monte_carlo_paths: int = 2000
    monte_carlo_block: int = 10
    monte_carlo_cap_years: int = 30

    def __post_init__(self) -> None:
        if self.initial_capital <= 0 or self.goal_capital <= 0:
            raise ValueError("capital and goal must be positive")
        if not 0 < self.risk_per_trade <= 1:
            raise ValueError("risk_per_trade must be in (0, 1]")
        if not 0 < self.max_position_pct <= 1:
            raise ValueError("max_position_pct must be in (0, 1]")
        if self.max_positions < 1 or self.max_per_sector < 1:
            raise ValueError("position caps must be at least 1")
        if not 0 < self.max_portfolio_heat <= 1:
            raise ValueError("max_portfolio_heat must be in (0, 1]")
        if not 0 < self.min_stop_pct < self.max_stop_pct < 1:
            raise ValueError("stop percents must satisfy 0 < min < max < 1")
        if self.max_entry_gap < 0 or self.slippage_pct < 0:
            raise ValueError("gap and slippage must be >= 0")
        if self.buy_cost_rate < 0 or self.sell_cost_rate < 0 or self.dp_charge_inr < 0:
            raise ValueError("costs must be >= 0")
        if not 0 < self.resume_dd < self.heat_reduction_dd < self.halt_new_entries_dd < 1:
            raise ValueError("drawdown brakes must be ordered resume < reduce < halt < 1")
        if self.breakeven_r <= 0 or self.atr_stop_mult <= 0 or self.atr_trail_mult <= 0:
            raise ValueError("ATR and R multiples must be positive")
        if self.max_hold_days < 1 or self.min_history < 0:
            raise ValueError("hold and history windows are invalid")
        if not 0 <= self.rsi_low < self.rsi_high <= 100:
            raise ValueError("RSI band is invalid")
        if self.ema_fast < 2 or self.ema_mid <= self.ema_fast or self.ema_slow <= self.ema_mid:
            raise ValueError("EMA spans must increase")
        if self.monte_carlo_paths < 1 or self.monte_carlo_block < 2:
            raise ValueError("Monte Carlo settings are invalid")
