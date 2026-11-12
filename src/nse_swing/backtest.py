from dataclasses import dataclass

import pandas as pd

from nse_swing.config import Config
from nse_swing.models import BacktestResult, OpenPosition, OrderTicket, Trade
from nse_swing.risk import buy_debit, drawdown_halt, plan_buy, risk_multiplier, sell_credit

REQUIRED = ("Open", "High", "Low", "Close", "atr", "ema50", "signal", "raw_stop")


@dataclass
class _Position:
    symbol: str
    shares: int
    signal_date: pd.Timestamp
    entry_date: pd.Timestamp
    entry_price: float
    stop: float
    initial_stop: float
    cost_basis: float
    last_close: float
    highest_close: float
    bars_held: int
    sector: str


@dataclass(frozen=True)
class _Entry:
    symbol: str
    signal_date: pd.Timestamp
    shares: int
    stop: float
    limit_price: float
    sector: str


def run_backtest(
    prices: dict[str, pd.DataFrame],
    index: pd.DataFrame,
    config: Config,
    *,
    sectors: dict[str, str] | None = None,
    trade_start: pd.Timestamp | None = None,
    mode: str = "nse",
    universe_name: str = "nifty50",
    skipped: tuple[str, ...] = (),
) -> BacktestResult:
    """Fill next open, and stop out at the stop or worse on a gap.

    Buys use only cash already in the account at the previous close. Sale
    proceeds from the same morning are not reused. Nothing here borrows.
    """
    if index.empty:
        raise ValueError("index calendar is empty")
    book = {symbol: _normalize(frame) for symbol, frame in prices.items()}
    for symbol, frame in book.items():
        missing = [column for column in REQUIRED if column not in frame.columns]
        if missing:
            raise ValueError(f"{symbol} is missing {missing}")
    calendar = _normalize(index)
    if "bullish" not in calendar.columns:
        raise ValueError("index frame needs a bullish column")
    sectors = sectors or {}
    start = None if trade_start is None else pd.Timestamp(trade_start).normalize()

    cash = config.initial_capital
    peak = cash
    halted = False
    positions: dict[str, _Position] = {}
    pending: list[_Entry] = []
    pending_exits: dict[str, str] = {}
    trades: list[Trade] = []
    rows: list[dict] = []
    dates = list(calendar.index)

    for i, day in enumerate(dates):
        entry_budget = cash
        for symbol in list(pending_exits):
            if symbol not in positions:
                pending_exits.pop(symbol, None)
                continue
            row = _row(book[symbol], day)
            if row is None:
                continue
            open_price = float(row["Open"])
            reason = "stop" if open_price <= positions[symbol].stop else pending_exits[symbol]
            _exit(positions, trades, symbol, day, open_price, reason, config)
            pending_exits.pop(symbol, None)
        cash = _account_cash(config.initial_capital, trades, positions)

        for symbol, position in list(positions.items()):
            row = _row(book[symbol], day)
            if row is None:
                continue
            if float(row["Open"]) <= position.stop:
                _exit(positions, trades, symbol, day, float(row["Open"]), "stop", config)
                pending_exits.pop(symbol, None)
        cash = _account_cash(config.initial_capital, trades, positions)

        for order in pending:
            if order.symbol in positions or len(positions) >= config.max_positions:
                continue
            if _sector_count(positions, order.sector) >= config.max_per_sector:
                continue
            row = _row(book[order.symbol], day)
            if row is None or not (order.signal_date < day):
                if row is not None and not (order.signal_date < day):
                    raise RuntimeError(f"refusing same-bar fill for {order.symbol}")
                continue
            open_price = float(row["Open"])
            if open_price > order.limit_price or open_price <= order.stop:
                continue
            fill = open_price * (1 + config.slippage_pct)
            if fill <= order.stop:
                continue
            shares = _shares_affordable(order.shares, fill, min(cash, entry_budget), config)
            if shares < 1:
                continue
            debit = buy_debit(shares, fill, config.buy_cost_rate)
            cash = round(cash - debit, 2)
            entry_budget = round(entry_budget - debit, 2)
            if cash < -0.01:
                raise RuntimeError(f"cash account went negative: {cash}")
            positions[order.symbol] = _Position(
                symbol=order.symbol,
                shares=shares,
                signal_date=order.signal_date,
                entry_date=day,
                entry_price=fill,
                stop=order.stop,
                initial_stop=order.stop,
                cost_basis=debit,
                last_close=fill,
                highest_close=0.0,
                bars_held=0,
                sector=order.sector,
            )
        pending = []

        for symbol, position in list(positions.items()):
            row = _row(book[symbol], day)
            if row is None or symbol not in positions:
                continue
            if float(row["Low"]) <= position.stop:
                raw = float(row["Open"]) if float(row["Open"]) <= position.stop else position.stop
                _exit(positions, trades, symbol, day, raw, "stop", config)
        cash = _account_cash(config.initial_capital, trades, positions)
        if cash < -0.01:
            raise RuntimeError(f"cash account went negative: {cash}")

        invested = 0.0
        for symbol, position in list(positions.items()):
            row = _row(book[symbol], day)
            if row is None:
                invested += position.shares * position.last_close
                continue
            close = float(row["Close"])
            position.last_close = close
            position.highest_close = max(position.highest_close, close)
            position.bars_held += 1
            atr_value = float(row["atr"]) if _finite(row["atr"]) else None
            if atr_value is not None and atr_value > 0:
                trail = position.highest_close - config.atr_trail_mult * atr_value
                risk_per_share = position.entry_price - position.initial_stop
                if risk_per_share > 0 and close >= position.entry_price + config.breakeven_r * risk_per_share:
                    trail = max(trail, position.entry_price)
                if _finite(trail):
                    position.stop = max(position.stop, trail)
            ema50 = float(row["ema50"]) if _finite(row["ema50"]) else None
            if ema50 is not None and close < ema50:
                pending_exits[symbol] = "trend"
            elif position.bars_held >= config.max_hold_days:
                pending_exits[symbol] = "time"
            invested += position.shares * position.last_close

        equity = round(cash + invested, 2)
        peak = max(peak, equity)
        drawdown = 0.0 if peak <= 0 else (peak - equity) / peak
        halted = drawdown_halt(halted, drawdown, config)
        multiplier = risk_multiplier(drawdown, halted, config)
        allow = (start is None or day >= start) and i >= config.min_history and multiplier > 0
        bullish = False
        index_row = _row(calendar, day)
        if index_row is not None:
            bullish = bool(index_row["bullish"]) is True and not _missing(index_row["bullish"])
        if allow and bullish:
            pending = _queue_entries(book, positions, pending_exits, sectors, day, equity, cash, config, multiplier)
        invested_frac = 0.0 if equity <= 0 else invested / equity
        rows.append({"date": day, "equity": equity, "cash": cash, "invested_frac": invested_frac})

    daily = pd.DataFrame(rows).set_index("date")
    final_equity = float(daily["equity"].iloc[-1])
    final_cash = float(daily["cash"].iloc[-1])
    return BacktestResult(
        daily=daily,
        trades=tuple(trades),
        final_cash=final_cash,
        final_equity=final_equity,
        open_positions=tuple(_snapshot(position) for position in positions.values()),
        next_orders=tuple(_tickets(pending, positions, pending_exits)),
        halted=halted,
        peak_equity=peak,
        mode=mode,
        universe_name=universe_name,
        trade_start=start,
        skipped=skipped,
        config=config,
    )


