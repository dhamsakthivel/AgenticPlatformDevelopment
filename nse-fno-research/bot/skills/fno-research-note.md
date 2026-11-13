---
description: Use when the user wants an NSE F&O research note, a trend view, or a comparison of underlyings.
---

# F&O research note

1. If the user names no symbol, ask for one. Indexes: NIFTY, BANKNIFTY, FINNIFTY, MIDCPNIFTY. Stocks use the NSE ticker, such as RELIANCE.
2. Call `market_technicals` once per symbol.
3. Call `published_results` when the symbol is a stock, or when the user asks about results or earnings. Do not call it for an index.
4. Write the note in this shape. Use only tool figures.

## <name> (<symbol>)

Bias: <trend>
As of: <asOf>. <priceDelay>

Last: <price> <currency> (<dayChangePercent>% on the day)
Range: day <dayLow>–<dayHigh>, 20 sessions <range20.low>–<range20.high>, 52-week <fiftyTwoWeekLow>–<fiftyTwoWeekHigh>
Technicals: SMA20 <sma20>, SMA50 <sma50>, RSI14 <rsi14>, 5-session return <return5d>%, 21-session return <return21d>%, 63-session return <return63d>%, 20-session realized vol <realizedVol20d>%
Results: for a stock, list each quarter with EPS actual, EPS estimate, and surprisePercent, then the next earnings date and whether that date is an estimate. For an index, say company results do not apply.
Outside this feed: option chain, open interest, put-call ratio, futures basis, lot size, and the F&O ban list.
What would change the view: one sentence tied to SMA20 or the 20-session range.

This is research context, not investment advice or a trade recommendation.

5. If `corporateAction` is set, quote it before the levels.
6. Compare symbols only after `market_technicals` has returned for each one.
7. Call `market_technicals` for INDIAVIX only when the user asks about volatility.
