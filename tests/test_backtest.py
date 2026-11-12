import pandas as pd
import pytest

from nse_swing.backtest import run_backtest
from nse_swing.config import Config
from nse_swing.data import synthetic_market
from nse_swing.strategy import prepare_index, prepare_symbol


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


def tape(dates, specs):
    rows = []
    for spec in specs:
        row = {
            "Open": 100.0,
            "High": 110.0,
            "Low": 99.0,
            "Close": 100.0,
            "atr": 2.0,
            "ema50": 50.0,
            "signal": False,
            "raw_stop": 96.0,
            "rs": 1.0,
        }
        row.update(spec)
        rows.append(row)
    return pd.DataFrame(rows, index=pd.to_datetime(dates))


def calendar(dates, bullish=True):
    flags = bullish if isinstance(bullish, list) else [True] * len(list(dates))
    return pd.DataFrame({"bullish": flags}, index=pd.to_datetime(list(dates)))


def test_winning_swing_exits_at_the_trailed_stop():
    dates = ["2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05"]
    prices = tape(
        dates,
        [
            {"signal": True, "Open": 100, "High": 101, "Low": 99, "Close": 100, "raw_stop": 96, "atr": 2},
            {"Open": 100, "High": 110, "Low": 99, "Close": 108, "atr": 2},
            {"Open": 107, "High": 112, "Low": 104, "Close": 111, "atr": 2},
            {"Open": 110, "High": 111, "Low": 100, "Close": 102, "atr": 2},
        ],
    )
    result = run_backtest({"AAA": prices}, calendar(dates), config(), sectors={"AAA": "X"})
    assert len(result.trades) == 1
    trade = result.trades[0]
    assert trade.entry_date > trade.signal_date
    assert trade.exit_reason == "stop"
    assert trade.shares == 250
    assert trade.exit_price == pytest.approx(106)
    assert trade.pnl == pytest.approx(1500)
    assert result.final_cash == pytest.approx(101_500)
    assert result.final_equity == pytest.approx(101_500)


def test_fees_and_time_exit():
    dates = ["2024-01-02", "2024-01-03", "2024-01-04"]
    prices = tape(
        dates,
        [
            {"signal": True, "atr": 0.1, "raw_stop": 96},
            {"Open": 100, "High": 101, "Low": 99, "Close": 100, "atr": 0.1},
            {"Open": 100, "High": 101, "Low": 99, "Close": 100, "atr": 0.1},
        ],
    )
    result = run_backtest(
        {"AAA": prices},
        calendar(dates),
        config(max_hold_days=1, buy_cost_rate=0.01, sell_cost_rate=0.01),
    )
    assert result.trades[0].exit_reason == "time"
    assert result.trades[0].shares == 250
    assert result.final_cash == pytest.approx(99_500)


def test_gap_through_the_stop_fills_at_the_open():
    dates = ["2024-01-02", "2024-01-03", "2024-01-04"]
    prices = tape(
        dates,
        [
            {"signal": True, "Close": 100, "raw_stop": 90},
            {"Open": 100, "High": 101, "Low": 95, "Close": 100},
            {"Open": 50, "High": 55, "Low": 40, "Close": 55},
        ],
    )
    result = run_backtest(
        {"AAA": prices},
        calendar(dates),
        config(risk_per_trade=0.5, max_stop_pct=0.5),
    )
    assert result.trades[0].exit_price == pytest.approx(50)
    assert result.trades[0].shares == 1000
    assert result.final_cash == pytest.approx(50_000)


def test_drawdown_halt_blocks_the_next_entry():
    dates = ["2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05"]
    loser = tape(
        dates,
        [
            {"signal": True, "Close": 100, "raw_stop": 90},
            {"Open": 100, "High": 101, "Low": 95, "Close": 100},
            {"Open": 50, "High": 55, "Low": 40, "Close": 55, "signal": False},
            {"Open": 55, "High": 56, "Low": 54, "Close": 55},
        ],
    )
    other = tape(
        dates,
        [
            {},
            {},
            {"signal": True, "Close": 100, "raw_stop": 96, "Open": 100},
            {"Open": 100, "High": 101, "Low": 99, "Close": 100},
        ],
    )
    result = run_backtest(
        {"AAA": loser, "BBB": other},
        calendar(dates),
        config(risk_per_trade=0.5, max_stop_pct=0.5),
    )
    assert result.halted is True
    assert [trade.symbol for trade in result.trades] == ["AAA"]
    assert all(position.symbol != "BBB" for position in result.open_positions)


