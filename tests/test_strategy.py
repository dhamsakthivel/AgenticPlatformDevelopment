import pandas as pd
import pytest

from nse_swing.config import Config
from nse_swing.strategy import prepare_symbol, pullback_signal


def config(**overrides) -> Config:
    params = dict(
        min_history=0,
        max_entry_gap=0.0,
        slippage_pct=0.0,
        buy_cost_rate=0.0,
        sell_cost_rate=0.0,
        dp_charge_inr=0.0,
        risk_per_trade=0.01,
        max_position_pct=1.0,
        max_portfolio_heat=1.0,
        max_positions=5,
        max_per_sector=5,
    )
    params.update(overrides)
    return Config(**params)


def valid_setup() -> pd.DataFrame:
    rows = [
        _row(close=100, ema20=100, low_5=99, high=101, low=99),
        _row(close=101, ema20=100, low_5=99, high=102, low=100),
    ]
    return pd.DataFrame(rows, index=pd.bdate_range("2024-01-02", periods=2))


def _row(close, ema20, low_5, high, low) -> dict:
    return {
        "Open": close,
        "High": high,
        "Low": low,
        "Close": close,
        "Volume": 1_000.0,
        "ema20": ema20,
        "ema50": 90.0,
        "ema200": 80.0,
        "ema50_slope": 1.0,
        "low_5": low_5,
        "rsi": 55.0,
        "vol_avg": 800.0,
        "atr": 2.0,
        "rs": 0.05,
    }


def test_pullback_accepts_the_base_case():
    signal = pullback_signal(valid_setup(), Config())
    assert signal.tolist() == [False, True]


@pytest.mark.parametrize(
    ("column", "value"),
    [
        ("ema50", 110.0),
        ("ema200", 95.0),
        ("ema50_slope", -1.0),
        ("low_5", 105.0),
        ("rsi", 80.0),
        ("rsi", 30.0),
        ("Volume", 100.0),
        ("atr", 0.2),
        ("atr", 20.0),
        ("rs", -0.2),
        ("High", 120.0),
    ],
)
def test_pullback_rejects_a_broken_rule(column, value):
    frame = valid_setup()
    frame.loc[frame.index[-1], column] = value
    if column == "ema200":
        frame.loc[frame.index[-1], "ema50"] = 90.0
    assert bool(pullback_signal(frame, Config()).iloc[-1]) is False


def test_pullback_rejects_a_flat_close():
    frame = valid_setup()
    frame.loc[frame.index[-1], "Close"] = 100.0
    assert bool(pullback_signal(frame, Config()).iloc[-1]) is False


def test_pullback_rejects_extension_above_the_fast_average():
    frame = valid_setup()
    last = frame.index[-1]
    frame.loc[last, "ema20"] = 90.0
    frame.loc[last, "low_5"] = 89.0
    frame.loc[last, "Close"] = 101.0
    assert bool(pullback_signal(frame, Config()).iloc[-1]) is False


def test_prepare_signal_uses_only_past_bars():
    dates = pd.bdate_range("2020-01-01", periods=120)
    close = pd.Series(range(100, 220), index=dates, dtype=float)
    frame = pd.DataFrame(
        {
            "Open": close.shift(1).fillna(close.iloc[0]),
            "High": close + 1,
            "Low": close - 1,
            "Close": close,
            "Volume": 1_000_000.0,
        }
    )
    bench = close * 10
    full = prepare_symbol(frame, bench, Config())
    short = prepare_symbol(frame.iloc[:80], bench.iloc[:80], Config())
    pd.testing.assert_series_equal(full.loc[short.index, "signal"], short["signal"], check_names=False)
