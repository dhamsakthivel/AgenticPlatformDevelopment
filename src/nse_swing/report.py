import json
import math
from dataclasses import asdict
from pathlib import Path

from nse_swing.config import Config
from nse_swing.goal import gain_to_recover, required_cagr, simulate_paths, years_to_goal
from nse_swing.metrics import performance
from nse_swing.models import BacktestResult, OrderTicket


def format_inr(amount: float, decimals: int = 0) -> str:
    sign = "-" if amount < 0 else ""
    text = f"{abs(amount):.{decimals}f}"
    whole, _, frac = text.partition(".")
    grouped = _indian_group(whole)
    if decimals:
        return f"{sign}₹{grouped}.{frac}"
    return f"{sign}₹{grouped}"


def render_goal(config: Config) -> str:
    multiple = config.goal_capital / config.initial_capital
    lines = [
        f"Starting capital {format_inr(config.initial_capital)}",
        f"Goal {format_inr(config.goal_capital)} ({multiple:.0f}x)",
        "",
        "Years    Required CAGR",
    ]
    for years in (3, 5, 7, 10, 15, 20):
        cagr = required_cagr(config.initial_capital, config.goal_capital, years)
        lines.append(f"{years:<8} {_pct(cagr)}")
    lines.extend(
        [
            "",
            f"A 50% drawdown needs a {_pct(gain_to_recover(0.5))} gain to get back to the same rupees.",
            (
                f"This system risks {_pct(config.risk_per_trade)} of equity per trade, "
                f"holds at most {config.max_positions} delivery positions, "
                f"and stops opening trades after a {_pct(config.halt_new_entries_dd)} drawdown from the peak."
            ),
            "Those limits make a fast jump to the goal implausible. They are there so the account is not a lottery ticket.",
            "This is arithmetic, not a forecast.",
        ]
    )
    return "\n".join(lines)


def render_result(result: BacktestResult) -> str:
    config = result.config
    perf = performance(result.daily, result.trades, config.initial_capital, config.risk_free_rate)
    lines = [banner(config), ""]
    if result.mode == "demo":
        lines.append("DEMO DATA. These numbers are not NSE results.")
    else:
        lines.append(
            "NSE history uses today's index members and adjusted Yahoo prices. "
            "That is survivorship bias, not a forecast."
        )
    lines.extend(
        [
            f"Universe: {result.universe_name}",
            f"Window: {result.daily.index[0].date()} to {result.daily.index[-1].date()} ({perf.years:.2f} years)",
            f"Start {format_inr(config.initial_capital)}    End {format_inr(result.final_equity)}    "
            f"Cash {format_inr(result.final_cash)}",
            f"Total return {_pct(perf.total_return)}    CAGR {_pct(perf.cagr)}    "
            f"Max drawdown {_pct(perf.max_drawdown)} over {perf.drawdown_sessions} sessions",
            f"Sharpe {_num(perf.sharpe)}    Sortino {_num(perf.sortino)}    "
            f"Average invested {_pct(perf.average_exposure)}",
            f"Closed trades: {perf.trade_count}    Win rate {_pct(perf.win_rate)}    "
            f"Average R {_num(perf.average_r)}    Profit factor {_num(perf.profit_factor)}",
        ]
    )
    if result.halted:
        lines.append(
            f"New entries are halted until equity is back within {_pct(config.resume_dd)} of the peak "
            f"({format_inr(result.peak_equity)})."
        )
    if result.trade_start is not None:
        lines.append(f"Model portfolio start: {result.trade_start.date()}. Earlier signals are ignored.")
    if result.skipped:
        lines.append("Skipped symbols: " + ", ".join(result.skipped))
    lines.extend(["", "Open positions"])
    if not result.open_positions:
        lines.append("  None")
    for position in result.open_positions:
        lines.append(
            f"  {position.symbol:<12} {position.shares:>6} shares  "
            f"entry {position.entry_price:.2f}  stop {position.stop:.2f}  "
            f"last {position.last_close:.2f}  held {position.bars_held}  {position.sector}"
        )
    lines.extend(["", "Closed trades"])
    if not result.trades:
        lines.append("  None")
    shown = result.trades[-12:]
    if len(result.trades) > len(shown):
        lines.append(f"  Last {len(shown)} of {len(result.trades)}")
    for trade in shown:
        lines.append(
            f"  {trade.exit_date.date()} {trade.symbol:<12} {trade.shares:>5}  "
            f"{trade.entry_price:.2f} -> {trade.exit_price:.2f}  "
            f"pnl {format_inr(trade.pnl)}  R {trade.r_multiple:.2f}  {trade.exit_reason}"
        )
    lines.extend(["", "Next session, CNC. Review before the open. Nothing is sent."])
    if not result.next_orders:
        lines.append("  No orders for the next open.")
    for ticket in result.next_orders:
        lines.append("  " + _ticket_line(ticket))
    lines.extend(["", _projection(result, perf), "", _footer(config)])
    return "\n".join(lines)


