import argparse
import json
from pathlib import Path

import pandas as pd

from nse_swing.backtest import run_backtest
from nse_swing.config import Config
from nse_swing.data import DataError, load_history, synthetic_market
from nse_swing.paper import resolve_account
from nse_swing.report import render_goal, render_result, write_outputs
from nse_swing.strategy import prepare_index, prepare_symbol
from nse_swing.universe import sector_map, select_universe


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="nse-swing",
        description="NSE delivery swing decisions. Long only. Nothing is sent to a broker.",
    )
    sub = parser.add_subparsers(dest="command", required=True)

    goal = sub.add_parser("goal", help="Show the CAGR required to grow the account to the goal.")
    _add_money(goal)

    backtest = sub.add_parser("backtest", help="Replay the rules on history.")
    _add_run(backtest)

    scan = sub.add_parser("scan", help="Size today's orders for a flat account. Does not remember positions.")
    _add_run(scan)

    paper = sub.add_parser("paper", help="Run the model portfolio forward from the day it was started.")
    _add_run(paper)
    paper.add_argument("--state", type=Path, default=None)
    paper.add_argument("--reset", action="store_true")

    args = parser.parse_args(argv)
    try:
        if args.command == "goal":
            config = _config(args)
            print(render_goal(config))
            print()
            print("Not a SEBI-registered adviser. No order is sent to a broker.")
            return 0
        return _execute(args)
    except (DataError, ValueError) as exc:
        print(exc)
        return 2


def _execute(args: argparse.Namespace) -> int:
    demo = bool(args.demo)
    requested = "demo" if demo else args.universe
    note = None
    trade_start = None
    if args.command == "paper":
        state_path = args.state or Path("state") / f"paper-{requested}.json"
        _reject_mismatched_book(state_path, requested, args.reset)
        prices, index, sectors, skipped, universe_name = _load(args, requested)
        spec, note = resolve_account(
            state_path,
            capital=args.capital,
            goal=args.goal,
            universe=requested,
            last_date=index.index.max(),
            reset=args.reset,
        )
        trade_start = pd.Timestamp(spec.trade_start)
        config = _config(args, capital=spec.capital, goal=spec.goal)
    else:
        prices, index, sectors, skipped, universe_name = _load(args, requested)
        config = _config(args)
        if args.command == "scan":
            trade_start = index.index.max()
            note = "Flat scan of the latest session. Earlier positions are not carried."

    prepared: dict[str, pd.DataFrame] = {}
    failed = list(skipped)
    benchmark = index["Close"]
    for symbol, frame in prices.items():
        try:
            prepared[symbol] = prepare_symbol(frame, benchmark, config)
        except Exception:
            failed.append(symbol)
    if not prepared:
        raise DataError("no symbol could be prepared")
    result = run_backtest(
        prepared,
        prepare_index(index, config),
        config,
        sectors=sectors,
        trade_start=trade_start,
        mode="demo" if universe_name == "synthetic" else "nse",
        universe_name=universe_name,
        skipped=tuple(dict.fromkeys(failed)),
    )
    text = render_result(result)
    if note:
        text = note + "\n\n" + text
    print(text)
    if args.save is not None:
        write_outputs(args.save, result, text)
        print(f"\nWrote {args.save / 'report.txt'} and {args.save / 'orders.json'}")
    return 0


def _reject_mismatched_book(path: Path, universe: str, reset: bool) -> None:
    if reset or not path.exists():
        return
    stored = json.loads(path.read_text())["universe"]
    if stored != universe:
        raise ValueError(f"{path} belongs to the {stored} book. Pass --reset or another --state path.")


def _load(args: argparse.Namespace, universe: str):
    if universe == "demo":
        prices, index, sectors = synthetic_market()
        return prices, index, sectors, (), "synthetic"
    members = select_universe(universe)

    def progress(symbol: str, index: int, total: int) -> None:
        print(f"Prices {index}/{total} {symbol}", flush=True)

    prices, index, skipped = load_history(
        [item.symbol for item in members],
        years=args.years,
        cache_dir=args.cache,
        refresh=args.refresh,
        progress=progress,
    )
    sectors = {symbol: sector_map(members)[symbol] for symbol in prices}
    return prices, index, sectors, skipped, universe


def _config(args: argparse.Namespace, capital: float | None = None, goal: float | None = None) -> Config:
    values: dict[str, float] = {
        "initial_capital": args.capital if capital is None else capital,
        "goal_capital": args.goal if goal is None else goal,
    }
    if getattr(args, "risk", None) is not None:
        values["risk_per_trade"] = args.risk
    return Config(**values)


def _add_money(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--capital", type=float, default=100_000.0)
    parser.add_argument("--goal", type=float, default=10_000_000.0)


def _add_run(parser: argparse.ArgumentParser) -> None:
    _add_money(parser)
    parser.add_argument("--risk", type=float, default=None)
    parser.add_argument("--years", type=int, default=8)
    parser.add_argument("--universe", choices=("nifty50", "liquid10"), default="nifty50")
    parser.add_argument("--demo", action="store_true")
    parser.add_argument("--refresh", action="store_true")
    parser.add_argument("--cache", type=Path, default=Path("data/cache"))
    parser.add_argument("--save", type=Path, default=None)
