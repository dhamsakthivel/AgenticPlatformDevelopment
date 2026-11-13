import numpy as np
import pandas as pd
import pytest

from nse_swing.indicators import atr, ema, rsi


def test_ema_matches_the_recursive_definition():
    series = pd.Series([1.0, 2.0, 3.0, 4.0])
    assert ema(series, 3).tolist() == pytest.approx([1.0, 1.5, 2.25, 3.125])


def test_ema_does_not_look_ahead():
    series = pd.Series(np.linspace(10, 30, 30))
    full = ema(series, 5)
    short = ema(series.iloc[:12], 5)
    assert full.iloc[11] == pytest.approx(short.iloc[11])


def test_atr_uses_wilder_smoothing():
    frame = pd.DataFrame(
        {
            "High": [10.0, 12.0, 11.0],
            "Low": [8.0, 9.0, 10.0],
            "Close": [9.0, 11.0, 10.5],
        }
    )
    assert atr(frame, 3).iloc[2] == pytest.approx(17 / 9)


def test_rsi_of_a_rising_series_is_near_100():
    value = rsi(pd.Series(np.arange(1, 40, dtype=float)), 14).iloc[-1]
    assert value > 99
