import pytest

from nse_swing.config import Config
from nse_swing.risk import drawdown_halt, plan_buy, risk_multiplier


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
    )
    params.update(overrides)
    return Config(**params)


def test_position_value_cap_binds_before_the_risk_budget():
    plan = plan_buy(
        close=100,
        raw_stop=96,
        equity=100_000,
        cash=100_000,
        open_risk=0,
        config=config(max_position_pct=0.18),
        risk_multiplier_value=1,
    )
    assert plan is not None
    assert plan.shares == 180


def test_cash_caps_the_order():
    plan = plan_buy(
        close=100,
        raw_stop=96,
        equity=100_000,
        cash=1_000,
        open_risk=0,
        config=config(),
        risk_multiplier_value=1,
    )
    assert plan is not None
    assert plan.shares == 10


def test_half_risk_after_a_drawdown_halves_the_shares():
    full = plan_buy(
        close=100, raw_stop=96, equity=100_000, cash=100_000, open_risk=0,
        config=config(), risk_multiplier_value=1,
    )
    half = plan_buy(
        close=100, raw_stop=96, equity=100_000, cash=100_000, open_risk=0,
        config=config(), risk_multiplier_value=0.5,
    )
    assert full is not None and half is not None
    assert full.shares == 250
    assert half.shares == 125


def test_stop_outside_the_band_is_rejected():
    wide = plan_buy(
        close=100, raw_stop=80, equity=100_000, cash=100_000, open_risk=0,
        config=config(max_stop_pct=0.07), risk_multiplier_value=1,
    )
    tight = plan_buy(
        close=100, raw_stop=99.5, equity=100_000, cash=100_000, open_risk=0,
        config=config(), risk_multiplier_value=1,
    )
    assert wide is None
    assert tight is None


def test_shares_are_sized_from_the_limit_so_a_gap_does_not_raise_risk():
    plan = plan_buy(
        close=100, raw_stop=96, equity=100_000, cash=100_000, open_risk=0,
        config=config(max_entry_gap=0.04, max_stop_pct=0.10),
        risk_multiplier_value=1,
    )
    assert plan is not None
    assert plan.limit_price == pytest.approx(104)
    assert plan.shares == 125
    assert plan.planned_risk == pytest.approx(1000)


def test_heat_and_a_halt_block_new_risk():
    blocked = plan_buy(
        close=100, raw_stop=96, equity=100_000, cash=100_000, open_risk=4_000,
        config=config(max_portfolio_heat=0.04), risk_multiplier_value=1,
    )
    halted = plan_buy(
        close=100, raw_stop=96, equity=100_000, cash=100_000, open_risk=0,
        config=config(), risk_multiplier_value=0,
    )
    assert blocked is None
    assert halted is None


def test_drawdown_brake_has_memory():
    settings = Config()
    assert drawdown_halt(False, 0.25, settings) is True
    assert drawdown_halt(True, 0.20, settings) is True
    assert drawdown_halt(True, 0.10, settings) is False
    assert drawdown_halt(False, 0.16, settings) is False
    assert risk_multiplier(0.16, False, settings) == 0.5
    assert risk_multiplier(0.16, True, settings) == 0.0
    assert risk_multiplier(0.05, False, settings) == 1.0


def test_brake_order_is_rejected():
    with pytest.raises(ValueError):
        Config(resume_dd=0.2, heat_reduction_dd=0.15, halt_new_entries_dd=0.25)
