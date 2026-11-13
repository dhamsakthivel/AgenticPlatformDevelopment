import socket
import time
from pathlib import Path
from urllib.parse import quote

import numpy as np
import pandas as pd

OHLC = ["Open", "High", "Low", "Close", "Volume"]


class DataError(RuntimeError):
    pass


def today_ist() -> pd.Timestamp:
    stamp = pd.Timestamp.now(tz="Asia/Kolkata")
    return pd.Timestamp(stamp.year, stamp.month, stamp.day)


def parse_ohlcv(frame: pd.DataFrame, symbol: str | None = None) -> pd.DataFrame:
    table = _select_symbol(frame, symbol) if isinstance(frame.columns, pd.MultiIndex) else frame.copy()
    table = table.rename(columns={column: str(column).strip().title() for column in table.columns})
    missing = [column for column in OHLC if column not in table.columns]
    if missing:
        raise DataError(f"missing columns {missing}")
    out = table.loc[:, OHLC].apply(pd.to_numeric, errors="coerce")
    out.index = _naive_dates(out.index)
    out = out[~out.index.duplicated(keep="last")].sort_index()
    out = out.dropna(subset=["Open", "High", "Low", "Close"])
    out = out[(out["Open"] > 0) & (out["Close"] > 0)]
    if out.empty:
        raise DataError("price frame has no usable rows")
    out["High"] = out[["High", "Open", "Close"]].max(axis=1)
    out["Low"] = out[["Low", "Open", "Close"]].min(axis=1)
    out["Volume"] = out["Volume"].fillna(0.0)
    return out


def synthetic_market(
    n_days: int = 420,
    n_symbols: int = 4,
    seed: int = 7,
) -> tuple[dict[str, pd.DataFrame], pd.DataFrame, dict[str, str]]:
    rng = np.random.default_rng(seed)
    dates = pd.bdate_range("2018-01-01", periods=n_days)
    index_returns = np.clip(rng.normal(0.00035, 0.01, n_days), -0.08, 0.08)
    index = _from_close(dates, 18_000 * np.cumprod(1 + index_returns), rng)
    prices: dict[str, pd.DataFrame] = {}
    sectors: dict[str, str] = {}
    for i in range(n_symbols):
        shock = np.clip(0.7 * index_returns + rng.normal(0.0002, 0.012, n_days), -0.1, 0.1)
        prices[f"SYN{i}"] = _from_close(dates, (200 + 40 * i) * np.cumprod(1 + shock), rng)
        sectors[f"SYN{i}"] = "SectorA" if i % 2 == 0 else "SectorB"
    return prices, index, sectors


def load_history(
    symbols: list[str],
    *,
    years: int,
    cache_dir: Path,
    refresh: bool = False,
    progress=None,
) -> tuple[dict[str, pd.DataFrame], pd.DataFrame, tuple[str, ...]]:
    if years < 1:
        raise DataError("years must be at least 1")
    cache_dir.mkdir(parents=True, exist_ok=True)
    start = today_ist() - pd.DateOffset(years=years)
    prices: dict[str, pd.DataFrame] = {}
    skipped: list[str] = []
    for i, symbol in enumerate(symbols, start=1):
        if progress is not None:
            progress(symbol, i, len(symbols))
        try:
            prices[symbol] = _cached_symbol(
                symbol, f"{symbol}.NS", start, cache_dir, refresh, require_coverage=False
            )
        except DataError:
            skipped.append(symbol)
    index = _cached_symbol(
        "INDEX_NSEI", "^NSEI", start, cache_dir, refresh, require_coverage=True
    )
    needed = min(5, len(symbols))
    if len(prices) < needed:
        raise DataError(f"only received {len(prices)} of {len(symbols)} symbols")
    return prices, index, tuple(skipped)


