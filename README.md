# NSE swing decisions

A long-only swing book for NSE cash equities. It decides after the close, sizes each order from a cash account, and writes a CNC limit sheet for the next open.

It does not send orders to a broker. It does not use futures, options, or leverage. It will not raise risk to chase a target.

₹1,00,000 to ₹1,00,00,000 is 100×. That is arithmetic, not a forecast:

| Years | CAGR required, every year, with no ruin |
| --- | --- |
| 3 | 364.2% |
| 5 | 151.2% |
| 7 | 93.1% |
| 10 | 58.5% |
| 15 | 35.9% |
| 20 | 25.9% |

A 50% drawdown needs a 100% gain just to get back to the same rupees. The book risks 0.8% of equity per trade, holds at most five delivery positions, and stops opening new trades after a 25% drawdown from the peak. Those limits make a fast jump to ₹1 crore implausible. They are there so the ₹1 lakh is not treated as a lottery ticket.

On adjusted Yahoo prices for today's Nifty 50, from 12 November 2018 through 12 November 2026, ₹1,00,000 ended at ₹99,839. CAGR was about zero, the deepest drawdown was 17.5%, and 436 closed trades had a profit factor of 1.00. The same rules on ten liquid names ended at ₹99,480. The account was not ruined. It also did not grow. Companies that left the index are missing from that test, so the sample is flattering rather than harsh. Changing the rules until the past looks like a path to ₹1 crore would be fitting history, not a way to get there.

This is not investment advice and it is not a SEBI-registered advisory.

## What it trades

Delivery (CNC) only, on the current Nifty 50. The index has to be above its 200-day average. A stock has to already be in an uptrend: price above a rising 50-day average, and the 50-day above the 200-day. The buy is a pullback to the 20-day average that closes back above it, in the upper half of the day's range, with RSI between 45 and 68, participation at least equal to the 20-day average volume, and the close not extended more than 8% above the 20-day average. Relative strength versus Nifty over 60 sessions has to be positive.

The protective stop is the tighter of the 10-day low and two ATRs under the close. Stops closer than 1.2% or wider than 7% are skipped. The position is sized off the limit, not the close, so a fill at the top of the allowed 4% gap still risks about 0.8% if the stop holds. A gap through the stop is a real loss and can be larger than 0.8%.

After entry the stop trails at 2.5 ATRs under the highest close and moves to the entry price once the trade is 1.5R in favour. A close under the 50-day average, or 20 sessions without an exit, is sold at the next open.

At most two names from the same NSE industry. Buys use cash that was already in the account at the previous close. Sale proceeds from the same morning are not reused. The account cannot borrow.

## Costs

Approximate NSE equity-delivery levies (STT 0.1% on both sides, stamp duty 0.015% on the buy, plus exchange, SEBI, and GST), a ₹15 depository charge on the sell, and 0.05% slippage each side. Brokerage is modelled as zero, which matches many discount brokers on delivery and will not match a broker that charges it. These rates change. They are a model, not a tax opinion. Edit `Config` in `src/nse_swing/config.py` if your broker's schedule is different.

The backtest fills a stop at the stop, or at the open if price gaps through it. The order sheet uses a limit or an SL-limit. If the open runs through that limit, you have to replace the order. The sheet is not a matching engine.

## Run it

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
pytest

nse-swing goal
nse-swing backtest --demo
nse-swing backtest --years 8 --universe liquid10
nse-swing backtest --years 8
nse-swing scan --capital 100000
nse-swing paper --capital 100000
```

`goal` prints the table above.

`backtest` replays the rules on downloaded NSE history. `--demo` uses a seeded random market and is not an NSE result. `--universe liquid10` is ten of the Nifty names, for a shorter download. The full run is today's Nifty 50.

`scan` sizes the next open for a flat account and forgets it. `paper` starts a model portfolio on the day you first run it. Later runs replay from that date and assume every order was filled at the next open. If you skip a trade, this ledger is not your broker account. Pass `--reset` to start again. `--capital` on an existing book is ignored until you reset.

Prices come from Yahoo Finance (`*.NS`, and `^NSEI` for the index), adjusted for splits and dividends, cached under `data/cache/`. That is not an NSE feed. Rupee profit and loss across a corporate action is an approximation. A backtest of today's Nifty 50 members leaves out companies that dropped out of the index. That survivorship bias pushes old results up. A printed CAGR is not a promise that it continues, and resampling those days does not create an edge that was not there.

After the cash close, the daily command is:

```bash
nse-swing paper
```

Read the next-session block. Place what you still want, yourself, as CNC limit or SL-limit orders. Nothing in this program opens a socket to a broker.

## If you later automate the orders

SEBI's retail algo framework (circular of 4 February 2025, applicable to brokers from 1 April 2026) expects API orders to go through the broker's risk checks and to carry an exchange algo id. Algo market orders are not allowed. A few delivery orders a day is far below the 10-orders-per-second registration threshold described in the NSE circulars, but the threshold and the process belong to your broker and the exchange. Confirm the current rule before you connect an API. This repository does not place orders.

## Layout

- `src/nse_swing/strategy.py` — pullback rules
- `src/nse_swing/risk.py` — size, heat, drawdown halt
- `src/nse_swing/backtest.py` — next-open fills, no borrowing
- `src/nse_swing/goal.py` — required CAGR and resampled paths
- `src/nse_swing/universe.py` — Nifty 50 members as published for late 2026

Update `NIFTY_50` when the index changes. Leaving it stale makes the survivorship problem worse.
