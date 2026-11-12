import numpy as np
import pandas as pd

from nse_swing.config import Config
from nse_swing.indicators import atr, ema, rsi

PULLBACK_BARS = 5
SWING_BARS = 10
SLOPE_BARS = 10
VOLUME_BARS = 20
RSI_PERIOD = 14


def pullback_signal(frame: pd.DataFrame, config: Config) -> pd.Series:
    close = frame["Close"]
    ema_fast = frame["ema20"]
    span = frame["High"] - frame["Low"]
    upper_half = (close - frame["Low"]) >= (span * 0.5)
    upper_half = upper_half.where(span > 0, True)
    atr_pct = frame["atr"] / close
    rules = (
        (close > frame["ema50"])
        & (frame["ema50"] > frame["ema200"])
        & (frame["ema50_slope"] > 0)
        & (frame["low_5"] <= ema_fast)
        & (close > ema_fast)
        & (frame["rsi"] >= config.rsi_low)
        & (frame["rsi"] <= config.rsi_high)
        & (close > close.shift(1))
        & (frame["Volume"] >= frame["vol_avg"])
        & (frame["vol_avg"] > 0)
        & ((close / ema_fast - 1) <= config.max_extension)
        & (atr_pct >= config.min_atr_pct)
        & (atr_pct <= config.max_atr_pct)
        & (frame["rs"] > 0)
        & upper_half
        & (ema_fast > 0)
    )
    return rules.fillna(False).astype(bool)


def prepare_symbol(frame: pd.DataFrame, benchmark_close: pd.Series, config: Config) -> pd.DataFrame:
    out = _naive_frame(frame)
    bench = benchmark_close.copy()
    bench.index = _naive_index(bench.index)
    bench = bench[~bench.index.duplicated(keep="last")].sort_index()
    out["ema20"] = ema(out["Close"], config.ema_fast)
    out["ema50"] = ema(out["Close"], config.ema_mid)
    out["ema200"] = ema(out["Close"], config.ema_slow)
    out["atr"] = atr(out, config.atr_period)
    out["rsi"] = rsi(out["Close"], RSI_PERIOD)
    out["vol_avg"] = out["Volume"].rolling(VOLUME_BARS).mean()
    out["low_5"] = out["Low"].rolling(PULLBACK_BARS).min()
    out["low_10"] = out["Low"].rolling(SWING_BARS).min()
    out["ema50_slope"] = out["ema50"] - out["ema50"].shift(SLOPE_BARS)
    bench = bench.reindex(out.index)
    stock_return = out["Close"] / out["Close"].shift(config.rs_lookback) - 1
    bench_return = bench / bench.shift(config.rs_lookback) - 1
    out["rs"] = stock_return - bench_return
    out["raw_stop"] = pd.concat(
        [out["low_10"], out["Close"] - config.atr_stop_mult * out["atr"]],
        axis=1,
    ).min(axis=1)
    out["signal"] = pullback_signal(out, config)
    return out


def prepare_index(frame: pd.DataFrame, config: Config) -> pd.DataFrame:
    out = _naive_frame(frame)
    out["ema_slow"] = ema(out["Close"], config.ema_slow)
    mature = np.arange(len(out)) >= (config.ema_slow - 1)
    out["bullish"] = (out["Close"] > out["ema_slow"]) & mature
    out["bullish"] = out["bullish"].fillna(False)
    return out


def _naive_frame(frame: pd.DataFrame) -> pd.DataFrame:
    out = frame.copy()
    out.index = _naive_index(out.index)
    return out[~out.index.duplicated(keep="last")].sort_index()


def _naive_index(index) -> pd.DatetimeIndex:
    dates = pd.DatetimeIndex(pd.to_datetime(index))
    if dates.tz is not None:
        dates = dates.tz_convert("Asia/Kolkata").tz_localize(None)
    return dates.normalize()