def _cached_symbol(
    symbol: str,
    ticker: str,
    start: pd.Timestamp,
    cache_dir: Path,
    refresh: bool,
    *,
    require_coverage: bool,
) -> pd.DataFrame:
    path = cache_dir / f"{quote(symbol, safe='')}.csv"
    if path.exists() and not refresh:
        cached = parse_ohlcv(pd.read_csv(path, parse_dates=["Date"], index_col="Date"), symbol=None)
        if history_is_usable(cached, start, require_coverage=require_coverage, today=today_ist()):
            return _window(cached, start)
    frame = _download(ticker)
    path.parent.mkdir(parents=True, exist_ok=True)
    frame.to_csv(path, index_label="Date")
    if not history_is_usable(frame, start, require_coverage=require_coverage, today=today_ist()):
        raise DataError(f"{ticker} does not have enough fresh history")
    return _window(frame, start)


def _download(ticker: str) -> pd.DataFrame:
    import yfinance as yf

    socket.setdefaulttimeout(30)
    start = (today_ist() - pd.DateOffset(years=12)).date().isoformat()
    end = (today_ist() + pd.Timedelta(days=1)).date().isoformat()
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            raw = yf.download(
                ticker,
                start=start,
                end=end,
                auto_adjust=True,
                progress=False,
                threads=False,
            )
            if raw is None or len(raw) == 0:
                raise DataError(f"no rows for {ticker}")
            symbol = ticker[:-3] if ticker.endswith(".NS") else None
            return parse_ohlcv(raw, symbol=symbol)
        except DataError:
            raise
        except Exception as exc:
            last_error = exc
            time.sleep(1.2 * (attempt + 1))
    raise DataError(f"download failed for {ticker}: {last_error}")


def history_is_usable(
    frame: pd.DataFrame,
    start: pd.Timestamp,
    *,
    require_coverage: bool,
    today: pd.Timestamp,
) -> bool:
    if frame.empty:
        return False
    fresh = frame.index.max() >= today - pd.Timedelta(days=10)
    if not fresh:
        return False
    if require_coverage:
        return bool(frame.index.min() <= start + pd.Timedelta(days=10))
    return len(frame) >= 250


def _window(frame: pd.DataFrame, start: pd.Timestamp) -> pd.DataFrame:
    if frame.index.min() <= start:
        return frame.loc[frame.index >= start]
    return frame


def _select_symbol(frame: pd.DataFrame, symbol: str | None) -> pd.DataFrame:
    keys = []
    if symbol:
        keys.extend([symbol, f"{symbol}.NS"])
    for level in range(frame.columns.nlevels):
        values = set(frame.columns.get_level_values(level))
        for key in keys:
            if key in values:
                return frame.xs(key, axis=1, level=level)
    reduced = frame
    while isinstance(reduced.columns, pd.MultiIndex):
        dropped = False
        for level in range(reduced.columns.nlevels):
            if len(set(reduced.columns.get_level_values(level))) == 1:
                reduced = reduced.droplevel(level, axis=1)
                dropped = True
                break
        if not dropped:
            raise DataError("could not parse price columns")
    return reduced


def _naive_dates(index) -> pd.DatetimeIndex:
    dates = pd.DatetimeIndex(pd.to_datetime(index))
    if dates.tz is not None:
        dates = dates.tz_convert("Asia/Kolkata").tz_localize(None)
    return dates.normalize()


def _from_close(dates: pd.DatetimeIndex, close: np.ndarray, rng: np.random.Generator) -> pd.DataFrame:
    close = np.asarray(close, dtype=float)
    open_ = np.empty_like(close)
    open_[0] = close[0]
    open_[1:] = close[:-1] * (1 + rng.normal(0, 0.004, len(close) - 1))
    spread = np.abs(rng.normal(0.004, 0.002, len(close)))
    high = np.maximum(open_, close) * (1 + spread)
    low = np.minimum(open_, close) * (1 - spread)
    volume = rng.integers(500_000, 2_000_000, len(close)).astype(float)
    return pd.DataFrame(
        {"Open": open_, "High": high, "Low": low, "Close": close, "Volume": volume},
        index=dates,
    )