def _queue_entries(book, positions, pending_exits, sectors, day, equity, cash, config, multiplier) -> list[_Entry]:
    exiting = set(pending_exits)
    active = [position for position in positions.values() if position.symbol not in exiting]
    slots = config.max_positions - len(active)
    if slots <= 0:
        return []
    sector_counts: dict[str, int] = {}
    for position in active:
        sector_counts[position.sector] = sector_counts.get(position.sector, 0) + 1
    open_risk = 0.0
    for position in positions.values():
        if position.symbol in exiting:
            continue
        open_risk += position.shares * max(position.last_close - position.stop, 0.0)
    candidates = []
    for symbol, frame in book.items():
        if symbol in positions:
            continue
        row = _row(frame, day)
        if row is None or not _flag(row["signal"]):
            continue
        rs = float(row["rs"]) if "rs" in row.index and _finite(row["rs"]) else 0.0
        candidates.append((rs, symbol, row))
    candidates.sort(key=lambda item: (-item[0], item[1]))
    orders: list[_Entry] = []
    reserved_cash = cash
    for _rs, symbol, row in candidates:
        if len(orders) >= slots:
            break
        sector = sectors.get(symbol, symbol)
        if sector_counts.get(sector, 0) >= config.max_per_sector:
            continue
        plan = plan_buy(
            close=float(row["Close"]),
            raw_stop=float(row["raw_stop"]),
            equity=equity,
            cash=reserved_cash,
            open_risk=open_risk,
            config=config,
            risk_multiplier_value=multiplier,
        )
        if plan is None:
            continue
        orders.append(
            _Entry(
                symbol=symbol,
                signal_date=day,
                shares=plan.shares,
                stop=plan.stop,
                limit_price=plan.limit_price,
                sector=sector,
            )
        )
        reserved_cash = round(reserved_cash - plan.reserved_cash, 2)
        open_risk += plan.planned_risk
        sector_counts[sector] = sector_counts.get(sector, 0) + 1
    return orders


