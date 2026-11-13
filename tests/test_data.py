import pandas as pd
import pytest

from nse_swing.data import DataError, history_is_usable, parse_ohlcv
from nse_swing.universe import NIFTY_50, select_universe


def _prices(index):
    return pd.DataFrame(
        {
            "Open": [10.0, 11.0],
            "High": [10.5, 11.5],
            "Low": [9.5, 10.5],
            "Close": [10.2, 11.2],
            "Volume": [1000.0, 1100.0],
        },
        index=index,
    )


def test_parse_repairs_a_high_below_the_close():
    frame = _prices(pd.bdate_range("2024-01-02", periods=2))
    frame.loc[frame.index[0], "High"] = 9.0
    parsed = parse_ohlcv(frame)
    assert parsed.iloc[0]["High"] >= parsed.iloc[0]["Close"]
    assert list(parsed.columns) == ["Open", "High", "Low", "Close", "Volume"]


def test_parse_reads_both_yfinance_orientations():
    raw = _prices(pd.bdate_range("2024-06-03", periods=2))
    ticker_first = pd.concat({"RELIANCE.NS": raw}, axis=1)
    field_first = raw.copy()
    field_first.columns = pd.MultiIndex.from_tuples((column, "RELIANCE.NS") for column in raw.columns)
    for frame in (ticker_first, field_first):
        parsed = parse_ohlcv(frame, symbol="RELIANCE")
        assert parsed["Close"].tolist() == pytest.approx([10.2, 11.2])


def test_parse_rejects_an_empty_frame():
    with pytest.raises(DataError):
        parse_ohlcv(pd.DataFrame())


def test_a_newer_listing_is_usable_without_covering_the_whole_window():
    dates = pd.bdate_range("2024-01-02", periods=300)
    frame = _prices(dates[:2]).reindex(dates).ffill()
    today = dates[-1]
    start = today - pd.DateOffset(years=8)
    assert history_is_usable(frame, start, require_coverage=False, today=today) is True
    assert history_is_usable(frame, start, require_coverage=True, today=today) is False
    stale = frame.iloc[:-30]
    assert history_is_usable(stale, start, require_coverage=False, today=today) is False


def test_nifty50_list_has_unique_members():
    symbols = [item.symbol for item in NIFTY_50]
    assert len(symbols) == 50
    assert len(set(symbols)) == 50
    assert "RELIANCE" in symbols
    assert "M&M" in symbols
    assert "TMPV" in symbols
    liquid = select_universe("liquid10")
    assert len(liquid) == 10
    assert set(item.symbol for item in liquid) <= set(symbols)
