import math

import numpy as np
import pytest

from nse_swing.config import Config
from nse_swing.goal import gain_to_recover, required_cagr, simulate_paths, years_to_goal
from nse_swing.report import format_inr, render_goal


def test_required_cagr_compounds_back_to_the_goal():
    cagr = required_cagr(100_000, 10_000_000, 10)
    assert cagr == pytest.approx(100**0.1 - 1)
    assert years_to_goal(100_000, 10_000_000, cagr) == pytest.approx(10)


def test_a_flat_or_losing_book_never_reaches_the_goal():
    assert years_to_goal(100_000, 10_000_000, 0) == math.inf
    assert years_to_goal(100_000, 10_000_000, -0.1) == math.inf


def test_recovering_a_loss_takes_more_than_the_loss():
    assert gain_to_recover(0.5) == pytest.approx(1.0)


def test_resampled_paths_do_not_invent_a_return():
    flat = simulate_paths(
        np.zeros(300), years=2, n_paths=30, block=10,
        target_multiple=100, ruin_drawdown=0.5, seed=1,
    )
    hot = simulate_paths(
        np.full(300, 0.01), years=2, n_paths=20, block=10,
        target_multiple=100, ruin_drawdown=0.5, seed=1,
    )
    assert flat is not None and hot is not None
    assert flat.hit_target_frac == 0
    assert flat.median_terminal == pytest.approx(1)
    assert hot.hit_target_frac == 1
    assert hot.median_terminal > 100


def test_goal_text_states_the_ten_year_requirement():
    text = render_goal(Config())
    assert "100x" in text
    assert "58.5%" in text
    assert "arithmetic, not a forecast." in text


def test_rupees_use_indian_grouping():
    assert format_inr(100_000) == "₹1,00,000"
    assert format_inr(10_000_000) == "₹1,00,00,000"
    assert format_inr(-1500) == "-₹1,500"
    assert format_inr(999) == "₹999"