def _tickets(pending: list[_Entry], positions: dict[str, _Position], pending_exits: dict[str, str]) -> list[OrderTicket]:
    tickets: list[OrderTicket] = []
    for order in pending:
        tickets.append(
            OrderTicket(
                symbol=order.symbol,
                side="BUY",
                shares=order.shares,
                order_type="LIMIT",
                limit_price=round(order.limit_price, 2),
                trigger_price=None,
                stop=round(order.stop, 2),
                reason="ema20-pullback",
            )
        )
    for symbol, position in sorted(positions.items()):
        if symbol in pending_exits:
            tickets.append(
                OrderTicket(
                    symbol=symbol,
                    side="SELL",
                    shares=position.shares,
                    order_type="LIMIT",
                    limit_price=round(position.last_close * 0.99, 2),
                    trigger_price=None,
                    stop=round(position.stop, 2),
                    reason=pending_exits[symbol],
                )
            )
        else:
            tickets.append(
                OrderTicket(
                    symbol=symbol,
                    side="SELL",
                    shares=position.shares,
                    order_type="SL-L",
                    limit_price=round(position.stop * 0.995, 2),
                    trigger_price=round(position.stop, 2),
                    stop=round(position.stop, 2),
                    reason="protective-stop",
                )
            )
    return tickets


def _exit(positions, trades, symbol, day, raw_price, reason, config: Config) -> None:
    position = positions.pop(symbol)
    fill = raw_price * (1 - config.slippage_pct)
    credit = sell_credit(position.shares, fill, config.sell_cost_rate, config.dp_charge_inr)
    pnl = round(credit - position.cost_basis, 2)
    risk_rupees = position.shares * (position.entry_price - position.initial_stop)
    r_multiple = 0.0 if risk_rupees <= 0 else pnl / risk_rupees
    if not (position.entry_date > position.signal_date):
        raise RuntimeError(f"lookahead fill on {symbol}")
    trades.append(
        Trade(
            symbol=symbol,
            signal_date=position.signal_date,
            entry_date=position.entry_date,
            exit_date=day,
            shares=position.shares,
            entry_price=position.entry_price,
            exit_price=fill,
            pnl=pnl,
            r_multiple=r_multiple,
            exit_reason=reason,
            bars_held=position.bars_held,
        )
    )


def _account_cash(initial: float, trades: list[Trade], positions: dict[str, _Position]) -> float:
    basis = sum(position.cost_basis for position in positions.values())
    closed = sum(trade.pnl for trade in trades)
    # Closed PnL is proceeds minus cost basis, and open cost basis is still out of cash.
    # Cash = initial - open cost basis + closed pnl.
    return round(initial - basis + closed, 2)


def _shares_affordable(desired: int, fill: float, budget: float, config: Config) -> int:
    shares = desired
    while shares > 0:
        if buy_debit(shares, fill, config.buy_cost_rate) <= budget + 1e-6:
            return shares
        shares -= 1
    return 0


def _sector_count(positions: dict[str, _Position], sector: str) -> int:
    return sum(1 for position in positions.values() if position.sector == sector)


def _snapshot(position: _Position) -> OpenPosition:
    return OpenPosition(
        symbol=position.symbol,
        shares=position.shares,
        entry_date=position.entry_date,
        entry_price=position.entry_price,
        stop=position.stop,
        last_close=position.last_close,
        bars_held=position.bars_held,
        sector=position.sector,
    )


def _normalize(frame: pd.DataFrame) -> pd.DataFrame:
    out = frame.copy()
    idx = pd.DatetimeIndex(pd.to_datetime(out.index))
    if idx.tz is not None:
        idx = idx.tz_convert("Asia/Kolkata").tz_localize(None)
    out.index = idx.normalize()
    return out[~out.index.duplicated(keep="last")].sort_index()


def _row(frame: pd.DataFrame, day: pd.Timestamp):
    if day not in frame.index:
        return None
    row = frame.loc[day]
    if isinstance(row, pd.DataFrame):
        row = row.iloc[-1]
    return row


def _finite(value) -> bool:
    try:
        if _missing(value):
            return False
        number = float(value)
    except (TypeError, ValueError):
        return False
    return number == number and number not in (float("inf"), float("-inf"))


def _missing(value) -> bool:
    try:
        return bool(pd.isna(value))
    except (TypeError, ValueError):
        return False


def _flag(value) -> bool:
    if _missing(value):
        return False
    return bool(value)