def banner(config: Config) -> str:
    multiple = config.goal_capital / config.initial_capital
    lines = [
        "Not a SEBI-registered adviser. No order is sent to a broker.",
        (
            f"{format_inr(config.initial_capital)} to {format_inr(config.goal_capital)} "
            f"is {multiple:.0f}x. Risk stays at {_pct(config.risk_per_trade)} per trade."
        ),
    ]
    if config.risk_per_trade > 0.01 or config.max_position_pct > 0.25:
        lines.append("Risk settings are above the safety defaults. Raising risk does not make the goal safer.")
    return "\n".join(lines)


def write_outputs(directory: Path, result: BacktestResult, text: str) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "report.txt").write_text(text + "\n")
    payload = {
        "sent_to_broker": False,
        "product": "CNC",
        "disclaimer": "Decision support only. No order has been sent to a broker.",
        "mode": result.mode,
        "universe": result.universe_name,
        "equity": result.final_equity,
        "cash": result.final_cash,
        "halted": result.halted,
        "orders": [asdict(ticket) for ticket in result.next_orders],
    }
    (directory / "orders.json").write_text(json.dumps(payload, indent=2) + "\n")


def _projection(result: BacktestResult, perf) -> str:
    config = result.config
    if perf.cagr is None or perf.trade_count < 20:
        return "Not enough closed trades or calendar time to project a path toward the goal."
    needed = years_to_goal(config.initial_capital, config.goal_capital, perf.cagr)
    if perf.cagr <= 0 or not math.isfinite(needed):
        return "At this CAGR the model does not reach the goal. Resampling history will not invent an edge."
    horizon = int(min(config.monte_carlo_cap_years, max(10, math.ceil(needed))))
    returns = result.daily["equity"].pct_change().dropna().to_numpy()
    paths = simulate_paths(
        returns,
        years=horizon,
        n_paths=config.monte_carlo_paths,
        block=config.monte_carlo_block,
        target_multiple=config.goal_capital / config.initial_capital,
        ruin_drawdown=0.5,
        seed=7,
    )
    sentence = (
        f"If this window's CAGR of {_pct(perf.cagr)} continued, the goal would take about {needed:.0f} years. "
        "A backtest CAGR is not a promise that it continues."
    )
    if paths is None:
        return sentence
    return (
        f"{sentence} Of {paths.paths} resampled {paths.years}-year paths, "
        f"{_pct(paths.hit_target_frac)} touched the goal and {_pct(paths.ruin_frac)} touched a 50% drawdown. "
        f"Median ending multiple {paths.median_terminal:.2f} "
        f"(5th {_pct_multiple(paths.low_terminal)}, 95th {_pct_multiple(paths.high_terminal)})."
    )


def _footer(config: Config) -> str:
    return (
        f"Costs, approximate NSE delivery levies: buy {_pct(config.buy_cost_rate, 2)}, "
        f"sell {_pct(config.sell_cost_rate, 2)}, DP {format_inr(config.dp_charge_inr)} per sell, "
        f"slippage {_pct(config.slippage_pct, 2)} a side. Brokerage is modelled as zero. "
        "The backtest completes a stop at the stop price, or at the open if price gaps through it. "
        "The sheet uses a limit or an SL-limit, which you may have to replace if the open runs through it. "
        "Long-only cash account. No futures, no options, no leverage."
    )


def _ticket_line(ticket: OrderTicket) -> str:
    if ticket.order_type == "SL-L":
        return (
            f"SELL {ticket.symbol:<12} {ticket.shares:>5} SL-L "
            f"trigger {ticket.trigger_price:.2f} limit {ticket.limit_price:.2f}  {ticket.reason}"
        )
    stop = "" if ticket.stop is None else f"  stop {ticket.stop:.2f}"
    return (
        f"{ticket.side} {ticket.symbol:<12} {ticket.shares:>5} {ticket.order_type} "
        f"{ticket.limit_price:.2f}{stop}  {ticket.reason}"
    )


def _indian_group(whole: str) -> str:
    if len(whole) <= 3:
        return whole
    head, tail = whole[:-3], whole[-3:]
    parts: list[str] = []
    while head:
        parts.append(head[-2:])
        head = head[:-2]
    return ",".join(reversed(parts)) + "," + tail


def _pct(value: float | None, digits: int = 1) -> str:
    if value is None:
        return "n/a"
    if math.isinf(value):
        return "unbounded"
    return f"{value * 100:.{digits}f}%"


def _pct_multiple(value: float) -> str:
    return f"{value:.2f}x"


def _num(value: float | None) -> str:
    if value is None:
        return "n/a"
    if math.isinf(value):
        return "unbounded"
    return f"{value:.2f}"