def test_open_above_the_limit_is_not_filled():
    dates = ["2024-01-02", "2024-01-03"]
    prices = tape(dates, [{"signal": True, "Close": 100}, {"Open": 104, "Close": 104}])
    result = run_backtest({"AAA": prices}, calendar(dates), config(max_entry_gap=0.02))
    assert result.trades == ()
    assert result.final_equity == pytest.approx(100_000)


def test_bear_index_blocks_entries():
    dates = ["2024-01-02", "2024-01-03"]
    prices = tape(dates, [{"signal": True}, {"Open": 100}])
    result = run_backtest({"AAA": prices}, calendar(dates, bullish=[False, True]), config())
    assert result.trades == ()
    assert result.open_positions == ()


def test_only_the_strongest_name_is_taken_when_slots_are_full():
    dates = ["2024-01-02", "2024-01-03"]
    book = {
        "HIGH": tape(dates, [{"signal": True, "rs": 0.9}, {}]),
        "MID": tape(dates, [{"signal": True, "rs": 0.3}, {}]),
        "LOW": tape(dates, [{"signal": True, "rs": 0.1}, {}]),
    }
    result = run_backtest(book, calendar(dates), config(max_positions=1))
    assert {position.symbol for position in result.open_positions} == {"HIGH"}


def test_sector_cap_keeps_a_second_bank_out():
    dates = ["2024-01-02", "2024-01-03"]
    book = {
        "BANKA": tape(dates, [{"signal": True, "rs": 0.8}, {}]),
        "BANKB": tape(dates, [{"signal": True, "rs": 0.2}, {}]),
        "TECH": tape(dates, [{"signal": True, "rs": 0.1}, {}]),
    }
    result = run_backtest(
        book,
        calendar(dates),
        config(max_per_sector=1),
        sectors={"BANKA": "Bank", "BANKB": "Bank", "TECH": "Tech"},
    )
    held = {position.symbol for position in result.open_positions}
    assert held == {"BANKA", "TECH"}


def test_same_morning_sale_proceeds_are_not_reused():
    dates = ["2024-01-02", "2024-01-03", "2024-01-04"]
    held = tape(
        dates,
        [
            {"signal": True},
            {"Open": 100, "High": 101, "Low": 99, "Close": 100},
            {"Open": 100, "High": 101, "Low": 99, "Close": 100},
        ],
    )
    fresh = tape(
        dates,
        [
            {},
            {"signal": True, "Close": 100, "raw_stop": 96},
            {"Open": 100, "High": 101, "Low": 99, "Close": 100},
        ],
    )
    result = run_backtest(
        {"AAA": held, "BBB": fresh},
        calendar(dates),
        config(max_hold_days=1, risk_per_trade=0.05),
    )
    assert [trade.symbol for trade in result.trades] == ["AAA"]
    assert all(position.symbol != "BBB" for position in result.open_positions)


def test_trade_start_does_not_backfill():
    dates = ["2024-01-02", "2024-01-03", "2024-01-04", "2024-01-05"]
    first = tape(dates, [{"signal": True}, {}, {}, {}])
    later = tape(dates, [{}, {}, {"signal": True}, {"Open": 100, "Low": 99, "Close": 100}])
    result = run_backtest(
        {"AAA": first, "BBB": later},
        calendar(dates),
        config(),
        trade_start=pd.Timestamp("2024-01-04"),
    )
    assert result.trades == ()
    assert {position.symbol for position in result.open_positions} == {"BBB"}


def test_signal_on_the_last_bar_is_an_order_not_a_fill():
    dates = ["2024-01-02"]
    prices = tape(dates, [{"signal": True, "Close": 100, "raw_stop": 96}])
    result = run_backtest({"AAA": prices}, calendar(dates), config())
    assert result.trades == ()
    assert result.final_equity == pytest.approx(100_000)
    assert len(result.next_orders) == 1
    assert result.next_orders[0].side == "BUY"
    assert result.next_orders[0].shares == 250
    assert result.next_orders[0].product == "CNC"


def test_synthetic_book_never_borrows():
    prices, index, sectors = synthetic_market(n_days=360, n_symbols=3, seed=3)
    settings = Config()
    prepared = {symbol: prepare_symbol(frame, index["Close"], settings) for symbol, frame in prices.items()}
    result = run_backtest(
        prepared,
        prepare_index(index, settings),
        settings,
        sectors=sectors,
        mode="demo",
        universe_name="synthetic",
    )
    assert result.final_cash >= -0.01
    assert result.final_equity > 0
    assert all(trade.entry_date > trade.signal_date for trade in result.trades)
